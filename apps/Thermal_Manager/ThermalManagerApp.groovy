definition(
    name: "Gestionnaire Thermique (Delta T, Rosée, 4 Membres & Offsets)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Gestion complète : Écarts internes, Free Cooling (Delta T/Rosée), Offsets, CO2 (Max), Humidité et routage SMS 4 membres.",
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
                    
                    String statusHtml = "<b>Intérieur (${ind.activeCount} capteur(s)) :</b> Moy:${ind.temperature} °C (Écart interne: ${ind.tempSpread} °C) | Hum: <b>${ind.humidity} \%</b> \vert{} PR: <b>${ind.dewPoint} °C</b><br>"
                    statusHtml += "<small>Détail capteurs (avec offsets) : ${ind.detailsSummary}</small><br>"
                    if (air) {
                        statusHtml += "<b>Qualité d'Air (CO2 Max) :</b> <span style='color:${air.co2Max > (settings.maxCo2Threshold ?: 1000) ? 'red' : 'green'}; font-weight:bold;'>${air.co2Max} ppm</span> (Moy: ${air.co2Avg} ppm \vert{}${air.details})<br>"
                    }
                    statusHtml += "<b>Extérieur (${settings.outdoorWeather.displayName}) :</b> ${ext.temperature} °C \vert{} Hum:${ext.humidity ?: 'N/A'} % | PR: <b>${ext.dewPoint} °C</b> (${ext.dewSource})<br>"
                    statusHtml += "<b>Écarts (Int - Ext) :</b> Delta T = <b>${deltaT} °C</b> \vert{} Delta PR = <b>${deltaDew} °C</b><br>"
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
            input "maxCo2Threshold", "number", title: "Seuil de CO2 maximal par pièce avant ventilation forcée (ppm)", defaultValue: 1000, required: true
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
    if (
