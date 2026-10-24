definition(
    name: "Gestionnaire Thermique (Delta T & Point de Rosée)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Analyse le Delta T et le Point de Rosée (via capteurs intérieurs et Meteo2 OpenWeatherMap) avec exclusion automatique des capteurs muets, suggestions d'ouverture des fenêtres par SMS et contrôle du ventilateur.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Configuration du Gestionnaire Thermique", install: true, uninstall: true) {
        
        // Affichage d'un résumé en direct si les appareils sont déjà configurés
        if (settings.outdoorWeather && settings.indoorTempSensors) {
            section("📊 État Thermique Actuel (En direct)") {
                Map ext = getOutdoorWeatherData(settings.outdoorWeather)
                Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)
                
                if (ext && ind) {
                    BigDecimal deltaT = (ind.temperature - ext.temperature).setScale(2, BigDecimal.ROUND_HALF_UP)
                    BigDecimal deltaDew = (ind.dewPoint - ext.dewPoint).setScale(2, BigDecimal.ROUND_HALF_UP)
                    
                    String statusHtml = "<b>Intérieur (Moyenne sur ${ind.activeCount} capteur(s)) :</b> ${ind.temperature} °C | Humidité : ${ind.humidity} % | Point de rosée : <b>${ind.dewPoint} °C</b><br>"
                    statusHtml += "<b>Extérieur (${settings.outdoorWeather.displayName}) :</b> ${ext.temperature} °C | Humidité : ${ext.humidity ?: 'N/A'} % | Point de rosée : <b>${ext.dewPoint} °C</b> (${ext.dewSource})<br>"
                    statusHtml += "<b>Écarts (Int - Ext) :</b> Delta T = <b>${deltaT} °C</b> | Delta Point de rosée = <b>${deltaDew} °C</b><br>"
                    statusHtml += "<b>État actuel de la recommandation :</b> <span style='color:blue; font-weight:bold;'>${state.currentAdvice ?: 'En attente du prochain cycle'}</span>"
                    
                    if (ind.excludedNames && ind.excludedNames.size() > 0) {
                        statusHtml += "<br><span style='color:red; font-weight:bold;'>⚠ Capteur(s) intérieur(s) exclu(s) temporairement (muets/HS) : ${ind.excludedNames.join(', ')}</span>"
                    } else {
                        statusHtml += "<br><span style='color:green;'>✓ Tous les capteurs intérieurs répondent normalement.</span>"
                    }
                    paragraph statusHtml
                } else {
                    paragraph "<span style='color:red; font-weight:bold;'>⚠ Impossible de calculer l'état thermique : Meteo2 ou tous les capteurs intérieurs sont muets.</span>"
                }
            }
        }

        section("1. Météo Extérieure (OpenWeatherMap / Meteo2)") {
            input "outdoorWeather", "capability.temperatureMeasurement", title: "Dispositif Météo Extérieur (ex: Meteo2)", required: true, multiple: false, submitOnChange: true
        }
        
        section("2. Capteurs Intérieurs (Moyenne automatique)") {
            input "indoorTempSensors", "capability.temperatureMeasurement", title: "Capteurs de Température intérieurs", required: true, multiple: true, submitOnChange: true
            input "indoorHumSensors", "capability.relativeHumidityMeasurement", title: "Capteurs d'Humidité intérieurs", required: false, multiple: true, submitOnChange: true
        }
        
        section("3. Sécurité & Exclusion automatique des capteurs HS") {
            input "maxIndoorInactivityHours", "number", title: "Capteurs INTÉRIEURS : exclure temporairement si aucune communication depuis (heures)", defaultValue: 4, required: true
            input "maxWeatherInactivityHours", "number", title: "Météo EXTÉRIEURE (Meteo2) : suspendre les calculs si l'API est muette depuis (heures)", defaultValue: 3, required: true
            input "notifyOnStaleSensor", "bool", title: "Envoyer une alerte SMS (1 seule fois) quand un capteur intérieur décroche ou se rebranche ?", defaultValue: true
        }
        
        section("4. Seuils de Décision (Ouverture des fenêtres & Free Cooling)") {
            input "minIndoorTempForCooling", "decimal", title: "Température intérieure minimale pour suggérer d'ouvrir/rafraîchir (°C)", defaultValue: 22.5, required: true
            input "minDeltaT", "decimal", title: "Delta T minimal requis (Temp Intérieure - Temp Extérieure en °C) pour ouvrir", defaultValue: 1.5, required: true
            input "maxOutdoorDewPoint", "decimal", title: "Point de rosée extérieur maximal toléré (°C) [Anti-moiteur : ex. 16.0 °C]", defaultValue: 16.0, required: true
            input "requireLowerOutdoorDewPoint", "bool", title: "Exiger aussi que le point de rosée extérieur soit inférieur au point de rosée intérieur ?", defaultValue: true
            input "hysteresisTemp", "decimal", title: "Hystérésis (°C) avant de suggérer de refermer les fenêtres", defaultValue: 0.5, required: true
        }

        section("5. Contrôle Automatique de la Ventilation (Optionnel)") {
            input "thermostatDevice", "capability.thermostat", title: "Thermostat (ex: Ecobee - passera de fanAuto à fanOn pendant le refroidissement favorable)", required: false, multiple: false
            input "fanSwitch", "capability.switch", title: "Interrupteur / Relais de ventilateur ou échangeur d'air (ON/OFF)", required: false, multiple: false
            input "contactSensors", "capability.contactSensor", title: "Capteurs d'ouverture de fenêtres/portes (Optionnel : n'active le ventilateur que si au moins 1 fenêtre est ouverte)", required: false, multiple: true
        }

        section("6. Notifications SMS (Voip.ms) & Fréquence") {
            input "smsDevice", "capability.notification", title: "Appareil de notification SMS (Voip.ms)", required: false, multiple: false
            input "allowedModes", "mode", title: "Envoyer les SMS uniquement dans ces modes (vide = tous les modes)", required: false, multiple: true
            input "minMinutesBetweenSms", "number", title: "Délai minimal entre deux recommandations SMS d'ouverture/fermeture (minutes)", defaultValue: 120, required: true
            input "evalIntervalMinutes", "enum", title: "Fréquence d'évaluation périodique", options: ["5": "Toutes les 5 minutes", "10": "Toutes les 10 minutes", "15": "Toutes les 15 minutes", "30": "Toutes les 30 minutes"], defaultValue: "15", required: true
            input "runEvalNow", "bool", title: "Cochez pour forcer une évaluation immédiate", defaultValue: false, submitOnChange: true
        }

        if (settings.runEvalNow) {
            app.updateSetting("runEvalNow", [value: "false", type: "bool"])
            evaluateThermalConditions()
            section { paragraph "<span style='color:green; font-weight:bold;'>✓ Évaluation manuelle exécutée !</span>" }
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unschedule()
    unsubscribe()
    initialize()
}

