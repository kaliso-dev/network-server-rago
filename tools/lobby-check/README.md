# lobby-check : le lobby, le duel sumo, Bed Wars et un event joués par des bots

Un test sur un vrai serveur PandaSpigot 1.8.8 (le build 226 du test de contrôle), avec
RagoCombatEngine, RagoLobby, RagoPractice et RagoBedwars. Deux bots mineflayer, puis trois (le script
`tools/kb-control/bots/lobby-bots.mjs`, qui partage les dépendances des bots du test de contrôle)
jouent comme des joueurs :

1. ils arrivent dans le lobby pour la première fois : « What do you want to do? » s'ouvre un
   instant après l'accueil (quatre façons de jouer, le livre de Getting started), et ils le
   ferment ; mode aventure, la boussole, le livre de l'éditeur de kit et le
   colorant dans la barre d'objets, le scoreboard ; le PNJ Duels se tient sur un bloc de quartz
   et le PNJ de stats sur un bloc de fer (plan 5.3) ; qRago (plan 5.6) se tient sur de
   l'obsidienne sous « [OWNER] qRago », les mains vides, et un clic dessus n'ouvre rien (le script
   retire les `skin-joueur:` du config.yml : le serveur ne demande rien à Mojang, qRago est en Steve). Le script donne au sumo un kit d'un bâton (le
   config.yml fourni n'en a pas) : RagoUn ouvre l'éditeur avec le livre, choisit le sumo, déplace
   le bâton de la première case à la quatrième, enregistre (Save ramène à la liste des kits), et le retrouve là en rouvrant
   l'éditeur. Le serveur de test n'a pas de clé d'API Hypixel : RagoDeux trouve l'émeraude et le
   coffre de l'import Hypixel en bas du livre, et l'émeraude comme `/hypixel import Notch` disent
   que l'import n'est pas en place, `/hypixel shop` que rien n'a été importé ;
2. RagoUn ouvre le sélecteur d'un clic droit (les catégories sur la ligne du milieu, comme
   avant), clique sur Duels : ses jeux s'ouvrent, rien sur leur ligne du haut (le sumo montre
   « In queue: 0 ») ; il clique sur le duel sumo : sous le PNJ Duels, RagoDeux lit « 0 in queue » puis « 1 in queue » (plan 3.8) ;
   RagoDeux tape `/queue sumo` ;
3. le duel démarre, après le décompte 3, 2, 1, « Fight! » affiché en titre (sans fondu), chacun a
   le bâton là où il l'a rangé (RagoUn : quatrième case, RagoDeux : la première, celle du kit), et
   le scoreboard montre l'adversaire et le ping des deux joueurs :
   RagoUn frappe RagoDeux une fois (il doit recevoir du knockback et aucun
   dégât), puis RagoDeux tombe trois fois de la plateforme : RagoUn gagne 3 - 0, et entend le
   ding (`random.orb`) à chaque point, et une fois de plus à la victoire ;
4. les deux reviennent au lobby, au spawn, avec les objets du lobby, et le PNJ Duels montre de
   nouveau « 0 in queue ». Chacun lit son profil
   au-dessus du PNJ de stats (victoires, kills) et les classements (RagoUn premier, 3 kills) ;
   RagoUn ouvre le menu du PNJ de stats : la catégorie Duels a sa victoire et ses 3 kills, un clic
   dessus montre le sumo avec les mêmes stats, « Go back » ramène aux catégories ; la
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
   reviennent au lobby. RagoDeux a quitté une partie classée qu'il jouait encore : les files
   classées lui sont fermées pendant 5 minutes, et il le lit.
8. un First Rush, un vrai 1v1 : RagoUn demande une map que le mode n'a pas (refusée), puis
   `bw-demo`, RagoDeux n'importe quelle map (`random`) ; ils se retrouvent sur `bw-demo`, l'un
   rouge, l'autre bleu, avec les événements d'une vraie partie dans le scoreboard. RagoDeux tape
   `/spawn` : RagoUn gagne et les deux reviennent au lobby.
