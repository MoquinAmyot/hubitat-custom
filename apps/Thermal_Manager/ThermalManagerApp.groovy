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
            section("Etat Thermique, Air et Humidite Actuel (En direct)") {
                Map ext = getOutdoorWeatherData(settings.outdoorWeather)
                Map ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)
                Map air = getAirQualityData(settings.airQualitySensors)
                
                if (ext && ind) {
                    BigDecimal deltaT = (ind.temperature - ext.temperature).setScale(2, BigDecimal.ROUND_HALF_UP)
                    BigDecimal deltaDew = (ind.dewPoint - ext.dewPoint).setScale(2, BigDecimal.ROUND_HALF_UP)
                    List activeRecipients = getActiveSmsDevices()
                    
                    int co2Limit = (settings.maxCo2Threshold ?: 1000) as int
                    String co2Color = (air && air.co2Max > co2Limit) ? "red" : "green"
                    String extHumStr = (ext.humidity != null) ? ext.humidity.toString() : "N/A"
                    String smsColor = activeRecipients ? "green" : "orange"
                    String smsText = activeRecipients ? (activeRecipients.size().toString() + " personne(s) presente(s)") : "Personne (SMS suspendus)"
                    String currentAdviceStr = state.currentAdvice ?: "En attente"
                    
                    String statusHtml = "<b>Interieur (" + ind.activeCount + " capteur(s)) :</b> Moy: " + ind.temperature + " C (Ecart: " + ind.tempSpread + " C) - Hum: <b>" + ind.humidity + " %</b> - PR: <b>" + ind.dewPoint + " C</b><br>"
                    statusHtml += "<small>Detail capteurs (avec offsets) : " + ind.detailsSummary + "</small><br>"
                    if (air) {
                        statusHtml += "<b>Qualite Air (CO2 Max) :</b> <span style=color:" + co2Color + ";font-weight:bold;>" + air.co2Max + " ppm</span> (Moy: " + air.co2Avg + " ppm - " + air.details + ")<br>"
                    }
                    statusHtml += "<b>Exterieur (" + settings.outdoorWeather.displayName + ") :</b> " + ext.temperature + " C - Hum: " + extHumStr + " % - PR: <b>" + ext.dewPoint + " C</b> (" + ext.dewSource + ")<br>"
                    statusHtml += "<b>Ecarts (Int - Ext) :</b> Delta T = <b>" + deltaT + " C</b> - Delta PR = <b>" + deltaDew + " C</b><br>"
                    statusHtml += "<b>Destinataires SMS actifs :</b> <span style=color:" + smsColor + ";font-weight:bold;>" + smsText + "</span><br>"
                    statusHtml += "<b>Etat actuel :</b> <span style=color:blue;font-weight:bold;>" + currentAdviceStr + "</span>"
                    
                    if (ind.excludedNames && ind.excludedNames.size() > 0) {
                        String excludedStr = ind.excludedNames.join(", ")
                        statusHtml += "<br><span style=color:red;>Attention - Capteur(s) exclu(s) : " + excludedStr + "</span>"
                    }
                    paragraph statusHtml
                } else {
                    paragraph "<span style=color:red;>Donnees meteo ou interieures indisponibles.</span>"
                }
            }
        }

        section("1. Meteo Exterieure (OpenWeatherMap / Meteo2)") {
            input "outdoorWeather", "capability.temperatureMeasurement", title: "Dispositif Meteo Exterieur (ex: Meteo2)", required: true, multiple: false
        }
        
        section("2. Capteurs Interieurs et Offsets de Calibrage") {
            input "indoorTempSensors", "capability.temperatureMeasurement", title: "Capteurs de Temperature interieurs", required: true, multiple: true
            input "indoorHumSensors", "capability.relativeHumidityMeasurement", title: "Capteurs d Humidite interieurs (si separes)", required: false, multiple: true
            
            paragraph "<b>Ajustement des Offsets (selon l ordre de selection ci-dessus) :</b>"
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

        section("3. Qualite de l Air (CO2 - ex: Capteurs Ikea)") {
            input "airQualitySensors", "capability.carbonDioxideMeasurement", title: "Capteurs de CO2 (carbonDioxide)", required: false, multiple: true
            input "maxCo2Threshold", "number", title: "Seuil de CO2 maximal par piece avant ventilation forcee (ppm)", defaultValue: 1000, required: true
        }

        section("4. Ecarts Internes, Humidite et Protection Hivernale") {
            input "maxInternalTempSpread", "decimal", title: "Ecart max entre pieces avant brassage d air (C)", defaultValue: 2.0, required: true
            input "maxHumidityThreshold", "decimal", title: "Seuil d humidite maximale (%) - Active l echangeur si ext. plus sec", defaultValue: 60.0, required: true
            input "minHumidityThreshold", "decimal", title: "Seuil d humidite minimale (%) - Envoie un SMS si trop sec", defaultValue: 30.0, required: true
            input "minOutdoorTempForCooling", "decimal", title: "Temperature exterieure minimale pour Free Cooling / Echangeur (C)", defaultValue: -10.0, required: true
        }

        section("5. Gestion de la Famille (4 Membres) et Routage SMS") {
            paragraph "<b>Membre 1 (Principal)</b>"
            input "pres1", "capability.presenceSensor", title: "Capteur de presence (Benoit)", required: true, multiple: false
            input "sms1", "capability.notification", title: "Pilote SMS Voip.ms (Benoit)", required: true, multiple: false
            
            paragraph "<b>Membre 2 (Optionnel)</b>"
            input "pres2", "capability.presenceSensor", title: "Capteur de presence", required: false, multiple: false
            input "sms2", "capability.notification", title: "Pilote SMS Voip.ms", required: false, multiple: false
            
            paragraph "<b>Membre 3 (Optionnel)</b>"
            input "pres3", "capability.presenceSensor", title: "Capteur de presence", required: false, multiple: false
            input "sms3", "capability.notification", title: "Pilote SMS Voip.ms", required: false, multiple: false

            paragraph "<b>Membre 4 (Optionnel)</b>"
            input "pres4", "capability.presenceSensor", title: "Capteur de presence", required: false, multiple: false
            input "sms4", "capability.notification", title: "Pilote SMS Voip.ms", required: false, multiple: false
        }
        
        section("6. Securite et Exclusion automatique des capteurs HS") {
            input "maxIndoorInactivityHours", "number", title: "Capteurs INTERIEURS : exclure si muets depuis (heures)", defaultValue: 4, required: true
            input "maxWeatherInactivityHours", "number", title: "Meteo EXTERIEURE (Meteo2) : suspendre si muette depuis (heures)", defaultValue: 3, required: true
            input "notifyOnStaleSensor", "bool", title: "Alerter par SMS si un capteur decroche ?", defaultValue: true
        }
        
        section("7. Seuils de Decision (Fenetres et Free Cooling)") {
            input "minIndoorTempForCooling", "decimal", title: "Temperature interieure minimale pour suggerer d ouvrir (C)", defaultValue: 22.5, required: true
            input "minDeltaT", "decimal", title: "Delta T minimal requis (Int - Ext en C)", defaultValue: 1.5, required: true
            input "maxOutdoorDewPoint", "decimal", title: "Point de rosee exterieur maximal tolere (C)", defaultValue: 16.0, required: true
            input "requireLowerOutdoorDewPoint", "bool", title: "Exiger que le PR exterieur soit inferieur au PR interieur ?", defaultValue: true
            input "hysteresisTemp", "decimal", title: "Hysteresis (C)", defaultValue: 0.5, required: true
        }

        section("8. Controle Automatique de la Ventilation (Echangeur / Fan)") {
            input "thermostatDevice", "capability.thermostat", title: "Thermostat (Ecobee fanOn / fanAuto)", required: false, multiple: false
            input "fanSwitch", "capability.switch", title: "Interrupteur / Relais d echangeur d air (ON/OFF)", required: false, multiple: false
            input "contactSensors", "capability.contactSensor", title: "Capteurs de fenetres (Optionnel)", required: false, multiple: true
        }

        section("9. Journalisation Google Forms (Optionnel)") {
            input "enableGoogleLogging", "bool", title: "Activer l envoi vers Google Forms ?", defaultValue: false
            input "googleFormUrl", "text", title: "URL Google Forms (se terminant par /formResponse)", required: false
            input "entryInTemp", "text", title: "ID Champ Temp Interieure (ex: entry.123456)", required: false
            input "entryInHum", "text", title: "ID Champ Hum Interieure", required: false
            input "entryInDew", "text", title: "ID Champ PR Interieur", required: false
            input "entryExtTemp", "text", title: "ID Champ Temp Exterieure", required: false
            input "entryExtHum", "text", title: "ID Champ Hum Exterieure", required: false
            input "entryExtDew", "text", title: "ID Champ PR Exterieur", required: false
            input "entryCo2Max", "text", title: "ID Champ CO2 Max", required: false
            input "entryAdvice", "text", title: "ID Champ Etat / Decision", required: false
        }

        section("10. Parametres de Notification SMS et Frequence") {
            input "allowedModes", "mode", title: "Modes autorises pour les SMS (laisser vide = tous)", required: false, multiple: true
            input "minMinutesBetweenSms", "number", title: "Delai minimal entre deux SMS (Anti-spam en minutes)", defaultValue: 120, required: true
            input "evalIntervalMinutes", "enum", title: "Frequence d evaluation (Intervalle en minutes)", options: ["5": "5 min", "10": "10 min", "15": "15 min", "30": "30 min"], defaultValue: "15", required: true
            input "runEvalNow", "bool", title: "Forcer une evaluation immediate", defaultValue: false, submitOnChange: true
        }

        if (settings.runEvalNow) {
            app.updateSetting("runEvalNow", [value: "false", type: "bool"])
            evaluateThermalConditions()
            section { paragraph "<span style=color:green;>Evaluation executee avec succes ! Consultez les logs.</span>" }
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
    if (pres4
