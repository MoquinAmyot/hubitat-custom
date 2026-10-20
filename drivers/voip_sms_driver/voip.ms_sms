/**
 *  VoIP.ms SMS — driver de notification Hubitat (v3)
 *
 *  Apparaît comme un appareil avec la capacité « Notification ».
 *  - File d'attente : les messages ne partent jamais en rafale.
 *  - Regroupement par type de message : le premier message d'un type part
 *    tout de suite; les répétitions du même type pendant X secondes sont
 *    regroupées en « texte (x7) » et envoyées à la fin de l'attente.
 *    Un message d'un autre type (ex. la sonnette pendant une rafale de la
 *    caméra) part immédiatement. Les chiffres (heures, compteurs) sont
 *    ignorés pour reconnaître le type.
 *  - Mots urgents (fuite, fumée...) : toujours envoyés sans attendre.
 *  - Heure de l'événement au début du message (« 22:50 Test »), pour voir
 *    tout délai d'acheminement; plage pour un regroupement (« 22:50-22:52 Test (x3) »).
 *  - Délai minimal entre deux envois à l'API.
 *  - Nouvel essai en cas d'erreur, avec délai croissant (30 s, 60 s, 2 min...).
 *  - Plafond quotidien : l'API VoIP.ms limite à 100 SMS/jour par défaut.
 *    Une réserve est gardée pour les alertes urgentes.
 *  - Le mot de passe API reste dans les préférences, jamais dans le code.
 *
 *  Note : la file est en mémoire; un redémarrage du hub vide les messages en attente.
 */

import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap

@Field static final ConcurrentHashMap<Long, Map> MEM = new ConcurrentHashMap<Long, Map>()

metadata {
    definition(name: "VoIP.ms SMS", namespace: "btremblay", author: "Benoit Tremblay") {
        capability "Notification"
        capability "Actuator"
        command "clearQueue"
        attribute "lastStatus", "string"
        attribute "lastSent", "string"
        attribute "queueSize", "number"
        attribute "smsToday", "number"
    }
    preferences {
        input "apiUser", "text", title: "Courriel du compte VoIP.ms", required: true
        input "apiPass", "password", title: "Mot de passe API VoIP.ms", required: true
        input "did", "text", title: "Numéro d'envoi VoIP.ms (DID, chiffres seulement)", required: true
        input "dst", "text", title: "Destinataire(s), séparés par des virgules", required: true
        input "groupSeconds", "number", title: "Après un envoi, regrouper les messages suivants pendant (secondes)", defaultValue: 120
        input "minDelaySeconds", "number", title: "Délai minimal entre deux SMS (secondes)", defaultValue: 15
        input "addTime", "bool", title: "Ajouter l'heure de l'événement au début du message", defaultValue: true
        input "maxRetries", "number", title: "Nombre d'essais en cas d'erreur", defaultValue: 5
        input "maxParts", "number", title: "Nombre max. de SMS pour un message regroupé", defaultValue: 2
        input "dailyLimit", "number", title: "Plafond quotidien de SMS (API VoIP.ms : 100)", defaultValue: 90
        input "urgentReserve", "number", title: "SMS réservés aux alertes urgentes", defaultValue: 15
        input "urgentWords", "text", title: "Mots urgents (séparés par des virgules)",
            defaultValue: "fuite,fumée,feu,incendie,monoxyde"
        input "logEnable", "bool", title: "Journalisation détaillée (30 min)", defaultValue: false
    }
}

// ---------- Cycle de vie ----------

def installed() { updated() }

