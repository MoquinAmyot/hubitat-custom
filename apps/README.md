# Gestionnaire Thermique Hubitat (Delta T & Point de Rosée)

Application Groovy sur mesure pour **Hubitat Elevation** qui combine l'équilibrage thermique intelligent des pièces et un conseiller de ventilation naturelle (*Free-Cooling / Chauffage passif*) basé sur le calcul précis du point de rosée.

---

## 🚀 Fonctionnalités principales

### 1. Équilibrage Thermique (Delta T)
* **Brassage d'air automatique :** Surveille un groupe de sondes de température intérieures. Si l'écart ($\Delta T$) entre la pièce la plus chaude et la plus fraîche dépasse le seuil configuré (ex. 2,5 °C), l'application active automatiquement le ventilateur du thermostat (`fanOn`) pour homogénéiser la température.
* **Hystérésis intégrée :** Coupe le brassage (`fanAuto`) lorsque l'écart redescend sous le seuil d'arrêt (ex. 1,5 °C).
* **Sécurités et pauses :** Intègre une durée maximale de cycle continu (ex. 45 min) suivie d'une période de pause (*cooldown*) pour éviter de solliciter inutilement le système CVC.
* **Respect du thermostat :** Se met automatiquement en pause si votre thermostat passe en mode `off` (par exemple, si vous ouvrez la porte patio).

### 2. Conseiller Point de Rosée (Ventilation Naturelle)
* **Calcul thermodynamique (Formule de Magnus-Tetens) :** Analyse en temps réel la température et l'humidité extérieure par rapport à l'intérieur.
* **Évite les pièges d'humidité :** Empêche d'ouvrir les fenêtres lorsque l'air extérieur est trop humide (point de rosée élevé), ce qui forcerait inutilement votre climatiseur par la suite.
* **Gestion des opportunités :** 
  * *Free-Cooling (Été) :* Suggère d'ouvrir les fenêtres pour rafraîchir la maison sans climatiser.
  * *Chauffage passif (Inter-saison) :* Suggère de profiter de la chaleur extérieure pour réchauffer la maison gratuitement.
* **Anti-spam & Heures silencieuses :** Évite d'envoyer des notifications à répétition et respecte vos plages horaires de repos.

---

## 📦 Prérequis

* Un hub **Hubitat Elevation** fonctionnel.
* Une ou plusieurs sondes de température intérieures connectées.
* Un thermostat compatible (ex. **Ecobee**).
* Un dispositif de notification configuré sur Hubitat (recommandé : votre driver SMS `VoipMsNotifier`).
* Une sonde de température et d'humidité extérieure (ou les attributs météo de votre thermostat).

---

## 🛠️ Installation

### Option A : Via Hubitat Package Manager (HPM) (Recommandé)
1. Installez votre dépôt principal dans HPM en utilisant l'URL de votre fichier `packageManifest.json`.
2. HPM installera et gérera les mises à jour automatiquement.

### Option B : Installation manuelle
1. Rendez-vous dans votre interface Hubitat : **Apps Code > New App**.
2. Copiez-collez le code source du fichier `ThermalManagerApp.groovy`.
3. Cliquez sur **Save**.
4. Allez dans **Apps > Add User App**, sélectionnez **Gestionnaire Thermique (Delta T & Point de Rosée)** et procédez à la configuration.

---

## ⚙️ Paramètres de configuration

| Section | Paramètre | Description | Défaut |
| :--- | :--- | :--- | :--- |
| **Intérieur** | Sondes intérieures | Sélection de toutes vos sondes pour calculer la moyenne et le $\Delta T$. | *Requis* |
| **Intérieur** | Thermostat | Votre thermostat principal (ex. Ecobee). | *Requis* |
| **Delta T** | Écart d'activation | Différence de température entre pièces pour lancer le ventilateur. | `2.5 °C` |
| **Delta T** | Écart d'arrêt (Hystérésis) | Seuil bas pour couper le ventilateur. | `1.5 °C` |
| **Point de Rosée** | Seuil Max d'ouverture | Point de rosée extérieur à ne pas dépasser pour garder l'air sec. | `15.5 °C` |
| **Notifications** | Appareil de notification | Votre dispositif d'alerte (ex. SMS Voip.ms). | *Optionnel* |

---

## 📄 Licence
Ce projet est sous licence **MIT**. Vous êtes libre de l'utiliser, de le modifier et de le partager.
