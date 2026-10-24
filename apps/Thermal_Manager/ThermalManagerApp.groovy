definition(
    name: "Gestionnaire Thermique (Delta T, Rosée & Présence 4 Membres)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Analyse Delta T et Point de rosée avec exclusion des capteurs HS, contrôle ventilateur, et routage des SMS pour 4 membres de la famille.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Configuration du Gestionnaire Thermique", install: true, uninstall: true) {
        
        if (settings.outdoorWeather && settings.indoorTempSensors) {
            section("📊 État Thermique Actuel (En direct)") {
                Map ext = getOutdoorWeatherData(settings.outdoorWeather)
                Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)
                
                if (ext && ind) {
                    BigDecimal deltaT = (ind.temperature - ext.temperature).setScale(2, BigDecimal.ROUND_HALF_UP)
                    BigDecimal deltaDew = (ind.dewPoint - ext.dewPoint).setScale(2, BigDecimal.ROUND_HALF_UP)
                    List activeRecipients = getActiveSmsDevices()
                    
                    String statusHtml = "<b>Intérieur (Moyenne sur ${ind.activeCount} capteur(s)) :</b> ${ind.temperature} °C | Humidité : ${ind.humidity} % | Point de rosée : <b>${ind.dewPoint} °C</b><br>"
                    statusHtml += "<small>Détail capteurs : ${ind.detailsSummary}</small><br>"
                    statusHtml += "<b>Extérieur (${settings.outdoorWeather.displayName}) :</b> ${ext.temperature} °C | Humidité : ${ext.humidity ?: 'N/A'} % | Point de rosée : <b>${ext.dewPoint} °C</b> (${ext.dewSource})<br>"
                    statusHtml += "<b>Écarts (Int - Ext) :</b> Delta T = <b>${deltaT} °C</b> | Delta Point de rosée = <b>${deltaDew} °C</b><br>"
                    statusHtml += "<b>Destinataires SMS actifs (Présents) :</b> <span style='color:${activeRecipients ? 'green' : 'orange'}; font-weight:bold;'>${activeRecipients ? activeRecipients.size() + ' personne(s) à la maison' : 'Personne (SMS suspendus)'}</span><br>"
                    statusHtml += "<b>État actuel :</b> <span style='color:blue; font-weight:bold;'>${state.currentAdvice ?: 'En attente'}</span>"
                    
                    if (ind.excludedNames && ind.excludedNames.size() > 0) {
                        statusHtml += "<br><span style='color:red;'>⚠ Capteur(s) exclu(s) : ${ind.excludedNames.join(', ')}</span>"
                    }
                    paragraph statusHtml
                } else {
                    paragraph "<span style='color:red;'>⚠ Données météo ou intérieures indisponibles.</span>"
                }
            }
        }

        section("1. Météo Extérieure (OpenWeatherMap / Meteo2)") {
            input "outdoorWeather", "capability.temperatureMeasurement", title: "Dispositif Météo Extérieur (ex: Meteo2)", required: true, multiple: false
        }
        
        section("2. Capteurs Intérieurs") {
            input "indoorTempSensors", "capability.temperatureMeasurement", title: "Capteurs de Température intérieurs", required: true, multiple: true
            input "indoorHumSensors", "capability.relativeHumidityMeasurement", title: "Capteurs d'Humidité intérieurs", required: false, multiple: true
        }

        section("3. Gestion de la Famille (4 Membres) & Routage SMS") {
            paragraph "Associez chaque membre à son capteur de présence et à son pilote Voip.ms :"
            
            paragraph "<b>Membre 1 (Principal)</b>"
            input "pres1", "capability.presenceSensor", title: "Capteur de présence (Benoit)", required: true, multiple: false
            input "sms1", "capability.notification", title: "Pilote SMS Voip.ms (Benoit)", required: true, multiple: false
            
            paragraph "<b>Membre 2 (Optionnel)</b>"
            input "pres2", "capability.presenceSensor", title: "Capteur de présence", required: false, multiple: false
            input "sms2", "capability.notification", title: "Pilote SMS Voip.ms", required: false, multiple: false
            
            paragraph "<b>Membre 3 (Optionnel)</b>"
            input "pres3", "capability.presenceSensor", title: "Capteur de présence", required: false, multiple: false
            input "sms3", "capability.notification", title: "Pilote SMS Voip.ms", required: false, multiple: false

            paragraph "<b>Membre 4 (Optionnel)</b>"
            input "pres4", "capability.presenceSensor", title: "Capteur de présence", required: false, multiple: false
            input "sms4", "capability.notification", title: "Pilote SMS Voip.ms", required: false, multiple: false
        }
        
        section("4. Sécurité & Exclusion automatique des capteurs HS") {
            input "maxIndoorInactivityHours", "number", title: "Capteurs INTÉRIEURS : exclure si muets depuis (heures)", defaultValue: 4, required: true
            input "maxWeatherInactivityHours", "number", title: "Météo EXTÉRIEURE (Meteo2) : suspendre si muette depuis (heures)", defaultValue: 3, required: true
            input "notifyOnStaleSensor", "bool", title: "Alerter par SMS si un capteur décroche ?", defaultValue: true
        }
        
        section("5. Seuils de Décision (Fenêtres & Free Cooling)") {
            input "minIndoorTempForCooling", "decimal", title: "Température intérieure minimale pour suggérer d'ouvrir (°C)", defaultValue: 22.5, required: true
            input "minDeltaT", "decimal", title: "Delta T minimal requis (Int - Ext en °C)", defaultValue: 1.5, required: true
            input "maxOutdoorDewPoint", "decimal", title: "Point de rosée extérieur maximal toléré (°C)", defaultValue: 16.0, required: true
            input "requireLowerOutdoorDewPoint", "bool", title: "Exiger que le PR extérieur soit inférieur au PR intérieur ?", defaultValue: true
            input "hysteresisTemp", "decimal", title: "Hystérésis (°C)", defaultValue: 0.5, required: true
        }

        section("6. Contrôle Automatique de la Ventilation") {
            input "thermostatDevice", "capability.thermostat", title: "Thermostat (ex: Ecobee - passe en fanOn / fanAuto)", required: false, multiple: false
            input "fanSwitch", "capability.switch", title: "Interrupteur / Relais de ventilateur (ON/OFF)", required: false, multiple: false
            input "contactSensors", "capability.contactSensor", title: "Capteurs de fenêtres (N'active le ventilateur que si une fenêtre est ouverte)", required: false, multiple: true
        }

        section("7. Paramètres de Notification SMS & Fréquence") {
            input "allowedModes", "mode", title: "Modes autorisés pour les SMS (laisser vide = tous)", required: false, multiple: true
            input "minMinutesBetweenSms", "number", title: "Délai minimal entre deux SMS (Anti-spam en minutes)", defaultValue: 120, required: true
            input "evalIntervalMinutes", "enum", title: "Fréquence d'évaluation (Intervalle en minutes)", options: ["5": "5 min", "10": "10 min", "15": "15 min", "30": "30 min"], defaultValue: "15", required: true
            input "runEvalNow", "bool", title: "Forcer une évaluation immédiate", defaultValue: false, submitOnChange: true
        }

        if (settings.runEvalNow) {
            app.updateSetting("runEvalNow", [value: "false", type: "bool"])
            evaluateThermalConditions()
            section { paragraph "<span style='color:green;'>✓ Évaluation exécutée avec succès ! Consultez les logs pour voir le détail des capteurs (Temp & Humidité).</span>" }
        }
    }
}