9. une party : RagoUn invite RagoDeux avec `/p RagoDeux`, RagoDeux accepte, `/party list` et
   `/pc` marchent ; RagoDeux ne peut pas lancer de file (seul le chef le fait). RagoUn lance le
   sumo : partie privée entre eux, sans stats ni XP. RagoUn regarde le replay d'un duel de sumo
   (`/replay`, le menu de ses parties) et y fait venir RagoDeux avec `/p warp` : RagoDeux arrive à
   côté de RagoUn, au même moment du replay, avec seulement les joueurs et le lit dans sa barre
   (le chef mène le replay), et revient au lobby quand RagoUn quitte le replay. RagoUn lance
   Ranked Bedwars 2v2 : refusé,
   RagoDeux a quitté une partie classée il y a un instant. RagoUn lance First Rush duo (2v2, non
   classé) : la party attend dans la même équipe, et quitte la file avec RagoDeux. RagoUn dissout
   la party.
10. RagoDeux quitte le serveur dans une party : RagoUn est prévenu, et RagoDeux, revenu, voit de
    nouveau le pseudo de RagoUn au-dessus de sa tête.
11. un event de `/host` (plan 4.2) : un troisième bot, RagoTrois, opérateur (le script écrit son
    `ops.json`), lance un event Sumo. Tout le serveur lit l'annonce (« SUMO EVENT », « Hosted by
    RagoTrois », « Starts in 25s • 1/40 players »), avec un **[CLICK TO JOIN]** qui lance `/join` ;
    RagoTrois lit « You are in! » et a la poudre rouge « Cancel the event » dans le dernier slot.
    RagoDeux, en plein Block clutch, s'inscrit et reste dans son entraînement ; RagoUn s'inscrit
    depuis le lobby, ne bouge pas et reçoit la poudre rouge « Leave the event » : il la clique, sort
    de l'event (son profil revient dans le slot), et s'inscrit de nouveau. L'annonce revient 20
    secondes après la première (« Starts in 5s • 3/40 players »). À la fin de l'attente
    (25 secondes : le script réduit `attente-secondes`), RagoDeux quitte son Block clutch comme
    avec `/spawn` (il lit « The Sumo event starts: you leave your game for it. ») et les trois
    partent à l'event (« The Sumo event starts with 3 players! »). Au premier combat, RagoTrois
    puis RagoDeux quittent l'event avec `/spawn` : RagoUn gagne, tout le serveur est prévenu, et
    chacun revient au lobby. Enfin RagoTrois lance un autre event et l'annule avec sa poudre rouge :
    tout le serveur lit qu'il est annulé.

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

## Le reset des arènes des modes à blocs, et la visibilité au lobby

`run-arena-test.sh` lance le même serveur, avec Block placement gagné à 10 points (une chute
dans le vide en donne 10) et Block edit à 2. Deux bots (`tools/kb-control/bots/arena-bots.mjs`)
jouent plusieurs parties de suite, et vérifient au début de chacune que sa zone est exactement
comme le mode l'a construite : aucun bloc laissé par la partie d'avant, aucun qui manque. Sans les
maps du propriétaire (le serveur de test n'en a pas), chaque mode construit sa propre arène dans
la première zone libre de sa rangée : une partie après l'autre se joue dans la même zone.

1. Block placement 1v1 : à chaque partie, les deux bots posent de la laine à côté de leur
   plateforme, celui de l'ouest aussi une échelle et de l'end stone, puis casse la laine qui tient
   l'échelle (l'échelle tombe avec, la laine tombe par terre). À la première partie, le bot de
   l'ouest s'enferme sauf au-dessus de sa tête : ce dernier bloc est refusé (l'anti full box). Les parties finissent de toutes les façons : chute dans le
   vide (victoire, écran de fin), `/spawn` (abandon), un bot qui quitte le serveur, `/playagain`
   depuis l'écran de fin (la partie suivante va dans une autre zone pendant que l'écran de fin
   garde la première), et `/l` ;
