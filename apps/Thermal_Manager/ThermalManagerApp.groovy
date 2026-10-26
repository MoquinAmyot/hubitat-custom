definition(
    name: "Gestionnaire Thermique (Delta T, Rosée, 4 Membres & Offsets)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Gestion complète : Écarts internes, Free Cooling, Offsets, CO2 Max, Humidité, Google Forms et SMS 4 membres.",
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
            section("📊 État Thermique, Air & Humidité Actuel") {
                Map ext = getOutdoorWeatherData(settings.outdoorWeather)
                Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)
                Map air = getAirQualityData(settings.airQualitySensors)
                
                if (ext && ind) {
                    BigDecimal deltaT = (ind.temperature - ext.temperature).setScale(2, BigDecimal.ROUND_HALF_UP)
                    BigDecimal deltaDew = (ind.dewPoint - ext.dewPoint).setScale(2, BigDecimal.ROUND_HALF_UP)
                    List activeRecipients = getActiveSmsDevices()
                    
                    paragraph "Intérieur : Moyenne " + ind.temperature + " °C (Écart: " + ind.tempSpread + " °C) | Humidité: " + ind.humidity + " % | Point de rosée: " + ind.dewPoint + " °C"
                    paragraph "Détail capteurs : " + ind.detailsSummary
                    if (air) {
                        paragraph "Qualité Air (CO2 Max) : " + air.co2Max + " ppm (Moyenne: " + air.co2Avg + " ppm)"
                    }
                    paragraph "Extérieur : " + ext.temperature + " °C | Humidité: " + (ext.humidity ?: "N/A") + " % | Point de rosée: " + ext.dewPoint + " °C"
                    paragraph "Écarts (Int - Ext) : Delta T = " + deltaT + " °C | Delta PR = " + deltaDew + " °C"
                    paragraph "Destinataires SMS actifs : " + (activeRecipients ? (activeRecipients.size() + " personne(s)") : "Personne")
                    paragraph "État actuel : " + (state.currentAdvice ?: "En attente")
                    
                    if (ind.excludedNames && ind.excludedNames.size() > 0) {
                        paragraph "Attention - Capteur(s) exclu(s) : " + ind.excludedNames.join(", ")
                    }
                } else {
                    paragraph "Données météo ou intérieures indisponibles."
                }
            }
        }

        section("1. Météo Extérieure") {
            input "outdoorWeather", "capability.temperatureMeasurement", title: "Dispositif Météo Extérieur", required: true, multiple: false
        }
        
        section("2. Capteurs Intérieurs & Offsets") {
            input "indoorTempSensors", "capability.temperatureMeasurement", title: "Capteurs de Température", required: true, multiple: true
            input "indoorHumSensors", "capability.relativeHumidityMeasurement", title: "Capteurs d'Humidité", required: false, multiple: true
            
            input "offsetTemp1", "decimal", title: "Offset Temp 1", required: false, defaultValue: 0.0
            input "offsetHum1", "decimal", title: "Offset Hum 1", required: false, defaultValue: 0.0
            input "offsetTemp2", "decimal", title: "Offset Temp 2", required: false, defaultValue: 0.0
            input "offsetHum2", "decimal", title: "Offset Hum 2", required: false, defaultValue: 0.0
            input "offsetTemp3", "decimal", title: "Offset Temp 3", required: false, defaultValue: 0.0
            input "offsetHum3", "decimal", title: "Offset Hum 3", required: false, defaultValue: 0.0
            input "offsetTemp4", "decimal", title: "Offset Temp 4", required: false, defaultValue: 0.0
            input "offsetHum4", "decimal", title: "Offset Hum 4", required: false, defaultValue: 0.0
            input "offsetTemp5", "decimal", title: "Offset Temp 5", required: false, defaultValue: 0.0
            input "offsetHum5", "decimal", title: "Offset Hum 5", required: false, defaultValue: 0.0
        }

        section("3. Qualité de l'Air (CO2)") {
            input "airQualitySensors", "capability.carbonDioxideMeasurement", title: "Capteurs de CO2", required: false, multiple: true
            input "maxCo2Threshold", "number", title: "Seuil CO2 maximal (ppm)", defaultValue: 1000, required: true
        }

        section("4. Écarts Internes, Humidité & Hiver") {
            input "maxInternalTempSpread", "decimal", title: "Écart max entre pièces (°C)", defaultValue: 2.0, required: true
            input "maxHumidityThreshold", "decimal", title: "Seuil humidité max (%)", defaultValue: 60.0, required: true
            input "minHumidityThreshold", "decimal", title: "Seuil humidité min (%)", defaultValue: 30.0, required: true
            input "minOutdoorTempForCooling", "decimal", title: "Température extérieure min (°C)", defaultValue: -10.0, required: true
        }

        section("5. Gestion de la Famille (4 Membres)") {
            input "pres1", "capability.presenceSensor", title: "Présence Benoit", required: true, multiple: false
            input "sms1", "capability.notification", title: "SMS Benoit", required: true, multiple: false
            input "pres2", "capability.presenceSensor", title: "Présence Membre 2", required: false, multiple: false
            input "sms2", "capability.notification", title: "SMS Membre 2", required: false, multiple: false
            input "pres3", "capability.presenceSensor", title: "Présence Membre 3", required: false, multiple: false
            input "sms3", "capability.notification", title: "SMS Membre 3", required: false, multiple: false
            input "pres4", "capability.presenceSensor", title: "Présence Membre 4", required: false, multiple: false
            input "sms4", "capability.notification", title: "SMS Membre 4", required: false, multiple: false
        }
        
        section("6. Sécurité Capteurs") {
            input "maxIndoorInactivityHours", "number", title: "Exclure intérieur si muet (h)", defaultValue: 4, required: true
            input "maxWeatherInactivityHours", "number", title: "Suspendre météo si muette (h)", defaultValue: 3, required: true
            input "notifyOnStaleSensor", "bool", title: "Alerter si capteur décroche ?", defaultValue: true
        }
        
        section("7. Seuils Free Cooling") {
            input "minIndoorTempForCooling", "decimal", title: "Temp intérieure min (°C)", defaultValue: 22.5, required: true
            input "minDeltaT", "decimal", title: "Delta T minimal (°C)", defaultValue: 1.5, required: true
            input "maxOutdoorDewPoint", "decimal", title: "Point de rosée ext max (°C)", defaultValue: 16.0, required: true
            input "requireLowerOutdoorDewPoint", "bool", title: "PR ext inférieur au PR int ?", defaultValue: true
            input "hysteresisTemp", "decimal", title: "Hystérésis (°C)", defaultValue: 0.5, required: true
        }

        section("8. Ventilation") {
            input "thermostatDevice", "capability.thermostat", title: "Thermostat Ecobee", required: false, multiple: false
            input "fanSwitch", "capability.switch", title: "Interrupteur Échangeur", required: false, multiple: false
            input "contactSensors", "capability.contactSensor", title: "Capteurs de fenêtres", required: false, multiple: true
        }

        section("9. Google Forms") {
            input "enableGoogleLogging", "bool", title: "Activer Google Forms", defaultValue: false
            input "googleFormUrl", "text", title: "URL formResponse", required: false
            input "entryInTemp", "text", title: "Entry Temp Intérieure", required: false
            input "entryInHum", "text", title: "Entry Hum Intérieure", required: false
            input "entryInDew", "text", title: "Entry PR Intérieur", required: false
            input "entryExtTemp", "text", title: "Entry Temp Extérieure", required: false
            input "entryExtHum", "text", title: "Entry Hum Extérieure", required: false
            input "entryExtDew", "text", title: "Entry PR Extérieur", required: false
            input "entryCo2Max", "text", title: "Entry CO2 Max", required: false
            input "entryAdvice", "text", title: "Entry Décision", required: false
        }

        section("10. SMS & Fréquence") {
            input "allowedModes", "mode", title: "Modes autorisés", required: false, multiple: true
            input "minMinutesBetweenSms", "number", title: "Délai SMS (min)", defaultValue: 120, required: true
            input "evalIntervalMinutes", "enum", title: "Fréquence évaluation", options: ["5": "5 min", "10": "10 min", "15": "15 min", "30": "30 min"], defaultValue: "15", required: true
            input "runEvalNow", "bool", title: "Forcer évaluation", defaultValue: false, submitOnChange: true
        }

        if (settings.runEvalNow) {
            app.updateSetting("runEvalNow", [value: "false", type: "bool"])
            evaluateThermalConditions()
            section { paragraph "Évaluation exécutée." }
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

    if (!ext || !ind) return

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
    BigDecimal maxSpread = (settings.maxInternalTempSpread ?: 2.0) as BigDecimal
    boolean needInternalMixing = (inSpread >= maxSpread)

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
        sendTargetedSms("Ouvrir les fenêtres / Free Cooling : Int " + inTemp + "°C vs Ext " + extTemp + "°C.")
    } else if (!isFavorableThermal && wasFavorable) {
        state.currentlyFavorable = false
        sendTargetedSms("Fermer les fenêtres : Int " + inTemp + "°C vs Ext " + extTemp + "°C.")
    }

    int co2Threshold = (settings.maxCo2Threshold ?: 1000) as int
    boolean highCo2 = air && (air.co2Max > co2Threshold) && !isTooColdOutside

    BigDecimal maxHumLimit = (settings.maxHumidityThreshold ?: 60.0) as BigDecimal
    boolean tooHumid = (inHum > maxHumLimit) && (extDew < inDew) && !isTooColdOutside

    BigDecimal minHumLimit = (settings.minHumidityThreshold ?: 30.0) as BigDecimal
    if (inHum < minHumLimit) {
        if (!state.dryAirAlertSent) {
            sendTargetedSms("Alerte Air Sec : Humidité intérieure à " + inHum + "%.")
            state.dryAirAlertSent = true
        }
    } else if (inHum >= (minHumLimit + 2.0)) {
        state.dryAirAlertSent = false
    }

    boolean finalVentilationNeeded = isFavorableThermal || highCo2 || tooHumid || needInternalMixing

    if (isFavorableThermal) {
        state.currentAdvice = "VENTILATION ACTIVE (Free Cooling)"
    } else if (highCo2) {
        state.currentAdvice = "VENTILATION FORCÉE (CO2 Max: " + air.co2Max + " ppm)"
    } else if (tooHumid) {
        state.currentAdvice = "VENTILATION FORCÉE (Trop humide: " + inHum + "%)"
    } else if (needInternalMixing) {
        state.currentAdvice = "BRASSAGE ACTIF (Écart pièces: " + inSpread + "°C)"
    } else {
        state.currentAdvice = "VENTILATION ARRÊTÉE"
    }

    manageVentilation(finalVentilationNeeded)
    logToGoogleForms(ind, ext, air, state.currentAdvice)
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

