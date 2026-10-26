definition(
    name: "Enregistreur de Données d'Éclairage (Google Forms & CSV)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Enregistre l'heure, les données météo/lux et l'état des switches vers Google Forms (Google Sheets) et/ou fichier CSV local.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def getDefaultGoogleUrl() {
    return "https://docs.google.com/forms/d/e/1FAIpQLSf05R_f64CxUdU6QbwEWpZyvrI5M6FJ4Hom54SLffNxDmwVgA/viewform?usp=pp_url&entry.514406997=Zone&entry.829383594=Type&entry.1703054039=Appareil&entry.1121258566=Valeur&entry.205995306=Mode&entry.276576955=Luxarr&entry.1160942133=Luxavant&entry.1809298243=Ilot&entry.1453255253=Spots&entry.1791513921=Sous&entry.995727929=Fam&entry.1240644563=Diner&entry.1519237034=Foyer&entry.30777479=Salonspots"
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Collecte de Données : Lumières, Heures, Lux & Météo", install: true, uninstall: true) {
        
        section("1. Station Météo & Capteurs Lux Extérieurs") {
            input "weatherDevice", "capability.illuminanceMeasurement", title: "Appareil Météo (pour Cloud, UV, Humidité, Pression, Lux ext.)", required: true, multiple: false
            input "hueBack", "capability.illuminanceMeasurement", title: "Capteur Hue Arrière (Lux)", required: true, multiple: false
            input "hueFront", "capability.illuminanceMeasurement", title: "Capteur Hue Avant (Lux)", required: true, multiple: false
        }

        section("2. Zone Arrière (Cuisine, Dînette & Salle familiale)") {
            input "kitchenIlotSwitch", "capability.switch", title: "Cuisine - Îlot", required: true, multiple: false
            input "kitchenSpotsSwitch", "capability.switch", title: "Cuisine - Spots", required: true, multiple: false
            input "kitchenUnderCabSwitch", "capability.switch", title: "Cuisine - Sous les armoires", required: true, multiple: false
            input "familySwitch", "capability.switch", title: "Salle familiale (1 switch)", required: true, multiple: false
        }
        
        section("3. Zone Avant (Salle à dîner & Salon)") {
            input "diningSwitch", "capability.switch", title: "Salle à dîner (1 switch)", required: true, multiple: false
            input "livingFoyerSwitch", "capability.switch", title: "Salon - Foyer", required: true, multiple: false
            input "livingSpotsSwitch", "capability.switch", title: "Salon - Spots", required: true, multiple: false
        }
        
        section("4. Intégration Google Forms (Temps réel vers Google Sheets)") {
            input "enableGoogleForm", "bool", title: "Envoyer les données vers Google Forms ?", defaultValue: true, submitOnChange: true
            if (settings.enableGoogleForm != false) {
                input "googlePrefilledUrl", "text", title: "URL pré-remplie de votre Google Form", required: true, defaultValue: getDefaultGoogleUrl(), submitOnChange: true
                
                String activeUrl = settings.googlePrefilledUrl ?: getDefaultGoogleUrl()
                def parsed = parseGoogleFormUrl(activeUrl)
                if (parsed.valid) {
                    paragraph "<span style='color:green; font-weight:bold;'>✓ URL Google Form valide détectée !</span><br>" +
                              "Champs détectés : <b>${parsed.entries.size()} colonnes</b>"
                    input "sendTestNow", "bool", title: "Cochez pour envoyer une ligne de test immédiate", defaultValue: false, submitOnChange: true
                } else {
                    paragraph "<span style='color:red; font-weight:bold;'>⚠ URL invalide.</span>"
                }
            }
        }

        if (settings.sendTestNow) {
            app.updateSetting("sendTestNow", [value: "false", type: "bool"])
            recordEvent("Global", "TEST_MANUEL", "Interface_Hubitat", "Test_OK")
            section { paragraph "<span style='color:blue; font-weight:bold;'>✓ Ligne de test envoyée à Google Forms !</span>" }
        }
        
        section("5. Options d'enregistrement & Fichier Local") {
            input "logPeriodic", "bool", title: "Enregistrer un relevé automatique toutes les heures (60 minutes) ?", defaultValue: true
            input "saveToFile", "bool", title: "Sauvegarder AUSSI dans un fichier CSV local ?", defaultValue: false, submitOnChange: true
            if (settings.saveToFile) {
                input "monthlyRotation", "bool", title: "Rotation mensuelle du fichier CSV ?", defaultValue: true
            }
            input "clearHistory", "bool", title: "Cochez pour vider l'aperçu en mémoire", defaultValue: false, submitOnChange: true
        }

        if (settings.clearHistory) {
            state.logEntries = []
            app.updateSetting("clearHistory", [value: "false", type: "bool"])
            section { paragraph "<b>Aperçu en mémoire effacé.</b>" }
        }

        section("6. Aperçu des 25 derniers événements") {
            def entries = state.logEntries ?: []
            if (entries.size() > 0) {
                def header = getCsvHeader()
                def csvPreview = header + entries.takeRight(25).join("\n")
                paragraph "<div style='font-family:monospace; font-size:11px; white-space:pre; overflow-x:auto; background:#f4f4f4; padding:10px; border:1px solid #ccc;'>${csvPreview}</div>"
            } else {
                paragraph "Aucune donnée enregistrée pour le moment."
            }
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
    if (state.logEntries == null) {
        state.logEntries = []
    }
    if (!settings.googlePrefilledUrl) {
        app.updateSetting("googlePrefilledUrl", [value: getDefaultGoogleUrl(), type: "text"])
    }
    
    // Abonnements uniquement aux changements d'état des interrupteurs (plus de LUX_UPDATE en continu)
    subscribe(kitchenIlotSwitch, "switch", backSwitchHandler)
    subscribe(kitchenSpotsSwitch, "switch", backSwitchHandler)
    subscribe(kitchenUnderCabSwitch, "switch", backSwitchHandler)
    subscribe(familySwitch, "switch", backSwitchHandler)
    
    subscribe(diningSwitch, "switch", frontSwitchHandler)
    subscribe(livingFoyerSwitch, "switch", frontSwitchHandler)
    subscribe(livingSpotsSwitch, "switch", frontSwitchHandler)
    
    // Relevé périodique fixé à 1 heure (60 minutes)
    if (logPeriodic) {
        runEvery1H(periodicHandler)
    }
    
    log.info("LightingDataLoggerApp initialisé avec succès (Relevé horaire + Switches).")
}

def backSwitchHandler(evt) {
    recordEvent("Arriere", "SWITCH", evt.displayName, evt.value)
}

def frontSwitchHandler(evt) {
    recordEvent("Avant", "SWITCH", evt.displayName, evt.value)
}

def periodicHandler() {
    recordEvent("Global", "TIMER_60M", "Horloge", "Snapshot")
}

def getCsvHeader() {
    return "Date;Jour;Heure;Heure_Decimale;Zone_Event;Type_Event;Appareil_Declencheur;Valeur;Mode_Hub;Lux_Arriere;Lux_Avant;Cloud;Lux_Meteo;UV;Humidite_Ext;Pression;Cuisine_Ilot;Cuisine_Spots;Cuisine_Sous_Armoires;Nb_Cuisine_ON;Salle_Fam;Salle_Diner;Salon_Foyer;Salon_Spots\n"
}

def getActiveFileName() {
    if (settings.monthlyRotation == false) {
        return "lighting_data.csv"
    }
    String yearMonth = new Date().format("yyyy-MM", location.timeZone)
    return "lighting_data_${yearMonth}.csv"
}

def getSwitchBit(dev) {
    return (dev && dev.currentValue("switch") == "on") ? 1 : 0
}

def parseGoogleFormUrl(String rawUrl) {
    def result = [valid: false, baseUrl: "", entries: []]
    if (!rawUrl) return result
    
    String cleanUrl = rawUrl.trim()
    def baseMatcher = (cleanUrl =~ /(?i)(https:\/\/docs\.google\.com\/forms\/d\/e\/[^\/]+\/)(viewform|formResponse)/)
    if (baseMatcher.find()) {
        String prefix = baseMatcher.group(1)
        prefix = "https://" + prefix.substring(8)
        result.baseUrl = prefix + "formResponse"
    }
    
    def entryMatcher = (cleanUrl =~ /(entry\.\d+)=/)
    while (entryMatcher.find()) {
        String entryId = entryMatcher.group(1)
        if (!result.entries.contains(entryId)) {
            result.entries << entryId
        }
    }
    
    if (result.baseUrl && result.entries.size() > 0) {
        result.valid = true
    }
    return result
}

def recordEvent(String zone, String triggerType, String deviceName, String actionValue) {
    Date now = new Date()
    def tz = location.timeZone
    
    def dateStr = now.format("yyyy-MM-dd", tz)
    def dayOfWeek = now.format("EEEE", tz)
    def timeStr = now.format("HH:mm:ss", tz)
    
    int hour = now.format("H", tz) as int
    int minute = now.format("m", tz) as int
    String decHour = String.format(Locale.US, "%.2f", hour + (minute / 60.0))
    
    def luxBack = hueBack ? hueBack.currentValue("illuminance") : ""
    def luxFront = hueFront ? hueFront.currentValue("illuminance") : ""
    
    // Récupération des 6 nouvelles métriques météo
    def cloudCover = weatherDevice ? weatherDevice.currentValue("cloud") : ""
    def luxMeteo = weatherDevice ? weatherDevice.currentValue("illuminance") : ""
    def uvIndex = weatherDevice ? weatherDevice.currentValue("ultravioletIndex") : ""
    def extHum = weatherDevice ? weatherDevice.currentValue("humidity") : ""
    def pressure = weatherDevice ? weatherDevice.currentValue("pressure") : ""
    
    def currentMode = location.currentMode
    
    int kIlot = getSwitchBit(kitchenIlotSwitch)
    int kSpots = getSwitchBit(kitchenSpotsSwitch)
    int kCab = getSwitchBit(kitchenUnderCabSwitch)
    int kTotal = kIlot + kSpots + kCab
    int fam = getSwitchBit(familySwitch)
    
    int din = getSwitchBit(diningSwitch)
    int livFoyer = getSwitchBit(livingFoyerSwitch)
    int livSpots = getSwitchBit(livingSpotsSwitch)
    
    def csvLine = "${dateStr};${dayOfWeek};${timeStr};${decHour};${zone};${triggerType};${deviceName};${actionValue};${currentMode};${luxBack};${luxFront};${cloudCover};${luxMeteo};${uvIndex};${extHum};${pressure};${kIlot};${kSpots};${kCab};${kTotal};${fam};${din};${livFoyer};${livSpots}"
    
    log.info("[DATA_LOG] ${csvLine}")
    
    def entries = state.logEntries ?: []
    entries << csvLine
    if (entries.size() > 25) {
        entries = entries.drop(entries.size() - 25)
    }
    state.logEntries = entries
    
    if (enableGoogleForm != false) {
        String activeUrl = settings.googlePrefilledUrl ?: getDefaultGoogleUrl()
        def valuesList = [
            zone, triggerType, deviceName, actionValue, "${currentMode}",
            "${luxBack}", "${luxFront}", "${cloudCover}", "${luxMeteo}", "${uvIndex}", "${extHum}", "${pressure}",
            "${kIlot}", "${kSpots}", "${kCab}", "${fam}",
            "${din}", "${livFoyer}", "${livSpots}"
        ]
        sendToGoogleForm(activeUrl, valuesList, csvLine)
    }
    
    if (saveToFile) {
        appendToCsvFile(csvLine)
    }
}

def sendToGoogleForm(String targetUrl, List valuesList, String fullCsvLine) {
    def parsed = parseGoogleFormUrl(targetUrl)
    if (!parsed.valid) {
        log.warn("LightingDataLoggerApp : URL Google Form invalide, envoi annulé.")
        return
    }
    
    Map queryParams = [:]
    if (parsed.entries.size() == 1) {
        queryParams[parsed.entries[0]] = fullCsvLine
    } else {
        int maxIdx = Math.min(parsed.entries.size(), valuesList.size())
        for (int i = 0; i < maxIdx; i++) {
            queryParams[parsed.entries[i]] = "${valuesList[i]}"
        }
    }
    queryParams["submit"] = "Submit"
    
    Map requestParams = [
        uri: parsed.baseUrl,
        query: queryParams,
        timeout: 10
    ]
    
    try {
        asynchttpGet("googleFormCallback", requestParams)
    } catch (Exception e) {
        log.error("Erreur lors de l'envoi vers Google Forms : ${e.message}")
    }
}

def googleFormCallback(response, data) {
    if (response.getStatus() == 200) {
        log.debug("LightingDataLoggerApp -> Données enregistrées avec succès dans Google Forms.")
    } else {
        log.warn("LightingDataLoggerApp -> Réponse Google Forms inattendue (Code HTTP ${response.getStatus()}).")
    }
}

def appendToCsvFile(String line) {
    String fileName = getActiveFileName()
    String header = getCsvHeader()
    try {
        byte[] existingBytes = downloadHubFile(fileName)
        String existingContent = existingBytes ? new String(existingBytes, "UTF-8") : header
        String updatedContent = existingContent + line + "\n"
        uploadHubFile(fileName, updatedContent.getBytes("UTF-8"))
    } catch (Exception e) {
        try {
            String initialContent = header + line + "\n"
            uploadHubFile(fileName, initialContent.getBytes("UTF-8"))
        } catch (Exception ex) {
            log.error("Erreur lors de l'écriture dans ${fileName} : ${ex.message}")
        }
    }
}
