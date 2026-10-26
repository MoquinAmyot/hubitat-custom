definition(
    name: "Gestionnaire Thermique (Delta T, Rosée, 4 Membres & Offsets)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Gestion complète : Écarts internes, Free Cooling, Offsets, CO2 Max par capteur, Humidité, Google Forms et SMS 4 membres.",
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
                        paragraph "Qualité Air (CO2 Max) : " + air.co2Max + " ppm (Déclenché par: " + air.triggeredSensor + " - Seuil effectif: " + air.effectiveThreshold + " ppm)"
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

        section("3. Qualité de l'Air (CO2) & Seuils par Capteur") {
            input "airQualitySensors", "capability.carbonDioxideMeasurement", title: "Capteurs de CO2", required: false, multiple: true
            input "maxCo2Threshold", "number", title: "Seuil CO2 global par défaut (ppm)", defaultValue: 1000, required: true
            
            paragraph "<b>Seuils spécifiques par capteur (laisser à 0 pour utiliser le seuil global) :</b>"
            input "co2Th1", "number", title: "Seuil CO2 Capteur 1 (ppm)", required: false, defaultValue: 0
            input "co2Th2", "number", title: "Seuil CO2 Capteur 2 (ppm)", required: false, defaultValue: 0
            input "co2Th3", "number", title: "Seuil CO2 Capteur 3 (ppm)", required: false, defaultValue: 0
            input "co2Th4", "number", title: "Seuil CO2 Capteur 4 (ppm)", required: false, defaultValue: 0
            input "co2Th5", "number", title: "Seuil CO2 Capteur 5 (ppm)", required: false, defaultValue: 0
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