def logToGoogleForms(Map ind, Map ext, Map air, String advice) {
    if (!settings.enableGoogleLogging || !settings.googleFormUrl) return
    String finalUrl = settings.googleFormUrl.trim().replace("/viewform", "/formResponse")
    Map bodyParams = [:]
    if (settings.entryInTemp) bodyParams[settings.entryInTemp.trim()] = ind.temperature.toString()
    if (settings.entryInHum) bodyParams[settings.entryInHum.trim()] = ind.humidity.toString()
    if (settings.entryInDew) bodyParams[settings.entryInDew.trim()] = ind.dewPoint.toString()
    if (settings.entryExtTemp) bodyParams[settings.entryExtTemp.trim()] = ext.temperature.toString()
    if (settings.entryExtHum) bodyParams[settings.entryExtHum.trim()] = ext.humidity.toString()
    if (settings.entryExtDew) bodyParams[settings.entryExtDew.trim()] = ext.dewPoint.toString()
    if (settings.entryCo2Max && air) bodyParams[settings.entryCo2Max.trim()] = air.co2Max.toString()
    if (settings.entryAdvice) bodyParams[settings.entryAdvice.trim()] = advice

    if (bodyParams.isEmpty()) return
    try {
        asynchttpPost("googleFormsCallback", [uri: finalUrl, requestContentType: "application/x-www-form-urlencoded", body: bodyParams, timeout: 10])
    } catch (Exception e) {}
}

