# Device State Monitor pour Hubitat

Une application Hubitat légère et efficace pour surveiller en temps réel l'état d'un groupe d'appareils (présences OwnTracks, lumières, serrures, capteurs, etc.) et recevoir une notification instantanée par SMS via **Voip.ms** à chaque changement d'état.

Idéal pour valider de temps à autre que vos intégrations de présence (comme OwnTracks) fonctionnent correctement.

---

## Fonctionnalités
* **Sélection flexible :** Choisissez n'importe quel appareil pris en charge et surveillez ses attributs clés (`switch`, `presence`, `motion`, `contact`, `battery`, `level`, etc.).
* **Notifications instantanées :** Envoie un SMS formaté avec le nom de l'appareil et son nouvel état dès qu'un changement survient.
* **Intégration native Voip.ms :** S'associe directement à votre pilote de notification Voip.ms existant sur Hubitat.

---

## Installation

### Option 1 : Installation manuelle (Code brut)
1. Ouvrez votre interface d'administration Hubitat.
2. Allez dans **Apps Code** > **New App**.
3. Copiez le code du fichier [DeviceStateMonitorApp.groovy](apps/DeviceStateMonitorApp.groovy) et collez-le.
4. Cliquez sur **Save**.
5. Allez dans **Apps** > **Add User App** et sélectionnez **Moniteur d'État des Appareils (SMS)**.

### Option 2 : Via Hubitat Package Manager (HPM) *(Recommandé)*
1. Ouvrez **Package Manager** sur votre hub.
2. Choisissez **Import** et entrez l'URL de votre fichier manifeste `packageManifest.json`.

---

## Auteur
Développé par **Benoit Tremblay**.
