definition(
    name: "Moniteur d'État des Appareils et Modes (SMS)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Surveille n'importe quel appareil (virtuel ou physique) et les changements de mode, avec notifications SMS via Voip.ms.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Configuration du Moniteur", install: true, uninstall: true) {
        
        section("1. Notification") {
            input "smsDevice", "capability.notification", title: "Appareil de notification (Voip.ms SMS)", multiple: false, required: true
        }
        
        section("2. Options de surveillance") {
            input "monitorModes", "bool", title: "Surveiller les changements de mode du hub ?", defaultValue: true, submitOnChange: true
        }
        
        section("3. Appareils à surveiller") {
            // Changé en "capability.tracking" ou simplement "capability.*" / sélection large pour accepter les virtuels
            input "monitoredDevices", "capability.sensed", title: "Sélectionnez les appareils à surveiller (Virtuels / Physiques)", multiple: true, required: false, submitOnChange: true
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    unsubscribe()
    
    // S'abonne à TOUS les événements ("*") de chaque appareil sélectionné, qu'il soit virtuel ou physique
    if (monitoredDevices) {
        subscribe(monitoredDevices, "expression", deviceHandler) // Attribut universel ou joker selon l'API Hubitat
        // Alternative plus large pour capturer tous les attributs sans filtre :
        monitoredDevices.each { dev ->
            dev.supportedAttributes.each { attr ->
                subscribe(dev, attr.name, deviceHandler)
            }
        }
    }
    
    if (monitorModes) {
        subscribe(location, "mode", modeHandler)
    }
    
    log.info("DeviceStateMonitor initialisé (Modes: ${monitorModes}, Appareils: ${monitoredDevices ? monitoredDevices.size() : 0}).")
}

def deviceHandler(evt) {
    def deviceName = evt.displayName
    def attrName = evt.name
    def attrValue = evt.value
    
    def message = "[Monitor] ${deviceName} (${attrName}) -> ${attrValue}"
    sendSms(message)
}

def modeHandler(evt) {
    def newMode = evt.value
    def message = "[Monitor] Mode du Hub -> ${newMode}"
    sendSms(message)
}

def sendSms(message) {
    if (smsDevice) {
        smsDevice.deviceNotification(message)
        log.info("DeviceStateMonitor -> SMS envoyé : ${message}")
    }
}