2. Block edit 1v1 : des trous dans le mur et de la laine remise dedans ; un abandon le mur ouvert,
   un bot qui quitte le serveur, et des edits propres (un trou, un coup à travers, le bloc remis :
   un point, et le mur entier pour la manche suivante) ;
3. l'entraînement Block edit contre le bot : des trous dans le mur et de la laine remise, puis
   `/spawn`, deux fois.

Sur le même serveur, deux autres bots (`tools/kb-control/bots/visibility-bots.mjs`) vérifient
ensuite qu'un joueur qui arrive sur le serveur est vu par celui qui attend au spawn du lobby, et le
voit : chaque bot enregistre tous les paquets reçus sur les joueurs, et le corps de l'autre doit
arriver après son entrée dans la tab list (un client 1.8.9 ne dessine pas le corps d'un joueur
qu'il ne connaît pas encore : il ne reste que la ligne au-dessus du nom), ni en mode spectateur ni
invisible. Les arrivées : la première fois, en revenant du lobby, après avoir quitté le serveur
pendant une training, pendant un duel, et l'arrivée de celui qui attendait.

Enfin, un bot (`tools/kb-control/bots/blockin-bots.mjs`) joue l'entraînement Block in sur l'île
du plugin (le serveur de test n'a pas la map) : depuis son départ sur un coin de l'anneau de verre,
il s'enferme dans la laine (les quatre côtés, aux pieds et à la tête), puis vérifie que l'ennemi
ne le frappe pas tant qu'il est enfermé, casse un bloc d'un côté avec ses fissures (pas d'un
coup), le frappe par ce trou en le repoussant à l'opposé, et casse à nouveau, du même côté et sans
frapper, le bloc que le bot remet à chaque fois ; et que rien d'autre n'est cassé (les autres
côtés, le verre).

Puis un bot joue l'entraînement Block clutch pendant que dix autres le regardent en même temps avec
`/spectate` (`tools/kb-control/bots/spectate-bots.mjs`) : chacun passe en mode spectateur près du
joueur, le joueur est prévenu de chacun (en rouge et en gras, avec un ding) et continue de jouer, les dix sont toujours là quelques
secondes plus tard, et `/spawn` les ramène tous au lobby. Le serveur de test a les 50 places du
`server.properties` du serveur local (il en avait 8, trop peu pour une partie et dix spectateurs).

Pour finir, deux bots jouent les deux modes de FUN PRACTICE (`tools/kb-control/bots/fun-bots.mjs`),
avec les réglages livrés (une manche) : au Snowball Spleef, la pelle casse un bloc de neige et
donne une boule, la boule casse la neige où elle tombe, puis l'un casse la neige sous ses pieds,
tombe et l'autre gagne ; au Fireball Fight, le souffle de vent (la plume, regard en l'air) fait
monter le bot, un deuxième tout de suite doit attendre, il retombe sans dégât, une fireball le
repousse sans dégât (la pile du lanceur reste à 64), puis il tombe de la plateforme et l'autre
gagne. Les deux reviennent au lobby après chaque partie.

Puis trois bots jouent Red Rover Sumo (`tools/kb-control/bots/rover-bots.mjs`), avec les réglages
livrés (aux points, le gagnant reste, premier à 3) : ils entrent seuls dans la file et jouent
ensemble, deux s'affrontent pendant que le troisième regarde en spectateur ; trois 1v1 de suite où
l'autre combattant sort de la plateforme, le gagnant restant contre celui qui attendait et le
perdant passant spectateur ; au troisième 1v1 gagné, victoire du gagnant, défaite des deux autres,
le top 3 lu par les trois (le gagnant premier avec ses 3 1v1 gagnés), et retour des trois au lobby. Ils jouent ensuite Red Rover Block Placement, sur les deux plateformes
du plugin : un combattant sort de la sienne, le vide donne 10 à l'autre qui gagne ainsi le 1v1, et
le 1v1 suivant l'oppose à celui qui attendait ; celui qui attend quitte la partie (`/spawn`), puis
un combattant, et le dernier en jeu gagne. Puis Red Rover Block Edit : un combattant quitte le
1v1, celui qui attendait entre contre l'autre, puis celui-là quitte aussi et le dernier gagne.
Enfin Red Rover Top Bridge (RagoBedwars, sur sa carte de démo `bw-demo`) : un combattant sort du
côté du top bridge, l'autre gagne le round et donc le 1v1 (un round d'une vie), le 1v1 suivant
l'oppose à celui qui attendait ; celui qui attend quitte la partie, puis un combattant, et le
dernier en jeu gagne.