def initialize() {
    if (state.currentlyFavorable == null) state.currentlyFavorable = false
    if (state.currentAdvice == null) state.currentAdvice = "Initialisé"
    if (state.alertedStaleDevices == null) state.alertedStaleDevices = []

    // Abonnements aux événements
    subscribe(outdoorWeather, "temperature", eventHandler)
    subscribe(outdoorWeather, "humidity", eventHandler)
    subscribe(outdoorWeather, "dewpoint", eventHandler)
    subscribe(indoorTempSensors, "temperature", eventHandler)
    if (indoorHumSensors) subscribe(indoorHumSensors, "humidity", eventHandler)
    if (contactSensors) subscribe(contactSensors, "contact", eventHandler)

    // Planification périodique (indispensable pour détecter un capteur qui cesse d'émettre)
    String interval = settings.evalIntervalMinutes ?: "15"
    if (interval == "5") runEvery5Minutes(evaluateThermalConditions)
    else if (interval == "10") runEvery10Minutes(evaluateThermalConditions)
    else if (interval == "30") runEvery30Minutes(evaluateThermalConditions)
    else runEvery15Minutes(evaluateThermalConditions)

    evaluateThermalConditions()
    log.info "[ThermalManager] Initialisé avec succès (Intervalle: ${interval} min)."
}

