definition(
    name: "Moniteur d'État des Appareils et Modes (SMS)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Surveille une sélection d'appareils et les changements de mode du hub, avec notifications SMS via Voip.ms.",
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
            input "monitoredDevices", "capability.actuator", title: "Sélectionnez les appareils à surveiller", multiple: true, required: false, submitOnChange: true
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
    
    // Abonnement aux appareils sélectionnés
    if (monitoredDevices) {
        subscribe(monitoredDevices, "switch", deviceHandler)
        subscribe(monitoredDevices, "presence", deviceHandler)
        subscribe(monitoredDevices, "motion", deviceHandler)
        subscribe(monitoredDevices, "contact", deviceHandler)
        subscribe(monitoredDevices, "battery", deviceHandler)
        subscribe(monitoredDevices, "level", deviceHandler)
    }
    
    // Abonnement aux changements de mode du hub si l'option est activée
    if (monitorModes) {
        subscribe(location, "mode", modeHandler)
    }
    
    log.info("DeviceStateMonitor initialisé (Modes surveillés: ${monitorModes}, Appareils: ${monitoredDevices ? monitoredDevices.size() : 0}).")
}

def deviceHandler(evt) {
    def deviceName = evt.displayName
    def attrName = evt.name
    def attrValue = evt.value
    
    def message = "[Monitor] ${deviceName} : ${attrName} -> ${attrValue}"
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
