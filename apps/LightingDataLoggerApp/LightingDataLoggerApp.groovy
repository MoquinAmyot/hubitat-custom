definition(
    name: "Enregistreur de Données d'Éclairage (Google Forms & CSV)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Enregistre l'heure, les données météo/lux et l'état/intensité des switches vers Google Forms.",
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
            input "weatherDevice", "capability.illuminanceMeasurement", title: "Appareil Météo", required: true, multiple: false
            input "hueBack", "capability.illuminanceMeasurement", title: "Capteur Hue Arrière (Lux)", required: true, multiple: false
            input "hueFront", "capability.illuminanceMeasurement", title: "Capteur Hue Avant (Lux)", required: true, multiple: false
        }

        section("2. Zone Arrière (Cuisine & Famille)") {
            input "kitchenIlotSwitch", "capability.switchLevel", title: "Cuisine - Îlot", required: true, multiple: false
            input "kitchenSpotsSwitch", "capability.switchLevel", title: "Cuisine - Spots", required: true, multiple: false
            input "kitchenUnderCabSwitch", "capability.switch", title: "Cuisine - Sous armoires", required: true, multiple: false
            input "familySwitch", "capability.switch", title: "Salle familiale", required: true, multiple: false
        }
        
        section("3. Zone Avant (Dîner & Salon)") {
            input "diningSwitch", "capability.switch", title: "Salle à dîner", required: true, multiple: false
            input "livingFoyerSwitch", "capability.switch", title: "Salon - Foyer", required: true, multiple: false
            input "livingSpotsSwitch", "capability.switchLevel", title: "Salon - Spots", required: true, multiple: false
        }
        
        section("4. Intégration Google Forms") {
            input "enableGoogleForm", "bool", title: "Envoyer les données vers Google Forms ?", defaultValue: true, submitOnChange: true
            if (settings.enableGoogleForm != false) {
                input "googlePrefilledUrl", "text", title: "URL pré-remplie de votre Google Form", required: true, defaultValue: getDefaultGoogleUrl(), submitOnChange: true
                input "sendTestNow", "bool", title: "Cochez pour envoyer une ligne de test immédiate", defaultValue: false, submitOnChange: true
            }
        }

        if (settings.sendTestNow) {
            app.updateSetting("sendTestNow", [value: "false", type: "bool"])
            recordEvent("Global", "TEST_MANUEL", "Interface_Hubitat", "Test_OK")
            section { paragraph "<span style='color:blue; font-weight:bold;'>✓ Ligne de test envoyée !</span>" }
        }

        section("5. Options") {
            input "logPeriodic", "bool", title: "Enregistrer un relevé automatique toutes les 60 minutes ?", defaultValue: true
        }
    }
}

def installed() { initialize() }
def updated() { unschedule(); unsubscribe(); initialize() }

def initialize() {
    subscribe(kitchenIlotSwitch, "switch", backSwitchHandler)
    subscribe(kitchenSpotsSwitch, "switch", backSwitchHandler)
    subscribe(kitchenUnderCabSwitch, "switch", backSwitchHandler)
    subscribe(familySwitch, "switch", backSwitchHandler)
    
    subscribe(diningSwitch, "switch", frontSwitchHandler)
    subscribe(livingFoyerSwitch, "switch", frontSwitchHandler)
    subscribe(livingSpotsSwitch, "switch", frontSwitchHandler)
    
    if (logPeriodic) {
        runEvery1H(periodicHandler)
    }
    log.info("LightingDataLoggerApp initialisé avec succès.")
}

def backSwitchHandler(evt) { recordEvent("Arriere", "SWITCH", evt.displayName, evt.value) }
def frontSwitchHandler(evt) { recordEvent("Avant", "SWITCH", evt.displayName, evt.value) }
def periodicHandler() { recordEvent("Global", "TIMER_60M", "Horloge", "Snapshot") }