def eventHandler(evt) {
    evaluateThermalConditions()
}

// --- LOGIQUE PRINCIPALE D'ÉVALUATION ---
def evaluateThermalConditions() {
    Map ext = getOutdoorWeatherData(settings.outdoorWeather)
    Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)

    if (!ext || !ind) {
        log.warn "[ThermalManager] Évaluation annulée : données extérieures ou intérieures indisponibles."
        return
    }

    // Gestion des alertes de capteurs intérieurs muets / rétablis
    handleStaleSensorNotifications(ind.excludedNames)

    BigDecimal inTemp = ind.temperature
    BigDecimal inDew = ind.dewPoint
    BigDecimal extTemp = ext.temperature
    BigDecimal extDew = ext.dewPoint

    BigDecimal deltaT = (inTemp - extTemp).setScale(2, BigDecimal.ROUND_HALF_UP)
    BigDecimal deltaDew = (inDew - extDew).setScale(2, BigDecimal.ROUND_HALF_UP)

    BigDecimal minInTemp = (settings.minIndoorTempForCooling ?: 22.5) as BigDecimal
    BigDecimal reqDeltaT = (settings.minDeltaT ?: 1.5) as BigDecimal
    BigDecimal maxExtDew = (settings.maxOutdoorDewPoint ?: 16.0) as BigDecimal
    BigDecimal hyst = (settings.hysteresisTemp ?: 0.5) as BigDecimal
    boolean checkDewDiff = (settings.requireLowerOutdoorDewPoint != false)

    boolean wasFavorable = state.currentlyFavorable ?: false
    boolean isFavorableNow = false

    if (!wasFavorable) {
        // Conditions strictes pour ACTIVER la recommandation d'ouvrir les fenêtres
        boolean tempHotEnough = (inTemp >= minInTemp)
        boolean coolerOutside = (deltaT >= reqDeltaT)
        boolean dryEnoughOutside = (extDew <= maxExtDew) && (!checkDewDiff || (extDew < inDew))

        isFavorableNow = (tempHotEnough && coolerOutside && dryEnoughOutside)
    } else {
        // Conditions avec hystérésis pour MAINTENIR l'ouverture sans effet yo-yo
        boolean stillWarmInside = (inTemp >= (minInTemp - hyst))
        boolean stillCoolerOutside = (deltaT >= (reqDeltaT - hyst))
        boolean stillDryOutside = (extDew <= (maxExtDew + hyst)) && (!checkDewDiff || (extDew <= (inDew + hyst)))

        isFavorableNow = (stillWarmInside && stillCoolerOutside && stillDryOutside)
    }

    // Changement d'état (Transition Favorable <-> Défavorable)
    if (isFavorableNow && !wasFavorable) {
        state.currentlyFavorable = true
        state.currentAdvice = "OUVRIR LES FENÊTRES (Air extérieur frais et sec)"
        String msg = "[Thermique] Ouvrir les fenetres : Int ${inTemp}C (PR ${inDew}C) vs Ext ${extTemp}C (PR ${extDew}C, DeltaT -${deltaT}C)."
        if (ind.excludedNames) msg += " [Exclu: ${ind.excludedNames.join(',')}]"
        sendRecommendationSms(msg)
    } else if (!isFavorableNow && wasFavorable) {
        state.currentlyFavorable = false
        state.currentAdvice = "FERMER LES FENÊTRES (Extérieur trop chaud ou humide)"
        String msg = "[Thermique] Fermer les fenetres : Int ${inTemp}C (PR ${inDew}C) vs Ext ${extTemp}C (PR ${extDew}C)."
        sendRecommendationSms(msg)
    } else {
        state.currentAdvice = isFavorableNow ? "OUVRIR LES FENÊTRES (Maintien)" : "FERMER LES FENÊTRES (Maintien)"
    }

    // Pilotage de la ventilation (Thermostat Ecobee / Ventilateur)
    manageVentilation(isFavorableNow)
}

