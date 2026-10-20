/**
 *  Gestionnaire Thermique Intelligent : Équilibrage Delta T & Point de Rosée
 *  - Brasse l'air (fanOn) si l'écart entre les pièces dépasse le seuil (sauf si le thermostat est sur OFF).
 *  - Alerte par SMS quand ouvrir/refermer les fenêtres selon la température et le point de rosée (sans capteurs de fenêtres).
 */
definition(
    name: "Gestionnaire Thermique (Delta T & Point de Rosée)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Équilibrage thermique inter-pièces et alertes point de rosée.",
    category: "Green Living",
    importUrl: "https://raw.githubusercontent.com/VOTRE_UTILISATEUR/VOTRE_REPO/main/ThermalManagerApp.groovy",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Configuration Thermique & Point de Rosée", install: true, uninstall: true) {
        section("<b>1. Capteurs Intérieurs et Thermostat</b>") {
            input "indoorTempSensors", "capability.temperatureMeasurement", title: "Sondes de température intérieures (calcul Delta T et moyenne)", multiple: true, required: true
            input "thermostat", "capability.thermostat", title: "Thermostat principal (Ecobee)", multiple: false, required: true
        }
        section("<b>2. Équilibrage Thermique (Brassage d'air Delta T)</b>") {
            input "enableDeltaT", "bool", title: "Activer le brassage d'air automatique (Delta T)", defaultValue: true
            input "deltaThresholdOn", "decimal", title: "Écart d'activation du ventilateur (°C)", defaultValue: 2.5, required: true
            input "deltaThresholdOff", "decimal", title: "Écart d'arrêt - Hystérésis (°C)", defaultValue: 1.5, required: true
            input "maxRunMinutes", "number", title: "Durée maximale d'un cycle continu (minutes)", defaultValue: 45, required: true
            input "cooldownMinutes", "number", title: "Pause entre deux cycles max (minutes)", defaultValue: 30, required: true
            input "disableSwitch", "capability.switch", title: "Interrupteur pour bloquer le brassage (ex: Pointe HQ)", multiple: false, required: false
        }
        section("<b>3. Conseiller Fenêtres & Point de Rosée (Sans capteurs de fenêtres)</b>") {
            input "enableAdvisor", "bool", title: "Activer les alertes SMS d'ouverture/fermeture des fenêtres", defaultValue: true
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Sonde de température extérieure (ou météo Ecobee)", multiple: false, required: false
            input "outdoorHumSensor", "capability.relativeHumidityMeasurement", title: "Sonde d'humidité extérieure", multiple: false, required: false
            input "coolTargetTemp", "decimal", title: "Seuil intérieur au-dessus duquel on veut rafraîchir (°C)", defaultValue: 22.5, required: true
            input "heatTargetTemp", "decimal", title: "Seuil intérieur en-dessous duquel on veut réchauffer (°C)", defaultValue: 20.5, required: true
            input "minDeltaTemp", "decimal", title: "Avantage minimal Extérieur vs Intérieur requis (°C)", defaultValue: 1.5, required: true
            input "maxDewPointOpen", "decimal", title: "Point de rosée extérieur MAX pour suggérer d'ouvrir (°C)", defaultValue: 15.5, required: true
            input "maxDewPointClose", "decimal", title: "Point de rosée extérieur MAX avant alerte de refermer (°C)", defaultValue: 17.0, required: true
            input "minOutdoorTemp", "decimal", title: "Température extérieure minimale tolérée avant de refermer (°C)", defaultValue: 14.0, required: true
        }
        section("<b>4. Notifications & Heures Silencieuses</b>") {
            input "notifiers", "capability.notification", title: "Appareil de notification (ex: Voip.ms SMS)", multiple: true, required: false
            input "quietStart", "time", title: "Début de la période silencieuse (ex: 22h00)", required: false
            input "quietEnd", "time", title: "Fin de la période silencieuse (ex: 07h00)", required: false
        }
        section("<b>5. Diagnostic</b>") {
            input "logEnable", "bool", title: "Activer les logs détaillés", defaultValue: true
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (state.fanForcedByApp == null) state.fanForcedByApp = false
    if (state.inCooldown == null) state.inCooldown = false
    if (state.windowWindowState == null) state.windowWindowState = "CLOSED_RECOMMENDED"

    subscribe(indoorTempSensors, "temperature", "evaluateAll")
    subscribe(thermostat, "thermostatMode", "evaluateAll")
    if (disableSwitch) subscribe(disableSwitch, "switch", "evaluateAll")
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", "evaluateAll")
    if (outdoorHumSensor) subscribe(outdoorHumSensor, "humidity", "evaluateAll")

    runEvery10Minutes("evaluateAll")
    evaluateAll()
}

def evaluateAll(evt = null) {
    def readings = []
    indoorTempSensors.each { sensor ->
        def val = sensor.currentValue("temperature")
        if (val != null) {
            readings << [name: sensor.displayName, temp: val as BigDecimal]
        }
    }
    if (!readings) return

    if (enableDeltaT && readings.size() >= 2) {
        evaluateDeltaT(readings)
    }
    if (enableAdvisor && outdoorTempSensor && outdoorHumSensor && notifiers) {
        evaluateWindowAdvisor(readings)
    }
}

// ==========================================
// VOLET 1 : ÉQUILIBRAGE THERMIQUE (DELTA T)
// ==========================================
def evaluateDeltaT(List readings) {
    def tstatMode = thermostat.currentValue("thermostatMode")
    if (tstatMode == "off") {
        stopCirculation("Thermostat en mode OFF (porte patio ou fenêtres ouvertes)")
        return
    }

    if (disableSwitch && disableSwitch.currentValue("switch") == "on") {
        stopCirculation("Interrupteur de blocage actif")
        return
    }

    def minReading = readings.min { it.temp }
    def maxReading = readings.max { it.temp }
    BigDecimal delta = (maxReading.temp - minReading.temp).setScale(2, BigDecimal.ROUND_HALF_UP)

    state.lastDelta = delta
    state.coldestRoom = "${minReading.name} (${minReading.temp}°C)"
    state.warmestRoom = "${maxReading.name} (${maxReading.temp}°C)"

    if (logEnable) log.debug "[Delta T] Écart: ${delta}°C | Min: ${state.coldestRoom} | Max: ${state.warmestRoom}"

    if (delta >= (deltaThresholdOn as BigDecimal)) {
        if (!state.inCooldown) {
            startCirculation(delta)
        }
    } else if (delta <= (deltaThresholdOff as BigDecimal)) {
        stopCirculation("Écart revenu sous le seuil (${delta}°C)")
    }
}

def startCirculation(delta) {
    if (!state.fanForcedByApp) {
        log.info "[Delta T] Activation du brassage (Écart = ${delta}°C entre ${state.coldestRoom} et ${state.warmestRoom})"
        state.fanForcedByApp = true
        if (thermostat.currentValue("thermostatFanMode") != "on") {
            thermostat.fanOn()
        }
        int maxSec = (maxRunMinutes ?: 45) * 60
        runIn(maxSec, "maxRunTimeReached")
    }
}

def stopCirculation(reason) {
    if (state.fanForcedByApp) {
        log.info "[Delta T] Arrêt du brassage (${reason}) -> Retour en fanAuto"
        state.fanForcedByApp = false
        unschedule("maxRunTimeReached")
        thermostat.fanAuto()
    }
}

def maxRunTimeReached() {
    if (state.fanForcedByApp) {
        stopCirculation("Durée maximale de ${maxRunMinutes} min atteinte")
        state.inCooldown = true
        int coolSec = (cooldownMinutes ?: 30) * 60
        runIn(coolSec, "clearCooldown")
    }
}

def clearCooldown() {
    state.inCooldown = false
    evaluateAll()
}

// ==========================================
// VOLET 2 : CONSEILLER POINT DE ROSÉE
// ==========================================
def evaluateWindowAdvisor(List readings) {
    BigDecimal tIn = (readings.collect { it.temp }.sum() / readings.size()).setScale(1, BigDecimal.ROUND_HALF_UP)

    def rawOutTemp = outdoorTempSensor.currentValue("temperature")
    def rawOutHum = outdoorHumSensor.currentValue("humidity")
    if (rawOutTemp == null || rawOutHum == null) return

    BigDecimal tOut = (rawOutTemp as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
    BigDecimal rhOut = (rawOutHum as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
    BigDecimal dewPoint = calculateDewPoint(tOut, rhOut)

    state.weatherSummary = "Int: ${tIn}°C | Ext: ${tOut}°C (${rhOut}% RH) | Point de rosée: ${dewPoint}°C | État: ${state.windowWindowState}"
    if (logEnable) log.debug "[Conseiller] ${state.weatherSummary}"

    BigDecimal delta = (minDeltaTemp as BigDecimal)

    if (state.windowWindowState == "CLOSED_RECOMMENDED") {
        if (tIn >= (coolTargetTemp as BigDecimal) &&
            tOut <= (tIn - delta) &&
            tOut >= (minOutdoorTemp as BigDecimal) &&
            dewPoint <= (maxDewPointOpen as BigDecimal)) {

            if (sendNotificationIfAllowed("Free-Cooling : Il fait ${tOut}°C dehors (point de rosée sec à ${dewPoint}°C) contre ${tIn}°C à l'intérieur. Ouvrez les fenêtres pour rafraîchir gratuitement.")) {
                state.windowWindowState = "OPEN_FOR_COOLING"
            }
            return
        }

        if (tIn <= (heatTargetTemp as BigDecimal) &&
            tOut >= (tIn + delta) &&
            dewPoint <= (maxDewPointOpen as BigDecimal)) {

            if (sendNotificationIfAllowed("Chauffage passif : Il fait ${tOut}°C dehors contre ${tIn}°C à l'intérieur (rosée ${dewPoint}°C). Ouvrez les fenêtres pour réchauffer gratuitement.")) {
                state.windowWindowState = "OPEN_FOR_HEATING"
            }
            return
        }
    }

    if (state.windowWindowState == "OPEN_FOR_COOLING") {
        if (dewPoint >= (maxDewPointClose as BigDecimal)) {
            if (sendNotificationIfAllowed("Fin Free-Cooling (Humidité) : Le point de rosée extérieur a grimpé à ${dewPoint}°C (${rhOut}%). Refermez les fenêtres.")) {
                state.windowWindowState = "CLOSED_RECOMMENDED"
            }
        } else if (tOut >= (tIn - 0.2)) {
            if (sendNotificationIfAllowed("Fin Free-Cooling (Chaleur) : Il fait maintenant ${tOut}°C dehors (int: ${tIn}°C). Refermez les fenêtres pour garder la fraîcheur.")) {
                state.windowWindowState = "CLOSED_RECOMMENDED"
            }
        } else if (tOut < (minOutdoorTemp as BigDecimal)) {
            if (sendNotificationIfAllowed("Fin Free-Cooling (Fraîcheur) : La température extérieure est descendue à ${tOut}°C. Refermez les fenêtres.")) {
                state.windowWindowState = "CLOSED_RECOMMENDED"
            }
        }
    }

    if (state.windowWindowState == "OPEN_FOR_HEATING") {
        if (tOut <= (tIn + 0.2)) {
            if (sendNotificationIfAllowed("Fin Chauffage passif : La température extérieure est redescendue à ${tOut}°C (int: ${tIn}°C). Refermez les fenêtres pour conserver la chaleur.")) {
                state.windowWindowState = "CLOSED_RECOMMENDED"
            }
        } else if (dewPoint >= (maxDewPointClose as BigDecimal)) {
            if (sendNotificationIfAllowed("Humidité extérieure élevée (rosée à ${dewPoint}°C). Refermez les fenêtres.")) {
                state.windowWindowState = "CLOSED_RECOMMENDED"
            }
        }
    }
}

BigDecimal calculateDewPoint(BigDecimal tempC, BigDecimal rh) {
    double t = tempC.toDouble()
    double r = Math.max(1.0d, Math.min(100.0d, rh.toDouble()))
    double a = 17.625d
    double b = 243.04d
    double alpha = ((a * t) / (b + t)) + Math.log(r / 100.0d)
    double dp = (b * alpha) / (a - alpha)
    return new BigDecimal(dp).setScale(1, BigDecimal.ROUND_HALF_UP)
}

boolean sendNotificationIfAllowed(String message) {
    if (isQuietTime()) {
        if (logEnable) log.debug "Notification repoussée (période silencieuse) : ${message}"
        return false
    }
    log.info "Envoi SMS : ${message}"
    notifiers.each { it.deviceNotification(message) }
    return true
}

boolean isQuietTime() {
    if (!quietStart || !quietEnd) return false
    def start = timeToday(quietStart, location.timeZone)
    def end = timeToday(quietEnd, location.timeZone)
    def now = new Date()
    return start.before(end) ? (now.after(start) && now.before(end)) : (now.after(start) || now.before(end))
}
