definition(
    name: "Moniteur d'État des Appareils (SMS)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Surveille une sélection d'appareils et envoie un SMS via Voip.ms à chaque changement d'état.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Configuration du Moniteur d'Appareils", install: true, uninstall: true) {
        
        section("1. Notification") {
            input "smsDevice", "capability.notification", title: "Appareil de notification (Voip.ms SMS)", multiple: false, required: true
        }
        
        section("2. Appareils à surveiller") {
            input "monitoredDevices", "capability.actuator", title: "Sélectionnez les appareils à surveiller", multiple: true, required: true, submitOnChange: true
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
    if (monitoredDevices) {
        // S'abonne à tous les types d'événements (switch, presence, motion, level, etc.) pour les appareils choisis
        subscribe(monitoredDevices, "switch", deviceHandler)
        subscribe(monitoredDevices, "presence", deviceHandler)
        subscribe(monitoredDevices, "motion", deviceHandler)
        subscribe(monitoredDevices, "contact", deviceHandler)
        subscribe(monitoredDevices, "battery", deviceHandler)
        subscribe(monitoredDevices, "level", deviceHandler)
        log.info("DeviceStateMonitor initialisé pour ${monitoredDevices.size()} appareil(s).")
    }
}

def deviceHandler(evt) {
    def deviceName = evt.displayName
    def attrName = evt.name
    def attrValue = evt.value
    
    // Construction du message de notification
    def message = "[Monitor] ${deviceName} : ${attrName} -> ${attrValue}"
    
    if (smsDevice) {
        smsDevice.deviceNotification(message)
        log.info("DeviceStateMonitor -> SMS envoyé : ${message}")
    }
}
