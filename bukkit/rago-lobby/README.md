# RagoLobby : le lobby principal

Un plugin Bukkit 1.8.8 : le lobby où arrivent les joueurs de Rago Practice, et d'où ils entrent
dans les files d'attente des modes de jeu.

## Ce qu'il fait

- **Le spawn.** Chaque joueur qui se connecte arrive au spawn du lobby, en aventure, avec sa vie
  et sa faim pleines. Tomber dans le vide (sous `hauteur-vide`) ramène au spawn.
- **La protection.** Dans le monde du lobby : pas de dégâts, pas de faim, rien à poser, casser,
  jeter ou ramasser, pas de coffres ni de portes, pas de pluie, pas de nuit, pas de monstres. Les
  autres mondes (arènes, monde du test de knockback, mondes de build) ne sont pas touchés.
- **La barre d'objets.** La boussole ouvre le sélecteur des modes ; le livre ouvre l'éditeur de
  kit ; le colorant rouge quitte la file ; le colorant vert ou gris cache ou montre les autres
  joueurs du lobby.
- **L'éditeur de kit.** Le livre (deuxième case) liste les modes qui ont un kit (`kit:` sous le
  mode dans config.yml). Un clic sur un mode met son kit dans l'inventaire du joueur, rangé comme
  il l'a laissé la dernière fois, sous un petit coffre : Save, Reset (le rangement du kit) et
  Cancel. Le joueur déplace et échange les objets comme il veut ; il ne peut pas couper une pile,
  en fusionner deux, jeter un objet ni en mettre un dans le coffre. Fermer le coffre enregistre
  aussi. Le rangement est gardé par joueur et par mode dans `plugins/RagoLobby/kits/<uuid>.yml` :
  ses prochaines parties de ce mode lui donnent le kit comme ça (RagoPractice et RagoBedwars le
  lisent : duels, FFA, invis practice...). Un mode sans `kit:` garde le kit de son jeu. Le sumo n'a
  pas de kit par défaut (mains nues) ; donne-lui-en un, par exemple `kit: [STICK]`, pour essayer
  l'éditeur en duel.
- **Les parties, comme sur Hypixel.** `/p <joueur>` (ou `/party invite`) invite ; l'invitation
  dure une minute, avec un « Click here to join! » cliquable, et la party naît avec la première
  invitation (8 joueurs au plus). `/party accept`, `leave`, `list`, `kick`, `transfer`, `warp`
  (les membres du lobby viennent au chef), `disband`, et le chat de party avec `/pc <message>` ou
  `/chat party` (`/chat all` pour revenir). Les messages sont entre deux lignes bleues. Seul le
  chef lance une file, pour toute la party :
  - une party qui remplit toute la partie (deux joueurs dans un mode 1v1, quatre dans un 2v2...)
    joue tout de suite une partie privée entre ses joueurs, qui ne compte dans aucune stat (ni
    ELO, ni XP) ;
  - une party qui tient dans une équipe (deux joueurs en 2v2, 3v3 ou 4v4) attend dans la file et
    joue dans la même équipe, contre d'autres joueurs (en classé, avec la moyenne de ses ELO) ;
  - plus grande qu'une équipe sans remplir la partie, elle est refusée.
  Un joueur qui quitte la file, la party, ou le serveur fait sortir toute la party de la file. Un
  chef qui part laisse la party au joueur suivant.
