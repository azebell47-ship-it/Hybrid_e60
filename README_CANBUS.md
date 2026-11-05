# Hybrid_e60 — diagnostic CANBUS (prototype)

Le bouton **Diagnostic** ou **Moteur** affiche les applications CANBUS visibles sur l’autoradio et permet de copier un rapport. Aucun accès aux trames CAN, aux températures moteur, aux commandes de climatisation ou au protocole propriétaire CarCanbus n’est implémenté. La recherche peut être incomplète selon les restrictions Android et les noms des applications.

## Test sur autoradio
1. Compiler/installer l’application Android sur l’autoradio.
2. Appuyer sur **Diagnostic**, puis **Copier le rapport**.
3. Transmettre le rapport en retirant toute donnée personnelle éventuelle.
4. Pour intégrer les données réelles, identifier une API, un broadcast documenté ou un adaptateur OBD2 compatible. Ne pas supposer que la présence de CarCanbus autorise l’accès à ses données.

Le composant `Car3DView.java` et les fichiers GLB ne sont pas modifiés. Compilation et fonctionnement sur autoradio non vérifiés.