def installed() { initialize() }
def updated() { unschedule(); unsubscribe(); initialize() }

def initialize() {
    if (state.currentlyFavorable == null) state.currentlyFavorable = false
    if (state.currentAdvice == null) state.currentAdvice = "Initialisé"
    if (state.alertedStaleDevices == null) state.alertedStaleDevices = []

    subscribe(outdoorWeather, "temperature", eventHandler)
    subscribe(outdoorWeather, "humidity", eventHandler)
    subscribe(outdoorWeather, "dewpoint", eventHandler)
    subscribe(indoorTempSensors, "temperature", eventHandler)
    if (indoorHumSensors) subscribe(indoorHumSensors, "humidity", eventHandler)
    if (contactSensors) subscribe(contactSensors, "contact", eventHandler)
    
    if (pres1) subscribe(pres1, "presence", eventHandler)
    if (pres2) subscribe(pres2, "presence", eventHandler)
    if (pres3) subscribe(pres3, "presence", eventHandler)
    if (pres4) subscribe(pres4, "presence", eventHandler)

    String interval = settings.evalIntervalMinutes ?: "15"
    if (interval == "5") runEvery5Minutes(evaluateThermalConditions)
    else if (interval == "10") runEvery10Minutes(evaluateThermalConditions)
    else if (interval == "30") runEvery30Minutes(evaluateThermalConditions)
    else runEvery15Minutes(evaluateThermalConditions)

    evaluateThermalConditions()
    log.info "[ThermalManager] Initialisé avec succès (4 membres)."
}

def eventHandler(evt) { evaluateThermalConditions() }

