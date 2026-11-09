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
  kit ; le colorant rouge quitte la file ; l'alambique ouvre le niveau Network, ses récompenses et
  les quêtes ; l'émeraude la boutique, la tête le profil ; le colorant vert ou gris cache ou montre
  les autres joueurs du lobby. La fireball part là où on regarde, comme en Bed Wars, et son souffle pousse
  les joueurs autour comme sur Hypixel : sous ses pieds, c'est un fireball jump (une toutes les
  demi-secondes, elle ne blesse personne et ne casse rien). Le bâton de blaze (teleport stick)
  emmène sur le bloc qu'on regarde, à 20 blocs au plus, jamais à travers un mur : le premier bloc
  sur le chemin est celui où on arrive, sur lui s'il y a la place, sinon juste devant (ou dessous,
  quand on vise un plafond). En visant le vide, il emmène 20 blocs devant, en l'air. Une fois toutes
  les 2 secondes. Le bâton est l'un des gadgets de la 4e case, comme sur Hypixel : un clic gauche
  dessus (ou « Gadgets » dans le profil) ouvre les autres, et celui qu'on prend reste d'une visite
  à l'autre. Le grappin (on vole jusqu'au bloc accroché, à 32 blocs au plus), le pistolet à
  paintball (les blocs touchés se colorent quelques secondes, aux yeux des joueurs seulement), le
  lanceur de feux d'artifice, le party popper (cookies et bonbons que personne ne ramasse, et de la
  musique) et la plume de saut.
- **Les slimes du lobby.** Un bloc de slime est un tremplin, comme les launch pads de Hypixel : il
  lance celui qui marche dessus là où il regarde (`slime: {avant: 2.0, haut: 0.9}` ; 0 et 0 pour
  des slimes ordinaires).
- **La vitesse du lobby (PREMIUM).** Dans les réglages du profil, « Lobby speed » : Speed I, II ou
  III dans le lobby, ou rien (clic gauche plus vite, clic droit moins vite). La permission est
  `rago.perk.speed`.
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
- **Le scoreboard.** Comme le lobby principal d'Hypixel, titre en jaune : la date, le rang, le
  niveau et l'avancée vers le suivant en %, les Rago Points, la meilleure division classée, le
  lobby et les joueurs connectés, la file et le temps d'attente quand on attend, l'adresse en
  jaune. Le Tab aussi (« You are playing on RAGO.GG », les joueurs et le ping, la boutique). En partie : le mode et le ping du joueur, et en 1v1 l'adversaire et son
  ping (vert sous 80 ms, jaune sous 150, rouge au-delà). Une ligne n'est renvoyée que quand elle
  change.
- **Le niveau Network.** Chaque partie donne de l'XP (50 pour une victoire, 15 pour une défaite),
  chaque kill 5 de plus. On commence niveau 1, et chaque niveau demande 50 XP de plus que le
  précédent (100, 150, 200...). Le niveau s'affiche comme sur Hypixel, `[12✫]`, au-dessus de la
  tête, dans le Tab et dans le chat, avec une couleur par palier de 10 niveaux (gris, blanc, or,
  aqua, vert, cyan, rouge, rose, bleu, violet, puis jaune au 100). La barre d'XP du lobby montre le
  niveau et l'avancée vers le suivant. Un message et un son à chaque niveau, un message à chaque
  nouvelle couleur, avec ce que le niveau débloque dans la boutique. Tout se règle dans `niveau:`.
  Le niveau va de 1 à 100 (environ 250 000 XP pour le 100).
