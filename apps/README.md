# Vérification de santé des appareils — Hubitat

App Hubitat qui surveille que vos appareils Zigbee, Z-Wave et Matter répondent toujours, surtout les appareils critiques (détecteurs de fuite, de fumée, etc.).

## Fonctionnement

- **Appareils alimentés (secteur)** : à l'heure de la sonde (ex. 8 h), l'app envoie `ping()` ou `refresh()`. À l'heure du rapport (ex. 20 h), elle signale ceux qui n'ont rien renvoyé depuis.
- **Appareils à pile (dormants)** : ils ne répondent pas aux sondes sur demande. L'app vérifie plutôt leur *dernière activité* et signale ceux qui sont silencieux depuis plus de X heures, ainsi que les piles faibles.
- **Message « tout va bien »** (optionnel, activé par défaut) : envoyé chaque soir quand tout fonctionne. S'il n'arrive pas, c'est le hub lui-même qui a un problème.

## Installation

1. Dans Hubitat : **Apps Code** → **New App** → coller le contenu de `verification-sante-appareils.groovy` → **Save**.
   *(Ou bouton **Import** avec l'URL « Raw » du fichier sur GitHub.)*
2. **Apps** → **Add User App** → *Vérification de santé des appareils*.
3. Choisir les appareils, les heures et l'appareil de notification, puis tester avec les boutons.

## Réglage du seuil des appareils à pile

Observez la colonne **Last Activity** de la liste des appareils pendant quelques jours, puis fixez le seuil un peu au-dessus du rythme de rapport le plus lent.

## Limite

L'app confirme que le lien radio fonctionne, pas que le capteur détecte. Continuez de tester vos détecteurs avec leur bouton de test.

## Licence

MIT
