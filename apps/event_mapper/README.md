### 3. Applications de la Suite

* **Mappeur d'Événements HTTP (`EventMapperApp`)** :
  * **Rôle** : Permet de centraliser et de simplifier les déclenchements externes (provenant de webhooks ou de systèmes tiers comme UniFi) en utilisant un point d'entrée unique (`Virtual Text Device`).
  * **Fonctionnement** : L'application écoute les mots-clés textuels reçus sur l'appareil virtuel (ex: `arriere`, `sonnette`, `voiture`) via une table de correspondance de type tableau configurée directement depuis l'interface Hubitat. À réception d'un mot-clé, elle exécute simultanément un double flux : l'envoi d'un message SMS personnalisé via votre driver **VoIP.ms SMS** et l'activation de la lumière associée.
  * **Utilisation (Maker API)** : 
    ```text
    http://[IP_HUBITAT]/apps/api/[API_ID]/devices/[ID_TEXT_DEVICE]/setText/[MOT_CLE]?access_token=[TOKEN]
    ```
