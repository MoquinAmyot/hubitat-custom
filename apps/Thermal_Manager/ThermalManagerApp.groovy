definition(
    name: "Gestionnaire Thermique (Delta T, Rosée, 4 Membres & Offsets)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Gestion complete : Ecarts internes, Free Cooling, Offsets, CO2 Max par capteur, Humidite, Google Forms et SMS 4 membres.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

// =====================================================================
// PAGE DE CONFIGURATION
// =====================================================================

def mainPage() {
    dynamicPage(name: "mainPage", title: "Configuration du Gestionnaire Thermique & Air", install: true, uninstall: true) {

        if (settings.outdoorWeather && settings.indoorTempSensors) {
            section("Etat Thermique, Air et Humidite Actuel") {
                Map ext = getOutdoorWeatherData(settings.outdoorWeather)
                Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)
                Map air = getAirQualityData(settings.airQualitySensors, false)

                if (ind.temperature != null) {
                    paragraph "Interieur : Moyenne " + fmt(ind.temperature) + " C (Ecart: " + fmt(ind.tempSpread) + " C) / Humidite: " + fmt(ind.humidity) + " % / Point de rosee: " + fmt(ind.dewPoint) + " C"
                    paragraph "Detail capteurs : " + ind.detailsSummary
                } else {
                    paragraph "Capteurs interieurs indisponibles."
                }

                if (air && air.co2Max != null) {
                    paragraph "Qualite Air (CO2 Max) : " + air.co2Max + " ppm (Declenche par: " + air.triggeredSensor + " / Moyenne: " + air.co2Avg + " ppm)"
                    paragraph "Detail CO2 : " + air.details
                } else if (settings.airQualitySensors) {
                    paragraph "Capteurs de CO2 indisponibles."
                }

                if (ext) {
                    paragraph "Exterieur : " + fmt(ext.temperature) + " C / Humidite: " + fmt(ext.humidity) + " % / Point de rosee: " + fmt(ext.dewPoint) + " C (" + ext.dewSource + ")"
                } else {
                    paragraph "Meteo exterieure indisponible (muette ou sans temperature)."
                }

                if (ext && ind.temperature != null) {
                    BigDecimal deltaT = r2(ind.temperature - ext.temperature)
                    BigDecimal deltaDew = (ind.dewPoint != null && ext.dewPoint != null) ? r2(ind.dewPoint - ext.dewPoint) : null
                    paragraph "Ecarts (Int - Ext) : Delta T = " + deltaT + " C / Delta PR = " + fmt(deltaDew) + " C"
                }

                List openWindows = settings.contactSensors?.findAll { it.currentValue("contact") == "open" }*.displayName ?: []
                if (settings.contactSensors) {
                    paragraph "Fenetres ouvertes : " + (openWindows ? openWindows.join(", ") : "Aucune")
                }

                List activeRecipients = getActiveSmsDevices()
                paragraph "Destinataires SMS actifs : " + (activeRecipients ? (activeRecipients.size() + " personne(s)") : "Personne")
                paragraph "Etat actuel : " + (state.currentAdvice ?: "En attente")

                List<String> allExcluded = []
                if (ind.excludedNames) allExcluded.addAll(ind.excludedNames)
                if (air?.excludedNames) allExcluded.addAll(air.excludedNames)
                if (!ext && settings.outdoorWeather) allExcluded << settings.outdoorWeather.displayName.toString()
                allExcluded = allExcluded.unique()

                if (allExcluded) {
                    paragraph "Attention - Capteur(s) exclu(s) : " + allExcluded.join(", ")
                }
            }
        }

        section("1. Meteo Exterieure") {
            input "outdoorWeather", "capability.temperatureMeasurement", title: "Dispositif Meteo Exterieur", required: true, multiple: false
        }

        section("2. Capteurs Interieurs et Offsets") {
            input "indoorTempSensors", "capability.temperatureMeasurement", title: "Capteurs de Temperature", required: true, multiple: true, submitOnChange: true
            input "indoorHumSensors", "capability.relativeHumidityMeasurement", title: "Capteurs d Humidite", required: false, multiple: true, submitOnChange: true

            if (settings.indoorTempSensors) {
                paragraph "Offsets de temperature (par capteur) :"
                settings.indoorTempSensors.eachWithIndex { dev, i ->
                    def legacy = legacySetting("offsetTemp", i)
                    input("offT_" + dev.id, "decimal", title: "Temp - " + dev.displayName + " (C)", required: false, defaultValue: (legacy != null ? legacy : 0.0))
                }
            }
            List humDevs = getHumidityDevices()
            if (humDevs) {
                paragraph "Offsets d humidite (par capteur) :"
                humDevs.each { dev ->
                    def legacy = legacySetting("offsetHum", indexInTempSensors(dev))
                    input("offH_" + dev.id, "decimal", title: "Hum - " + dev.displayName + " (%)", required: false, defaultValue: (legacy != null ? legacy : 0.0))
                }
            }
        }

        section("3. Qualite de l Air (CO2) et Seuils par Capteur") {
            input "airQualitySensors", "capability.carbonDioxideMeasurement", title: "Capteurs de CO2", required: false, multiple: true, submitOnChange: true
            input "maxCo2Threshold", "number", title: "Seuil CO2 global par defaut (ppm)", defaultValue: 1000, required: true
            input "co2Hysteresis", "number", title: "Hysteresis CO2 avant arret de la ventilation (ppm)", defaultValue: 50, required: true

            if (settings.airQualitySensors) {
                paragraph "Seuils specifiques par capteur (0 = seuil global) :"
                settings.airQualitySensors.eachWithIndex { dev, i ->
                    def legacy = legacySetting("co2Th", i)
                    input("co2Th_" + dev.id, "number", title: "Seuil CO2 - " + dev.displayName + " (ppm)", required: false, defaultValue: (legacy != null ? legacy : 0))
                }
            }
        }

        section("4. Ecarts Internes, Humidite et Hiver") {
            input "maxInternalTempSpread", "decimal", title: "Ecart max entre pieces (C)", defaultValue: 2.0, required: true
            input "maxHumidityThreshold", "decimal", title: "Seuil humidite max (%)", defaultValue: 60.0, required: true
            input "minHumidityThreshold", "decimal", title: "Seuil humidite min (%)", defaultValue: 30.0, required: true
            input "humidityHysteresis", "decimal", title: "Hysteresis humidite (%)", defaultValue: 2.0, required: true
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
            input "maxIndoorInactivityHours", "number", title: "Exclure interieur si muet (h)", defaultValue: 4, required: true
            input "maxWeatherInactivityHours", "number", title: "Suspendre meteo si muette (h)", defaultValue: 3, required: true
            input "notifyOnStaleSensor", "bool", title: "Alerter si capteur decroche ?", defaultValue: true
        }

        section("7. Seuils Free Cooling") {
            input "minIndoorTempForCooling", "decimal", title: "Temp interieure min (C)", defaultValue: 22.5, required: true
            input "minDeltaT", "decimal", title: "Delta T minimal (C)", defaultValue: 1.5, required: true
            input "maxOutdoorDewPoint", "decimal", title: "Point de rosee ext max (C)", defaultValue: 16.0, required: true
            input "requireLowerOutdoorDewPoint", "bool", title: "PR ext inferieur au PR int ?", defaultValue: true
            input "hysteresisTemp", "decimal", title: "Hysteresis thermique (C)", defaultValue: 0.5, required: true
        }

        section("8. Ventilation") {
            input "thermostatDevice", "capability.thermostat", title: "Thermostat Ecobee", required: false, multiple: false
            input "fanSwitch", "capability.switch", title: "Interrupteur Echangeur", required: false, multiple: false
            input "contactSensors", "capability.contactSensor", title: "Capteurs de fenetres (affichage seulement)", required: false, multiple: true
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
            input "allowedModes", "mode", title: "Modes autorises pour les SMS (vide = tous)", required: false, multiple: true
            input "minMinutesBetweenSms", "number", title: "Delai min. entre 2 SMS Ouvrir (min)", defaultValue: 120, required: true
            input "evalIntervalMinutes", "enum", title: "Frequence evaluation", options: ["5": "5 min", "10": "10 min", "15": "15 min", "30": "30 min"], defaultValue: "15", required: true
            input "runEvalNow", "bool", title: "Forcer evaluation", defaultValue: false, submitOnChange: true
        }

        if (settings.runEvalNow) {
            app.updateSetting("runEvalNow", [value: "false", type: "bool"])
            evaluateThermalConditions()
            section { paragraph "Evaluation executee : " + (state.currentAdvice ?: "") }
        }
    }
}