// Fonction robuste : retourne 0 si off, ou le niveau réel (1-100) si on
def getSwitchLevelOrBit(dev) {
    if (!dev) return 0
    if (dev.currentValue("switch") != "on") return 0
    if (dev.hasAttribute("level")) {
        def lvl = dev.currentValue("level")
        return lvl != null ? (lvl as Integer) : 100
    }
    return 1
}

def parseGoogleFormUrl(String rawUrl) {
    def result = [valid: false, baseUrl: "", entries: []]
    if (!rawUrl) return result
    String cleanUrl = rawUrl.trim()
    def baseMatcher = (cleanUrl =~ /(?i)(https:\/\/docs\.google\.com\/forms\/d\/e\/[^\/]+\/)(viewform|formResponse)/)
    if (baseMatcher.find()) {
        result.baseUrl = "https://" + baseMatcher.group(1).substring(8) + "formResponse"
    }
    def entryMatcher = (cleanUrl =~ /(entry\.\d+)=/)
    while (entryMatcher.find()) {
        String entryId = entryMatcher.group(1)
        if (!result.entries.contains(entryId)) result.entries << entryId
    }
    if (result.baseUrl && result.entries.size() > 0) result.valid = true
    return result
}

def recordEvent(String zone, String triggerType, String deviceName, String actionValue) {
    Date now = new Date()
    
    def luxFront = hueFront ? hueFront.currentValue("illuminance") : ""
    def luxBack = hueBack ? hueBack.currentValue("illuminance") : ""
    
    def cloudCover = weatherDevice ? weatherDevice.currentValue("cloud") : ""
    def luxMeteo = weatherDevice ? weatherDevice.currentValue("illuminance") : ""
    def uvIndex = weatherDevice ? weatherDevice.currentValue("ultravioletIndex") : ""
    def extHum = weatherDevice ? weatherDevice.currentValue("humidity") : ""
    def pressure = weatherDevice ? weatherDevice.currentValue("pressure") : ""
    def wind = weatherDevice ? weatherDevice.currentValue("wind") : ""
    
    def currentMode = location.currentMode
    
    int kIlot = getSwitchLevelOrBit(kitchenIlotSwitch)
    int kSpots = getSwitchLevelOrBit(kitchenSpotsSwitch)
    int kCab = getSwitchLevelOrBit(kitchenUnderCabSwitch)
    int fam = getSwitchLevelOrBit(familySwitch)
    int din = getSwitchLevelOrBit(diningSwitch)
    int livFoyer = getSwitchLevelOrBit(livingFoyerSwitch)
    int livSpots = getSwitchLevelOrBit(livingSpotsSwitch)
    
    if (enableGoogleForm != false) {
        String activeUrl = settings.googlePrefilledUrl ?: getDefaultGoogleUrl()
        def valuesList = [
            zone, triggerType, deviceName, actionValue, "${currentMode}",
            "${luxFront}", "${luxBack}",
            "${kIlot}", "${kSpots}", "${kCab}", "${fam}", "${din}", "${livFoyer}", "${livSpots}",
            "${cloudCover}", "${luxMeteo}", "${uvIndex}", "${extHum}", "${pressure}", "${wind}"
        ]
        sendToGoogleForm(activeUrl, valuesList)
    }
}

def sendToGoogleForm(String targetUrl, List valuesList) {
    def parsed = parseGoogleFormUrl(targetUrl)
    if (!parsed.valid) return
    Map queryParams = [:]
    int maxIdx = Math.min(parsed.entries.size(), valuesList.size())
    for (int i = 0; i < maxIdx; i++) {
        queryParams[parsed.entries[i]] = "${valuesList[i]}"
    }
    queryParams["submit"] = "Submit"
    try {
        asynchttpGet("googleFormCallback", [uri: parsed.baseUrl, query: queryParams, timeout: 10])
    } catch (Exception e) {
        log.error("Erreur Google Forms : ${e.message}")
    }
}

def googleFormCallback(response, data) {}