def googleFormsCallback(response, data) {}

boolean isDeviceResponsive(dev, int maxHours) {
    if (!dev) return false
    if (dev.hasAttribute("healthStatus") && dev.currentValue("healthStatus") == "offline") return false
    Date lastAct = dev.getLastActivity()
    if (!lastAct) return false
    return ((now() - lastAct.time) <= (maxHours * 3600000L))
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
    List offsets = [settings.offsetTemp1, settings.offsetTemp2, settings.offsetTemp3, settings.offsetTemp4, settings.offsetTemp5]
    return (offsets[idx] != null) ? (offsets[idx] as BigDecimal) : 0.0
}

BigDecimal getHumOffsetForIndex(int idx) {
    List offsets = [settings.offsetHum1, settings.offsetHum2, settings.offsetHum3, settings.offsetHum4, settings.offsetHum5]
    return (offsets[idx] != null) ? (offsets[idx] as BigDecimal) : 0.0
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
            BigDecimal adjustedT = (rawT + getTempOffsetForIndex(index)).setScale(2, BigDecimal.ROUND_HALF_UP)

            if (adjustedT >= 5.0 && adjustedT <= 45.0) {
                validTemps << adjustedT
                def rawH = dev.hasAttribute("humidity") ? dev.currentValue("humidity") : null
                BigDecimal adjustedH = rawH != null ? ((rawH as BigDecimal) + getHumOffsetForIndex(index)).setScale(1, BigDecimal.ROUND_HALF_UP) : null
                if (adjustedH != null) validHums << adjustedH
                sensorDetails << (dev.displayName + ": " + adjustedT + "°C")
            } else {
                excluded << dev.displayName
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
    
    return [temperature: avgTemp, humidity: avgHum, dewPoint: calculateDewPoint(avgTemp, avgHum), tempSpread: spread, activeCount: validTemps.size(), excludedNames: excluded.unique(), detailsSummary: sensorDetails.join(" / ")]
}

Map getAirQualityData(airSensors) {
    if (!airSensors) return null
    int maxHours = (settings.maxIndoorInactivityHours ?: 4) as int
    List<Integer> co2Values = []
    List<String> details = []
    airSensors.each { dev ->
        if (isDeviceResponsive(dev, maxHours)) {
            def co2 = dev.currentValue("carbonDioxide")
            if (co2 != null) {
                int val = co2 as Integer
                co2Values << val
                details << (dev.displayName + ": " + val)
            }
        }
    }
    if (co2Values.isEmpty()) return null
    return [co2Max: co2Values.max() as int, co2Avg: (co2Values.sum() / co2Values.size()) as int, details: details.join(", ")]
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
        activeSsm.each { it.deviceNotification("Capteur(s) exclu(s) : " + newlyStale.join(", ")) }
    }
    state.alertedStaleDevices = currentExcluded
}

def sendTargetedSms(String message) {
    List activeSms = getActiveSmsDevices()
    if (activeSms.isEmpty()) return
    if (settings.allowedModes && !settings.allowedModes.contains(location.currentMode)) return
    long minWaitMs = ((settings.minMinutesBetweenSms ?: 120) as long) * 60000L
    long lastSent = state.lastSmsTimestamp ?: 0L
    if ((now() - lastSent) >= minWaitMs) {
        activeSms.each { it.deviceNotification(message) }
        state.lastSsmTimestamp = now()
    }
}