// =====================================================================
// CYCLE DE VIE
// =====================================================================

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
    if (state.notifiedFavorable == null) state.notifiedFavorable = state.currentlyFavorable
    if (state.currentAdvice == null) state.currentAdvice = "Initialise"
    if (state.alertedStaleDevices == null) state.alertedStaleDevices = []
    if (state.dryAirAlertSent == null) state.dryAirAlertSent = false
    if (state.co2ActiveIds == null) state.co2ActiveIds = []
    if (state.humVentActive == null) state.humVentActive = false
    if (state.mixingActive == null) state.mixingActive = false
    migrateFanState()

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

    String interval = (settings.evalIntervalMinutes ?: "15").toString()
    if (interval == "5") runEvery5Minutes(evaluateThermalConditions)
    else if (interval == "10") runEvery10Minutes(evaluateThermalConditions)
    else if (interval == "30") runEvery30Minutes(evaluateThermalConditions)
    else runEvery15Minutes(evaluateThermalConditions)

    evaluateThermalConditions()
}

def eventHandler(evt) {
    runIn(10, "evaluateFromEvent")
}

def evaluateFromEvent() {
    runEvaluation(false)
}

def evaluateThermalConditions() {
    runEvaluation(true)
}

// =====================================================================
// LOGIQUE PRINCIPALE
// =====================================================================

