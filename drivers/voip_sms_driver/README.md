# VoIP.ms SMS — driver de notification pour Hubitat

Driver Hubitat qui envoie des SMS par l’API de [VoIP.ms](https://voip.ms). Il crée un appareil avec la capacité **Notification**. N’importe quelle app ou règle (Rule Machine, Vérification de santé des appareils, Suivi de présence, Alerte caméra…) peut donc l’utiliser comme notificateur.

Le driver est conçu pour respecter les limites de l’API VoIP.ms : il ne fait jamais partir des messages en rafale et il surveille le quota quotidien.

## Fonctionnalités

- **File d’attente** : les messages sont envoyés un à la fois, avec un délai minimal entre deux appels à l’API.
- **Regroupement par type de message** : le premier message d’un type part tout de suite. Pendant la période d’attente qui suit (2 min par défaut), les répétitions du même type sont regroupées et partent en un seul SMS à la fin de l’attente, par exemple `Mouvement caméra (x14)`. Un message d’un **autre type** (par exemple la sonnette pendant une rafale de la caméra) part immédiatement. Si rien n’arrive pendant l’attente, le message suivant repart instantanément.
  - Les chiffres (heures, compteurs) sont ignorés pour reconnaître le type : `14:05 Mouvement : Caméra entrée` et `14:07 Mouvement : Caméra entrée` sont du même type.
- **Messages urgents** : les messages contenant un mot urgent (fuite, fumée, feu, incendie, monoxyde) partent toujours sans attendre et passent en tête de la file.
- **Heure de l’événement** : chaque message commence par l’heure où le driver l’a reçu, par exemple `22:50 Test`. Un regroupement affiche la plage : `22:50-22:52 Test (x3)`. En comparant avec l’heure de réception sur le téléphone, on voit tout délai d’acheminement. Si le texte commence déjà par une heure, elle est remplacée pour éviter les doublons.
- **Réessais** : en cas d’erreur temporaire, le driver réessaie avec un délai croissant (30 s, 1 min, 2 min…), jusqu’à 5 essais. Les erreurs sans espoir (identifiants ou numéro invalides) sont abandonnées tout de suite.
- **Plafond quotidien** : le driver compte les SMS envoyés et s’arrête avant la limite de l’API. Une réserve est gardée pour les messages urgents, et un avertissement est envoyé quand seuls les messages urgents peuvent encore partir.
- **Découpage** : un message de plus de 160 caractères est découpé en `(1/2)`, `(2/2)`.
- **Plusieurs destinataires** : séparés par des virgules.
- **Encodage** : accents, espaces et caractères spéciaux sont encodés correctement.
- **Sécurité** : le mot de passe API est saisi dans les préférences de l’appareil, jamais dans le code.

## Prérequis côté VoIP.ms

1. **Activer l’API** : *Main Menu → SOAP and REST/JSON API*.
   - Définir un **mot de passe API**. Il est distinct du mot de passe du compte.
   - Autoriser l’**adresse IP publique** de la maison, d’où le hub envoie ses requêtes.
2. **Activer les SMS sur le numéro** (DID) qui servira à l’envoi, dans les réglages du DID.
3. **Connaître la limite** : l’API VoIP.ms est plafonnée à **100 SMS par jour** par défaut. Pour l’augmenter, il faut ouvrir un billet au support. VoIP.ms peut aussi limiter un compte qui présente des comportements d’envoi automatisé en masse, d’où le regroupement et la limitation du débit.

## Installation

1. Dans Hubitat, va dans **Drivers Code → New Driver**, colle le contenu de `voipms-sms-driver.groovy`, puis **Save**.
   *(Ou utilise le bouton **Import** avec l’URL « Raw » du fichier sur GitHub.)*
2. Va dans **Devices → Add Device → Virtual**, donne un nom à l’appareil (par exemple *SMS VoIP.ms*) et choisis le type **VoIP.ms SMS**.
3. Remplis les préférences, puis **Save Preferences**.
4. Teste avec la commande **Device Notification** sur la page de l’appareil.

## Préférences

| Préférence | Défaut | Rôle |
|---|---|---|
| Courriel du compte VoIP.ms | — | Identifiant API (`api_username`) |
| Mot de passe API VoIP.ms | — | Mot de passe API (`api_password`) |
| Numéro d’envoi (DID) | — | Numéro VoIP.ms qui envoie, chiffres seulement |
| Destinataire(s) | — | Un ou plusieurs numéros, séparés par des virgules |
| Regrouper après un envoi (s) | 120 | Période d’attente après l’envoi d’un type de message, pendant laquelle ses répétitions sont regroupées |
| Délai minimal entre deux SMS (s) | 15 | Espacement des appels à l’API |
| Ajouter l’heure | oui | Heure de l’événement au début du message |
| Nombre d’essais | 5 | Essais maximum en cas d’erreur |
| SMS max. par message regroupé | 2 | Au-delà, le texte est tronqué avec `...` |
| Plafond quotidien | 90 | Nombre de SMS par jour, sous la limite de 100 de l’API |
| Réserve urgente | 15 | SMS gardés pour les messages urgents |
| Mots urgents | fuite, fumée, feu, incendie, monoxyde | Insensible à la casse |
| Journalisation détaillée | non | Se désactive seule après 30 min |

**Le plafond compte chaque SMS réel.** Un message envoyé à 2 destinataires compte pour 2. Un message découpé en 2 parties compte aussi pour 2.

## Attributs et commandes

| Élément | Type | Description |
|---|---|---|
| `deviceNotification(texte)` | commande | Ajoute un message à la file |
| `clearQueue()` | commande | Vide la file d’attente |
| `queueSize` | attribut | Messages en attente |
| `smsToday` | attribut | SMS envoyés aujourd’hui (remis à zéro à minuit) |
| `lastStatus` | attribut | Dernière réponse de VoIP.ms (`success` ou code d’erreur) |
| `lastSent` | attribut | Dernier texte envoyé |

## Utilisation

- **Rule Machine** : action *Send, Speak or Log a Message → Send notification*, puis choisis l’appareil VoIP.ms SMS.
- **Apps** : choisis l’appareil dans tout champ « Appareil de notification ».
- **Rendre un message urgent** : il suffit d’y inclure un des mots urgents, par exemple *« Fuite détectée sous l’évier »*.

## Dépannage

| `lastStatus` | Cause probable |
|---|---|
| `invalid_credentials` | Courriel ou mot de passe API incorrect |
| `ip_not_enabled` | L’IP publique de la maison n’est pas autorisée dans VoIP.ms |
| `invalid_did` | Numéro d’envoi incorrect ou SMS non activé sur ce DID |
| `invalid_dst` | Numéro de destinataire incorrect |
| `HTTP 4xx/5xx` ou `erreur` | Problème réseau ou API, le driver réessaie |
| `plafond quotidien atteint` | Le plafond configuré est atteint, reprise à minuit |

Si ton IP publique change (fournisseur Internet, routeur remplacé), mets à jour l’IP autorisée dans VoIP.ms.

## Limites connues

- La file d’attente est gardée en mémoire : un **redémarrage du hub** perd les messages en attente.
- Certains caractères (emojis, et certains accents comme ê, ç ou ô) peuvent obliger l’opérateur à utiliser un encodage où un SMS contient moins de caractères. Le message peut alors arriver en plusieurs morceaux.
- Le compteur quotidien est tenu par le driver : les SMS envoyés ailleurs (portail VoIP.ms, autre système) n’y sont pas comptés.

## Sécurité

- Ne mets jamais le mot de passe API dans le code, dans une variable de hub ou dans une capture d’écran.
- Si le mot de passe a été exposé, régénère-le dans VoIP.ms et mets à jour les préférences du driver.
- Limite l’API à l’IP de la maison seulement.

## Licence

MIT
