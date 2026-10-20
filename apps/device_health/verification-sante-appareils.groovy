/**
 *  Vérification de santé des appareils
 *
 *  - À l'heure de la sonde : envoie refresh()/ping() aux appareils alimentés (secteur).
 *  - À l'heure du rapport  : signale
 *      • les appareils alimentés qui n'ont rien envoyé depuis la sonde;
 *      • les appareils à pile inactifs depuis plus de X heures;
 *      • les piles sous le seuil choisi.
 *  - Option « tout va bien » : un message quotidien même sans problème.
 *    Si ce message n'arrive pas, c'est le hub lui-même qui a un problème.
 */

definition(
    name: "Vérification de santé des appareils",
    namespace: "btremblay",
    author: "Benoit Tremblay",
    description: "Sonde les appareils le matin et signale le soir ceux qui n'ont pas donné signe de vie.",
    category: "Utility",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Vérification de santé des appareils", install: true, uninstall: true) {
        section("Appareils alimentés (secteur) — sondés activement") {
            input "poweredDevices", "capability.refresh", title: "Appareils à sonder",
                multiple: true, required: false
        }
        section("Appareils à pile (dormants) — vérifiés par leur dernière activité") {
            input "batteryDevices", "capability.*", title: "Appareils à pile",
                multiple: true, required: false
            input "batteryHours", "number", title: "Seuil d'inactivité (heures)",
                defaultValue: 26, required: true
            input "batteryLow", "number", title: "Signaler les piles sous (%)",
                defaultValue: 20, required: true
        }
        section("Horaire") {
            input "pingTime", "time", title: "Heure de la sonde (ex. 8 h)", required: true
            input "reportTime", "time", title: "Heure du rapport (ex. 20 h)", required: true
        }
        section("Notification") {
            input "notifier", "capability.notification", title: "Appareil(s) de notification",
                multiple: true, required: true
            input "notifyIfAllOk", "bool", title: "Envoyer aussi un message quand tout va bien",
                defaultValue: true
        }
        section("Test") {
            input "btnPing", "button", title: "Sonder maintenant"
            input "btnReport", "button", title: "Envoyer le rapport maintenant"
        }
    }
}

def installed() { initialize() }

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    schedule(toCron(pingTime), "pingDevices")
    schedule(toCron(reportTime), "sendReport")
}

def appButtonHandler(String btn) {
    if (btn == "btnPing") pingDevices()
    if (btn == "btnReport") sendReport()
}

def pingDevices() {
    state.pingAt = now()
    poweredDevices?.each { dev ->
        try {
            if (dev.hasCommand("ping")) dev.ping()
            else dev.refresh()
        } catch (e) {
            log.warn "Échec de la sonde pour ${dev.displayName} : ${e}"
        }
        pauseExecution(500)   // étale les commandes pour ne pas inonder le maillage
    }
    log.info "Sonde envoyée à ${poweredDevices?.size() ?: 0} appareil(s)"
}

def sendReport() {
    List<String> problems = []
    Long pingAt = (state.pingAt ?: 0L) as Long

    poweredDevices?.each { dev ->
        Date last = dev.getLastActivity()
        if (!last || last.time < pingAt) {
            problems << "• ${dev.displayName} — aucune réponse (dernier signe : ${fmt(last)})"
        }
    }

    Long limit = now() - ((batteryHours ?: 26) as Long) * 3600000L
    batteryDevices?.each { dev ->
        Date last = dev.getLastActivity()
        if (!last || last.time < limit) {
            problems << "• ${dev.displayName} — inactif (dernier signe : ${fmt(last)})"
        }
        def batt = dev.currentValue("battery")
        if (batt != null && (batt as Integer) < ((batteryLow ?: 20) as Integer)) {
            problems << "• ${dev.displayName} — pile faible (${batt} %)"
        }
    }

    String msg
    if (problems) {
        msg = "⚠️ Hubitat : ${problems.size()} problème(s)\n" + problems.join("\n")
    } else if (notifyIfAllOk) {
        msg = "✅ Hubitat : tous les appareils surveillés ont répondu."
    }

    if (msg) notifier*.deviceNotification(msg)
    log.info msg ?: "Rapport : aucun problème"
}

private String fmt(Date d) {
    d ? d.format("d MMM HH:mm", location.timeZone) : "jamais"
}

private String toCron(String t) {
    Date d = toDateTime(t)
    String m = d.format("m", location.timeZone)
    String h = d.format("H", location.timeZone)
    return "0 ${m} ${h} * * ?"
}
