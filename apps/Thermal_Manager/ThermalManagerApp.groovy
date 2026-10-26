definition(
    name: "Gestionnaire Thermique (Delta T, Rosée, 4 Membres & Offsets)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Gestion complete : Ecarts internes, Free Cooling (Delta T/Rosee), Offsets, CO2 (Max), Humidite et routage SMS 4 membres.",
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
                    
                    int co2Limit = (settings.maxCo2Threshold ?: 1000) as int
                    String co2Color = (air && air.co2Max > co2Limit) ? "red" : "green"
                    String extHumStr = (ext.humidity != null) ? "${ext.humidity}" : "N/A"
                    String smsColor = activeRecipients ? "green" : "orange"
                    String smsText = activeRecipients ? "${activeRecipients.size()} personne(s) présente(s)" : "Personne (SMS suspendus)"
                    String currentAdviceStr = state.currentAdvice ?: "En attente"
                    
                    String statusHtml = "<b>Intérieur (${ind.activeCount} capteur(s)) :</b> Moy:${ind.temperature} °C (Écart interne: ${ind.tempSpread} °C) | Hum: <b>${ind.humidity} \%</b> \vert{} PR: <b>${ind.dewPoint} °C</b><br>"
                    statusHtml += "<small>Détail capteurs (avec offsets) : ${ind.detailsSummary}</small><br>"
                    if (air) {
                        statusHtml += "<b>Qualité d'Air (CO2 Max) :</b> <span style='color:${co2Color}; font-weight:bold;'>${air.co2Max} ppm</span> (Moy: ${air.co2Avg} ppm \vert{}${air.details})<br>"
                    }
                    statusHtml += "<b>Extérieur (${settings.outdoorWeather.displayName}) :</b> ${ext.temperature} °C \vert{} Hum:${extHumStr} % | PR: <b>${ext.dewPoint} °C</b> (${ext.dewSource})<br>"
                    statusHtml += "<b>Écarts (Int - Ext) :</b> Delta T = <b>${deltaT} °C</b> \vert{} Delta PR = <b>${deltaDew} °C</b><br>"
                    statusHtml += "<b>Destinataires SMS actifs :</b> <span style='color:${smsColor}; font-weight:bold;'>${smsText}</span><br>"
                    statusHtml += "<b>État actuel :</b> <span style='color:blue; font-weight:bold;'>${currentAdviceStr}</span>"
                    
                    if (ind.excludedNames && ind.excludedNames.size() > 0) {
                        String excludedStr = ind.excludedNames.join(", ")
                        statusHtml += "<br><span style='color:red;'>⚠ Capteur(s) exclu(s) : ${excludedStr}</span>"
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
            input "pres1", "capability.presenceSensor", title: "Capteur de présence (B
