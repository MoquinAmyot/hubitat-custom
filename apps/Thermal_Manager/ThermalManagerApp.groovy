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
    dynamicPage(name: "mainPage", title: "Configuration du Gestionnaire Thermique & Air", install: true, uninstall: true) {
        
        if (settings.outdoorWeather && settings.indoorTempSensors) {
            section("Etat Thermique, Air & Humidite Actuel (En direct)") {
                def ext = getOutdoorWeatherData(settings.outdoorWeather)
                def ind = getIndoorAverageData(settings.indoorTempSensors, settings.indoorHumSensors)
                def air = getAirQualityData(settings.airQualitySensors)
                
                if (ext && ind) {
                    BigDecimal deltaT = (ind.temperature - ext.temperature).setScale(2, BigDecimal.ROUND_HALF_UP)
                    BigDecimal deltaDew = (ind.dewPoint - ext.dewPoint).setScale(2, BigDecimal.ROUND_HALF_UP)
                    def activeRecipients = getActiveSmsDevices()
                    
                    String statusHtml = "<b>Interieur (${ind.activeCount} capteur(s)) :</b> Moy: ${ind.temperature} C (Ecart interne:${ind.tempSpread} C) | Hum: <b>${ind.humidity} \%</b> \vert{} PR: <b>${ind.dewPoint} C</b><br>"
                    statusHtml += "<small>Detail capteurs (avec offsets) : ${ind.detailsSummary}</small><br>"
                    if (air) {
                        String co2Color = (air.co2Max > (settings.maxCo2Threshold ?: 1000)) ? "red" : "green"
                        statusHtml += "<b>Qualite d'Air (CO2 Max) :</b> <span style='color:${co2Color}; font-weight:bold;'>${air.co2Max} ppm</span> (Moy: ${air.co2Avg} ppm \vert{}${air.details})<br>"
                    }
                    statusHtml += "<b>Exterieur (${settings.outdoorWeather.displayName}) :</b> ${ext.temperature} C \vert{} Hum:${ext.humidity ?: 'N/A'} % | PR: <b>${ext.dewPoint} C</b> (${ext.dewSource})<br>"
                    statusHtml += "<b>Ecarts (Int - Ext) :</b> Delta T = <b>${deltaT} C</b> \vert{} Delta PR = <b>${deltaDew} C</b><br>"
                    String smsColor = activeRecipients ? "green" : "orange"
                    String smsText = activeRecipients ? "${activeRecipients.size()} personne(s) presente(s)" : "Personne (SMS suspendus)"
                    statusHtml += "<b>Destinataires SMS actifs :</b> <span style='color:${smsColor}; font-weight:bold;'>${smsText}</span><br>"
                    statusHtml += "<b>Etat actuel :</b> <span style='color:blue; font-weight:bold;'>${state.currentAdvice ?: 'En attente'}</span>"
                    
                    if (ind.excludedNames && ind.excludedNames.size() > 0) {
                        statusHtml += "<br><span style='color:red;'>Capteur(s) exclu(s) : ${ind.excludedNames.join(', ')}</span>"
                    }
                    paragraph statusHtml
                } else {
                    paragraph "<span style='color:red;'>Donnees meteo ou interieures indisponibles.</span>"
                }
            }
        }

        section("1. Meteo Exterieure (OpenWeatherMap / Meteo2)") {
            input "outdoorWeather", "capability.temperatureMeasurement", title: "Dispositif Meteo Exterieur (ex: Meteo2)", required: true, multiple: false
        }
        
        section("2. Capteurs Interieurs & Offsets de Calibrage") {
            input "indoorTempSensors", "capability.temperatureMeasurement", title: "Capteurs de Temperature interieurs", required: true, multiple: true
            input "indoorHumSensors", "capability.relativeHumidityMeasurement", title: "Capteurs d'Humidite interieurs (si separes)", required: false, multiple: true
            
            paragraph "<b>Ajustement des Offsets (selon l'ordre de selection ci-dessus) :</b>"
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

        section("3. Qualite de l'Air (CO2 - ex: Capteurs Ikea)") {
            input "airQualitySensors", "capability.airQuality", title: "Capteurs de Qualite d'air / CO2 (Ikea)", required: false, multiple: true
            input "maxCo2Threshold", "number", title: "Seuil de CO2 maximal par piece avant ventilation forcee (ppm)", defaultValue: 1000, required: true
        }

        section("4. Ecarts Internes, Humidite & Protection Hivernale") {
            input "maxInternalTempSpread", "decimal", title: "Ecart max entre pieces avant brassage d'air (C)", defaultValue: 2.0, required: true
            input "maxHumidityThreshold", "decimal", title: "Seuil d'humidite maximale (%) - Active l'echangeur si ext. plus sec", defaultValue: 60.0, required: true
            input "minHumidityThreshold", "decimal", title: "Seuil d'humidite minimale (%) - Envoie un SMS si trop sec", defaultValue: 30.0, required: true
            input "minOutdoorTempForCooling", "decimal", title: "Temperature exterieure minimale pour Free Cooling / Echangeur (C)", defaultValue: -10.0, required: true
        }

        section("5. Gestion de la Famille (4 Membres) & Routage SMS") {
            paragraph "<b>Membre 1 (Principal)</b>"
            input "pres1", "capability.presenceSensor", title: "Capteur de presence (Benoit)", required: true, multiple: false
            input "sms1", "capability.notification", title: "Pilote SMS Voip.ms (Benoit)", required: true, multiple: false
            
            