- **L'import Hypixel.** Pour les modes qui se jouent sur une map de Bed Wars (Ranked Bedwars,
  First Rush, Private Game) et Fast Shop, chaque joueur copie son shop Hypixel : le quick buy
  (les 21 cases, trois lignes de sept) et le hotbar manager (le genre d'objet de chaque case de la
  barre). En un clic avec l'émeraude en bas du livre (son propre compte Hypixel), ou avec
  `/hypixel import <pseudo>` (n'importe quel compte). Le coffre à côté de l'émeraude, ou
  `/hypixel shop`, montre ce qui a été copié. C'est gardé par joueur dans
  `plugins/RagoLobby/hypixel/<uuid>.yml`, et les plugins de jeu le lisent par `LobbyService`
  (voir plus bas). Il faut une clé d'API Hypixel dans `hypixel.cle-api` de config.yml : sans clé,
  l'import dit qu'il n'est pas encore en place. La clé se crée sur
  [developer.hypixel.net](https://developer.hypixel.net) (une application pour le serveur) ; elle
  ne se partage pas et ne se met jamais dans le dépôt. Un joueur peut importer une fois par minute,
  et tout le serveur cent fois en cinq minutes, sous la limite d'une clé (300 requêtes en cinq
  minutes). Hypixel n'envoie le quick buy que si le joueur l'a déjà modifié une fois.
- **Le sélecteur.** Un menu avec un objet par mode, sept par ligne : son format (1v1, 2v2, solo),
  sa description, le nombre de joueurs en file. Un clic entre dans la file, un deuxième en sort.
  Quand le jeu d'un mode propose plusieurs maps (First Rush), le clic ouvre d'abord le menu des
  maps : « Random map » (n'importe laquelle, l'attente la plus courte) puis chaque map, avec le
  nombre de joueurs qui l'attendent. Un joueur qui a choisi une map ne joue qu'avec ceux qui ont
  choisi la même ou n'importe laquelle. `/queue <mode> <map>` fait la même chose.
  Un mode qu'aucun plugin de jeu ne prend en charge affiche « Bientôt disponible ». Survoler un
  mode (boussole ou menu d'un PNJ) montre aussi, comme sur Minemen, « Daily Streak » (les
  victoires d'affilée du joueur dans ce mode aujourd'hui) puis les trois plus longues séries du
  jour, avec les pseudos de toute l'équipe en 2v2, 3v3... (« 1. Alice, Bob - 24 »). Une série
  appartient à une équipe : les mêmes joueurs ensemble. Elles sont gardées dans
  `plugins/RagoLobby/stats/equipes/`, un fichier par mode.
- **Les files d'attente.** Le matchmaking de `rago-game-model`, une fois par seconde. Un joueur
  n'attend que dans une file à la fois.
- **Le scoreboard.** Les joueurs connectés, le niveau Network, le rang et l'XP du joueur, la file
  et le temps d'attente. En partie : le mode et le ping du joueur, et en 1v1 l'adversaire et son
  ping (vert sous 80 ms, jaune sous 150, rouge au-delà). Une ligne n'est renvoyée que quand elle
  change.
- **Le niveau Network.** Chaque partie donne de l'XP (50 pour une victoire, 15 pour une défaite),
  chaque kill 5 de plus. On commence niveau 1, et chaque niveau demande 50 XP de plus que le
  précédent (100, 150, 200...). Certains niveaux donnent un rang (Iron au 5, Gold au 10, Diamond
  au 20, Emerald au 30, Sapphire au 50, Ruby au 75, Crystal au 100), affiché dans le chat
  (`[12] [Gold] Pseudo: message`), au-dessus de la tête, dans le Tab (les plus hauts rangs en
  premier) et dans le scoreboard. La barre d'XP du lobby montre le niveau et l'avancée vers le
  suivant. Un message et un son à chaque niveau, un message à chaque nouveau rang. Tout se règle
  dans `niveau:` de config.yml.

## Commandes

Tout est en anglais : les messages, les menus, les PNJ, les commandes, la console et les
commentaires de `config.yml`. Les mots français de la première version (`quitter`, `accepter`,
`refuser`, `retirer`) marchent toujours, et les clés de `config.yml` restent en français (`monde`,
`modes`, `nom`...) pour que les fichiers déjà écrits marchent sans changement.

| Commande | Effet | Permission |
| --- | --- | --- |
| `/spawn` (ou `/hub`) | retour au spawn ; pendant une partie, c'est un abandon | tout le monde |
| `/queue <mode> [map]`, `/queue leave` (ou `/file`, `/q`) | entrer dans une file (sur une map, ou `random`), en sortir | tout le monde |
| `/duel <joueur> [mode]` | défier un joueur du lobby dans un mode 1v1 (sans mode : un menu des modes 1v1) | tout le monde |
| `/duel accept <joueur>`, `/duel deny <joueur>` | répondre à un défi (valable une minute) | tout le monde |
| `/party invite <joueur>` (ou `/p <joueur>`), `/party accept <joueur>` | inviter dans sa party (valable une minute), rejoindre une party | tout le monde |
| `/party leave`, `list`, `kick <joueur>`, `transfer <joueur>`, `warp`, `disband` | quitter, voir, gérer la party (le chef) | tout le monde |
| `/pc <message>`, `/chat party`, `/chat all` | écrire à sa party, y envoyer tout son chat, revenir au chat de tous | tout le monde |
| `/hypixel import [pseudo]` | copier le shop Bed Wars (quick buy et hotbar manager) de son compte Hypixel, ou de celui de ce pseudo | tout le monde |
| `/hypixel shop` | voir le shop Hypixel copié | tout le monde |
| `/lobby modes` | les modes et leur file (aussi depuis la console) | tout le monde |
| `/lobby setspawn` | place le spawn du lobby là où tu es (écrit dans `spawn.yml`, dans le dossier du monde du lobby) | `rago.lobby.admin` |
| `/lobby point <nom>` | place ou déplace un point nommé là où tu es : où iront les PNJ des modes, les classements, les décorations (écrit dans `points.yml`, dans le dossier du monde) | `rago.lobby.admin` |
| `/lobby point remove <nom>`, `/lobby point tp <nom>`, `/lobby points` | retirer un point, y aller, la liste | `rago.lobby.admin` |
| `/lobby build` | mode construction dans le lobby (créatif, protection levée) | `rago.lobby.admin` |

## Configuration

`plugins/RagoLobby/config.yml` : le monde du lobby, la hauteur du vide, le titre et la dernière
ligne du scoreboard, les réglages de la file classée, et la liste des modes (nom, icône, équipes,
joueurs par équipe, classé, description). Le fichier fourni explique chaque réglage. Un fichier
invalide désactive le plugin avec un message qui dit quoi corriger.

Si le monde du lobby n'existe pas, le plugin le crée : un monde vide avec une plateforme de
quartz. On peut construire le lobby directement dedans (`/lobby build`), ou mettre le nom d'un autre
monde dans `monde`, puis `/lobby setspawn`. Le spawn et les points sont rangés dans le dossier du
monde : ils partent avec la map quand on l'envoie, et survivent à une mise à jour du plugin.
Le plugin se charge après RagoBuildTools (`softdepend`), qui charge les mondes de build avec leur
générateur vide ; un monde de build peut donc servir de lobby.

## Catégories, PNJ et classements

- `categories:` range les modes par zone du lobby : la boussole montre les catégories, puis les
  modes de celle qu'on choisit (7 catégories, 28 modes par catégorie au plus). Chaque mode dit sa
  `categorie:`.
- `pnj:` place un PNJ sur un point de `/lobby point` (la clé) : un joueur au skin de Steve, tourné
  vers le spawn, qui tient l'icône du mode, avec son nom et le nombre de joueurs en file au-dessus
  de la tête. Un clic rejoint la file du mode, ou ouvre un menu quand le PNJ a plusieurs modes. Les
  PNJ réapparaissent quand un point bouge.
- `pnj:` avec `type: stats` place un PNJ de stats : un Steve qui tient un livre, avec au-dessus
  le profil de celui qui le regarde (niveau Network et rang, victoires, défaites, kills, meilleure
  série, victoires du jour, ELO de chaque mode classé). Chaque joueur lit le sien. Un clic ouvre
  « Your stats » : la tête du joueur avec son niveau, son XP, son rang, le prochain rang et ses
  totaux (kills, morts et K/D compris), puis chaque mode avec ses stats (survoler un mode pour les
  lire).
- `classements:` place un mur de classements sur un point : de 1 à 5 tableaux côte à côte, face au
  spawn (`tableaux: [victoires, series, parties, kills, niveau, elo]`). Chaque tableau montre le
  top 10, la place de celui qui regarde et quand il repart de zéro. Un clic sur un tableau change
  sa période pour ce joueur (du jour, de la semaine, à vie) ; sur un tableau `elo`, il change de
  mode classé. Un tableau peut regrouper plusieurs classements entre crochets
  (`tableaux: [[elo, kills, niveau]]`, celui de leaderboardprincipal) : un clic droit passe au
  classement suivant, un clic gauche change la période ou le mode classé. Le tableau `niveau` est
  classé par XP et montre le niveau. leaderboardplayer montre les kills du jour, de la semaine et
  à vie, entre les meilleures séries de victoires (à gauche) et le niveau Network (à droite).

Les PNJ n'existent que dans les paquets envoyés aux joueurs (plugin PacketEvents, installé à côté
de RagoLobby) : le serveur ne les voit pas, ils ne bougent pas et ne prennent pas de coups. Ils
quittent la liste des joueurs (Tab) deux secondes après être apparus, et le scoreboard du lobby
cache leur nom au-dessus de la tête (équipe `pnj`).

