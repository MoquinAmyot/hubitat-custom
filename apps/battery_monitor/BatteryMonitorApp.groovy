definition(
    name: "Gestionnaire de Batteries Bas Niveau",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Surveille les niveaux de batterie des appareils et envoie une alerte sous un seuil critique.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    section("Appareils à surveiller") {
        input "batteries", "capability.battery", title: "Sélectionnez les appareils sur batterie", multiple: true, required: true
        input "threshold", "number", title: "Seuil d'alerte (%)", defaultValue: 15, required: true
    }
    section("Notification") {
        input "notifiers", "capability.notification", title: "Appareil de notification (ex: Voip.ms SMS)", multiple: true, required: false
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
    // Vérification quotidienne à 9h00 du matin
    schedule("0 0 9 * * ?", checkBatteryLevels)
}

def checkBatteryLevels() {
    def lowBatteries = []
    
    batteries.each { device ->
        def battLevel = device.currentValue("battery")
        if (battLevel != null && battLevel <= threshold) {
            lowBatteries << "${device.displayName} (${battLevel}%)"
        }
    }
    
    if (lowBatteries) {
        def msg = "⚠️ Batteries faibles détectées :\n" + lowBatteries.join("\n")
        log.warn(msg)
        if (notifiers) {
            notifiers.device.each { it.deviceNotification(msg) }
        }
    } else {
        log.info("Vérification des batteries : Tous les niveaux sont corrects (> ${threshold}%).")
    }
}