def runEvaluation(boolean logToForms) {
    Map ext = getOutdoorWeatherData(settings.outdoorWeather)
    Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)
    Map air = getAirQualityData(settings.airQualitySensors, true)

    List<String> excluded = []
    if (ind.excludedNames) excluded.addAll(ind.excludedNames)
    if (air?.excludedNames) excluded.addAll(air.excludedNames)
    if (!ext && settings.outdoorWeather) excluded << settings.outdoorWeather.displayName.toString()
    handleStaleSensorNotifications(excluded.unique())

    BigDecimal minExtTempForCooling = decSetting("minOutdoorTempForCooling", -10.0)
    BigDecimal extTemp = ext?.temperature
    boolean weatherOk = (extTemp != null)
    boolean isTooColdOutside = weatherOk && (extTemp < minExtTempForCooling)

    // Aucun capteur interieur de temperature valide : repli sur CO2 uniquement
    if (ind.temperature == null) {
        boolean co2Only = (air?.co2Exceeded ?: false) && !isTooColdOutside
        state.currentAdvice = co2Only ? ("VENTILATION FORCEE (CO2: " + air.triggeredSensor + ") - temp. int. indisponible") : "CAPTEURS INTERIEURS INDISPONIBLES"
        manageVentilation(co2Only)
        return
    }

    BigDecimal inTemp = ind.temperature
    BigDecimal inHum = ind.humidity
    BigDecimal inDew = ind.dewPoint
    BigDecimal inSpread = ind.tempSpread
    BigDecimal extDew = ext?.dewPoint

    BigDecimal hyst = decSetting("hysteresisTemp", 0.5)
    BigDecimal humHyst = decSetting("humidityHysteresis", 2.0)
    BigDecimal maxSpread = decSetting("maxInternalTempSpread", 2.0)
    BigDecimal minInTemp = decSetting("minIndoorTempForCooling", 22.5)
    BigDecimal reqDeltaT = decSetting("minDeltaT", 1.5)
    BigDecimal maxExtDew = decSetting("maxOutdoorDewPoint", 16.0)
    BigDecimal maxHumLimit = decSetting("maxHumidityThreshold", 60.0)
    BigDecimal minHumLimit = decSetting("minHumidityThreshold", 30.0)
    boolean checkDewDiff = (settings.requireLowerOutdoorDewPoint != false)

    // 1. Brassage interne avec hysteresis
    boolean wasMixing = state.mixingActive ?: false
    boolean needInternalMixing = !wasMixing ? (inSpread >= maxSpread) : (inSpread >= (maxSpread - hyst))
    state.mixingActive = needInternalMixing

    // 2. Free cooling avec hysteresis (seulement si la meteo est fiable)
    boolean isFavorableThermal = false
    if (weatherOk) {
        boolean wasFavorable = state.currentlyFavorable ?: false
        BigDecimal deltaT = r2(inTemp - extTemp)
        if (!isTooColdOutside && extDew != null) {
            if (!wasFavorable) {
                isFavorableThermal = (inTemp >= minInTemp) && (deltaT >= reqDeltaT) && (extDew <= maxExtDew) &&
                    (!checkDewDiff || inDew == null || extDew < inDew)
            } else {
                isFavorableThermal = (inTemp >= (minInTemp - hyst)) && (deltaT >= (reqDeltaT - hyst)) && (extDew <= (maxExtDew + hyst)) &&
                    (!checkDewDiff || inDew == null || extDew <= (inDew + hyst))
            }
        }
        state.currentlyFavorable = isFavorableThermal
        notifyFreeCoolingIfNeeded(inTemp, extTemp)
    }

    // 3. CO2 avec hysteresis par capteur (calcule dans getAirQualityData)
    boolean highCo2 = (air?.co2Exceeded ?: false) && !isTooColdOutside

    // 4. Humidite haute avec hysteresis
    boolean wasHumVent = state.humVentActive ?: false
    boolean tooHumid = false
    if (!isTooColdOutside && inHum != null && inDew != null && extDew != null) {
        if (!wasHumVent) {
            tooHumid = (inHum > maxHumLimit) && (extDew < inDew)
        } else {
            tooHumid = (inHum > (maxHumLimit - humHyst)) && (extDew <= (inDew + hyst))
        }
    }
    state.humVentActive = tooHumid

    // 5. Alerte Air Sec avec hysteresis
    if (inHum != null) {
        if (inHum < minHumLimit) {
            if (!state.dryAirAlertSent) {
                if (sendTargetedSms("Alerte Air Sec : Humidite interieure a " + inHum + "%.", null, true)) {
                    state.dryAirAlertSent = true
                }
            }
        } else if (inHum >= (minHumLimit + humHyst)) {
            state.dryAirAlertSent = false
        }
    }

    boolean finalVentilationNeeded = isFavorableThermal || highCo2 || tooHumid || needInternalMixing

    String advice
    if (isFavorableThermal) {
        advice = "VENTILATION ACTIVE (Free Cooling)"
    } else if (highCo2) {
        advice = "VENTILATION FORCEE (CO2 Depasse: " + air.triggeredSensor + ")"
    } else if (tooHumid) {
        advice = "VENTILATION FORCEE (Trop humide: " + inHum + "%)"
    } else if (needInternalMixing) {
        advice = "BRASSAGE ACTIF (Ecart pieces: " + inSpread + "C)"
    } else {
        advice = "VENTILATION ARRETEE"
    }
    if (!weatherOk) advice += " - METEO INDISPONIBLE"
    state.currentAdvice = advice

    manageVentilation(finalVentilationNeeded)
    if (logToForms) logToGoogleForms(ind, ext, air, advice)
}