## Les stats

Les plugins de jeu donnent le résultat de chaque partie au lobby (`recordMatch`, voir plus bas) :
victoires, défaites, série de victoires en cours et meilleure série, par mode, à vie, du jour et
de la semaine (heure de Paris : le jour repart à minuit, la semaine le lundi à minuit). Un abandon
compte comme une défaite. Les kills et les morts aussi (`recordKill`) : au sumo, chaque point est
un kill pour celui qui le marque et une mort pour celui qui tombe. Chaque joueur garde aussi son
XP Network. Une partie d'un mode classé fait aussi bouger l'ELO des deux camps
(formule classique, 32 points au plus par partie, 1000 au départ). Le duel sumo est le premier
mode qui compte.

Sur le serveur de test, les stats sont gardées dans `plugins/RagoLobby/stats/` (un fichier par
joueur), lues au démarrage et écrites à chaque partie, jamais sur le thread principal. Sur le
réseau, elles passeront dans la base de données.

## Pour les plugins de jeu

Un plugin de jeu dépend de RagoLobby (`depend: [RagoLobby]` dans son `plugin.yml`, et
`compileOnly` de `rago-lobby` et `rago-game-model` dans son build, sans les copier dans son jar)
et récupère le service :

```java
LobbyService lobby = getServer().getServicesManager().load(LobbyService.class);
lobby.registerHost(this, "sumo", host);   // ouvre la file du mode "sumo"
```