List getActiveSmsDevices() {
    List activeSms = []
    if (sms1 && (!pres1 || pres1.currentValue("presence") == "present")) activeSms << sms1
    if (sms2 && pres2 && pres2.currentValue("presence") == "present") activeSms << sms2
    if (sms3 && pres3 && pres3.currentValue("presence") == "present") activeSms << sms3
    if (sms4 && pres4 && pres4.currentValue("presence") == "present") activeSms << sms4
    return activeSms.unique()
}

def evaluateThermalConditions() {
    Map ext = getOutdoorWeatherData(settings.outdoorWeather)
    Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)

    if (!ext || !ind) {
        log.warn "[ThermalManager] Évaluation impossible : données météo ou intérieures manquantes."
        return
    }

    log.info "[ThermalManager] --- Évaluation Thermique ---"
    log.info "[ThermalManager] Extérieur (${settings.outdoorWeather.displayName}) -> Temp: ${ext.temperature}°C, Hum: ${ext.humidity}%, Point de rosée: ${ext.dewPoint}°C"
    log.info "[ThermalManager] Intérieur (Moyenne Temp: ${ind.temperature}°C, Hum: ${ind.humidity}%, PR: ${ind.dewPoint}°C) | Détails: ${ind.detailsSummary}"
    if (ind.excludedNames) {
        log.warn "[ThermalManager] Capteurs intérieurs exclus/muets : ${ind.excludedNames.join(', ')}"
    }

    handleStaleSensorNotifications(ind.excludedNames)

    BigDecimal inTemp = ind.temperature
    BigDecimal inDew = ind.dewPoint
    BigDecimal extTemp = ext.temperature
    BigDecimal extDew = ext.dewPoint

    BigDecimal deltaT = (inTemp - extTemp).setScale(2, BigDecimal.ROUND_HALF_UP)

    BigDecimal minInTemp = (settings.minIndoorTempForCooling ?: 22.5) as BigDecimal
    BigDecimal reqDeltaT = (settings.minDeltaT ?: 1.5) as BigDecimal
    BigDecimal maxExtDew = (settings.maxOutdoorDewPoint ?: 16.0) as BigDecimal
    BigDecimal hyst = (settings.hysteresisTemp ?: 0.5) as BigDecimal
    boolean checkDewDiff = (settings.requireLowerOutdoorDewPoint != false)

    boolean wasFavorable = state.currentlyFavorable ?: false
    boolean isFavorableNow = false

    if (!wasFavorable) {
        isFavorableNow = (inTemp >= minInTemp) && (deltaT >= reqDeltaT) && (extDew <= maxExtDew) && (!checkDewDiff || (extDew < inDew))
    } else {
        isFavorableNow = (inTemp >= (minInTemp - hyst)) && (deltaT >= (reqDeltaT - hyst)) && (extDew <= (maxExtDew + hyst)) && (!checkDewDiff || (extDew <= (inDew + hyst)))
    }

    if (isFavorableNow && !wasFavorable) {
        state.currentlyFavorable = true
        state.currentAdvice = "OUVRIR LES FENÊTRES"
        String msg = "[Thermique] Ouvrir les fenetres : Int ${inTemp}C (PR ${inDew}C) vs Ext ${extTemp}C (PR ${extDew}C, DeltaT -${deltaT}C)."
        sendTargetedSms(msg)
    } else if (!isFavorableNow && wasFavorable) {
        state.currentlyFavorable = false
        state.currentAdvice = "FERMER LES FENÊTRES"
        String msg = "[Thermique] Fermer les fenetres : Int ${inTemp}C vs Ext ${extTemp}C."
        sendTargetedSms(msg)
    } else {
        state.currentAdvice = isFavorableNow ? "OUVRIR LES FENÊTRES (Maintien)" : "FERMER LES FENÊTRES (Maintien)"
    }

    manageVentilation(isFavorableNow)
}

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
        }
        if (settings.fanSwitch && settings.fanSwitch.currentValue("switch") != "on") {
            settings.fanSwitch.on()
            state.fanControlledByApp = true
        }
    } else if (state.fanControlledByApp) {
        if (settings.thermostatDevice && settings.thermostatDevice.currentValue("thermostatFanMode") != "auto") {
            settings.thermostatDevice.fanAuto()
        }
        if (settings.fanSwitch && settings.fanSwitch.currentValue("switch") != "off") {
            settings.fanSwitch.off()
        }
        state.fanControlledByApp = false
    }
}

boolean isDeviceResponsive(dev, int maxHours) {
    if (!dev) return false
    if (dev.hasAttribute("healthStatus") && dev.currentValue("healthStatus") == "offline") return false
    Date lastAct = dev.getLastActivity()
    if (!lastAct) return false
    long diffMs = now() - lastAct.time
    return (diffMs <= (maxHours * 3600000L))
}