def notifyFreeCoolingIfNeeded(BigDecimal inTemp, BigDecimal extTemp) {
    boolean current = state.currentlyFavorable ?: false
    if (state.notifiedFavorable == null) {
        state.notifiedFavorable = current
        return
    }
    boolean notified = state.notifiedFavorable
    if (current == notified) return

    String msg = current ?
        ("Ouvrir les fenetres / Free Cooling : Int " + inTemp + "C vs Ext " + extTemp + "C.") :
        ("Fermer les fenetres : Int " + inTemp + "C vs Ext " + extTemp + "C.")
    if (sendTargetedSms(msg, current ? "freeCoolingOpen" : null, true)) {
        state.notifiedFavorable = current
    }
}

// =====================================================================
// SMS
// =====================================================================

List getActiveSmsDevices() {
    List activeSms = []
    if (sms1 && (!pres1 || pres1.currentValue("presence") == "present")) activeSms << sms1
    if (sms2 && pres2 && pres2.currentValue("presence") == "present") activeSms << sms2
    if (sms3 && pres3 && pres3.currentValue("presence") == "present") activeSms << sms3
    if (sms4 && pres4 && pres4.currentValue("presence") == "present") activeSms << sms4
    return activeSms.unique { it.id }
}

boolean sendTargetedSms(String msg, String throttleKey, boolean respectModes) {
    if (respectModes && settings.allowedModes && !(location.mode in settings.allowedModes)) {
        log.debug "SMS retenu (mode " + location.mode + ") : " + msg
        return false
    }
    if (throttleKey) {
        Map last = (state.lastSmsTimes ?: [:]) as Map
        def t = last[throttleKey]
        long minMs = intSetting("minMinutesBetweenSms", 120) * 60000L
        if (t != null && (now() - (t as Long)) < minMs) {
            log.debug "SMS retenu (delai) : " + msg
            return false
        }
    }
    List recipients = getActiveSmsDevices()
    if (!recipients) {
        log.debug "SMS retenu (personne a la maison) : " + msg
        return false
    }
    recipients.each { it.deviceNotification(msg) }
    if (throttleKey) {
        Map last = (state.lastSmsTimes ?: [:]) as Map
        last[throttleKey] = now()
        state.lastSmsTimes = last
    }
    log.info "SMS envoye a " + recipients.size() + " personne(s) : " + msg
    return true
}