Enfin deux bots jouent CTF The Bridge 1v1 (`tools/kb-control/bots/ctf-bots.mjs`, RagoBedwars, sur
la carte `CtfBridge` que le plugin fabrique), avec les réglages livrés (premier à 3 captures) :
chacun part de sa base tourné vers le pont, avec le kit de The Bridge (épée en fer, arc, pioche en
diamant, deux stacks d'argile de son équipe, 8 pommes d'or, la flèche dans la dernière case) et
l'armure en cuir sans casque ; le pont fait un bloc de large, chaque drapeau est une bannière sur
de l'or, tous deux « Safe » dans le scoreboard. Le défenseur casse le bout du pont de son côté à la
pioche et remet de l'argile, mange une pomme d'or (absorption, pas de régénération), tire sa flèche
qui revient dans la dernière case environ 3,5 s plus tard, puis s'écarte. L'attaquant traverse,
prend le drapeau adverse (la bannière sur sa tête, « Taken » chez le défenseur), saute dans le vide
avec : le drapeau retourne à sa base et l'attaquant revient aussitôt sur la sienne avec son kit.
Puis trois fois il prend le drapeau et le ramène au sien : une capture à chaque fois, la troisième
gagne la partie (le gagnant et les captures dans le résumé de fin), et les deux reviennent au lobby.
Puis quatre bots jouent CTF Castle 2v2 (sur `CtfCastle`, l'autre carte du plugin) : chacun part
dans la cour de son château avec le départ de Bed Wars (épée en bois, armure en cuir), les drapeaux
« Safe » ; la porte du château est ouverte, les murs de chaque côté, le drapeau est une bannière sur
de l'or dans le donjon, la forge sur son fer, l'item shop dans la cour, l'émeraude et les diamants
au mid ; un bot rouge ramasse du fer à sa forge puis entre dans son donjon jusqu'à son drapeau :
rien ne se passe (il est chez lui). Les deux bleus quittent la partie : les rouges gagnent, et les
quatre reviennent au lobby.
Enfin deux bots jouent Fast Bedwars 1v1 (`tools/kb-control/bots/fast-bots.mjs`, RagoBedwars, sur
la carte démo : aucune de ses 20 maps n'est sur le serveur de test). Chacun part sur son île avec le
départ de Bed Wars et lit les deux lignes d'accueil (forges plus rapides, shop plus petit, pas
d'améliorations, les lits à 15:00, la fin à 22:30) ; le scoreboard annonce Emerald II à 6 minutes,
sans Diamond II ; il voit le générateur d'émeraudes, aucun de diamants, l'item shop et pas de PNJ
des améliorations. Le bot rouge ramasse au moins 7 ors en 12 secondes sur son spawn (une forge deux
fois plus rapide ; 4 ou 5 avec celle d'une partie), ouvre le shop : le Quick Buy par défaut, sa 21e
case vide sans panneau, et les Blocks avec seulement laine, verre, end stone et planches ; il achète
de la laine. Le bleu quitte la partie : le rouge gagne, et les deux reviennent au lobby.
Enfin un bot invite RagoBot dans sa party (`tools/kb-control/bots/ragobot-bots.mjs`) : RagoBot
entre tout de suite, à côté de lui ; une deuxième invitation, une file classée, un mode qu'il ne
joue pas et un `/duel RagoBot` d'un autre joueur sont refusés. Le bot joue le Sumo duel contre
RagoBot sans bouger : RagoBot vient à lui, frappe, le pousse et le sort de la plateforme. En Top
Bridge 1v1 (carte démo), RagoBot traverse le pont, le frappe à l'épée et gagne une manche ; le bot
s'en va. En Top Bridge 2v2, la party (le bot et RagoBot) attend dans la file comme une équipe, deux
autres bots seuls la rejoignent : RagoBot frappe l'un d'eux, ils s'en vont. Après `/p kick
RagoBot`, puis après une party dissoute, RagoBot quitte le serveur. Le log du serveur ne doit
compter aucune de ces parties comme partie privée (elles comptent dans les stats). Les bots notent
ce que leur client voit de RagoBot pendant chaque partie (ses apparitions, ses déplacements, ses
coups ; les coups reçus, les knockbacks, les téléportations, le bloc sous les pieds, le chemin) :
le joueur doit voir RagoBot venir et recevoir son knockback, vérifié après toutes les parties.
Pour finir, les outils Owner (`tools/kb-control/bots/owner-bots.mjs`), avec dans `config.yml` l'id
offline de qRago (`outils-owner.uuid`) et qRago et RagoOp opérateurs : `/owner` est une commande
inconnue pour RagoCible et pour RagoOp ; qRago voit ses outils coupés et une portée hors de 3 à 6
est refusée. En Sumo duel, qRago active Reach 6 dans la partie (elle ne compte plus pour personne)
et frappe RagoCible à 6 blocs : le coup porte (knockback, compté dans le récap), sans XP ni points.
En Ranked Sumo, Reach ne fait rien (« Never in a ranked game »), ses coups à 6 blocs ne poussent
personne. En Block placement 1v1, Scaffold actif, qRago recule hors de sa plateforme, laine en
main : la laine se pose sous lui et il ne tombe pas. Le log du serveur doit dire que le Sumo et le
Block placement n'ont pas compté, et rien de tel pour la partie classée.

```bash
./gradlew build
(cd tools/kb-control/bots && npm ci)
KB_ACCEPT_EULA=true tools/lobby-check/run-arena-test.sh
```

Réglages : `KB_SERVER_JAVA`, `KB_PORT` et `ARENA_RUN_DIR` (`arena-run` à la racine du dépôt). La
CI le lance à chaque pull request, sous Java 21.

## Les panneaux publicitaires vus par un vrai client Minecraft 1.8.9

Les bots lisent les paquets des panneaux (`panneaux:` du lobby) mais ne dessinent rien.
`run-billboard-test.sh` lance le vrai client Minecraft 1.8.9 (téléchargé chez Mojang par
`minecraft-client.py`, chaque fichier vérifié avec le SHA-1 que donne Mojang) sur un serveur avec
RagoLobby seul, dans le nouveau monde vide du lobby (sa plateforme de 9 × 9 blocs), avec deux
panneaux tournés vers le spawn : un rouge à 13 blocs devant le spawn, un jaune à 76 blocs, seul
dans un chunk sans aucun bloc. Il prend une capture d'écran au spawn, puis une autre depuis 76
blocs derrière le spawn (la console passe le joueur en spectateur), et `billboard-shot.py` compte
les pixels de chaque couleur. Le test échoue si l'un des trois n'est pas vu : le rouge de près et
de loin, le jaune dans le vide.

```bash
./gradlew :rago-lobby:assemble
KB_ACCEPT_EULA=true xvfb-run -a -s "-screen 0 854x480x24" tools/lobby-check/run-billboard-test.sh
```

Il faut python3, ImageMagick (`import` et `convert`) et Java 8 pour le client
(`BILLBOARD_CLIENT_JAVA`). Les captures, les journaux et les téléchargements restent dans
`billboard-run` à la racine du dépôt. La CI le lance à chaque pull request et garde les captures.