- **Les récompenses de niveau (l'alambique du hotbar, `/level`).** Une récompense tous les 5
  niveaux : des Rago Points, des cosmétiques de la boutique (offerts ; déjà achetés, leur prix
  revient en points), les couleurs de pseudo Aqua (25), Gold (50), Legend (100) et le badge
  `✫100`. Un joueur reçoit les récompenses de tous les niveaux atteints, même ceux d'avant leur
  arrivée (à la connexion). Le menu montre le niveau, l'avancée vers le suivant, le palier de
  couleur et le suivant, chaque récompense (reçue, la prochaine, à venir) et les quêtes. Tout se
  règle dans `niveau.recompenses`. Tout est dans le fichier du joueur (`profil.*`), qui passera
  tel quel dans la base de données du réseau.
- **Les quêtes (`/quests`).** Quatre quêtes du jour (jouer 5 parties, en gagner 3, faire 15 kills,
  gagner une classée : 500 à 750 XP) et quatre de la semaine (40 parties, 25 victoires, 100 kills,
  10 classées : 3 000 à 5 000 XP), qui recommencent chaque jour et chaque lundi. Elles avancent
  seules et donnent leur XP une fois (avec le boost de PREMIUM). Tout se règle dans `quetes:`.
- **Les rangs.** Huit rangs dans `rangs:` (OWNER, ADMIN, DEV, MOD, HELPER, BUILDER, MEDIA et
  PREMIUM), chacun avec son préfixe, la couleur du pseudo, la couleur du chat, ses permissions et
  le rang dont il hérite. Un joueur montre le plus haut de ses rangs, dans le chat
  (`[12✫] [PREMIUM] Pseudo ✦: message`), au-dessus de la tête et dans le Tab (les plus hauts rangs
  en premier). Les rangs du staff se donnent avec `/rank set`.
- **Le rang PREMIUM.** Le seul rang à acheter, sur la boutique en ligne (rago-optimizer.com) :
  la boutique du jeu montre ce qu'il donne et le lien, la boutique en ligne le donne depuis la
  console avec `/rank give <joueur> premium`. Il donne un pseudo en dégradé rose et le badge
  `✦`, +50 % d'XP Network (`boost-xp: 1.5`), `/fly` au lobby, `/nick`, un message à soi
  au-dessus de la tête, l'arrivée annoncée avec un feu d'artifice, des messages blancs sans délai.
  Le staff et MEDIA en héritent.
- **La ligne au-dessus du pseudo (`/display`).** Au lobby, chaque joueur a une ligne au-dessus
  de son pseudo, qu'il choisit : son ELO classé (par défaut, « Unranked » sans partie classée),
  son niveau, ses victoires, ses kills, sa meilleure série, un message à lui (PREMIUM :
  `/display message <texte>`, 32 caractères, couleurs avec `&`, sans texte brouillé) ou rien. Le
  menu est dans le profil. C'est un porte-armure invisible, sans hitbox, connu des seuls clients,
  qui suit le joueur (mêmes paquets de mouvement) ; la ligne change au plus une fois par seconde.
- **`/nick` (PREMIUM).** On joue sous un autre pseudo (3 à 16 lettres, chiffres ou `_`, pas celui
  d'un joueur du serveur) : les autres voient ce pseudo au-dessus de la tête, dans le Tab, le chat
  et les messages des parties, avec le skin par défaut, sans rang, sans badge, sans ligne et au
  niveau 1. Les stats restent celles du vrai pseudo. Le nick est gardé d'une connexion à l'autre ;
  `/nick reset` l'enlève, `/realname <nick>` (staff) dit qui c'est.
- **Les couleurs de pseudo et les badges (`/style`).** Un pseudo peut prendre un dégradé (les
  16 couleurs de la 1.8, d'une lettre à l'autre : Pink, Fire, Ocean, Forest, Sunset, Ice,
  Galaxy, Rainbow, Shadow, et Aqua, Gold, Legend pour les niveaux) et un badge après le pseudo
  (`✦`, RAGO, BETTER, PVP, YT, `✫100`). Le dégradé se voit dans le chat, le Tab et les messages ;
  au-dessus de la tête un pseudo n'a qu'une couleur. Les dégradés sont animés : leurs couleurs
  défilent le long du pseudo dans le Tab (deux fois par seconde), au-dessus de la tête le pseudo
  passe d'une couleur du dégradé à l'autre, et chaque message du chat le montre là où il en est.
  Des couleurs fixes s'achètent en Rago Points dans `/style` (Red, Green, Blue, Yellow, Purple,
  White à 3 000, les dégradés fixes Cherry et Mint à 6 000 ; `obtention: points, prix:`, et
  `anime: false` pour un dégradé fixe). Chaque style dit comment l'avoir : avec PREMIUM, sur la
  boutique en ligne (`/style give <joueur> couleur-feu` depuis la console), en Rago Points, ou
  comme récompense de niveau. Le menu (`/style`, le profil, la boutique) montre le rendu, ce
  qu'on a et comment avoir le reste. Tout se règle dans `styles:`.
- **Les Rago Points et la boutique (`/shop`).** Les points se gagnent en partie (20 par victoire,
  5 par défaite, 2 par kill), à chaque niveau (le niveau fois 10) et avec la récompense du jour
  (`/daily` : 100, plus 25 par jour d'affilée, jusqu'à 7 jours). La boutique vend 18 cosmétiques
  (traînées de projectiles, effets de kill, effets de victoire), chacun débloqué à un niveau
  Network, et montre le rang PREMIUM et les styles de pseudo. Un rang peut aussi se vendre en
  points (`prix:` et `niveau:` dans `rangs:`). Les cosmétiques ne jouent qu'en partie.
- **L'ELO classé.** Chaque mode classé a son ELO, qui commence à 0, et onze divisions de Coal
  (0) à Obsidian (1000). Une victoire rapporte plus et une défaite coûte moins dans les basses
  divisions ; le MVP de la partie (le plus de kills) gagne 5 de plus. La file classée apparie
  les joueurs par ELO. Ces valeurs sont les nôtres (les chiffres de RBW ne sont pas publics), et
  se règlent dans `elo:`.
- **L'arrivée au lobby.** Chaque arrivée (connexion ou retour d'une partie) s'annonce au lobby,
  une fois par minute au plus : en gris pour un joueur sans rang, comme sur Hypixel
  (`>>> [VIP] Pseudo joined the lobby! <<<`) avec des particules et un son pour un rang, et un
  feu d'artifice de sa couleur avec `rago.perk.join` (PREMIUM et le staff).