def handleStaleSensorNotifications(List<String> currentExcluded) {
    List<String> prev = (state.alertedStaleDevices ?: []) as List<String>
    List<String> stillAlerted = prev.findAll { it in currentExcluded }
    List<String> newlyStale = currentExcluded.findAll { !(it in prev) }
    if (newlyStale && settings.notifyOnStaleSensor) {
        if (sendTargetedSms("Capteur(s) exclu(s) : " + newlyStale.join(", "), null, false)) {
            stillAlerted.addAll(newlyStale)
        }
    }
    state.alertedStaleDevices = stillAlerted
}

// =====================================================================
// VENTILATION
// =====================================================================

def manageVentilation(boolean shouldRunFan) {
    migrateFanState()
    def thermo = settings.thermostatDevice
    def sw = settings.fanSwitch
    if (shouldRunFan) {
        if (thermo && thermo.currentValue("thermostatFanMode") != "on") {
            thermo.fanOn()
            state.thermoFanByApp = true
        }
        if (sw && sw.currentValue("switch") != "on") {
            sw.on()
            state.switchByApp = true
        }
    } else {
        if (state.thermoFanByApp) {
            if (thermo && thermo.currentValue("thermostatFanMode") != "auto") thermo.fanAuto()
            state.thermoFanByApp = false
        }
        if (state.switchByApp) {
            if (sw && sw.currentValue("switch") != "off") sw.off()
            state.switchByApp = false
        }
    }
}

def migrateFanState() {
    if (state.fanControlledByApp != null) {
        if (state.fanControlledByApp) {
            state.thermoFanByApp = true
            state.switchByApp = true
        }
        state.remove("fanControlledByApp")
    }
}

// =====================================================================
// GOOGLE FORMS
// =====================================================================

def logToGoogleForms(Map ind, Map ext, Map air, String advice) {
    if (!settings.enableGoogleLogging || !settings.googleFormUrl) return
    String finalUrl = settings.googleFormUrl.trim().replace("/viewform", "/formResponse")
    List<String> parts = []
    addFormField(parts, settings.entryInTemp, ind?.temperature)
    addFormField(parts, settings.entryInHum, ind?.humidity)
    addFormField(parts, settings.entryInDew, ind?.dewPoint)
    addFormField(parts, settings.entryExtTemp, ext?.temperature)
    addFormField(parts, settings.entryExtHum, ext?.humidity)
    addFormField(parts, settings.entryExtDew, ext?.dewPoint)
    addFormField(parts, settings.entryCo2Max, air?.co2Max)
    addFormField(parts, settings.entryAdvice, advice)
    if (parts.isEmpty()) return
    try {
        asynchttpPost("googleFormsCallback", [uri: finalUrl, requestContentType: "application/x-www-form-urlencoded", body: parts.join("&"), timeout: 10])
    } catch (Exception e) {
        log.warn "Google Forms : " + e.message
    }
}

def addFormField(List<String> parts, String entry, value) {
    if (!entry || value == null) return
    parts << (URLEncoder.encode(entry.trim(), "UTF-8") + "=" + URLEncoder.encode(value.toString(), "UTF-8"))
}

