definition(
    name: "Gestionnaire Thermique (Delta T, Rosée, 4 Membres & Offsets)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Gestion complete : Ecarts internes, Free Cooling, Offsets, CO2 Max, Humidite, Google Forms et SMS 4 membres.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Configuration du Gestionnaire Thermique et Air", install: true, uninstall: true) {
        
        if (settings.outdoorWeather && settings.indoorTempSensors) {
            section("Etat Thermique, Air et Humidite Actuel") {
                Map ext = getOutdoorWeatherData(settings.outdoorWeather)
                Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)
                Map air = getAirQualityData(settings.airQualitySensors)
                
                if (ext && ind) {
                    BigDecimal deltaT = (ind.temperature - ext.temperature).setScale(2, BigDecimal.ROUND_HALF_UP)
                    BigDecimal deltaDew = (ind.dewPoint - ext.dewPoint).setScale(2, BigDecimal.ROUND_HALF_UP)
                    List activeRecipients = getActiveSmsDevices()
                    
                    paragraph "Interieur : Moyenne " + ind.temperature + " C (Ecart: " + ind.tempSpread + " C) - Humidite: " + ind.humidity + " % - Point de rosee: " + ind.dewPoint + " C"
                    paragraph "Detail capteurs : " + ind.detailsSummary
                    if (air) {
                        paragraph "Qualite Air (CO2 Max) : " + air.co2Max + " ppm (Moyenne: " + air.co2Avg + " ppm)"
                    }
                    paragraph "Exterieur : " + ext.temperature + " C - Humidite: " + (ext.humidity ?: "N/A") + " % - Point de rosee: " + ext.dewPoint + " C"
                    paragraph "Ecarts (Int - Ext) : Delta T = " + deltaT + " C - Delta PR = " + deltaDew + " C"
                    paragraph "Destinataires SMS actifs : " + (activeRecipients ? (activeRecipients.size() + " personne(s)") : "Personne")
                    paragraph "Etat actuel : " + (state.currentAdvice ?: "En attente")
                    
                    if (ind.excludedNames && ind.excludedNames.size() > 0) {
                        paragraph "Attention - Capteur(s) exclu(s) : " + ind.excludedNames.join(", ")
                    }
                } else {
                    paragraph "Donnees meteo ou interieures indisponibles."
                }
            }
        }

        section("1. Meteo Exterieure") {
            input "outdoorWeather", "capability.temperatureMeasurement", title: "Dispositif Meteo Exterieur", required: true, multiple: false
        }
        
        section("2. Capteurs Interieurs et Offsets de Calibrage") {
            input "indoorTempSensors", "capability.temperatureMeasurement", title: "Capteurs de Temperature interieurs", required: true, multiple: true
            input "indoorHumSensors", "capability.relativeHumidityMeasurement", title: "Capteurs d Humidite interieurs", required: false, multiple: true
            
            input "offsetTemp1", "decimal", title: "Offset Temperature Capteur 1 (C)", required: false, defaultValue: 0.0
            input "offsetHum1", "decimal", title: "Offset Humidite Capteur 1 (%)", required: false, defaultValue: 0.0
            input "offsetTemp2", "decimal", title: "Offset Temperature Capteur 2 (C)", required: false, defaultValue: 0.0
            input "offsetHum2", "decimal", title: "Offset Humidite Capteur 2 (%)", required: false, defaultValue: 0.0
            input "offsetTemp3", "decimal", title: "Offset Temperature Capteur 3 (C)", required: false, defaultValue: 0.0
            input "offsetHum3", "decimal", title: "Offset Humidite Capteur 3 (%)", required: false, defaultValue: 0.0
            input "offsetTemp4", "decimal", title: "Offset Temperature Capteur 4 (C)", required: false, defaultValue: 0.0
            input "offsetHum4", "decimal", title: "Offset Humidite Capteur 4 (%)", required: false, defaultValue: 0.0
            input "offsetTemp5", "decimal", title: "Offset Temperature Capteur 5 (C)", required: false, defaultValue: 0.0
            input "offsetHum5", "decimal", title: "Offset Humidite Capteur 5 (%)", required: false, defaultValue: 0.0
        }

        section("3. Qualite de l Air (CO2)") {
            input "airQualitySensors", "capability.carbonDioxideMeasurement", title: "Capteurs de CO2", required: false, multiple: true
            input "maxCo2Threshold", "number", title: "Seuil de CO2 maximal (ppm)", defaultValue: 1000, required: true
        }

        section("4. Ecarts Internes, Humidite et Hiver") {
            input "maxInternalTempSpread", "decimal", title: "Ecart max entre pieces (C)", defaultValue: 2.0, required: true
            input "maxHumidityThreshold", "decimal", title: "Seuil humidite max (%)", defaultValue: 60.0, required: true
            input "minHumidityThreshold", "decimal", title: "Seuil humidite min (%)", defaultValue: 30.0, required: true
            input "minOutdoorTempForCooling", "decimal", title: "Temperature exterieure min (C)", defaultValue: -10.0, required: true
        }

        section("5. Gestion de la Famille (4 Membres)") {
            input "pres1", "capability.presenceSensor", title: "Presence Benoit", required: true, multiple: false
            input "sms1", "capability.notification", title: "SMS Benoit", required: true, multiple: false
            input "pres2", "capability.presenceSensor", title: "Presence Membre 2", required: false, multiple: false
            input "sms2", "capability.notification", title: "SMS Membre 2", required: false, multiple: false
            input "pres3", "capability.presenceSensor", title: "Presence Membre 3", required: false, multiple: false
            input "sms3", "capability.notification", title: "SMS Membre 3", required: false, multiple: false
            input "pres4", "capability.presenceSensor", title: "Presence Membre 4", required: false, multiple: false
            input "sms4", "capability.notification", title: "SMS Membre 4", required: false, multiple: false
        }
        
        section("6. Securite Capteurs") {
            input "maxIndoorInactivityHours", "number", title: "Exclure interieur si muet depuis (h)", defaultValue: 4, required: true
            input "maxWeatherInactivityHours", "number", title: "Suspendre meteo si muette depuis (h)", defaultValue: 3, required: true
            input "notifyOnStaleSensor", "bool", title: "Alerter si capteur decroche ?", defaultValue: true
        }
        
        section("7. Seuils Free Cooling") {
            input "minIndoorTempForCooling", "decimal", title: "Temp interieure min pour ouvrir (C)", defaultValue: 22.5, required: true
            input "minDeltaT", "decimal", title: "Delta T minimal requis (C)", defaultValue: 1.5, required: true
            input "maxOutdoorDewPoint", "decimal", title: "Point de rosee ext max (C)", defaultValue: 16.0, required: true
            input "requireLowerOutdoorDewPoint", "bool", title: "PR ext inferieur au PR int ?", defaultValue: true
            input "hysteresisTemp", "decimal", title: "Hysteresis (C)", defaultValue: 0.5, required: true
        }

        section("8. Ventilation") {
            input "thermostatDevice", "capability.thermostat", title: "Thermostat Ecobee", required: false, multiple: false
            input "fanSwitch", "capability.switch", title: "Interrupteur Echangeur", required: false, multiple: false
            input "contactSensors", "capability.contactSensor", title: "Capteurs de fenetres", required: false, multiple: true
        }

        section("9. Google Forms") {
            input "enableGoogleLogging", "bool", title: "Activer Google Forms", defaultValue: false
            input "googleFormUrl", "text", title: "URL formResponse", required: false
            input "entryInTemp", "text", title: "Entry Temp Interieure", required: false
            input "entryInHum", "text", title: "Entry Hum Interieure", required: false
            input "entryInDew", "text", title: "Entry PR Interieur", required: false
            input "entryExtTemp", "text", title: "Entry Temp Exterieure", required: false
            input "entryExtHum", "text", title: "Entry Hum Exterieure", required: false
            input "entryExtDew", "text", title: "Entry PR Exterieur", required: false
            input "entryCo2Max", "text", title: "Entry CO2 Max", required: false
            input "entryAdvice", "text", title: "Entry Decision", required: false
        }

        section("10. SMS et Frequence") {
            input "allowedModes", "mode", title: "Modes autorises", required: false, multiple: true
            input "minMinutesBetweenSms", "number", title: "Delai anti-spam SMS (min)", defaultValue: 120, required: true
            input "evalIntervalMinutes", "enum", title: "Frequence evaluation", options: ["5": "5 min", "10": "10 min", "15": "15 min", "30": "30 min"], defaultValue: "15", required: true
            input "runEvalNow", "bool", title: "Forcer evaluation immediate", defaultValue: false, submitOnChange: true
        }

        if (settings.runEvalNow) {
            app.updateSetting("runEvalNow", [value: "false", type: "bool"])
            evaluateThermalConditions()
            section { paragraph "Evaluation executee." }
        }
    }
}

