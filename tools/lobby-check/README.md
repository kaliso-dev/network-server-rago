# lobby-check : le lobby et le duel sumo joués par deux bots

Un test sur un vrai serveur PandaSpigot 1.8.8 (le build 226 du test de contrôle), avec
RagoCombatEngine, RagoLobby et RagoPractice. Deux bots mineflayer (le script
`tools/kb-control/bots/lobby-bots.mjs`, qui partage les dépendances des bots du test de contrôle)
jouent comme des joueurs :

1. ils arrivent dans le lobby : mode aventure, la boussole et le colorant dans la barre d'objets,
   le scoreboard ;
2. RagoUn ouvre le sélecteur d'un clic droit et clique sur le duel sumo ; RagoDeux tape
   `/file sumo` ;
3. le duel démarre : RagoUn frappe RagoDeux une fois (il doit recevoir du knockback et aucun
   dégât), puis RagoDeux tombe trois fois de la plateforme : RagoUn gagne 3 - 0 ;
4. les deux reviennent au lobby, au spawn, avec les objets du lobby ;
5. un second duel, que RagoUn quitte avec `/spawn` : RagoDeux gagne par abandon.
6. RagoUn défie RagoDeux avec `/duel RagoDeux sumo`, RagoDeux accepte avec `/duel accepter RagoUn`
   (ce que lance le bouton cliquable), le duel démarre sans passer par la file, puis RagoDeux
   abandonne.

Le test échoue si un bot n'obtient pas ce qu'il attend, ou si le journal du serveur contient une
erreur ou une exception.

```bash
./gradlew build
(cd tools/kb-control/bots && npm ci)
KB_ACCEPT_EULA=true tools/lobby-check/run-lobby-test.sh
```

Lancer un serveur Minecraft demande d'accepter le CLUF de Minecraft (https://aka.ms/MinecraftEULA) :
le script refuse de démarrer sans `KB_ACCEPT_EULA=true`. Réglages : `KB_SERVER_JAVA` (le java du
serveur), `KB_PORT` (25565) et `LOBBY_RUN_DIR` (`lobby-run` à la racine du dépôt, où restent le
journal et le dossier du serveur). La CI le lance à chaque pull request, sous Java 8 et Java 21.