def googleFormsCallback(response, data) {
    if (response.hasError() || response.status >= 400) {
        String errMsg = response.hasError() ? response.getErrorMessage() : ""
        log.warn "Google Forms : HTTP " + response.status + " " + errMsg
    }
}

// =====================================================================
// LECTURE DES CAPTEURS
// =====================================================================

boolean isDeviceResponsive(dev, int maxHours) {
    if (!dev) return false
    if (dev.hasAttribute("healthStatus") && dev.currentValue("healthStatus") == "offline") return false
    Date lastAct = dev.getLastActivity()
    if (!lastAct) return false
    return ((now() - lastAct.time) <= (maxHours * 3600000L))
}

Map getOutdoorWeatherData(weatherDev) {
    if (!weatherDev) return null
    if (!isDeviceResponsive(weatherDev, intSetting("maxWeatherInactivityHours", 3))) return null
    def rawT = weatherDev.currentValue("temperature")
    if (rawT == null) return null
    BigDecimal extTemp = rawT as BigDecimal

    def rawH = weatherDev.currentValue("humidity")
    BigDecimal extHum = (rawH != null) ? (rawH as BigDecimal) : null

    def rawDew = weatherDev.currentValue("dewpoint")
    if (rawDew == null) rawDew = weatherDev.currentValue("dewPoint")

    BigDecimal extDewPoint = null
    String dewSource = "N/A"
    if (rawDew != null) {
        extDewPoint = r2(rawDew as BigDecimal)
        dewSource = "direct"
    } else if (extHum != null) {
        extDewPoint = calculateDewPoint(extTemp, extHum)
        dewSource = "magnus"
    }
    return [temperature: extTemp, humidity: extHum, dewPoint: extDewPoint, dewSource: dewSource]
}

Map getIndoorAverageData(tempSensors, humSensors) {
    int maxHours = intSetting("maxIndoorInactivityHours", 4)
    List<BigDecimal> validTemps = []
    List<BigDecimal> validHums = []
    List<String> excluded = []
    List<String> sensorDetails = []

    tempSensors?.each { dev ->
        def rawT = dev.currentValue("temperature")
        if (isDeviceResponsive(dev, maxHours) && rawT != null) {
            BigDecimal adjustedT = r2((rawT as BigDecimal) + decSetting("offT_" + dev.id, 0.0))
            if (adjustedT >= 5.0 && adjustedT <= 45.0) {
                validTemps << adjustedT
                String detail = dev.displayName + ": " + adjustedT + "C"
                def rawH = dev.hasAttribute("humidity") ? dev.currentValue("humidity") : null
                if (rawH != null) {
                    BigDecimal adjustedH = r1((rawH as BigDecimal) + decSetting("offH_" + dev.id, 0.0))
                    if (adjustedH >= 0.0 && adjustedH <= 100.0) {
                        validHums << adjustedH
                        detail += " " + adjustedH + "%"
                    }
                }
                sensorDetails << detail
            } else {
                excluded << dev.displayName.toString()
            }
        } else {
            excluded << dev.displayName.toString()
        }
    }

    humSensors?.each { dev ->
        if (!tempSensors?.find { it.id == dev.id }) {
            def rawH = dev.currentValue("humidity")
            if (isDeviceResponsive(dev, maxHours) && rawH != null) {
                BigDecimal adjustedH = r1((rawH as BigDecimal) + decSetting("offH_" + dev.id, 0.0))
                if (adjustedH >= 0.0 && adjustedH <= 100.0) validHums << adjustedH
            } else {
                excluded << dev.displayName.toString()
            }
        }
    }

    Map result = [temperature: null, humidity: null, dewPoint: null, tempSpread: null, activeCount: validTemps.size(),
                  excludedNames: excluded.unique(), detailsSummary: sensorDetails.join(" / ")]
    if (validTemps.isEmpty()) return result

    BigDecimal avgTemp = r2(validTemps.sum() / validTemps.size())
    BigDecimal avgHum = validHums.isEmpty() ? null : r1(validHums.sum() / validHums.size())
    result.temperature = avgTemp
    result.humidity = avgHum
    result.dewPoint = calculateDewPoint(avgTemp, avgHum)
    result.tempSpread = (validTemps.size() > 1) ? r2(validTemps.max() - validTemps.min()) : 0.0
    return result
}