- **Le coffre de la récompense du jour.** `/lobby point daily` pose un coffre à cet endroit (au
  spawn), avec au-dessus « DAILY REWARD », si la récompense attend ou dans combien de temps vient
  la suivante, et la série de jours. Un clic dessus la donne, comme `/daily`.
- **Le clic sur un joueur.** Un clic droit sur un joueur du lobby ouvre son menu : sa tête avec
  son rang, son niveau et ses divisions, un duel (le choix du mode), ses stats mode par mode, une
  invitation dans sa party, une demande d'ami et un rang à lui offrir.
- **Offrir un rang (`/gift <joueur>`).** Les rangs de la boutique, payés avec ses propres Rago
  Points : il faut le niveau et le prix du rang, comme pour l'acheter pour soi. Celui qui le reçoit
  est prévenu, et le lobby aussi.
- **Le profil (`/profile`)** : rang, niveau, points, divisions, stats, amis, boutique et
  réglages (messages privés, invitations de party, demandes d'ami et défis, chacun activable).
- **Les amis et les messages privés.** `/friend` (100 amis au plus, demandes cliquables qui
  expirent après 5 minutes, « Friend > X joined. »), `/msg` et `/r`.
- **Le staff.** Le chat du staff, le vanish, le freeze (pour une vérification), les reports, le
  nettoyage et la coupure du chat, les bans et mutes temporaires ou définitifs, les kicks, les
  avertissements et l'historique de chaque joueur (`sanctions.yml`). Un membre du staff ne peut
  pas sanctionner un autre membre du staff, sauf un admin. Chaque sanction est annoncée au staff.
- **Le chat.** Sans rang, 2 secondes entre deux messages et pas deux fois le même message (le
  staff et les rangs avec `rago.perk.chat` n'ont ni l'un ni l'autre).
- **Le serveur.** La liste des serveurs montre le MOTD de `motd:`, le Tab a un en-tête et un pied
  (`tab:`, avec le nombre de joueurs et le ping), et le lobby entend une astuce toutes les 5
  minutes (`annonces:`). `/discord`, `/rules`, `/site` et `/store` montrent `liens:`.

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
| `/shop` (ou `/boutique`), `/profile` (ou `/me`), `/daily` | la boutique, son profil, la récompense du jour | tout le monde |
| `/gift <joueur>` (ou `/cadeau`) | offrir un rang vendu en Rago Points (PREMIUM s'offre sur la boutique en ligne) | tout le monde |
| `/friend add\|accept\|deny\|remove\|list\|requests\|toggle <joueur>` (ou `/f`) | les amis | tout le monde |
| `/msg <joueur> <message>` (ou `/tell`, `/w`), `/r <message>` | message privé, réponse | tout le monde |
| `/stats [joueur]`, `/top elo\|level\|wins\|kills\|streak\|games\|points [mode]` | les stats, les meilleurs | tout le monde |
| `/report <joueur> <raison>`, `/discord`, `/rules`, `/site`, `/store` | signaler un joueur, les liens | tout le monde |
| `/fly` | voler dans le lobby | `rago.perk.fly` (PREMIUM) |
| `/style` (ou `/couleur`, `/badge`) | choisir sa couleur de pseudo et son badge | tout le monde |
| `/level` (ou `/niveau`), `/quests` (ou `/quetes`) | son niveau et ses récompenses, les quêtes | tout le monde |
| `/display <elo\|level\|wins\|kills\|streak\|none>` | la ligne au-dessus de son pseudo | tout le monde |
| `/display message <texte>` | son propre message au-dessus du pseudo | `rago.perk.message` (PREMIUM) |
| `/nick <pseudo>`, `/nick reset` (ou `/unnick`) | jouer sous un autre pseudo | `rago.perk.nick` (PREMIUM) |
| `/realname <nick>` | qui joue sous ce nick | `rago.staff` |
| `/style give\|take <joueur> <couleur-feu\|badge-rago>`, `/style list` | donner ou retirer un style (la boutique en ligne, depuis la console) | `rago.admin` |
| `/staffchat [message]` (ou `/sc`), `/vanish` (ou `/v`), `/freeze <joueur>`, `/clearchat` | chat du staff, invisible, figer un joueur, vider le chat | `rago.staff` |
| `/staff [joueur]` (ou `/panel`, `/sp`), ou « Staff » dans le menu d'un joueur | le panel du staff : les joueurs en ligne (où ils sont, leur ping, figés ou mute), et pour chacun se téléporter à lui ou l'amener, le figer, le mute ou le ban (une raison et une durée en deux clics), le kick, l'avertir, lire son historique ; chaque bouton passe par la commande qu'il remplace, avec ses vérifications (un helper ne ban pas) | `rago.staff` |
| `/mute <joueur> [durée] <raison>`, `/unmute`, `/kick`, `/warn`, `/history <joueur>` | sanctions (durée : `30m`, `2h`, `7d`, `2w` ou `perm`) | `rago.staff` |
| `/ban <joueur> [durée] <raison>`, `/unban`, `/mutechat` | bans, couper le chat | `rago.mod` |
| `/rank list\|set\|give\|take <joueur> <rang>`, `/points give\|take\|set <joueur> <nombre>` | les rangs et les points (aussi depuis la console) | `rago.admin` |

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

- `categories:` range les modes par zone du lobby : la boussole montre les catégories (Practice à
  gauche, Ranked Bedwars au milieu, Duels à droite), puis les jeux de celle qu'on choisit
  (7 catégories, 28 modes par catégorie au plus). Chaque mode dit sa `categorie:`.
- `groupes:` réunit les tailles d'un même jeu en un seul objet de sa catégorie, comme les jeux de
  Hypixel : Ranked Bedwars ouvre 1v1, 2v2, 3v3 et 4v4 côte à côte, Top Bridge ouvre 1v1, 2v2 et
  FFA, etc. Un mode y entre avec `groupe:` ; l'objet du groupe montre chaque taille avec sa file.
  Un mode `cache: true` ne paraît pas dans la boussole et ne se joue qu'en partie privée (le Top
  fight 1v1, pour la partie privée Top fight d'une party de deux).
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
  classement suivant, un clic gauche change la période ou le mode classé, et un clic sur la ligne
  « Click here: next leaderboard » ou « Click here: change the period » fait ce qu'elle dit. Un
  tableau se clique de 16 blocs, n'importe où sur son texte : le plugin suit le regard du joueur. Le tableau `niveau` est
  classé par XP et montre le niveau. leaderboardplayer montre les kills du jour, de la semaine et
  à vie, entre les meilleures séries de victoires (à gauche) et le niveau Network (à droite).
- `podium:` (dans un mur de `classements:`) : jusqu'à 5 marches, la 1re place d'abord. Sur chacune
  se tient le joueur à cette place du premier tableau du mur, avec son skin, et une ligne au-dessus
  (« #1 Pseudo - Gold 245 »). Chaque joueur voit le top de ce qu'il lit sur le tableau : ELO, kills
  ou niveau, la période, le mode classé. Une marche est un point `/lobby point`, ou `"x y z"` (là où
  vont les pieds). Un clic sur une marche ouvre les stats de ce joueur. Celui de leaderboardprincipal
  est le podium derrière le tableau : émeraude (1er), diamant (2e), bloc d'or (3e). Le skin d'un
  joueur est gardé dans ses stats à chaque connexion ; pour un joueur pas revenu depuis, le lobby
  le demande une fois aux serveurs de Mojang (serveur en online-mode ou derrière BungeeCord).

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
(et l'XP qui va avec). Un entraînement donne de l'XP Network avec
`lobby.giveXp("aim-trainer", joueur, xp, "aim trainer")` (rien dans une partie privée). À la fin, `lobby.recordMatch("sumo", gagnants, perdants)` compte la partie
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
- Des skins choisis pour les PNJ, et des cosmétiques pour le lobby (chapeaux, familiers).

## Construire et tester

```bash
./gradlew :rago-lobby:build
```

Les tests unitaires lisent le `config.yml` fourni et des configurations invalides, `spawn.yml`, la
disposition du sélecteur, l'horloge du scoreboard, les rangs, les divisions ELO, la récompense du
jour, les durées et le fichier des sanctions. Le comportement en jeu est testé par deux
bots sur un vrai serveur : voir [tools/lobby-check](../../tools/lobby-check/README.md).
