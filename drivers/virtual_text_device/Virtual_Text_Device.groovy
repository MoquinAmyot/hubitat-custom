metadata {
    definition (name: "Virtual Text Device", namespace: "custom", author: "Benoit Tremblay") {
        capability "Actuator"
        capability "Sensor"
        attribute "text", "string"
        command "setText", ["string"]
    }
}

def setText(val) {
    sendEvent(name: "text", value: val)
    log.info("VirtualTextDevice -> Texte mis à jour : ${val}")
}