// --- PILOTAGE DU VENTILATEUR ---
def manageVentilation(boolean favorable) {
    boolean windowsOpen = true
    if (settings.contactSensors) {
        windowsOpen = settings.contactSensors.any { it.currentValue("contact") == "open" }
    }

    boolean shouldRunFan = (favorable && windowsOpen)

    if (shouldRunFan) {
        if (settings.thermostatDevice && settings.thermostatDevice.currentValue("thermostatFanMode") != "on") {
            settings.thermostatDevice.fanOn()
            state.fanControlledByApp = true
            log.info "[ThermalManager] Ventilateur du thermostat activé (fanOn)."
        }
        if (settings.fanSwitch && settings.fanSwitch.currentValue("switch") != "on") {
            settings.fanSwitch.on()
            state.fanControlledByApp = true
            log.info "[ThermalManager] Interrupteur de ventilation activé (ON)."
        }
    } else if (state.fanControlledByApp) {
        if (settings.thermostatDevice && settings.thermostatDevice.currentValue("thermostatFanMode") != "auto") {
            settings.thermostatDevice.fanAuto()
            log.info "[ThermalManager] Ventilateur du thermostat remis en mode automatique (fanAuto)."
        }
        if (settings.fanSwitch && settings.fanSwitch.currentValue("switch") != "off") {
            settings.fanSwitch.off()
            log.info "[ThermalManager] Interrupteur de ventilation désactivé (OFF)."
        }
        state.fanControlledByApp = false
    }
}

// --- VALIDATION ET EXCLUSION AUTOMATIQUE DES CAPTEURS ---
boolean isDeviceResponsive(dev, int maxHours) {
    if (!dev) return false

    if (dev.hasAttribute("healthStatus") && dev.currentValue("healthStatus") == "offline") {
        log.warn "[ThermalManager] ${dev.displayName} ignoré (healthStatus = offline)."
        return false
    }

    Date lastAct = dev.getLastActivity()
    if (!lastAct) {
        log.warn "[ThermalManager] ${dev.displayName} ignoré (aucune date d'activité connue)."
        return false
    }

    long diffMs = now() - lastAct.time
    long maxMs = maxHours * 3600000L
    if (diffMs > maxMs) {
        BigDecimal hoursAgo = (diffMs / 3600000.0).setScale(1, BigDecimal.ROUND_HALF_UP)
        log.warn "[ThermalManager] ${dev.displayName} exclu temporairement : muet depuis ${hoursAgo} h (seuil: ${maxHours} h)."
        return false
    }
    return true
}

Map getOutdoorWeatherData(weatherDev) {
    int maxHours = (settings.maxWeatherInactivityHours ?: 3) as int
    if (!isDeviceResponsive(weatherDev, maxHours)) {
        log.error "[ThermalManager] Météo extérieure (${weatherDev?.displayName}) muette depuis plus de ${maxHours} h."
        return null
    }

    def rawTemp = weatherDev.currentValue("temperature")
    def rawHum = weatherDev.currentValue("humidity")
    if (rawTemp == null) return null

    BigDecimal extTemp = rawTemp as BigDecimal
    BigDecimal extHum = (rawHum != null) ? (rawHum as BigDecimal) : 50.0

    // Lecture directe de l'attribut 'dewpoint' de OpenWeatherMap-Alerts Weather Driver (ou calcul Magnus en secours)
    def rawDew = weatherDev.currentValue("dewpoint")
    if (rawDew == null) rawDew = weatherDev.currentValue("dewPoint")

    BigDecimal extDewPoint = null
    String source = ""
    if (rawDew != null) {
        extDewPoint = (rawDew as BigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP)
        source = "attribut direct OpenWeatherMap"
    } else {
        extDewPoint = calculateDewPoint(extTemp, extHum)
        source = "calculé via Magnus"
    }

    return [
        temperature: extTemp,
        humidity: extHum,
        dewPoint: extDewPoint,
        dewSource: source
    ]
}