def updated() {
    Map m = mem()
    synchronized (m) { m.pendingAt = 0L; m.inFlight = false }
    resetDailyIfNeeded()
    if (logEnable) runIn(1800, "logsOff")
    if (queueSize() > 0) scheduleProcess(1000L)
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def clearQueue() {
    Map m = mem()
    synchronized (m) {
        m.inbox.clear(); m.outbox.clear()
        m.inFlight = false; m.pendingAt = 0L; m.windows.clear()
    }
    updateQueueSize()
    log.info "File d'attente vidée"
}

// ---------- Réception des notifications ----------

def deviceNotification(text) {
    if (!text) return
    String t = text.toString().trim()
    boolean urgent = isUrgent(t)
    String key = keyOf(t)
    Map m = mem()
    Long windowLeft
    synchronized (m) {
        m.inbox << [text: t, ts: now(), urgent: urgent, key: key]
        windowLeft = ((m.windows[key] ?: 0L) as Long) - now()
    }
    updateQueueSize()
    // Nouveau type de message, attente terminée ou urgent : envoi immédiat
    scheduleProcess((urgent || windowLeft <= 0) ? 200L : windowLeft)
}

// ---------- Traitement de la file ----------

def processQueue() {
    Map m = mem()
    synchronized (m) { m.pendingAt = 0L }
    resetDailyIfNeeded()

    // Sécurité : une réponse jamais reçue ne bloque pas la file
    if (m.inFlight && now() - (m.inFlightSince as Long) > 60000L) {
        synchronized (m) { m.inFlight = false }
    }
    if (m.inFlight) return

    Long wait = (m.nextAllowedAt as Long) - now()
    if (wait > 0) { scheduleProcess(wait); return }

    if (m.outbox.isEmpty()) {
        Long remaining = buildOutbox()
        if (remaining > 0) { scheduleProcess(remaining); return }
        if (m.outbox.isEmpty()) { updateQueueSize(); return }
    }

    Map item
    synchronized (m) { item = m.outbox[0] }

    int count = (state.count ?: 0) as int
    int hardCap = (dailyLimit ?: 90) as int
    int softCap = Math.max(0, hardCap - ((urgentReserve ?: 15) as int))
    if (count >= hardCap || (!item.urgent && count >= softCap)) {
        synchronized (m) { m.outbox.remove(0) }
        log.warn "Plafond quotidien atteint, SMS abandonné : ${item.text}"
        sendEvent(name: "lastStatus", value: "plafond quotidien atteint")
        if (!state.capWarned && count < hardCap) {
            state.capWarned = true
            String warn = "Hubitat : plafond de SMS presque atteint. Seules les alertes urgentes seront envoyées jusqu'à minuit."
            synchronized (m) {
                numbers().reverse().each { n -> m.outbox.add(0, [dst: n, text: warn, attempts: 0, urgent: true]) }
            }
        }
        updateQueueSize()
        scheduleProcess(200L)
        return
    }

    synchronized (m) {
        m.inFlight = true
        m.inFlightSince = now()
        m.nextAllowedAt = now() + minDelayMs()
    }
    send(item)
}

private Long buildOutbox() {
    Map m = mem()
    List batch = []
    Long nextWait = 0L
    synchronized (m) {
        if (m.inbox.isEmpty()) return 0L
        Long t = now()
        Set readyKeys = new HashSet()
        m.inbox.each { if (it.urgent || ((m.windows[it.key] ?: 0L) as Long) <= t) readyKeys << it.key }
        List keep = []
        m.inbox.each { (readyKeys.contains(it.key) ? batch : keep) << it }
        m.inbox.clear()
        m.inbox.addAll(keep)
        // Chaque type envoyé démarre sa propre période d'attente
        readyKeys.each { m.windows[it] = t + groupMs() }
        List expired = m.windows.findAll { k, v -> (v as Long) < t }.collect { it.key }
        expired.each { m.windows.remove(it) }
        if (batch.isEmpty()) {
            nextWait = keep.collect { ((m.windows[it.key] ?: 0L) as Long) - t }.min() as Long
        }
    }
    if (batch.isEmpty()) return Math.max(nextWait ?: 0L, 200L)

    boolean urgent = batch.any { it.urgent }
    Map<String, List> byKey = new LinkedHashMap<String, List>()
    batch.each { if (!byKey[it.key]) byKey[it.key] = []; byKey[it.key] << it }
    List<String> keys = new ArrayList<String>(byKey.keySet())
    keys.sort { a, b -> (byKey[b].any { it.urgent } ? 1 : 0) <=> (byKey[a].any { it.urgent } ? 1 : 0) }   // urgents en premier
    boolean withTime = (addTime != false)
    String merged = keys.collect { k ->
        List l = byKey[k]
        String body = withTime ? stripTime(l[-1].text) : l[-1].text
        String prefix = ""
        if (withTime) {
            String first = hhmm(l[0].ts as Long)
            String last = hhmm(l[-1].ts as Long)
            prefix = (first == last ? first : "${first}-${last}") + " "
        }
        prefix + body + (l.size() > 1 ? " (x${l.size()})" : "")
    }.join(" | ")

    int parts = Math.max(1, (maxParts ?: 2) as int)
    int maxChars = (parts == 1) ? 160 : 152 * parts
    if (merged.size() > maxChars) merged = merged.take(maxChars - 3) + "..."

    List<String> chunks = splitMessage(merged, 160)
    synchronized (m) {
        numbers().each { n ->
            chunks.each { c -> m.outbox << [dst: n, text: c, attempts: 0, urgent: urgent] }
        }
    }
    updateQueueSize()
    return 0L
}

private void send(Map item) {
    Map params = [
        uri    : "https://voip.ms/api/v1/rest.php",
        query  : [
            api_username: apiUser,
            api_password: apiPass,
            method      : "sendSMS",
            did         : did,
            dst         : item.dst,
            message     : item.text
        ],
        timeout: 20
    ]
    if (logEnable) log.debug "Envoi à ${item.dst} : ${item.text}"
    asynchttpGet("handleResponse", params)
}

def handleResponse(resp, data) {
    Map m = mem()
    String status
    try {
        status = (resp.status == 200) ? (resp.json?.status ?: "réponse inconnue") : "HTTP ${resp.status}"
    } catch (e) {
        status = "erreur : ${e.message}"
    }

    Map item
    synchronized (m) {
        m.inFlight = false
        item = m.outbox ? m.outbox[0] : null
    }
    sendEvent(name: "lastStatus", value: status)
    if (item == null) { scheduleProcess(minDelayMs()); return }

    if (status == "success") {
        synchronized (m) { m.outbox.remove(0) }
        state.count = ((state.count ?: 0) as int) + 1
        sendEvent(name: "smsToday", value: state.count)
        sendEvent(name: "lastSent", value: item.text.take(255))
        if (logEnable) log.debug "SMS envoyé à ${item.dst}"
        scheduleProcess(minDelayMs())
    } else {
        item.attempts = ((item.attempts ?: 0) as int) + 1
        if (isFatal(status) || item.attempts >= ((maxRetries ?: 5) as int)) {
            synchronized (m) { m.outbox.remove(0) }
            log.error "SMS abandonné vers ${item.dst} après ${item.attempts} essai(s) : ${status}"
            scheduleProcess(minDelayMs())
        } else {
            Long backoff = Math.min(minDelayMs() * (1L << (item.attempts as int)), 600000L)
            synchronized (m) { m.nextAllowedAt = now() + backoff }
            log.warn "Échec SMS vers ${item.dst} (${status}), nouvel essai dans ${(backoff / 1000) as int} s"
            scheduleProcess(backoff)
        }
    }
    updateQueueSize()
}

// ---------- Outils ----------

private Map mem() {
    Long id = device.idAsLong
    Map m = MEM.get(id)
    if (m == null) {
        MEM.putIfAbsent(id, Collections.synchronizedMap([
            inbox: [], outbox: [], inFlight: false, inFlightSince: 0L,
            nextAllowedAt: 0L, pendingAt: 0L, windows: [:]
        ]))
        m = MEM.get(id)
    }
    if (m.windows == null) m.windows = [:]   // mémoire créée par une version précédente
    return m
}

private void scheduleProcess(Long ms) {
    Map m = mem()
    Long target = now() + Math.max(ms ?: 0L, 200L)
    boolean doIt = false
    synchronized (m) {
        Long pending = (m.pendingAt ?: 0L) as Long
        if (pending == 0L || target < pending || pending < now() - 5000L) {
            m.pendingAt = target
            doIt = true
        }
    }
    if (doIt) runInMillis(Math.max(target - now(), 200L), "processQueue")
}

private void resetDailyIfNeeded() {
    String today = new Date().format("yyyy-MM-dd", location.timeZone)
    if (state.day != today) {
        state.day = today
        state.count = 0
        state.capWarned = false
        sendEvent(name: "smsToday", value: 0)
    }
}

private boolean isUrgent(String t) {
    List<String> words = (urgentWords ?: "").split(",")*.trim().findAll { it }*.toLowerCase()
    String lt = (t ?: "").toLowerCase()
    return words.any { lt.contains(it) }
}

private boolean isFatal(String status) {
    return status.startsWith("invalid") || status.startsWith("missing") ||
        status in ["ip_not_enabled", "no_api", "sms_toolong", "message_empty"]
}

// Heure de réception par le driver (≈ heure de l'événement)
private String hhmm(Long ts) {
    return new Date(ts).format("HH:mm", location.timeZone)
}

// Retire une heure déjà présente au début du texte (« 22:50 », « 22h50 ») pour éviter les doublons
private String stripTime(String t) {
    return (t ?: "").replaceFirst(/^\s*\d{1,2}[:h]\d{2}\s*/, "")
}

// « 14:05 Mouvement : Caméra » et « 14:07 Mouvement : Caméra » = même type
private String keyOf(String t) {
    return (t ?: "").replaceAll(/\d+/, "#").toLowerCase().trim()
}

private List<String> numbers() {
    return (dst ?: "").split(",")*.trim().findAll { it }
}

private Long groupMs() { return ((groupSeconds ?: 120) as Long) * 1000L }

private Long minDelayMs() { return ((minDelaySeconds ?: 15) as Long) * 1000L }

private int queueSize() {
    Map m = mem()
    synchronized (m) { return m.inbox.size() + m.outbox.size() }
}

private void updateQueueSize() {
    sendEvent(name: "queueSize", value: queueSize())
}

private List<String> splitMessage(String text, int max) {
    if (text.size() <= max) return [text]
    int size = max - 8   // place pour le préfixe « (1/n) »
    List<String> chunks = []
    for (int i = 0; i < text.size(); i += size) {
        chunks << text.substring(i, Math.min(i + size, text.size()))
    }
    int n = chunks.size()
    List<String> out = []
    chunks.eachWithIndex { c, i -> out << "(${i + 1}/${n}) ${c}" }
    return out
}
