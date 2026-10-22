definition(
    name: "Mappeur d'Événements HTTP (SMS & Lumières)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Reçoit des libellés textuels via un appareil virtuel, avec conditions de kill-switch et modes par ligne.",
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
        
        section("2. Paramètres d'affichage") {
            input "numRows", "number", title: "Nombre de lignes de correspondance à afficher", required: true, defaultValue: 5, range: "1..*", submitOnChange: true
        }
        
        def maxRows = (settings.numRows != null) ? (settings.numRows as int) : 5
        if (maxRows < 1) maxRows = 1

        section("3. Table de Correspondance et Conditions (${maxRows} active(s))") {
            paragraph "Associez chaque mot-clé reçu et définissez des conditions d'exclusion spécifiques pour chaque ligne."
            
            for (int i = 1; i <= maxRows; i++) {
                input "code_${i}", "text", title: "Ligne ${i} - Mot-clé reçu (ex: arriere_personne)", required: false, submitOnChange: true
                if (settings."code_${i}") {
                    input "sms_${i}", "text", title: "   ↳ Message SMS associé", required: false
                    input "light_${i}", "capability.switch", title: "   ↳ Lumière à allumer", required: false, multiple: false
                    input "disableSwitch_${i}", "capability.switch", title: "   ↳ Interrupteur de kill-switch (ex: Tonte de pelouse)", required: false, multiple: false
                    input "disabledModes_${i}", "mode", title: "   ↳ Modes désactivant cette ligne spécifique", required: false, multiple: true
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
    
    def maxRows = (settings.numRows != null) ? (settings.numRows as int) : 5
    for (int i = 1; i <= maxRows; i++) {
        def targetCode = settings."code_${i}"?.trim()?.toLowerCase()
        if (targetCode && targetCode == receivedValue) {
            
            // 1. Vérification du Kill-Switch spécifique à cette ligne
            def lineSwitch = settings."disableSwitch_${i}"
            if (lineSwitch && lineSwitch.currentValue("switch") == "on") {
                log.info("EventMapperApp -> Ligne ${i} ignorée : l'interrupteur de désactivation est actif.")
                return
            }
            
            // 2. Vérification des Modes spécifiques à cette ligne
            def lineModes = settings."disabledModes_${i}"
            if (lineModes && lineModes.contains(location.currentMode)) {
                log.info("EventMapperApp -> Ligne ${i} ignorée : le mode actuel '${location.currentMode}' est exclu pour cette ligne.")
                return
            }
            
            def message = settings."sms_${i}"
            def targetLight = settings."light_${i}"
            
            if (message && smsDevice) {
                smsDevice.deviceNotification(message)
                log.info("EventMapperApp -> SMS envoyé (Ligne ${i}) : ${message}")
            }
            
            if (targetLight) {
                targetLight.on()
                log.info("EventMapperApp -> Lumière allumée (Ligne ${i}) : ${targetLight.displayName}")
            }
            return
        }
    }
    log.warn("EventMapperApp : Aucun équivalent trouvé pour le mot-clé '${receivedValue}'")
}
