definition(
    name: "Gestionnaire Thermique (Delta T, Rosée, 4 Membres & Offsets)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Gestion complète : Écarts internes, Free Cooling (Delta T/Rosée), Offsets, CO2, Humidité et routage SMS 4 membres.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Configuration du Gestionnaire Thermique & Air", install: true, uninstall: true) {
        
        if (settings.outdoorWeather && settings.indoorTempSensors) {
            section("📊 État Thermique, Air & Humidité Actuel (En direct)") {
                Map ext = getOutdoorWeatherData(settings.outdoorWeather)
                Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)
                Map air = getAirQualityData(settings.airQualitySensors)
                
                if (ext && ind) {
                    BigDecimal deltaT = (ind.temperature - ext.temperature).setScale(2, BigDecimal.ROUND_HALF_UP)
                    BigDecimal deltaDew = (ind.dewPoint - ext.dewPoint).setScale(2, BigDecimal.ROUND_HALF_UP)
                    List activeRecipients = getActiveSmsDevices()
                    
                    String statusHtml = "<b>Intérieur (${ind.activeCount} capteur(s)) :</b> Moy: ${ind.temperature} °C (Écart interne: ${ind.tempSpread} °C) | Hum: <b>${ind.humidity} %</b> | PR: <b>${ind.dewPoint} °C</b><br>"
                    statusHtml += "<small>Détail capteurs (avec offsets) : ${ind.detailsSummary}</small><br>"
                    if (air) {
                        statusHtml += "<b>Qualité d'Air (CO2 moyen) :</b> <span style='color:${air.co2 > (settings.maxCo2Threshold ?: 1000) ? 'red' : 'green'}; font-weight:bold;'>${air.co2} ppm</span> (${air.details})<br>"
                    }
                    statusHtml += "<b>Extérieur (${settings.outdoorWeather.displayName}) :</b> ${ext.temperature} °C | Hum: ${ext.humidity ?: 'N/A'} % | PR: <b>${ext.dewPoint} °C</b> (${ext.dewSource})<br>"
                    statusHtml += "<b>Écarts (Int - Ext) :</b> Delta T = <b>${deltaT} °C</b> | Delta PR = <b>${deltaDew} °C</b><br>"
                    statusHtml += "<b>Destinataires SMS actifs :</b> <span style='color:${activeRecipients ? 'green' : 'orange'}; font-weight:bold;'>${activeRecipients ? activeRecipients.size() + ' personne(s) présente(s)' : 'Personne (SMS suspendus)'}</span><br>"
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
        
        section("2. Capteurs Intérieurs & Offsets de Calibrage") {
            input "indoorTempSensors", "capability.temperatureMeasurement", title: "Capteurs de Température intérieurs", required: true, multiple: true
            input "indoorHumSensors", "capability.relativeHumidityMeasurement", title: "Capteurs d'Humidité intérieurs (si séparés)", required: false, multiple: true
            
            paragraph "<b>Ajustement des Offsets (selon l'ordre de sélection ci-dessus) :</b>"
            input "offsetTemp1", "decimal", title: "Offset Température Capteur 1 (°C)", required: false, defaultValue: 0.0
            input "offsetHum1", "decimal", title: "Offset Humidité Capteur 1 (%)", required: false, defaultValue: 0.0
            input "offsetTemp2", "decimal", title: "Offset Température Capteur 2 (°C)", required: false, defaultValue: 0.0
            input "offsetHum2", "decimal", title: "Offset Humidité Capteur 2 (%)", required: false, defaultValue: 0.0
            input "offsetTemp3", "decimal", title: "Offset Température Capteur 3 (°C)", required: false, defaultValue: 0.0
            input "offsetHum3", "decimal", title: "Offset Humidité Capteur 3 (%)", required: false, defaultValue: 0.0
            input "offsetTemp4", "decimal", title: "Offset Température Capteur 4 (°C)", required: false, defaultValue: 0.0
            input "offsetHum4", "decimal", title: "Offset Humidité Capteur 4 (%)", required: false, defaultValue: 0.0
            input "offsetTemp5", "decimal", title: "Offset Température Capteur 5 (°C)", required: false, defaultValue: 0.0
            input "offsetHum5", "decimal", title: "Offset Humidité Capteur 5 (%)", required: false, defaultValue: 0.0
        }

        section("3. Qualité de l'Air (CO2 - ex: Capteurs Ikea)") {
            input "airQualitySensors", "capability.carbonDioxideMeasurement", title: "Capteurs de CO2 (carbonDioxide)", required: false, multiple: true
            input "maxCo2Threshold", "number", title: "Seuil de CO2 maximal avant ventilation forcée (ppm)", defaultValue: 1000, required: true
        }

        section("4. Écarts Internes, Humidité & Protection Hivernale") {
            input "maxInternalTempSpread", "decimal", title: "Écart max entre pièces avant brassage d'air (°C)", defaultValue: 2.0, required: true
            input "maxHumidityThreshold", "decimal", title: "Seuil d'humidité maximale (%) - Active l'échangeur si ext. plus sec", defaultValue: 60.0, required: true
            input "minHumidityThreshold", "decimal", title: "Seuil d'humidité minimale (%) - Envoie un SMS si trop sec", defaultValue: 30.0, required: true
            input "minOutdoorTempForCooling", "decimal", title: "Température extérieure minimale pour Free Cooling / Échangeur (°C)", defaultValue: -10.0, required: true
        }

        section("5. Gestion de la Famille (4 Membres) & Routage SMS") {
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
        
        section("6. Sécurité & Exclusion automatique des capteurs HS") {
            input "maxIndoorInactivityHours", "number", title: "Capteurs INTÉRIEURS : exclure si muets depuis (heures)", defaultValue: 4, required: true
            input "maxWeatherInactivityHours", "number", title: "Météo EXTÉRIEURE (Meteo2) : suspendre si muette depuis (heures)", defaultValue: 3, required: true
            input "notifyOnStaleSensor", "bool", title: "Alerter par SMS si un capteur décroche ?", defaultValue: true
        }
        
        section("7. Seuils de Décision (Fenêtres & Free Cooling)") {
            input "minIndoorTempForCooling", "decimal", title: "Température intérieure minimale pour suggérer d'ouvrir (°C)", defaultValue: 22.5, required: true
            input "minDeltaT", "decimal", title: "Delta T minimal requis (Int - Ext en °C)", defaultValue: 1.5, required: true
            input "maxOutdoorDewPoint", "decimal", title: "Point de rosée extérieur maximal toléré (°C)", defaultValue: 16.0, required: true
            input "requireLowerOutdoorDewPoint", "bool", title: "Exiger que le PR extérieur soit inférieur au PR intérieur ?", defaultValue: true
            input "hysteresisTemp", "decimal", title: "Hystérésis (°C)", defaultValue: 0.5, required: true
        }

        section("8. Contrôle Automatique de la Ventilation (Échangeur / Fan)") {
            input "thermostatDevice", "capability.thermostat", title: "Thermostat (Ecobee fanOn / fanAuto)", required: false, multiple: false
            input "fanSwitch", "capability.switch", title: "Interrupteur / Relais d'échangeur d'air (ON/OFF)", required: false, multiple: false
            input "contactSensors", "capability.contactSensor", title: "Capteurs de fenêtres (Optionnel)", required: false, multiple: true
        }

        section("9. Paramètres de Notification SMS & Fréquence") {
            input "allowedModes", "mode", title: "Modes autorisés pour les SMS (laisser vide = tous)", required: false, multiple: true
            input "minMinutesBetweenSms", "number", title: "Délai minimal entre deux SMS (Anti-spam en minutes)", defaultValue: 120, required: true
            input "evalIntervalMinutes", "enum", title: "Fréquence d'évaluation (Intervalle en minutes)", options: ["5": "5 min", "10": "10 min", "15": "15 min", "30": "30 min"], defaultValue: "15", required: true
            input "runEvalNow", "bool", title: "Forcer une évaluation immédiate", defaultValue: false, submitOnChange: true
        }

        if (settings.runEvalNow) {
            app.updateSetting("runEvalNow", [value: "false", type: "bool"])
            evaluateThermalConditions()
            section { paragraph "<span style='color:green;'>✓ Évaluation exécutée avec succès ! Consultez les logs.</span>" }
        }
    }
}

def installed() { initialize() }
def updated() { unschedule(); unsubscribe(); initialize() }

def initialize() {
    if (state.currentlyFavorable == null) state.currentlyFavorable = false
    if (state.currentAdvice == null) state.currentAdvice = "Initialisé"
    if (state.alertedStaleDevices == null) state.alertedStaleDevices = []
    if (state.dryAirAlertSent == null) state.dryAirAlertSent = false

    subscribe(outdoorWeather, "temperature", eventHandler)
    subscribe(outdoorWeather, "humidity", eventHandler)
    subscribe(outdoorWeather, "dewpoint", eventHandler)
    subscribe(indoorTempSensors, "temperature", eventHandler)
    if (indoorHumSensors) subscribe(indoorHumSensors, "humidity", eventHandler)
    if (airQualitySensors) subscribe(airQualitySensors, "carbonDioxide", eventHandler)
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
    log.info "[ThermalManager] Initialisé avec succès."
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
    Map air = getAirQualityData(settings.airQualitySensors)

    if (!ext || !ind) {
        log.warn "[ThermalManager] Évaluation impossible : données météo ou intérieures manquantes."
        return
    }

    log.info "[ThermalManager] --- Évaluation Globale ---"
    log.info "[ThermalManager] Extérieur -> Temp: ${ext.temperature}°C, Hum: ${ext.humidity}%, PR: ${ext.dewPoint}°C"
    log.info "[ThermalManager] Intérieur -> Temp Moy: ${ind.temperature}°C (Écart: ${ind.tempSpread}°C), Hum Moy: ${ind.humidity}%, PR: ${ind.dewPoint}°C | ${ind.detailsSummary}"
    if (air) log.info "[ThermalManager] Qualité d'air -> CO2 moyen: ${air.co2} ppm (${air.details})"

    handleStaleSensorNotifications(ind.excludedNames)

    BigDecimal inTemp = ind.temperature
    BigDecimal inHum = ind.humidity
    BigDecimal inDew = ind.dewPoint
    BigDecimal inSpread = ind.tempSpread
    BigDecimal extTemp = ext.temperature
    BigDecimal extDew = ext.dewPoint

    BigDecimal deltaT = (inTemp - extTemp).setScale(2, BigDecimal.ROUND_HALF_UP)
    BigDecimal minExtTempForCooling = (settings.minOutdoorTempForCooling ?: -10.0) as BigDecimal
    BigDecimal hyst = (settings.hysteresisTemp ?: 0.5) as BigDecimal

    boolean isTooColdOutside = (extTemp < minExtTempForCooling)

    // 1. Écart de température entre les pièces (Brassage interne)
    BigDecimal maxSpread = (settings.maxInternalTempSpread ?: 2.0) as BigDecimal
    boolean needInternalMixing = (inSpread >= maxSpread)

    // 2. Free Cooling avec hystérésis
    BigDecimal minInTemp = (settings.minIndoorTempForCooling ?: 22.5) as BigDecimal
    BigDecimal reqDeltaT = (settings.minDeltaT ?: 1.5) as BigDecimal
    BigDecimal maxExtDew = (settings.maxOutdoorDewPoint ?: 16.0) as BigDecimal
    boolean checkDewDiff = (settings.requireLowerOutdoorDewPoint != false)

    boolean wasFavorable = state.currentlyFavorable ?: false
    boolean isFavorableThermal = false

    if (!isTooColdOutside) {
        if (!wasFavorable) {
            isFavorableThermal = (inTemp >= minInTemp) && (deltaT >= reqDeltaT) && (extDew <= maxExtDew) && (!checkDewDiff || (extDew < inDew))
        } else {
            isFavorableThermal = (inTemp >= (minInTemp - hyst)) && (deltaT >= (reqDeltaT - hyst)) && (extDew <= (maxExtDew + hyst)) && (!checkDewDiff || (extDew <= (inDew + hyst)))
        }
    }

    if (isFavorableThermal && !wasFavorable) {
        state.currentlyFavorable = true
        sendTargetedSms("[Thermique] Ouvrir les fenetres / Free Cooling : Int ${inTemp}C (PR ${inDew}C) vs Ext ${extTemp}C (PR ${extDew}C, DeltaT -${deltaT}C).")
    } else if (!isFavorableThermal && wasFavorable) {
        state.currentlyFavorable = false
        sendTargetedSms("[Thermique] Fermer les fenetres : Int ${inTemp}C vs Ext ${extTemp}C.")
    }

    // 3. Qualité d'air (CO2 > seuil)
    int co2Threshold = (settings.maxCo2Threshold ?: 1000) as int
    boolean highCo2 = air && (air.co2 > co2Threshold) && !isTooColdOutside

    // 4. Humidité excessive (> 60% et air extérieur plus sec)
    BigDecimal maxHumLimit = (settings.maxHumidityThreshold ?: 60.0) as BigDecimal
    boolean tooHumid = (inHum > maxHumLimit) && (extDew < inDew) && !isTooColdOutside

    // 5. Air trop sec (< 30%) -> Alerte SMS avec hystérésis (+2%)
    BigDecimal minHumLimit = (settings.minHumidityThreshold ?: 30.0) as BigDecimal
    if (inHum < minHumLimit) {
        if (!state.dryAirAlertSent) {
            sendTargetedSms("[Alerte Air Sec] L'humidite interieure est de ${inHum}% (sous le seuil de ${minHumLimit}%).")
            state.dryAirAlertSent = true
        }
    } else if (inHum >= (minHumLimit + 2.0)) {
        state.dryAirAlertSent = false
    }

    boolean finalVentilationNeeded = isFavorableThermal || highCo2 || tooHumid || needInternalMixing

    if (isFavorableThermal) {
        state.currentAdvice = "VENTILATION ACTIVE (Free Cooling)"
    } else if (highCo2) {
        state.currentAdvice = "VENTILATION FORCÉE (CO2 élevé: ${air.co2} ppm)"
    } else if (tooHumid) {
        state.currentAdvice = "VENTILATION FORCÉE (Trop humide: ${inHum}%)"
    } else if (needInternalMixing) {
        state.currentAdvice = "BRASSAGE ACTIF (Écart pièces: ${inSpread}°C)"
    } else {
        state.currentAdvice = "VENTILATION ARRÊTÉE (Normal)"
    }

    manageVentilation(finalVentilationNeeded)
}

def manageVentilation(boolean shouldRunFan) {
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

BigDecimal getTempOffsetForIndex(int idx) {
    if (idx == 0) return (settings.offsetTemp1 ?: 0.0) as BigDecimal
    if (idx == 1) return (settings.offsetTemp2 ?: 0.0) as BigDecimal
    if (idx == 2) return (settings.offsetTemp3 ?: 0.0) as BigDecimal
    if (idx == 3) return (settings.offsetTemp4 ?: 0.0) as BigDecimal
    if (idx == 4) return (settings.offsetTemp5 ?: 0.0) as BigDecimal
    return 0.0
}

BigDecimal getHumOffsetForIndex(int idx) {
    if (idx == 0) return (settings.offsetHum1 ?: 0.0) as BigDecimal
    if (idx == 1) return (settings.offsetHum2 ?: 0.0) as BigDecimal
    if (idx == 2) return (settings.offsetHum3 ?: 0.0) as BigDecimal
    if (idx == 3) return (settings.offsetHum4 ?: 0.0) as BigDecimal
    if (idx == 4) return (settings.offsetHum5 ?: 0.0) as BigDecimal
    return 0.0
}

Map getIndoorAverageData(tempSensors, humSensors) {
    int maxHours = (settings.maxIndoorInactivityHours ?: 4) as int
    List<BigDecimal> validTemps = []
    List<BigDecimal> validHums = []
    List<String> excluded = []
    List<String> sensorDetails = []

    tempSensors?.eachWithIndex { dev, index ->
        if (isDeviceResponsive(dev, maxHours) && dev.currentValue("temperature") != null) {
            BigDecimal rawT = dev.currentValue("temperature") as BigDecimal
            BigDecimal tempOffset = getTempOffsetForIndex(index)
            BigDecimal adjustedT = (rawT + tempOffset).setScale(2, BigDecimal.ROUND_HALF_UP)

            if (adjustedT >= 5.0 && adjustedT <= 45.0) {
                validTemps << adjustedT
                
                def rawH = dev.hasAttribute("humidity") ? dev.currentValue("humidity") : null
                BigDecimal humOffset = getHumOffsetForIndex(index)
                BigDecimal adjustedH = rawH != null ? ((rawH as BigDecimal) + humOffset).setScale(1, BigDecimal.ROUND_HALF_UP) : null
                if (adjustedH != null) validHums << adjustedH

                String humStr = adjustedH != null ? " (${adjustedH}%)" : ""
                sensorDetails << "${dev.displayName}: ${adjustedT}°C${humStr}"
            } else {
                excluded << "${dev.displayName} (valeur aberrante: ${adjustedT}°C)"
            }
        } else {
            excluded << dev.displayName
        }
    }

    humSensors?.each { dev ->
        if (!tempSensors?.find { it.id == dev.id }) {
            if (isDeviceResponsive(dev, maxHours) && dev.currentValue("humidity") != null) {
                validHums << (dev.currentValue("humidity") as BigDecimal)
            }
        }
    }

    if (validTemps.isEmpty()) return null
    BigDecimal avgTemp = (validTemps.sum() / validTemps.size()).setScale(2, BigDecimal.ROUND_HALF_UP)
    BigDecimal avgHum = validHums.isEmpty() ? 50.0 : (validHums.sum() / validHums.size()).setScale(1, BigDecimal.ROUND_HALF_UP)
    BigDecimal spread = (validTemps.size() > 1) ? (validTemps.max() - validTemps.min()).setScale(2, BigDecimal.ROUND_HALF_UP) : 0.0
    
    return [
        temperature: avgTemp,
        humidity: avgHum,
        dewPoint: calculateDewPoint(avgTemp, avgHum),
        tempSpread: spread,
        activeCount: validTemps.size(),
        excludedNames: excluded.unique(),
        detailsSummary: sensorDetails.join(" | ")
    ]
}

Map getAirQualityData(airSensors) {
    if (!airSensors) return null
    List<Integer> co2Values = []
    List<String> details = []
    airSensors.each { dev ->
        def co2 = dev.currentValue("carbonDioxide")
        if (co2 != null) {
            int val = co2 as Integer
            co2Values << val
            details << "${dev.displayName}: ${val} ppm"
        }
    }
    if (co2Values.isEmpty()) return null
    return [
        co2: (co2Values.sum() / co2Values.size()) as int,
        details: details.join(", ")
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
    if (activeSms.isEmpty()) return

    if (settings.allowedModes && !settings.allowedModes.contains(location.currentMode)) return

    long minWaitMs = ((settings.minMinutesBetweenSms ?: 120) as long) * 60000L
    long lastSent = state.lastSmsTimestamp ?: 0L

    if ((now() - lastSent) >= minWaitMs) {
        activeSms.each { it.deviceNotification(message) }
        state.lastSmsTimestamp = now()
    }
}
