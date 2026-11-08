# lobby-check : le lobby, le duel sumo et Bed Wars joués par deux bots

Un test sur un vrai serveur PandaSpigot 1.8.8 (le build 226 du test de contrôle), avec
RagoCombatEngine, RagoLobby, RagoPractice et RagoBedwars. Deux bots mineflayer (le script
`tools/kb-control/bots/lobby-bots.mjs`, qui partage les dépendances des bots du test de contrôle)
jouent comme des joueurs :

1. ils arrivent dans le lobby : mode aventure, la boussole, le livre de l'éditeur de kit et le
   colorant dans la barre d'objets, le scoreboard. Le script donne au sumo un kit d'un bâton (le
   config.yml fourni n'en a pas) : RagoUn ouvre l'éditeur avec le livre, choisit le sumo, déplace
   le bâton de la première case à la quatrième, enregistre, et le retrouve là en rouvrant
   l'éditeur. Le serveur de test n'a pas de clé d'API Hypixel : RagoDeux trouve l'émeraude et le
   coffre de l'import Hypixel en bas du livre, et l'émeraude comme `/hypixel import Notch` disent
   que l'import n'est pas en place, `/hypixel shop` que rien n'a été importé ;
2. RagoUn ouvre le sélecteur d'un clic droit et clique sur le duel sumo ; RagoDeux tape
   `/queue sumo` ;
3. le duel démarre, après le décompte 3, 2, 1, « Fight! » affiché en titre (sans fondu), chacun a
   le bâton là où il l'a rangé (RagoUn : quatrième case, RagoDeux : la première, celle du kit), et
   le scoreboard montre l'adversaire et le ping des deux joueurs :
   RagoUn frappe RagoDeux une fois (il doit recevoir du knockback et aucun
   dégât), puis RagoDeux tombe trois fois de la plateforme : RagoUn gagne 3 - 0 ;
4. les deux reviennent au lobby, au spawn, avec les objets du lobby. Chacun lit son profil
   au-dessus du PNJ de stats (victoires, kills) et les classements (RagoUn premier, 3 kills) ; la
   barre d'XP montre le niveau Network (65 XP pour RagoUn : la victoire et trois kills, 15 pour
   RagoDeux) ; RagoUn clique sur le classement principal (clic droit : Elo, puis Kills, clic
   gauche : la semaine, clic droit : niveau Network) ; dans le menu du PNJ Duels, le sumo montre
   « Daily Streak: 0 » pour RagoDeux et RagoUn premier des séries du jour ;
5. un second duel, que RagoUn quitte avec `/spawn` : RagoDeux gagne par abandon.
6. RagoUn défie RagoDeux avec `/duel RagoDeux sumo`, RagoDeux accepte avec `/duel accept RagoUn`
   (ce que lance le bouton cliquable), le duel démarre sans passer par la file, puis RagoDeux
   abandonne ;
7. une partie de Ranked Bedwars 1v1 sur la map de démo de RagoBedwars (`bw-demo`, créée au
   démarrage, et copiée dans `bw_bw-demo_1` où se joue la partie) : les deux tapent
   `/queue ranked-1v1`, arrivent sur leur île avec une épée en bois et une armure en cuir, le
   scoreboard montre les équipes et les kills. Après « Fight! », RagoUn ramasse le fer de son
   générateur sans bouger de son spawn, ouvre le shop du villageois, achète de la laine dans le
   Quick Buy, ne peut pas la poser près de son spawn, la pose à côté de son lit et la recasse, ne
   peut pas casser son propre lit, puis achète une boule de feu et saute dessus (le souffle le
   soulève et lui prend un cœur). RagoDeux recule hors de son île, meurt dans le vide et revient
   sur son île 5 secondes plus tard (son lit tient), puis tape `/spawn` : RagoUn gagne et les deux
   reviennent au lobby.
8. un First Rush, un vrai 1v1 : RagoUn demande une map que le mode n'a pas (refusée), puis
   `bw-demo`, RagoDeux n'importe quelle map (`random`) ; ils se retrouvent sur `bw-demo`, l'un
   rouge, l'autre bleu, avec les événements d'une vraie partie dans le scoreboard. RagoDeux tape
   `/spawn` : RagoUn gagne et les deux reviennent au lobby.

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
