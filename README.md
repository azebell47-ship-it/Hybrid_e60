# Hybrid_e60 V1 — projet Android Studio

Prototype natif Android (Java) pour BMW E60 530d 2005, autoradio ZQ9109 Android 14, format panoramique 1920×720.

## Fonctions V1
- Écran d'accueil plein écran, voiture photographique fidèle au visuel approuvé.
- Animation cinématique d'apparition de la voiture et des compteurs (les Angel Eyes jaunes sont présents sur l'image).
- Huit onglets tactiles ; écran véhicule et moteur en mode démonstration.
- Boutons de simulation des quatre portes ; pas d'ouverture physique ou 3D.
- Accès aux paramètres Android depuis Réglages.
- Déclaration HOME pour pouvoir choisir l'application comme launcher par défaut si le firmware le permet.

## Compiler
1. Ouvrir le dossier dans Android Studio (JDK 17, Android SDK 35).
2. Laisser Gradle synchroniser les dépendances (Internet requis au premier lancement).
3. Build > Build APK(s). Le fichier sera sous app/build/outputs/apk/debug/.
4. Installer et lancer manuellement **avant** de définir l'application comme écran d'accueil.
5. Vérifier la caméra de recul, le retour à l'iDrive d'origine, les réglages et la sortie de veille.
6. Si tout fonctionne, sélectionner Hybrid_e60 comme application d'accueil dans les réglages Android, **si cette option est proposée**.

## Limitations et sécurité
Aucun accès CAN, OBD, IHKA ou MCU n'est implémenté. Les valeurs affichées sont fictives et ne doivent jamais être utilisées pour diagnostiquer le véhicule. L'image de la voiture n'est pas un modèle 3D : rotation et animations d'ouverture des portes nécessitent un modèle GLB et un moteur 3D. La photo de la voiture est extraite du visuel de concept validé. Aucune modification du firmware, de la configuration CANBUS ou des commandes de climatisation n'est effectuée. Les actions propres au firmware (caméra de recul, iDrive d'origine) ne sont pas encore intégrées ni garanties lorsque le launcher est actif.

## Prochain jalon
Identifier le protocole d'accès aux données CANBUS de ZQ9109 et tester la compatibilité du launcher par défaut ; remplacer ensuite l'image par un vrai modèle 3D optimisé.