def installed() { initialize() }
def updated() { unschedule(); unsubscribe(); initialize() }

def initialize() {
    if (state.currentlyFavorable == null) state.currentlyFavorable = false
    if (state.currentAdvice == null) state.currentAdvice = "Initialise"
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
        sendTargetedSms("Ouvrir les fenetres / Free Cooling : Int " + inTemp + "C vs Ext " + extTemp + "C.")
    } else if (!isFavorableThermal && wasFavorable) {
        state.currentlyFavorable = false
        sendTargetedSms("Fermer les fenetres : Int " + inTemp + "C vs Ext " + extTemp + "C.")
    }

    int co2Threshold = (settings.maxCo2Threshold ?: 1000) as int
    boolean highCo2 = air && (air.co2Max > co2Threshold) && !isTooColdOutside

    BigDecimal maxHumLimit = (settings.maxHumidityThreshold ?: 60.0) as BigDecimal
    boolean tooHumid = (inHum > maxHumLimit) && (extDew < inDew) && !isTooColdOutside

    BigDecimal minHumLimit = (settings.minHumidityThreshold ?: 30.0) as BigDecimal
    if (inHum < minHumLimit) {
        if (!state.dryAirAlertSent) {
            sendTargetedSms("Alerte Air Sec : Humidite interieure a " + inHum + "%.")
            state.dryAirAlertSent = true
        }
    } else if (inHum >= (minHumLimit + 2.0)) {
        state.dryAirAlertSent = false
    }

    boolean finalVentilationNeeded = isFavorableThermal || highCo2 || tooHumid || needInternalMixing

    if (isFavorableThermal) {
        state.currentAdvice = "VENTILATION ACTIVE (Free Cooling)"
    } else if (highCo2) {
        state.currentAdvice = "VENTILATION FORCEE (CO2 Max: " + air.co2Max + " ppm)"
    } else if (tooHumid) {
        state.currentAdvice = "VENTILATION FORCEE (Trop humide: " + inHum + "%)"
    } else if (needInternalMixing) {
        state.currentAdvice = "BRASSAGE ACTIF (Ecart pieces: " + inSpread + "C)"
    } else {
        state.currentAdvice = "VENTILATION ARRETEE"
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
                sensorDetails << (dev.displayName + ": " + adjustedT + "C")
            } else {
                excluded << dev.displayName
            }
        } else {
            excluded << dev.displayName
        }
    }

    humSensors?.each { dev ->
        if (!tempSensors?.find { it.id == dev.id }) {
            if (isDeviceResponsive(dev, maxHours) && dev.currentValue("humidity") != null)