Map getAirQualityData(airSensors, boolean updateState = true) {
    if (!airSensors) return null
    int maxHours = intSetting("maxIndoorInactivityHours", 4)
    int globalTh = intSetting("maxCo2Threshold", 1000)
    int co2Hyst = intSetting("co2Hysteresis", 50)
    List<String> prevActiveIds = (state.co2ActiveIds ?: []) as List<String>
    List<String> newActiveIds = []
    List<Integer> co2Values = []
    List<String> details = []
    List<String> triggered = []
    List<String> excluded = []

    airSensors.each { dev ->
        def co2 = dev.currentValue("carbonDioxide")
        if (isDeviceResponsive(dev, maxHours) && co2 != null) {
            int val = (co2 as BigDecimal).intValue()
            if (val >= 250 && val <= 10000) {
                co2Values << val
                int custom = intSetting("co2Th_" + dev.id, 0)
                int th = (custom > 0) ? custom : globalTh
                String devId = dev.id.toString()
                boolean wasActive = (devId in prevActiveIds)
                int stopTh = Math.max(400, th - co2Hyst)
                boolean isActive = !wasActive ? (val > th) : (val > stopTh)

                if (isActive) {
                    newActiveIds << devId
                    triggered << (dev.displayName + " (" + val + " ppm, seuil " + th + ")")
                }
                details << (dev.displayName + ": " + val + " ppm (Seuil: " + th + ")")
            } else {
                excluded << dev.displayName.toString()
            }
        } else {
            excluded << dev.displayName.toString()
        }
    }

    if (updateState) {
        state.co2ActiveIds = newActiveIds
    }

    if (co2Values.isEmpty()) {
        return [
            co2Max: null,
            co2Avg: null,
            co2Exceeded: false,
            triggeredSensor: "Aucun",
            effectiveThreshold: globalTh,
            details: "Aucun capteur actif",
            excludedNames: excluded.unique()
        ]
    }

    return [
        co2Max: co2Values.max(),
        co2Avg: (int) Math.round((co2Values.sum() as double) / co2Values.size()),
        co2Exceeded: !triggered.isEmpty(),
        triggeredSensor: triggered ? triggered.join(", ") : "Aucun",
        effectiveThreshold: globalTh,
        details: details.join(", "),
        excludedNames: excluded.unique()
    ]
}

BigDecimal calculateDewPoint(BigDecimal tempC, BigDecimal relHum) {
    if (tempC == null || relHum == null) return null
    double t = tempC as double
    double rh = Math.max(1.0d, Math.min(100.0d, relHum as double))
    double a = 17.62d, b = 243.12d
    double alpha = ((a * t) / (b + t)) + Math.log(rh / 100.0d)
    return r2(new BigDecimal((b * alpha) / (a - alpha)))
}

// =====================================================================
// UTILITAIRES
// =====================================================================

BigDecimal decSetting(String name, BigDecimal dflt) {
    def v = settings[name]
    return (v != null && v.toString().trim() != "") ? (v as BigDecimal) : dflt
}

int intSetting(String name, int dflt) {
    def v = settings[name]
    return (v != null && v.toString().trim() != "") ? (v as BigDecimal).intValue() : dflt
}

BigDecimal r1(BigDecimal v) { return v?.setScale(1, BigDecimal.ROUND_HALF_UP) }
BigDecimal r2(BigDecimal v) { return v?.setScale(2, BigDecimal.ROUND_HALF_UP) }

String fmt(v) { return (v != null) ? v.toString() : "N/A" }

def legacySetting(String prefix, int idx) {
    return (idx >= 0 && idx < 5) ? settings[prefix + (idx + 1)] : null
}

int indexInTempSensors(dev) {
    List list = settings.indoorTempSensors ?: []
    return list.findIndexOf { it.id == dev.id }
}

List getHumidityDevices() {
    List devs = []
    settings.indoorTempSensors?.each { if (it.hasAttribute("humidity")) devs << it }
    settings.indoorHumSensors?.each { h -> if (!devs.find { it.id == h.id }) devs << h }
    return devs
}
