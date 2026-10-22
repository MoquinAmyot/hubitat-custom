### 2. Drivers
* **Virtual Text Device (`VirtualTextDevice.groovy`)** : 
  * **Rôle** : Appareil virtuel léger et indispensable servant de passerelle de texte pour recevoir des webhooks externes (provenant de caméras, sonnettes ou systèmes tiers) via **Maker API**.
  * **Fonctionnement** : Il expose un attribut texte standard et une commande `setText` permettant d'injecter dynamiquement un mot-clé (ex: `avant_personne`, `sonnette`) pour déclencher vos automatisations sans multiplier les interrupteurs virtuels.