`MatchHost.start(match)` reçoit chaque partie trouvée, sur le thread principal, avec les joueurs
connectés et au lobby ; il renvoie `false` s'il ne peut pas la démarrer (aucune arène libre), et
les joueurs gardent leur place dans la file. Au départ, `lobby.kit(player, "sumo")` donne le kit du
mode rangé comme le joueur l'a voulu (36 cases, `null` si le mode n'a pas de kit), à donner avec
`player.getInventory().setContents(kit)`. Pendant la partie, les joueurs appartiennent au jeu,
qui les rend avec `lobby.sendToLobby(player)`. `MatchHost.abandon(player)` est appelé quand un
joueur tape `/spawn` en pleine partie. `lobby.recordKill("sumo", tueur, victime)` compte un kill
(et l'XP qui va avec). À la fin, `lobby.recordMatch("sumo", gagnants, perdants)` compte la partie
dans les stats, les classements et l'XP Network.

Les modes Bed Wars et Fast Shop rangent leur shop avec `lobby.hypixelQuickBuy(player)` : les 21
cases du quick buy copié de Hypixel (les identifiants Hypixel, `wool`, `stone_sword`,
`speed_ii_potion_(45_seconds)`..., `null` pour une case vide), et la barre avec
`lobby.hypixelHotbar(player)` : le genre d'objet de chaque case (`Melee`, `Blocks`, `Tools`,
`Ranged`, `Potions`, `Utility`, `Compass`, `null` pour une case libre). Les deux renvoient `null`
si le joueur n'a rien importé.

Sur le réseau, les files passeront dans le proxy (`rago-proxy`) et les serveurs de jeu recevront
leurs parties de lui ; le côté `MatchHost` d'un plugin de jeu ne changera pas.

## Ce qui n'est pas encore là

- Les stats partagées entre serveurs : elles viendront avec la base de données (`rago-data`).
- Des skins choisis pour les PNJ, les groupes et les cosmétiques.

## Construire et tester

```bash
./gradlew :rago-lobby:build
```

Les tests unitaires lisent le `config.yml` fourni et des configurations invalides, `spawn.yml`, la
disposition du sélecteur et l'horloge du scoreboard. Le comportement en jeu est testé par deux
bots sur un vrai serveur : voir [tools/lobby-check](../../tools/lobby-check/README.md).
