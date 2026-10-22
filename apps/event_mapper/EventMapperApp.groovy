definition(
    name: "Mappeur d'Événements HTTP (SMS & Lumières)",
    namespace: "custom",
    author: "Benoit Tremblay",
    description: "Reçoit des libellés textuels via un appareil virtuel, avec kill-switch par mode/interrupteur et nombre de lignes dynamique.",
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
        
        section("2. Kill-Switch & Conditions d'exclusion") {
            input "disableSwitch", "capability.switch", title: "Interrupteur de désactivation (ex: Mode Tonte)", required: false, multiple: false
            input "disabledModes", "mode", title: "Modes Hubitat désactivant les alertes", required: false, multiple: true
        }
        
        section("3. Paramètres d'affichage") {
            input "numRows", "number", title: "Nombre de lignes de correspondance à afficher (max 25)", required: true, defaultValue: 5, range: "1..25", submitOnChange: true
        }
        
        def maxRows = (settings.numRows ?: 5) as int
        if (maxRows > 25) maxRows = 25

        section("4. Table de Correspondance par Mots-clés (${maxRows} active(s))") {
            paragraph "Associez chaque mot-clé reçu à un message texte et à une lumière à allumer."
            
            for (int i = 1; i <= maxRows; i++) {
                def currentCode = settings."code_${i}"
                // Section repliable par défaut s'il y a du contenu, ou affichée si vide pour configurer
                boolean isHidden = currentCode ? true : false
                
                href(name: "lineDetail_${i}", title: currentCode ? "Ligne ${i} : ${currentCode}" : "Ligne ${i} (Vide - Cliquer pour configurer)", page: "rowConfigPage", params: [row: i], description: currentCode ? "SMS: ${settings.'sms_' + i ?: 'Aucun'}" : "Configurer ce déclencheur", state: currentCode ? "complete" : null)
            }
        }
    }
}

// Page secondaire pour configurer proprement chaque ligne sans alourdir la page principale
def rowConfigPage(params) {
    def row = params.row as int
    dynamicPage(name: "rowConfigPage", title: "Configuration de la Ligne ${row}", install: false, uninstall: false, nexPage: "mainPage") {
        section("Paramètres du déclencheur ${row}") {
            input "code_${row}", "text", title: "Mot-clé reçu (ex: avant_personne)", required: false, submitOnChange: true
            input "sms_${row}", "text", title: "Message SMS associé", required: false
            input "light_${row}", "capability.switch", title: "Lumière à allumer", required: false, multiple: false
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
    // 1. Vérification du Kill-Switch par interrupteur
    if (disableSwitch && disableSwitch.currentValue("switch") == "on") {
        log.info("EventMapperApp -> Événement ignoré : l'interrupteur de désactivation est actif.")
        return
    }
    
    // 2. Vérification du Kill-Switch par Mode Hubitat (ex: week-end de tonte ou mode spécifique)
    if (disabledModes && disabledModes.contains(location.currentMode)) {
        log.info("EventMapperApp -> Événement ignoré : le mode actuel '${location.currentMode}' est désactivé.")
        return
    }
    
    def receivedValue = evt.value?.trim()?.toLowerCase()
    if (!receivedValue) return
    
    log.info("EventMapperApp a reçu le mot-clé : ${receivedValue}")
    
    def maxRows = (settings.numRows ?: 5) as int
    for (int i = 1; i <= maxRows; i++) {
        def targetCode = settings."code_${i}"?.trim()?.toLowerCase()
        if (targetCode && targetCode == receivedValue) {
            def message = settings."sms_${i}"
            def targetLight = settings."light_${i}"
            
            // Envoi du SMS
            if (message && smsDevice) {
                smsDevice.deviceNotification(message)
                log.info("EventMapperApp -> SMS envoyé : ${message}")
            }
            
            // Action sur la lumière
            if (targetLight) {
                targetLight.on()
                log.info("EventMapperApp -> Lumière allumée : ${targetLight.displayName}")
            }
            return
        }
    }
    log.warn("EventMapperApp : Aucun équivalent trouvé pour le mot-clé '${receivedValue}'")
}
