definition(
    name: "Enregistreur de Données d'Éclairage (Google Forms & CSV)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Enregistre l'heure, les données météo/lux et l'état/intensité des switches vers Google Forms et/ou fichier CSV.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def getDefaultGoogleUrl() {
    return "VOTRE_NOUVELLE_URL_PRE_REMPLIE_ICI"
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Collecte de Données : Lumières, Heures, Lux & Météo", install: true, uninstall: true) {
        
        section("1. Station Météo & Capteurs Lux Extérieurs") {
            input "weatherDevice", "capability.illuminanceMeasurement", title: "Appareil Météo (Cloud, UV, Humidité, Pression, Lux ext.)", required: true, multiple: false
            input "hueBack", "capability.illuminanceMeasurement", title: "Capteur Hue Arrière (Lux)", required: true, multiple: false
            input "hueFront", "capability.illuminanceMeasurement", title: "Capteur Hue Avant (Lux)", required: true, multiple: false
        }

        section("2. Zone Arrière (Cuisine, Dînette & Salle familiale)") {
            input "kitchenIlotSwitch", "capability.switchLevel", title: "Cuisine - Îlot (Dimmer)", required: true, multiple: false
            input "kitchenSpotsSwitch", "capability.switchLevel", title: "Cuisine - Spots (Dimmer)", required: true, multiple: false
            input "kitchenUnderCabSwitch", "capability.switch", title: "Cuisine - Sous les armoires", required: true, multiple: false
            input "familySwitch", "capability.switch", title: "Salle familiale (1 switch)", required: true, multiple: false
        }
        
        section("3. Zone Avant (Salle à dîner & Salon)") {
            input "diningSwitch", "capability.switch", title: "Salle à dîner (1 switch)", required: true, multiple: false
            input "livingFoyerSwitch", "capability.switch", title: "Salon - Foyer", required: true, multiple: false
            input "livingSpotsSwitch", "capability.switchLevel", title: "Salon - Spots (Dimmer)", required: true, multiple: false
        }
        
        section("4. Intégration Google Forms") {
            input "enableGoogleForm", "bool", title: "Envoyer les données vers Google Forms ?", defaultValue: true, submitOnChange: true
            if (settings.enableGoogleForm != false) {
                input "googlePrefilledUrl", "text", title: "URL pré-remplie de votre nouveau Google Form", required: true, defaultValue: getDefaultGoogleUrl(), submitOnChange: true
            }
        }

        section("5. Options d'enregistrement") {
            input "logPeriodic", "bool", title: "Enregistrer un relevé automatique toutes les heures (60 minutes) ?", defaultValue: true
            input "saveToFile", "bool", title: "Sauvegarder AUSSI dans un fichier CSV local ?", defaultValue: false, submitOnChange: true
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
}

def backSwitchHandler(evt) { recordEvent("Arriere", "SWITCH", evt.displayName, evt.value) }
def frontSwitchHandler(evt) { recordEvent("Avant", "SWITCH", evt.displayName, evt.value) }
def periodicHandler() { recordEvent("Global", "TIMER_60M", "Horloge", "Snapshot") }

// Retourne 0 si éteint, ou le niveau réel (1-100) si c'est un dimmer, ou 1/0 pour un switch simple
def getSwitchLevelOrBit(dev, evt) {
    if (!dev) return 0
    
    // Si l'événement vient de cet appareil et concerne le switch, on regarde sa valeur en direct
    if (evt && evt.device && evt.device.id == dev.id && evt.name == "switch") {
        if (evt.value != "on") return 0
    } else {
        // Sinon (pour le snapshot horaire par exemple), on vérifie l'état actuel
        if (dev.currentValue("switch") != "on") return 0
    }
    
    // Si c'est un dimmer, on cherche son niveau actuel
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
    def tz = location.timeZone
    
    def dateStr = now.format("yyyy-MM-dd", tz)
    def dayOfWeek = now.format("EEEE", tz)
    def timeStr = now.format("HH:mm:ss", tz)
    int hour = now.format("H", tz) as int
    int minute = now.format("m", tz) as int
    String decHour = String.format(Locale.US, "%.2f", hour + (minute / 60.0))
    
    def luxBack = hueBack ? hueBack.currentValue("illuminance") : ""
    def luxFront = hueFront ? hueFront.currentValue("illuminance") : ""
    
    def cloudCover = weatherDevice ? weatherDevice.currentValue("cloud") : ""
    def luxMeteo = weatherDevice ? weatherDevice.currentValue("illuminance") : ""
    def uvIndex = weatherDevice ? weatherDevice.currentValue("ultravioletIndex") : ""
    def extHum = weatherDevice ? weatherDevice.currentValue("humidity") : ""
    def pressure = weatherDevice ? weatherDevice.currentValue("pressure") : ""
    def wind = weatherDevice ? weatherDevice.currentValue("wind") : ""
    
    def currentMode = location.currentMode
    
    // Récupération des niveaux (0 à 100 pour les dimmers, 0 ou 1 pour les switches)
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
            "${luxFront}", "${luxBack}", // Assurez-vous de l'ordre de vos colonnes dans le Form
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