Map getOutdoorWeatherData(weatherDev) {
    int maxHours = (settings.maxWeatherInactivityHours ?: 3) as int
    if (!isDeviceResponsive(weatherDev, maxHours)) return null
    BigDecimal extTemp = weatherDev.currentValue("temperature") as BigDecimal
    BigDecimal extHum = (weatherDev.currentValue("humidity") != null) ? (weatherDev.currentValue("humidity") as BigDecimal) : 50.0
    def rawDew = weatherDev.currentValue("dewpoint") ?: weatherDev.currentValue("dewPoint")
    BigDecimal extDewPoint = (rawDew != null) ? (rawDew as BigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP) : calculateDewPoint(extTemp, extHum)
    return [temperature: extTemp, humidity: extHum, dewPoint: extDewPoint, dewSource: rawDew != null ? "direct" : "magnus"]
}

Map getIndoorAverageData(tempSensors, humSensors) {
    int maxHours = (settings.maxIndoorInactivityHours ?: 4) as int
    List<BigDecimal> validTemps = []
    List<BigDecimal> validHums = []
    List<String> excluded = []
    List<String> sensorDetails = []

    // Associe chaque capteur à sa température et son humidité individuelle pour les logs
    tempSensors?.each { dev ->
        if (isDeviceResponsive(dev, maxHours) && dev.currentValue("temperature") != null) {
            BigDecimal t = dev.currentValue("temperature") as BigDecimal
            if (t >= 5.0 && t <= 45.0) {
                validTemps << t
                // Cherche l'humidité correspondante sur le même appareil s'il l'offre, sinon cherche dans humSensors
                def hVal = dev.hasAttribute("humidity") ? dev.currentValue("humidity") : null
                String humStr = hVal != null ? " (${hVal}%)" : ""
                sensorDetails << "${dev.displayName}: ${t}°C${humStr}"
            } else {
                excluded << "${dev.displayName} (valeur aberrante: ${t}°C)"
            }
        } else {
            excluded << dev.displayName
        }
    }

    humSensors?.each { dev ->
        if (isDeviceResponsive(dev, maxHours) && dev.currentValue("humidity") != null) {
            validHums << (dev.currentValue("humidity") as BigDecimal)
        }
    }

    if (validTemps.isEmpty()) return null
    BigDecimal avgTemp = (validTemps.sum() / validTemps.size()).setScale(2, BigDecimal.ROUND_HALF_UP)
    BigDecimal avgHum = validHums.isEmpty() ? 50.0 : (validHums.sum() / validHums.size()).setScale(1, BigDecimal.ROUND_HALF_UP)
    
    String summaryStr = sensorDetails.join(" | ")

    return [
        temperature: avgTemp,
        humidity: avgHum,
        dewPoint: calculateDewPoint(avgTemp, avgHum),
        activeCount: validTemps.size(),
        excludedNames: excluded.unique(),
        detailsSummary: summaryStr
    ]
}

BigDecimal calculateDewPoint(BigDecimal tempC, BigDecimal relHum) {
    double t = tempC as double
    double rh = Math.max(1.0d, Math.min(100.0d, relHum as double))
    double a = 17.62d, b = 243.12d
    double alpha = ((a * t) / (b + t)) + Math.log(rh / 100.0d)
    return new BigDecimal((b * alpha) / (a - alpha)).setScale(2, BigDecimal.ROUND_HALF_UP)
}

def handleStaleSensorNotifications(List<String> currentExcluded) {
    if (!settings.notifyOnStaleSensor) return
    List activeSms = getActiveSmsDevices()
    if (activeSms.isEmpty()) return

    List<String> prev = state.alertedStaleDevices ?: []
    List<String> newlyStale = currentExcluded - prev
    if (newlyStale.size() > 0) {
        String msg = "[Thermique] Capteur(s) exclu(s) : ${newlyStale.join(', ')}"
        activeSms.each { it.deviceNotification(msg) }
    }
    state.alertedStaleDevices = currentExcluded
}

def sendTargetedSms(String message) {
    log.info message
    List activeSms = getActiveSmsDevices()
    if (activeSms.isEmpty()) {
        log.info "[ThermalManager] SMS suspendu : aucun membre de la famille n'est présent à la maison."
        return
    }

    if (settings.allowedModes && !settings.allowedModes.contains(location.currentMode)) return

    long minWaitMs = ((settings.minMinutesBetweenSms ?: 120) as long) * 60000L
    long lastSent = state.lastSmsTimestamp ?: 0L

    if ((now() - lastSent) >= minWaitMs) {
        activeSms.each { smsDev ->
            smsDev.deviceNotification(message)
        }
        state.lastSmsTimestamp = now()
    } else {
        log.info "[ThermalManager] SMS anti-spam bloqué (délai minimal non atteint)."
    }
}
