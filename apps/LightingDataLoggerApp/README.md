# Enregistreur de Données d'Éclairage (Google Forms & CSV) pour Hubitat

Application de collecte de données (Étape 1) conçue pour analyser la corrélation entre la luminosité extérieure réelle (capteurs **Philips Hue Motion Sensor SML004** en façade Avant et Arrière), l'heure de la journée et les habitudes d'utilisation manuelle des 7 interrupteurs du rez-de-chaussée.

Les données peuvent être envoyées en temps réel vers **Google Forms (Google Sheets)** via `asynchttpGet` (zéro écriture disque sur le hub) et/ou enregistrées dans un fichier CSV local avec rotation mensuelle automatique (`lighting_data_YYYY-MM.csv`).

---

## Architecture des Zones Surveillées

1. **Zone Arrière (Orientation Est-Sud-Est)**
   * **Capteur :** Philips Hue Lux Arrière (`Lux_Arriere`).
   * **Cuisine (3 interrupteurs) :** Îlot (`Cuisine_Ilot`), Spots (`Cuisine_Spots`), et Sous les armoires (`Cuisine_Sous_Armoires`).
   * **Salle familiale (1 interrupteur) :** `Salle_Fam`.
2. **Zone Avant (Orientation Ouest-Nord-Ouest)**
   * **Capteur :** Philips Hue Lux Avant (`Lux_Avant`).
   * **Salle à dîner (1 interrupteur) :** `Salle_Diner`.
   * **Salon (2 interrupteurs) :** Foyer (`Salon_Foyer`) et Spots (`Salon_Spots`).

---

## Configuration rapide du Google Form (14 colonnes)

1. Créez un nouveau formulaire sur [Google Forms](https://docs.google.com/forms/) avec **14 questions de type « Réponse courte »** dans cet ordre exact :
   1. `Zone_Event`
   2. `Type_Event`
   3. `Appareil_Declencheur`
   4. `Valeur`
   5. `Mode_Hub`
   6. `Lux_Arriere`
   7. `Lux_Avant`
   8. `Cuisine_Ilot`
   9. `Cuisine_Spots`
   10. `Cuisine_Sous_Armoires`
   11. `Salle_Fam`
   12. `Salle_Diner`
   13. `Salon_Foyer`
   14. `Salon_Spots`
2. Cliquez sur le menu **⋮** (en haut à droite) > **Obtenir le lien pré-rempli**.
3. Saisissez `1` dans chacun des 14 champs, cliquez sur **Obtenir le lien**, puis **Copier le lien**.
4. Collez ce lien unique dans l'application Hubitat : elle extrait automatiquement l'URL `/formResponse` ainsi que les 14 identifiants `entry.xxxxxx`.
