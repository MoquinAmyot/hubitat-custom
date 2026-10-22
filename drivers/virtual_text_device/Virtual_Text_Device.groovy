metadata {
    definition (name: "Virtual Text Device", namespace: "custom", author: "Benoit Tremblay") {
        capability "Actuator"
        attribute "text", "string"
        command "setText", [[name: "Text", type: "STRING"]]
    }
}

def installed() {
}

def updated() {
}

def setText(String val) {
    sendEvent(name: "text", value: val)
    log.info "VirtualTextDevice -> Texte mis à jour : ${val}"
}
