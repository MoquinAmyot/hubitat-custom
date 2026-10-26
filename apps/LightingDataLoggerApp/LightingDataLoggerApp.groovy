definition(
    name: "Enregistreur de Données d'Éclairage (Google Forms & CSV)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Enregistre l'heure, les données météo/lux et l'état/intensité des switches vers Google Forms (Google Sheets) et/ou fichier CSV local.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def getDefaultGoogleUrl() {
    return "https://docs.google.com/forms/d/e/1FAIpQLSdbLDmjSlE8jnxRu4J_xTMLTiGK2uOjOaRaRPunYixdpi8AOA/viewform?usp=pp_url&entry.1638483558=Zone&entry.1868715799=Type&entry.1027971308=Declencheur&entry.1629850364=Valeur&entry.33299896=Mode&entry.1411099267=LuxAv&entry.169086622=LuxAr&entry.1344018188=Ilot&entry.558560481=Spot&entry.879635431=Armoire&entry.438656806=FAm&entry.880421055=Diner&entry.1552842943=Foyer&entry.593558930=Salon&entry.1713371477=Cloud&entry.348510840=Illu&entry.2127039020=UV&entry.925795843=Hum&entry.1410502511=Pres&entry.660655667=Vnet"
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Collecte de Données : Lumières, Heures, Lux & Météo", install: true, uninstall: true) {
        
        section("1. Station Météo & Capteurs Lux Extérieurs") {
            input "weatherDevice", "capability.illuminanceMeasurement", title: "Appareil Météo (Cloud, UV, Humidité, Pression, Vent, Lux ext.)", required: true, multiple: false
            input "hueFront", "capability.illuminanceMeasurement", title: "Capteur Hue Avant (Lux)", required: true, multiple: false
            input "hueBack", "capability.illuminanceMeasurement", title: "Capteur Hue Arrière (Lux)", required: true, multiple: false
        }

        section("2. Zone Arrière (Cuisine, Dînette & Salle familiale)") {
            input "kitchenIlotSwitch", "capability.switch", title: "Cuisine - Îlot (Switch ou Dimmer)", required: true, multiple: false
            input "kitchenSpotsSwitch", "capability.switch", title: "Cuisine - Spots (Switch ou Dimmer)", required: true, multiple: false
            input "kitchenUnderCabSwitch", "capability.switch", title: "Cuisine - Sous les armoires", required: true, multiple: false
            input "familySwitch", "capability.switch", title: "Salle familiale (Switch ou Dimmer)", required: true, multiple: false
        }
        
        section("3. Zone Avant (Salle à dîner & Salon)") {
            input "diningSwitch", "capability.switch", title: "Salle à dîner (Switch ou Dimmer)", required: true, multiple: false
            input "livingFoyerSwitch", "capability.switch", title: "Salon - Foyer (Switch ou Dimmer)", required: true, multiple: false
            input "livingSpotsSwitch", "capability.switch", title: "Salon - Spots (Switch ou Dimmer)", required: true, multiple: false
        }
        
        section("4. Intégration Google Forms (Temps réel vers Google Sheets)") {
            input "enableGoogleForm", "bool", title: "Envoyer les données vers Google Forms ?", defaultValue: true, submitOnChange: true
            if (settings.enableGoogleForm != false) {
                input "googlePrefilledUrl", "text", title: "URL pré-remplie de votre Google Form", required: true, defaultValue: getDefaultGoogleUrl(), submitOnChange: true
                
                String activeUrl = settings.googlePrefilledUrl ?: getDefaultGoogleUrl()
                def parsed = parseGoogleFormUrl(activeUrl)
                if (parsed.valid) {
                    paragraph "<span style='color:green; font-weight:bold;'>✓ URL Google Form valide détectée !</span><br>" +
                              "URL d'envoi : <code>${parsed.baseUrl}</code><br>" +
                              "Champs détectés : <b>${parsed.entries.size()} colonnes</b>"
                    input "sendTestNow", "bool", title: "Cochez pour envoyer une ligne de test immédiate vers Google Forms", defaultValue: false, submitOnChange: true
                } else {
                    paragraph "<span style='color:red; font-weight:bold;'>⚠ URL invalide. Assurez-vous qu'elle contient 'docs.google.com/forms/d/e/...' et au moins un 'entry.xxxxx='.</span>"
                }
            }
        }

        if (settings.sendTestNow) {
            app.updateSetting("sendTestNow", [value: "false", type: "bool"])
            recordEvent("Global", "TEST_MANUEL", "Interface_Hubitat", "Test_OK")
            section { paragraph "<span style='color:blue; font-weight:bold;'>✓ Ligne de test envoyée à Google Forms ! Vérifiez votre Google Sheet.</span>" }
        }
        
        section("5. Options d'enregistrement & Fichier Local") {
            input "logPeriodic", "bool", title: "Enregistrer un relevé automatique toutes les heures (60 minutes) ?", defaultValue: true
            input "saveToFile", "bool", title: "Sauvegarder AUSSI dans un fichier CSV local (File Manager) ?", defaultValue: false, submitOnChange: true
            if (settings.saveToFile) {
                input "monthlyRotation", "bool", title: "Rotation mensuelle du fichier CSV (ex: lighting_data_2026-10.csv) ?", defaultValue: true
            }
            input "clearHistory", "bool", title: "Cochez et sauvegardez pour vider l'aperçu en mémoire", defaultValue: false, submitOnChange: true
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
    
    // Abonnement Zone Arrière (4 switches/dimmers)
    subscribe(kitchenIlotSwitch, "switch", backSwitchHandler)
    subscribe(kitchenSpotsSwitch, "switch", backSwitchHandler)
    subscribe(kitchenUnderCabSwitch, "switch", backSwitchHandler)
    subscribe(familySwitch, "switch", backSwitchHandler)
    
    // Abonnement Zone Avant (3 switches/dimmers)
    subscribe(diningSwitch, "switch", frontSwitchHandler)
    subscribe(livingFoyerSwitch, "switch", frontSwitchHandler)
    subscribe(livingSpotsSwitch, "switch", frontSwitchHandler)
    
    // Relevé périodique fixé à 1 heure (60 minutes)
    if (logPeriodic) {
        runEvery1H(periodicHandler)
    }
    
    log.info("LightingDataLoggerApp initialisé (Google Form: ${enableGoogleForm}, CSV Local: ${saveToFile}).")
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
    return "Date;Jour;Heure;Heure_Decimale;Zone_Event;Type_Event;Appareil_Declencheur;Valeur;Mode_Hub;Lux_Avant;Lux_Arriere;Cuisine_Ilot;Cuisine_Spots;Cuisine_Sous_Armoires;Salle_Fam;Salle_Diner;Salon_Foyer;Salon_Spots;Cloud;Illuminance_Meteo;UV;Humidite_Ext;Pression;Vent\n"
}

def getActiveFileName() {
    if (settings.monthlyRotation == false) {
        return "lighting_data.csv"
    }
    String yearMonth = new Date().format("yyyy-MM", location.timeZone)
    return "lighting_data_${yearMonth}.csv"
}

// Retourne 0 si off, le level (1-100) si dimmer on, ou 1 si switch simple on
def getSwitchLevelOrBit(dev, String triggerDeviceName, String triggerValue) {
    if (!dev) return 0
    
    String swState = ""
    // Si cet appareil précis vient de déclencher l'événement, on prend la valeur directe de l'événement (évite le délai de rampe du dimmer)
    if (triggerDeviceName && dev.displayName == triggerDeviceName && (triggerValue?.equalsIgnoreCase("on") || triggerValue?.equalsIgnoreCase("off"))) {
        swState = triggerValue.toLowerCase()
    } else {
        // Le paramètre "true" force la lecture directe en base de données sans passer par le cache
        swState = dev.currentValue("switch", true)?.toString()?.trim()?.toLowerCase()
    }
    
    if (swState != "on") return 0
    
    // Si l'appareil est "on", on vérifie s'il possède un attribut "level" (Dimmer)
    def rawLvl = dev.currentValue("level", true)
    if (rawLvl != null) {
        try {
            int parsedLvl = rawLvl.toString().trim().toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()
            return parsedLvl > 0 ? parsedLvl : 100
        } catch (Exception e) {
            log.warn("Erreur conversion level pour ${dev.displayName} (valeur: ${rawLvl}) : ${e.message}")
            return 100
        }
    }
    
    // Switch simple (sans attribut level)
    return 1
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
    
    def luxFront = hueFront ? hueFront.currentValue("illuminance", true) : ""
    def luxBack = hueBack ? hueBack.currentValue("illuminance", true) : ""
    def currentMode = location.currentMode
    
    // États individuels : 0 (off), 1-100 (dimmer on) ou 1 (switch simple on)
    int kIlot = getSwitchLevelOrBit(kitchenIlotSwitch, deviceName, actionValue)
    int kSpots = getSwitchLevelOrBit(kitchenSpotsSwitch, deviceName, actionValue)
    int kCab = getSwitchLevelOrBit(kitchenUnderCabSwitch, deviceName, actionValue)
    int fam = getSwitchLevelOrBit(familySwitch, deviceName, actionValue)
    int din = getSwitchLevelOrBit(diningSwitch, deviceName, actionValue)
    int livFoyer = getSwitchLevelOrBit(livingFoyerSwitch, deviceName, actionValue)
    int livSpots = getSwitchLevelOrBit(livingSpotsSwitch, deviceName, actionValue)
    
    // Métriques météo
    def cloudCover = weatherDevice ? weatherDevice.currentValue("cloud", true) : ""
    def luxMeteo = weatherDevice ? weatherDevice.currentValue("illuminance", true) : ""
    def uvIndex = weatherDevice ? weatherDevice.currentValue("ultravioletIndex", true) : ""
    def extHum = weatherDevice ? weatherDevice.currentValue("humidity", true) : ""
    def pressure = weatherDevice ? weatherDevice.currentValue("pressure", true) : ""
    def wind = weatherDevice ? weatherDevice.currentValue("wind", true) : ""
    
    def csvLine = "${dateStr};${dayOfWeek};${timeStr};${decHour};${zone};${triggerType};${deviceName};${actionValue};${currentMode};${luxFront};${luxBack};${kIlot};${kSpots};${kCab};${fam};${din};${livFoyer};${livSpots};${cloudCover};${luxMeteo};${uvIndex};${extHum};${pressure};${wind}"
    
    log.info("[DATA_LOG] ${csvLine}")
    
    def entries = state.logEntries ?: []
    entries << csvLine
    if (entries.size() > 25) {
        entries = entries.drop(entries.size() - 25)
    }
    state.logEntries = entries
    
    // 1. Envoi asynchrone vers Google Forms (20 colonnes exactes)
    if (enableGoogleForm != false) {
        String activeUrl = settings.googlePrefilledUrl ?: getDefaultGoogleUrl()
        def valuesList = [
            zone, triggerType, deviceName, actionValue, "${currentMode}",
            "${luxFront}", "${luxBack}",
            "${kIlot}", "${kSpots}", "${kCab}", "${fam}", "${din}", "${livFoyer}", "${livSpots}",
            "${cloudCover}", "${luxMeteo}", "${uvIndex}", "${extHum}", "${pressure}", "${wind}"
        ]
        sendToGoogleForm(activeUrl, valuesList, csvLine)
    }
    
    // 2. Sauvegarde dans le fichier CSV local (si activé)
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