Map getIndoorAverageData(tempSensors, humSensors) {
    int maxHours = (settings.maxIndoorInactivityHours ?: 4) as int
    List<BigDecimal> validTemps = []
    List<BigDecimal> validHums = []
    List<String> excludedDevices = []

    tempSensors?.each { dev ->
        if (isDeviceResponsive(dev, maxHours) && dev.currentValue("temperature") != null) {
            BigDecimal t = dev.currentValue("temperature") as BigDecimal
            if (t >= 5.0 && t <= 45.0) {
                validTemps << t
            } else {
                excludedDevices << "${dev.displayName} (val aberrante: ${t}C)"
            }
        } else {
            excludedDevices << dev.displayName
        }
    }

    humSensors?.each { dev ->
        if (isDeviceResponsive(dev, maxHours) && dev.currentValue("humidity") != null) {
            BigDecimal h = dev.currentValue("humidity") as BigDecimal
            if (h >= 5.0 && h <= 99.0) {
                validHums << h
            }
        }
    }

    if (validTemps.isEmpty()) {
        log.error "[ThermalManager] Aucun capteur de température intérieur valide disponible !"
        return null
    }

    BigDecimal avgTemp = (validTemps.sum() / validTemps.size()).setScale(2, BigDecimal.ROUND_HALF_UP)
    BigDecimal avgHum = validHums.isEmpty() ? 50.0 : (validHums.sum() / validHums.size()).setScale(1, BigDecimal.ROUND_HALF_UP)
    BigDecimal inDewPoint = calculateDewPoint(avgTemp, avgHum)

    return [
        temperature: avgTemp,
        humidity: avgHum,
        dewPoint: inDewPoint,
        activeCount: validTemps.size(),
        excludedNames: excludedDevices.unique()
    ]
}

// Formule de Magnus-Tetens pour le calcul du point de rosée (°C)
BigDecimal calculateDewPoint(BigDecimal tempC, BigDecimal relHum) {
    double t = tempC as double
    double rh = Math.max(1.0d, Math.min(100.0d, relHum as double))
    double a = 17.62d
    double b = 243.12d
    double alpha = ((a * t) / (b + t)) + Math.log(rh / 100.0d)
    double dp = (b * alpha) / (a - alpha)
    return new BigDecimal(dp).setScale(2, BigDecimal.ROUND_HALF_UP)
}

// --- ALERTES SMS (CAPTEURS MUETS & RECOMMANDATIONS) ---
def handleStaleSensorNotifications(List<String> currentExcluded) {
    if (!settings.notifyOnStaleSensor || !settings.smsDevice) return

    List<String> previouslyAlerted = state.alertedStaleDevices ?: []

    // Nouveaux capteurs qui viennent de tomber hors ligne
    List<String> newlyStale = currentExcluded - previouslyAlerted
    if (newlyStale.size() > 0) {
        String msg = "[Thermique] Capteur(s) exclu(s) temporairement (muet > ${settings.maxIndoorInactivityHours}h) : ${newlyStale.join(', ')}"
        settings.smsDevice.deviceNotification(msg)
        log.warn msg
    }

    // Capteurs qui étaient hors ligne et qui viennent de se rebrancher
    List<String> recovered = previouslyAlerted - currentExcluded
    if (recovered.size() > 0) {
        String msg = "[Thermique] Capteur(s) rebranche(s) et reintegre(s) aux calculs : ${recovered.join(', ')}"
        settings.smsDevice.deviceNotification(msg)
        log.info msg
    }

    state.alertedStaleDevices = currentExcluded
}

def sendRecommendationSms(String message) {
    log.info message
    if (!settings.smsDevice) return

    if (settings.allowedModes && !settings.allowedModes.contains(location.currentMode)) {
        log.info "[ThermalManager] SMS non envoyé (Mode actuel ${location.currentMode} hors des modes autorisés)."
        return
    }

    long minWaitMs = ((settings.minMinutesBetweenSms ?: 120) as long) * 60000L
    long lastSent = state.lastSmsTimestamp ?: 0L

    if ((now() - lastSent) >= minWaitMs) {
        settings.smsDevice.deviceNotification(message)
        state.lastSmsTimestamp = now()
    } else {
        log.info "[ThermalManager] SMS anti-spam bloqué (dernier SMS envoyé il y a moins de ${settings.minMinutesBetweenSms} min)."
    }
}
