definition(
    name: "Mappeur d'Événements HTTP (SMS & Lumières)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Reçoit des libellés textuels via un appareil virtuel (compatible Maker API), envoie un SMS et pilote une lumière selon une table de correspondance.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Configuration du Mappeur d'Événements", install: true, uninstall: true) {
        section("1. Appareil & Notification") {
            input "textDevice", "capability.actuator", title: "Appareil virtuel de texte (Point d'entrée Maker API)", multiple: false, required: true, submitOnChange: true
            input "smsDevice", "capability.notification", title: "Appareil de notification (Voip.ms SMS)", multiple: false, required: true
        }
        
        section("2. Table de Correspondance par Mots-clés") {
            paragraph "Associez chaque mot-clé reçu (ex: arriere, sonnette, voiture) à un message texte et à une lumière à allumer."
            
            for (int i = 1; i <= 25; i++) {
                input "code_${i}", "text", title: "Ligne ${i} - Mot-clé reçu (ex: arriere)", required: false, submitOnChange: true
                if (settings."code_${i}") {
                    input "sms_${i}", "text", title: "   ↳ Message SMS associé", required: false
                    input "light_${i}", "capability.switch", title: "   ↳ Lumière à allumer", required: false, multiple: false
                }
            }
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
    subscribe(textDevice, "text", textHandler)
    log.info("EventMapperApp initialisé et à l'écoute de ${textDevice.displayName}")
}

def textHandler(evt) {
    def receivedValue = evt.value?.trim()?.toLowerCase()
    if (!receivedValue) return
    
    log.info("EventMapperApp a reçu le mot-clé : ${receivedValue}")
    
    for (int i = 1; i <= 25; i++) {
        def targetCode = settings."code_${i}"?.trim()?.toLowerCase()
        if (targetCode && targetCode == receivedValue) {
            def message = settings."sms_${i}"
            def targetLight = settings."light_${i}"
            
            // 1. Envoi du SMS
            if (message && smsDevice) {
                smsDevice.deviceNotification(message)
                log.info("EventMapperApp -> SMS envoyé : ${message}")
            }
            
            // 2. Action sur la lumière
            if (targetLight) {
                targetLight.on()
                log.info("EventMapperApp -> Lumière allumée : ${targetLight.displayName}")
            }
            return
        }
    }
    log.warn("EventMapperApp : Aucun équivalent trouvé pour le mot-clé '${receivedValue}'")
}
