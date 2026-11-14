# RagoLobby : le lobby principal

Un plugin Bukkit 1.8.8 : le lobby où arrivent les joueurs de Rago Practice, et d'où ils entrent
dans les files d'attente des modes de jeu.

## Ce qu'il fait

- **Le spawn.** Chaque joueur qui se connecte arrive au spawn du lobby, en aventure, avec sa vie
  et sa faim pleines. Tomber dans le vide (sous `hauteur-vide`) ramène au spawn.
- **L'accueil : quatre façons de jouer.** À chaque arrivée, le chat montre les catégories de la
  boussole, une ligne chacune avec son nom et son slogan : « Practice : Train alone, at your own
  pace », « RBW Modes : Ranked and RBW situations », « Duels : Duel your friends or anyone »,
  « Arenas : Jump in and fight now ». Un clic sur une ligne ouvre ses modes (`/play <catégorie>`),
  le survol montre ce qu'elle contient. À la toute première venue sur le serveur, un titre aussi
  (le `titre:` et les quatre noms).
  `accueil: false` le coupe.
- **Un titre resté d'ailleurs est effacé.** Le jeu 1.8 garde un titre quand on change de serveur
  ou de compte, aussi longtemps que ce serveur l'a demandé (un écran « You are AFK » peut rester
  des heures). À la connexion, le lobby l'efface et remet les durées normales des titres.
- **La première venue : « What do you want to do? ».** Deux secondes après l'accueil de sa toute
  première venue, un joueur reçoit un menu de quatre choix, chacun à un clic de jouer : « Train a
  skill » (le block clutch, où un nouveau commence), « Play Ranked Bedwars » (la file du 1v1
  classé, en s'entraînant pendant l'attente), « Duel a friend » (les Duels) et « Fight now » (le
  FFA, sans file). Un mode que le serveur ne joue pas encore ouvre sa catégorie. En bas, le livre
  « Getting started » montre les quêtes d'un nouveau joueur et y mène. Le menu ne s'ouvre qu'une
  fois, et pas si le joueur a déjà quitté le lobby ou ouvert un menu ; Échap le ferme. Les choix
  se règlent dans `premiere-venue:` (sept au plus, chacun avec un `nom:`, un `mode:` ou une
  `categorie:` de la boussole, une `icone:` et une `description:`) ; `premiere-venue: {}` le coupe.
- **La protection.** Dans le monde du lobby : pas de dégâts, pas de faim, rien à poser, casser,
  jeter ou ramasser, pas de coffres ni de portes, pas de monstres. Les autres mondes (arènes,
  monde du test de knockback, mondes de build) ne sont pas touchés, sauf le ciel.
- **Le ciel.** Dans tous les mondes du serveur (le lobby, les maps, les arènes, les copies des
  parties) : jamais de pluie ni d'orage, et toujours midi (le cycle du jour est arrêté). Chaque
  joueur garde l'heure du ciel de ses réglages (« Time of day »).
- **La barre d'objets.** La boussole ouvre le sélecteur des modes ; le livre ouvre l'éditeur de
  kit ; le colorant rouge quitte la file ; l'émeraude ouvre la boutique, la tête le profil (où se
  trouvent aussi le niveau Network, ses récompenses et les quêtes). Dans l'ordre : boussole, livre,
  une case libre (3), gadget, fireball, une case libre (6), colorant rouge (ou le papier Play
  again, en 7e case), émeraude et tête. La fireball part là où on regarde, comme en Bed Wars, et son souffle pousse
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
  lance celui qui marche dessus là où il regarde, à environ 30 blocs et 9 de haut
  (`slime: {avant: 3.5, haut: 1.3}` ; 0 et 0 pour des slimes ordinaires).
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
  lisent : duels, FFA, invis practice...). Un mode sans `kit:` garde le kit de son jeu. Plusieurs
  modes peuvent jouer le kit d'un autre (`kit-de:`) : les quatre formats de l'Invis practice
  (`invis-practice-1v1` à `1v4`) jouent celui du mode caché `invis-practice`, ceux du Bed Break
  Simulator celui de `bed-break-simulator` ; l'éditeur le montre une fois, et le rangement vaut
  pour les quatre. Le sumo n'a
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
- **« Popular now ».** La ligne du haut de la boussole et de chaque catégorie montre les modes où
  il y a du monde en ce moment : au moins un joueur en partie ou en file, les plus joués d'abord
  (à égalité, celui où l'on attend : une partie bientôt), trois au plus, ceux de la catégorie dans
  une catégorie. Chacun dit combien jouent et attendent, et un clic entre dans sa file comme
  ailleurs. Au bout de la ligne, le **défi du jour** des entraînements (une montre qui brille, ce
  qu'il demande) : un clic le lance (`/challenge play`), depuis le lobby ou depuis un entraînement.
  Les parties privées ne comptent pas. La ligne se met à jour chaque seconde avec le menu.
- **« Start here ».** Le mode marqué `commencer-ici: true` (le block clutch) brille et dit
  « ✦ Start here » dans sa catégorie, sur l'objet de son groupe, et l'objet de sa catégorie dans la
  boussole dit « ✦ Start here: Block clutch » : le premier entraînement d'un nouveau joueur.
- **Les files d'attente.** Le matchmaking de `rago-game-model`, une fois par seconde. Un joueur
  seul peut attendre dans trois files à la fois : un clic sur un autre mode le met aussi dans sa
  file, et la première partie trouvée l'emporte (il sort des autres). Un clic sur un mode où il
  attend ne sort que de cette file ; l'objet **Leave the queue** du lobby, ou `/queue leave`, les
  quitte toutes. Le scoreboard montre la première file et combien d'autres (« Queue: Sumo +1 »).
  Pendant un entraînement, le joueur garde sa place : il peut lancer un entraînement en attendant,
  ou rejoindre une file depuis l'entraînement (la boussole **Trainings**, puis la catégorie, ou
  `/queue`). Le scoreboard de l'entraînement montre l'attente (« Queue: 0:42 (Sumo) ») et la partie
  trouvée l'en sort directement, sans repasser par le lobby. Une partie qui ne laisse pas partir
  ses joueurs (un FFA, par exemple) fait quitter les files en y entrant.
- **La fin d'une partie gagnée.** Les jeux (Bed Wars, duels) gardent tout le monde
  `fin-de-partie-secondes` secondes dans l'arène (3 ; 0 pour repartir tout de suite), le temps de voir
  les gagnants et leur effet de victoire, avec le papier **Play again** (dans la case que le joueur
  a en main, sans la changer) et le lit **Leave** (dernière case) dans la barre, et **[PLAY AGAIN]** **[LEAVE]** dans le chat. `/playagain` (ou `/again`) quitte la partie
  comme `/spawn` et remet tout de suite dans la file du même mode (en party, seul le chef) ; au
  lobby, il fait comme le papier.
- **Après une partie : rejouer, revanche ou replay.** De retour au lobby, une ligne cliquable :
  **[PLAY AGAIN]** remet dans la file du même mode, après un 1v1 qui n'est pas classé,
  **[REMATCH]** défie le même adversaire dans le même mode (s'il est encore là), et après une partie
  enregistrée, **[REPLAY]** la montre à nouveau (voir « Les replays »). Le papier
  **Play again** de la barre (7e case, celle de « Leave the queue », juste avant l'émeraude) fait comme [PLAY AGAIN] d'un
  clic droit ; il reste 10 secondes, et la case en main ne change jamais (un clic rapide sur la
  boussole ne relance pas le dernier mode par erreur) ; ensuite, [PLAY AGAIN] et `/playagain`. Avec « Play again »
  sur « Automatic » dans `/settings`, le joueur est remis dans la file tout seul, sans clic (pas
  après un `/spawn` : il est parti de lui-même). En party, seul le chef relance la file. Rien pour
  un mode fermé ou caché (les events).
- **Les replays.** Les duels de RagoPractice (le sumo compris ; pas les arènes ouvertes ni les
  entraînements) et les parties de Bed Wars en équipes (pas les FFA, Fast Shop, Invis, le Bed Break
  Simulator ni le Top Bridge d'entraînement d'une party) sont enregistrés : les joueurs (leurs mouvements, leurs coups,
  leur vie, ce qu'ils tiennent et portent), les blocs posés et cassés, les flèches, boules de feu,
  perles et TNT, et les messages de la partie. `/replay` ouvre le menu de ses parties des derniers
  jours, `/replay CODE` montre n'importe quelle partie par son code (à partager), et **[REPLAY]**
  celle qu'on vient de jouer. On regarde depuis le lobby (demandé pendant une partie, le replay
  attend le retour au lobby), en volant autour des joueurs. La barre met en pause, recule ou avance
  de 10 secondes, change la vitesse (x0.25 à x4 : clic droit plus vite, clic gauche moins vite sur
  l'horloge), va voir un joueur (la boussole), et le lit (ou `/replay leave`, ou `/spawn`) ramène
  au lobby. Un duel est rejoué dans un monde à part (`rago_replays`, effacé à chaque démarrage),
  une partie de Bed Wars dans une copie libre de sa map. Les fichiers sont dans
  `plugins/RagoLobby/replays` : quelques dizaines de Ko pour un duel, quelques centaines pour une
  partie de Bed Wars. Réglages dans `replays:` : `jours` (3), `taille-max-mo` (2000 ; au-delà, les
  plus vieux partent d'abord), `spectateurs-max` (6 replays regardés en même temps), et
  `actif: false` pour ne rien enregistrer.
- **Regarder une partie en direct.** `/spectate <joueur>` (ou `/spec`) depuis le lobby : la partie
  d'un ami, et n'importe quelle partie pour le staff (un soupçon de triche) et pour un rang qui a
  `rago.perk.spectate` (MEDIA, mais pas la partie d'un joueur sous un nick, qui reste caché sauf
  pour le staff et ses amis). Le spectateur arrive là où est le joueur, en mode spectateur : les
  joueurs ne le voient pas (même pas son corps, seulement son nom grisé dans le Tab), il vole à
  travers tout et ne touche à rien. Un clic gauche sur un joueur montre la partie par ses yeux, et
  les touches de la barre ne l'emmènent que vers les joueurs de la partie. Il lit les messages de
  la partie et voit le sidebar du joueur qu'il est venu voir. `/spawn` le ramène au lobby, comme la
  fin de la partie, sans papier Play again. Il garde sa place dans les files (et peut en rejoindre
  depuis la partie) : la partie trouvée l'en sort. Tout ce que jouent RagoPractice et RagoBedwars
  se regarde : les duels, le sumo, les entraînements, les events et les parties de Bed Wars.
- **Les duels ne sont pas classés.** Une partie lancée sans la file (un `/duel`, une partie privée)
  ne fait bouger ni l'ELO ni les séries de victoires (du joueur et de l'équipe), même dans un mode
  classé : deux amis ne peuvent pas se donner des victoires pour monter. Les victoires, l'XP et les
  Rago Points comptent quand même. Le défi en mode classé le dit (« A duel never changes the
  ranked ELO. »).
- **Le scoreboard.** Peu de mots et de l'espace, titre en rouge : le rang, le niveau et l'avancée
  vers le suivant en % ; les Rago Points (« Points ») et la meilleure division classée ; le ping et
  les joueurs connectés ; la file et le temps d'attente quand on attend ; l'adresse en rouge tout en
  bas. Le Tab aussi (RAGO PRACTICE en rouge et le slogan en haut ; les joueurs, le ping, la boutique
  et rago.gg en bas). En partie : le mode en tête, à la couleur de sa catégorie (Bed Wars compris),
  le ping du joueur, et en 1v1 l'adversaire et son
  ping (vert sous 80 ms, jaune sous 150, rouge au-delà). Un jeu qui donne ses propres lignes sans
  ping (Bed Wars, entraînements) reçoit en dessous « Your ping » et « Their ping » (celui de
  l'adversaire en 1v1, de chacun jusqu'à trois, la moyenne au-delà). Une ligne n'est renvoyée que
  quand elle change. En partie, la vie des joueurs autour s'affiche sous leur nom (« 20 ❤ », l'absorption
  comprise), mise à jour à chaque tick, et leur niveau Network reste après leur nom au-dessus de
  leur tête.
- **Le niveau Network.** Chaque partie donne de l'XP (50 pour une victoire, 15 pour une défaite),
  chaque kill 5 de plus. On commence niveau 1, et chaque niveau demande 50 XP de plus que le
  précédent (100, 150, 200...). Le niveau s'affiche comme sur Hypixel, `[12✫]`, au-dessus de la
  tête, dans le Tab et dans le chat, avec une couleur par palier de 10 niveaux (gris, blanc, or,
  aqua, vert, cyan, rouge, rose, bleu, violet, puis arc-en-ciel au 100). La barre d'XP du lobby montre le
  niveau et l'avancée vers le suivant. Un message et un son à chaque niveau, un message à chaque
  nouvelle couleur, avec ce que le niveau débloque dans la boutique. Tout se règle dans `niveau:`.
  Le niveau va de 1 à 1000 : environ 250 000 XP pour le 100, puis 5 050 XP par niveau.
- **Les prestiges (après le niveau 100).** Tous les 100 niveaux (200, 300... 1000), le `[200✫]`
  prend un nouveau look : les crochets et l'étoile d'une couleur, le nombre d'une autre (jaune et
  blanc au 200, rouge et jaune au 300, aqua et vert au 400, rose et aqua au 500, rouge foncé et
  or au 600, bleu et rose au 700, vert et blanc au 800, violet et jaune au 900, or, jaune et blanc
  qui brillent au 1000). Chaque prestige donne des Rago Points (20 000 au 200, 5 000 de plus à
  chacun, 100 000 au 1000) et tout le serveur l'entend (« PRESTIGE! Pseudo reached the level
  [200✫]! », sauf sous un `/nick`). Après le 100, chaque niveau rapporte autant de points que le
  100. Un prestige demande environ 3 mois à un joueur actif. Tout se règle dans
  `niveau.prestiges` (`couleur`, `points`, `objets`) ; une config.yml sans cette section prend
  ceux du plugin.
- **Les récompenses de niveau (le profil, `/level`).** Une récompense tous les 5
  niveaux jusqu'au 100, puis à chaque prestige : des Rago Points, des cosmétiques de la boutique (offerts ; déjà achetés, leur prix
  revient en points), les couleurs de pseudo Aqua (25), Gold (50), Legend (100) et le badge
  `✫100`. Un joueur reçoit les récompenses de tous les niveaux atteints, même ceux d'avant leur
  arrivée (à la connexion). Le menu montre le niveau, l'avancée vers le suivant, le palier de
  couleur et le suivant, 21 récompenses autour de la prochaine (un bloc d'or quand elle est débloquée, un bloc de
  charbon sinon, la prochaine qui brille), les paliers et les prestiges, et les quêtes. Tout se
  règle dans `niveau.recompenses`. Tout est dans le fichier du joueur (`profil.*`), qui passera
  tel quel dans la base de données du réseau.
- **Les quêtes (`/quests`).** Chaque jour, trois quêtes du jour tirées dans une liste de huit, les
  mêmes pour tout le monde, dont au moins une des parties et une des entraînements : jouer 5
  parties, en gagner 3, faire 15 kills, gagner une classée, réussir 25 clutchs (le block clutch,
  île ou top fight), battre un record personnel, jouer le défi du jour, s'entraîner 15 minutes
  (500 à 750 XP). Et cinq quêtes de la semaine (40 parties, 25 victoires, 100 kills, 10 classées,
  2 heures d'entraînement : 3 000 à 5 000 XP). Elles recommencent chaque jour et chaque lundi,
  avancent seules et donnent leur XP une fois (avec le boost de PREMIUM). Une minute
  d'entraînement compte quand le joueur y a fait quelque chose (un clic, un bloc), pas quand il
  attend sans rien faire. Tout se règle dans `quetes:` (`par-jour` : combien en tirer chaque jour,
  0 pour toutes ; cinq au plus en même temps dans une période, la rangée du menu).
- **Getting started : les quêtes d'un nouveau joueur.** Cinq étapes, une fois chacune et dans
  n'importe quel ordre : jouer un entraînement (un premier score), battre un de ses records, jouer
  un duel (un 1v1 jusqu'au bout, hors partie privée), importer son quick buy de Hypixel
  (`/hypixel import`) et trouver le Discord (`/discord`). Chacune donne des Rago Points (100 à 150,
  600 en tout), et toutes ensemble la traînée Emerald sparks (un joueur qui l'a déjà reçoit son
  prix en points). Une étape que le serveur ne peut pas offrir (sans clé Hypixel, sans lien
  Discord) n'apparaît pas. À chaque étape, « QUEST COMPLETE! » et la suivante. Tant qu'il en
  reste, elles sont en tête de `/quests` (une rangée de plus), devant les lignes du PNJ des quêtes
  (« Start 1/5 ») et dans le message d'arrivée. Elles sont dans le fichier du joueur et ne
  recommencent jamais. Tout se règle dans `quetes.debut` (`actif`, `points` de chaque étape,
  `recompense` : un cosmétique de la boutique ou un style, `''` pour rien).
- **Les rangs.** Neuf rangs dans `rangs:` (OWNER, ADMIN, DEV, MOD, HELPER, BUILDER, MEDIA,
  PREMIUM et VIP, du plus haut au plus bas), chacun avec son préfixe, la couleur du pseudo, la couleur du chat, ses permissions et
  le rang dont il hérite. Un joueur montre le plus haut de ses rangs, dans le chat
  (`[12✫] [PREMIUM+] Pseudo ✦: message`), au-dessus de la tête et dans le Tab (les plus hauts rangs
  en premier). Les rangs du staff se donnent avec `/rank set`.
- **Les rangs PREMIUM et VIP.** Les deux rangs à acheter, sur la boutique en ligne
  (rago-optimizer.com) : la boutique du jeu montre ce qu'ils donnent, leur prix (`prix-en-ligne`)
  et le lien, VIP à gauche et PREMIUM+ à sa droite (le plus bas d'abord), comme partout ; la
  boutique en ligne les donne depuis la console avec `/rank give <joueur> vip` ou `premium`.
  **VIP** (4,99 €), le petit rang :
  un pseudo bleu clair (le style `aqua`) et le préfixe `[VIP]`, +25 % d'XP Network
  (`boost-xp: 1.25`), `/fly` et la vitesse au lobby, l'arrivée annoncée avec un feu d'artifice, des
  messages blancs sans délai, la priorité dans les files (hors classé) après PREMIUM+, et trois cosmétiques à lui,
  parmi les moins chers (la traînée Clouds, le kill Heart burst, la victoire Love : les permissions
  `rago.cosmetic.<id>`). **PREMIUM** (9,99 €) hérite de VIP et garde tout le reste.
- **La priorité dans les files (`priorite-file`).** PREMIUM+ (2) passe avant VIP (1), qui passe
  avant les autres (0) : quand une file a plus de joueurs qu'une partie n'en prend, les plus hauts
  jouent d'abord, puis le temps d'attente décide. Après une minute d'attente, tout le monde passe en
  premier, pour que personne n'attende longtemps derrière eux. **Jamais dans une file classée** : là,
  le premier arrivé joue le premier, quel que soit son rang, pour qu'un rang acheté ne choisisse
  jamais un adversaire ni ne fasse attendre un autre joueur. Le message des autres files dit
  « Priority queue ». **PREMIUM+** donne un pseudo en dégradé rose et le badge
  `✦`, +50 % d'XP Network (`boost-xp: 1.5`), `/fly` au lobby, `/nick`, un message à soi
  au-dessus de la tête, l'arrivée annoncée avec un feu d'artifice, des messages blancs sans délai,
  trois gadgets à lui (bat blaster, mouton explosif, boule disco : `rago.perk.gadgets`), les effets
  de kill et de victoire les plus rares (squid rocket, météore, dieu du tonnerre, pluie de météores)
  un booster d'XP gratuit par jour (`rago.perk.booster`) et les events pour tout le serveur avec
  `/host` (`rago.perk.host`, voir RagoPractice). Le staff et MEDIA en héritent. MEDIA a aussi
  `/spectate` pour toutes les parties (`rago.perk.spectate`).
- **Le + de [PREMIUM+].** Le `{+}` d'un préfixe (`prefixe: "&d[PREMIUM{+}] "`) est un + que chaque
  joueur colore dans `/style` (« Colour of your + ») : blanc par défaut, six couleurs vives en
  récompense de niveau (`plus-jaune` au niveau 5... `plus-bleu` au 85), et le noir, le plus rare, à
  50 000 Rago Points (`styles.plus`). Au-dessus de la tête, où une équipe 1.8 n'a que 16
  caractères, le préfixe perd ses crochets : `PREMIUM+ Pseudo`.
- **Les boosters (`/booster`).** Chacun dure 30 minutes de jeu (le temps ne passe qu'en partie),
  en plus du boost du rang (+50 % pour PREMIUM). Ceux qu'on achète sont plus forts que celui
  offert aux [PREMIUM+] : le **booster d'XP** (x3 XP Network) s'achète 2 500 Rago Points dans la
  boutique ou sur la boutique en ligne ; le **booster d'XP du jour** des [PREMIUM+] (gratuit, un
  par jour) fait x2 ; le **booster de points** (« Points booster », x3 Rago Points des parties, kills et
  entraînements) vient de la boutique en ligne et du pack de départ. Un booster d'XP et un booster
  de pièces peuvent tourner en même temps. La boutique en ligne en donne avec
  `/booster give <joueur> [nombre] [xp|coins|daily]` (xp par défaut).
- **La ligne au-dessus du pseudo (`/display`).** Au lobby, chaque joueur a une ligne au-dessus
  de son pseudo, qu'il choisit : son ELO classé (par défaut, « Unranked » sans partie classée),
  son niveau, ses victoires, ses kills, sa meilleure série, le titre qu'il porte, un message à lui (PREMIUM :
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
  (`✦`, RAGO, BETTER, PVP, YT, `✫100`, la couronne ♛ du top de la semaine, et le badge de chaque
  saison classée finie, voir plus bas). Les badges ont leur page (`/style badges` ou `/badge`). Le dégradé se voit dans le Tab et les menus, où il est
  animé : ses couleurs défilent le long du pseudo (environ trois fois par seconde). Dans le chat,
  les messages et au-dessus de la tête, le pseudo n'a qu'une couleur fixe, sa couleur principale
  (celle du milieu du dégradé) : bleu clair pour VIP, rose pour PREMIUM.
  Des couleurs fixes s'achètent en Rago Points dans `/style` (Red, Green, Blue, Yellow, Purple,
  White à 3 000, les dégradés fixes Cherry et Mint à 6 000 ; `obtention: points, prix:`, et
  `anime: false` pour un dégradé fixe). Chaque style dit comment l'avoir : avec PREMIUM, sur la
  boutique en ligne (`/style give <joueur> couleur-feu` depuis la console), en Rago Points, ou
  comme récompense de niveau. Le menu (`/style`, le profil, la boutique) montre le rendu, ce
  qu'on a et comment avoir le reste. Tout se règle dans `styles:`.
- **Les titres des modes (`/titles`).** Chaque mode a son titre, gagné avec les victoires
  (« Sumo Gold III ») : rien à payer. Un titre compte les victoires de ses modes ensemble (Bed
  Fight : le 1v1, le 2v2 et le Top BedFight) et monte les paliers Bronze, Silver, Gold,
  Platinum, Diamond, Master, Legend et Mythic, chacun en cinq niveaux (I à V) : Bronze I à 10
  victoires, Silver I à 75, Gold I à 200, Mythic I à 10 000. Le joueur porte son meilleur titre
  dans le chat (`[12✫] [Sumo Gold III] [PREMIUM+] Pseudo`) et dans son profil, et au-dessus de
  sa tête s'il le choisit (`/display title`). `/titles` (ou le lingot d'or du profil) montre
  tous les titres, ses victoires dans chacun et le niveau suivant ; un clic porte un autre de
  ses titres, son meilleur (par défaut) ou aucun. Un nouveau titre ou niveau est annoncé à la
  victoire. Les stats de chaque mode disent son titre. Les modes classés n'en ont pas : leurs
  divisions d'ELO jouent ce rôle. Sous un `/nick`, le titre ne se voit pas. `titres:` dans
  config.yml règle les paliers, les titres et leurs modes.
- **Les classements de la semaine et leurs récompenses.** Le lundi à minuit (heure de Paris), le
  top 3 de chaque classement de la semaine (victoires, meilleure série, kills et parties jouées, tous
  modes ensemble, et les plus gros clutchs de la semaine du practice) gagne des Rago Points (2 000,
  1 000 et 500) et la couronne ♛, un badge porté jusqu'aux résultats suivants (sauf pour qui a
  choisi un autre badge dans `/style`). Tout le serveur l'entend ; un gagnant absent l'apprend à son
  retour, et chaque joueur qui arrive dans les 24 heures voit les résultats une fois. Le mur
  `semaine` (`/lobby point semaine`) montre ces classements, toujours sur la semaine en cours, avec
  ce que leur top gagne, et son podium le top 3 des victoires de la semaine dans son skin (les
  marches `/lobby point semaine1`, `semaine2` et `semaine3`). `semaine:` dans config.yml règle les classements récompensés, les points
  (10 places au plus) et le badge ; `plugins/RagoLobby/semaine.yml` garde la semaine comptée et les
  derniers résultats. La première fois, le lobby note seulement la semaine en cours.
- **Les Rago Points et la boutique (`/shop`).** Les points se gagnent en partie (20 par victoire,
  5 par défaite, 2 par kill), dans le practice, les entraînements, l'aim trainer et les events
  (un point pour 3 XP Network qu'ils donnent, au moins un : `rago-points.xp-par-point`), à chaque
  niveau (le niveau fois 10) et avec la récompense du jour
  (`/daily` : 100, plus 25 par jour d'affilée, jusqu'à 7 jours). La boutique vend 18 cosmétiques
  (traînées de projectiles, effets de kill, effets de victoire), chacun débloqué à un niveau
  Network, et montre le rang PREMIUM et les styles de pseudo. Un rang peut aussi se vendre en
  points (`prix:` et `niveau:` dans `rangs:`). Les cosmétiques ne jouent qu'en partie.
- **Le PNJ Shop et le pack de départ (`/pack`).** Au centre du lobby (le point `shop`,
  `type: shop`), un PNJ Shop ouvre la vitrine de celui qui débute : d'abord le **pack de départ**,
  ce qu'il y a de moins cher (3,99 €, une fois par joueur : 20 000 Rago Points, 3 boosters d'XP,
  2 boosters de points et 3 cosmétiques sans leur niveau, sans rang), puis VIP (4,99 €) et ses
  avantages, puis ce que les Rago Points achètent : les couleurs de pseudo, les boosters et les
  cosmétiques ; enfin comment gagner des Rago Points et Rago Optimizer. PREMIUM+ est dans `/shop`,
  sous l'icône du lingot d'or des rangs. Ce qui se vend sur la boutique en ligne donne son lien
  cliquable ; le reste ouvre sa page de `/shop`. La boutique en ligne donne le pack depuis la
  console avec `/pack give <joueur>` (une seule fois : un deuxième appel ne donne rien), et tout le
  serveur le voit. Prix, contenu et lien dans `pack-depart:` ; `prix-normal:` montre un prix barré
  et la remise, seulement si le pack s'est vraiment vendu à ce prix avant (le même que `prix:` pour
  aucun) ; `rang: vip` ajouterait un rang au pack.
- **L'ELO classé.** Chaque mode classé a son ELO, qui commence à 0, et onze divisions de Coal
  (0) à Obsidian (1000). Une victoire rapporte plus et une défaite coûte moins dans les basses
  divisions ; le MVP de la partie (le plus de kills) gagne 5 de plus. La file classée apparie
  les joueurs par ELO. Ces valeurs sont les nôtres (les chiffres de RBW ne sont pas publics), et
  se règlent dans `elo:`.
- **Pas de file classée après un abandon.** Quitter une partie classée qu'on joue encore (`/spawn`,
  Play again, quitter le serveur, pas après avoir été éliminé) ferme les files classées au joueur
  pendant 5 minutes, comme sur les serveurs ranked : il le lit tout de suite, et la file lui dit le
  temps qui reste (« you can join a ranked queue again in 4:32 »). Une party où il est ne peut pas
  non plus entrer dans une file classée. Rien après un `/duel` ni une partie privée, qui ne bougent
  pas l'ELO, et les autres files restent ouvertes. `file.abandon-classe-minutes` (0 à 60, 0 pour
  jamais) ; un redémarrage les rouvre.
- **Les saisons classées (`/season`).** Comme sur les serveurs Ranked Bedwars, l'ELO repart de 0
  à chaque saison. La saison en cours est `saison.numero` (2 : celle des divisions) ; pour en
  commencer une nouvelle, on passe au numéro suivant et on redémarre le serveur. Le meilleur ELO
  que chaque joueur a atteint dans la saison finie (tous modes classés confondus) reste dans son
  profil (« Season 2: Diamond 310 »), et sa division lui rapporte des Rago Points à sa prochaine
  arrivée, une seule fois (`saison.recompenses` : de 250 pour Iron à 10 000 pour Obsidian), avec
  un message. `saison.nom` est le nom montré, `saison.fin` le dernier jour, affiché avec les jours
  qui restent (il ne change rien tout seul). `/season` montre la saison, sa fin, les récompenses,
  ses divisions (et son meilleur ELO de la saison) et ses saisons passées ; le profil aussi.
  Chaque saison finie donne aussi son badge, dans la couleur de la division atteinte
  (`S2 Diamond`) : il se porte après le pseudo, dans le chat, le Tab et au-dessus de la tête.
  Sans autre badge (choisi, couronne de la semaine ou badge du rang), un joueur porte celui de sa
  dernière saison, sauf dans la première division (Coal). La page des badges de `/style` les
  montre tous ; un badge de config.yml ne peut pas s'appeler `saison-...`.
- **L'arrivée au lobby.** Chaque arrivée (connexion ou retour d'une partie) s'annonce au lobby,
  une fois par minute au plus : en gris pour un joueur sans rang, comme sur Hypixel
  (`>>> [VIP] Pseudo joined the lobby! <<<`) avec des particules et un son pour un rang, et un
  feu d'artifice de sa couleur avec `rago.perk.join` (PREMIUM et le staff).
- **Le coffre de la récompense du jour.** `/lobby point daily` pose un coffre à cet endroit (au
  spawn), avec au-dessus « DAILY REWARD », si la récompense attend ou dans combien de temps vient
  la suivante, et la série de jours. Un clic dessus la donne, comme `/daily`.
- **Le PNJ des quêtes.** À deux blocs du coffre de la récompense du jour (trois si la place est
  prise), du côté le plus loin des autres PNJ, face au spawn : un PNJ dans son skin (`quetes`),
  un livre à la main, avec au-dessus « QUESTS », où en est chacun (« Daily 2/4 · Weekly 0/4 »,
  avec « Start 1/5 » devant tant que Getting started n'est pas fini) et « Click to see them ». Un clic dessus (ou sur ses lignes) ouvre les quêtes du jour et de la
  semaine, comme `/quests`. `/lobby point quests` le place ailleurs ; sans coffre ni ce point, il
  n'y en a pas.
- **Le clic sur un joueur.** Un clic droit sur un joueur du lobby ouvre son menu : sa tête avec
  son rang, son niveau et ses divisions, un duel (le choix du mode), ses stats mode par mode, une
  invitation dans sa party, une demande d'ami et un rang à lui offrir.
- **Offrir un rang (`/gift <joueur>`, le bouton « Gift a rank » du shop, ou le clic sur un
  joueur).** Payé avec ses propres Rago Points, même un rang de la boutique en ligne : VIP pour
  25 000, PREMIUM+ pour 50 000 (`prix-cadeau` de chaque rang dans `rangs:` ; sans lui, son `prix`
  du shop). Réservé à PREMIUM et au-dessus (`rago.perk.gift`). Le bouton du shop montre les têtes
  des joueurs en ligne, puis les rangs. Comme sur Hypixel, un livre s'ouvre chez l'autre joueur
  (« [PREMIUM] Sofiene wants to give you [VIP]! », YES ou NO, les mêmes boutons dans son chat) : il
  a une minute pour répondre, il doit être en ligne, et le donneur ne paie qu'au YES. Celui qui le
  reçoit a un titre à l'écran ; tout le lobby voit un grand message au milieu du chat, et des feux
  d'artifice de toutes les couleurs éclatent autour de lui (ou du donneur s'il n'est pas au lobby)
  pendant trois secondes. Les joueurs en partie n'ont qu'une ligne, pour ne pas gêner un combat.
- **Le profil (`/profile`)** : rang, niveau, points, divisions, stats, amis, boutique et
  réglages (`/settings` les ouvre aussi) : messages privés, invitations de party, demandes d'ami
  et défis (chacun activable), l'heure du ciel (« Time of day » : jour, coucher de soleil ou nuit,
  pour soi seul, au lobby et en partie) et « Play again » (remis dans la file tout seul après une
  partie, ou avec le bouton).
- **Les amis et les messages privés.** `/friend` (100 amis au plus, demandes cliquables qui
  expirent après 5 minutes, « Friend > X joined. »), `/msg` et `/r`.
- **Le staff.** Le chat du staff, le vanish, le freeze (pour une vérification), les reports, le
  nettoyage et la coupure du chat, les bans et mutes temporaires ou définitifs, les kicks, les
  avertissements et l'historique de chaque joueur (`sanctions.yml`). Un membre du staff ne peut
  pas sanctionner un autre membre du staff, sauf un admin. Chaque sanction est annoncée au staff.
- **Le chat.** Sans rang, 2 secondes entre deux messages et pas deux fois le même message (le
  staff et les rangs avec `rago.perk.chat` n'ont ni l'un ni l'autre).
- **Le serveur.** La liste des serveurs montre le MOTD de `motd:` (ce qu'est le serveur, puis ce qui
  est nouveau), le Tab a un en-tête et un pied (`tab:`, avec le nombre de joueurs et le ping), et le
  lobby entend une astuce toutes les 5 minutes (`annonces:`), avec le préfixe du serveur
  (« RAGO » en rouge). `/discord`, `/rules`, `/site` et `/store` montrent `liens:`. Tout suit les
  couleurs du serveur (plus bas).

## Commandes

Tout est en anglais : les messages, les menus, les PNJ, les commandes, la console et les
commentaires de `config.yml`. Les mots français de la première version (`quitter`, `accepter`,
`refuser`, `retirer`) marchent toujours, et les clés de `config.yml` restent en français (`monde`,
`modes`, `nom`...) pour que les fichiers déjà écrits marchent sans changement.

| Commande | Effet | Permission |
| --- | --- | --- |
| `/spawn` (ou `/hub`) | retour au spawn ; pendant une partie, c'est un abandon | tout le monde |
| `/playagain` (ou `/again`) | la partie suivante du même mode : quitte la partie (comme `/spawn`) et remet dans sa file tout de suite ; au lobby, comme le papier Play again | tout le monde |
| `/replay`, `/replay <code>`, `/replay leave` (ou `/replays`) | le menu de ses dernières parties, une partie par son code, quitter le replay | tout le monde |
| `/spectate <joueur>` (ou `/spec`) | regarder la partie d'un ami, invisible (`/spawn` pour revenir) | tout le monde ; toutes les parties : `rago.perk.spectate` (MEDIA, le staff) |
| `/queue <mode> [map]`, `/queue leave [mode]` (ou `/file`, `/q`) | entrer dans une file (sur une map, ou `random`), aussi depuis un entraînement ; sortir de toutes, ou d'une | tout le monde |
| `/duel <joueur> [mode]` | défier un joueur du lobby dans un mode 1v1 (sans mode : un menu des modes 1v1) | tout le monde |
| `/duel accept <joueur>`, `/duel deny <joueur>` | répondre à un défi (valable une minute) | tout le monde |
| `/party invite <joueur>` (ou `/p <joueur>`), `/party accept <joueur>` | inviter dans sa party (valable une minute), rejoindre une party | tout le monde |
| `/party leave`, `list`, `kick <joueur>`, `transfer <joueur>`, `warp`, `disband` | quitter, voir, gérer la party (le chef) | tout le monde |
| `/pc <message>`, `/chat party`, `/chat all` | écrire à sa party, y envoyer tout son chat, revenir au chat de tous | tout le monde |
| `/hypixel import [pseudo]` | copier le shop Bed Wars (quick buy et hotbar manager) de son compte Hypixel, ou de celui de ce pseudo | tout le monde |
| `/hypixel shop` | voir le shop Hypixel copié | tout le monde |
| `/play [catégorie]` (ou `/modes`, `/games`, `/jouer`) | la boussole, ou les modes d'une catégorie (son id ou son nom : `/play duels`, `/play rbw modes`) | tout le monde |
| `/lobby modes` | les modes et leur file (aussi depuis la console) | tout le monde |
| `/lobby setspawn` | place le spawn du lobby là où tu es (écrit dans `spawn.yml`, dans le dossier du monde du lobby) | `rago.lobby.admin` |
| `/lobby point <nom>` | place ou déplace un point nommé là où tu es : où iront les PNJ des modes, les classements, les décorations (écrit dans `points.yml`, dans le dossier du monde) | `rago.lobby.admin` |
| `/lobby point remove <nom>`, `/lobby point tp <nom>`, `/lobby points` | retirer un point, y aller, la liste | `rago.lobby.admin` |
| `/lobby build` | mode construction dans le lobby (créatif, protection levée) | `rago.lobby.admin` |
| `/shop` (ou `/boutique`), `/profile` (ou `/me`), `/daily` | la boutique, son profil, la récompense du jour | tout le monde |
| `/settings` (ou `/options`, `/reglages`) | ses réglages : messages, invitations, heure du ciel, rejouer tout seul | tout le monde |
| `/season` (ou `/saison`) | la saison classée : sa fin, ses récompenses, ses divisions et ses saisons passées | tout le monde |
| `/gift <joueur>` (ou `/cadeau`) | offrir un rang avec ses Rago Points (VIP 25 000, PREMIUM+ 50 000) ; l'autre accepte dans un livre (YES / NO) | PREMIUM et au-dessus |
| `/friend add\|accept\|deny\|remove\|list\|requests\|toggle <joueur>` (ou `/f`) | les amis | tout le monde |
| `/msg <joueur> <message>` (ou `/tell`, `/w`), `/r <message>` | message privé, réponse | tout le monde |
| `/stats [joueur]`, `/top elo\|level\|wins\|kills\|fkdr\|streak\|games\|points [mode]` | les stats, les meilleurs | tout le monde |
| `/report <joueur> <raison>`, `/discord`, `/rules`, `/site`, `/store` | signaler un joueur, les liens | tout le monde |
| `/fly` | voler dans le lobby, gardé d'une partie et d'une connexion à l'autre (`/fly` encore pour atterrir) | `rago.perk.fly` (VIP, PREMIUM) |
| `/style` (ou `/couleur`), `/style badges` (ou `/badge`) | choisir sa couleur de pseudo, son badge (dont ceux des saisons) et la couleur de son + | tout le monde |
| `/booster`, `/booster on`, `/booster coins` | ses boosters, lancer un booster d'XP ou de pièces | tout le monde |
| `/booster give <joueur> [nombre] [xp\|coins\|daily]` | donner des boosters (la boutique en ligne, depuis la console) | `rago.admin` |
| `/pack`, `/pack info` | la vitrine du PNJ Shop, le pack de départ dans le chat | tout le monde |
| `/pack give <joueur>` | donner le pack de départ, une fois (la boutique en ligne, depuis la console) | `rago.admin` |
| `/level` (ou `/niveau`), `/quests` (ou `/quetes`) | son niveau et ses récompenses, les quêtes | tout le monde |
| `/titles` (ou `/titres`), `/titles <titre\|best\|none>` | les titres des modes, en porter un, son meilleur ou aucun | tout le monde |
| `/display <elo\|level\|wins\|kills\|streak\|title\|none>` | la ligne au-dessus de son pseudo | tout le monde |
| `/display message <texte>` | son propre message au-dessus du pseudo | `rago.perk.message` (PREMIUM) |
| `/nick <pseudo>`, `/nick reset` (ou `/unnick`) | jouer sous un autre pseudo | `rago.perk.nick` (PREMIUM) |
| `/realname <nick>` | qui joue sous ce nick | `rago.staff` |
| `/style give\|take <joueur> <couleur-feu\|badge-rago>`, `/style list` | donner ou retirer un style (la boutique en ligne, depuis la console) | `rago.admin` |
| `/staffchat [message]` (ou `/sc`), `/vanish` (ou `/v`), `/freeze <joueur>`, `/clearchat` | chat du staff, invisible, figer un joueur, vider le chat | `rago.staff` |
| `/reports` | les 20 derniers reports, le plus récent d'abord (un report n'apparaît que dans le chat de celui qui l'a fait, et dans la console) | `rago.staff` |
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

## Les couleurs du serveur

Le serveur se reconnaît partout aux couleurs de son logo, le rouge, le blanc et le gris, et le
config.yml fourni les suit :

- **La marque** : le rouge clair (`&c`). Le titre du sidebar, la liste des serveurs, le Tab,
  l'adresse rago.gg, le préfixe des messages du serveur et ce qu'ils montrent (une commande, un
  lien), les titres des murs de classement et du règlement.
- **Le texte** : `&f` blanc pour les valeurs, `&7` gris pour les étiquettes, `&8` gris foncé pour
  les séparateurs.
- **Le sens** : `&a` vert pour jouer et réussir ; le rouge reste celui d'une erreur ou d'un départ.
- **Les menus** : ce qu'on peut faire est en rouge (« Click to open », « Click to buy », la commande
  à taper), ce qu'on ne peut pas encore faire en gris foncé (« Locked », « Not enough Rago Points »),
  ce qui est déjà pris en vert (« Worn », « Unlocked »).
- **Les messages** : une information est en gris, les noms et les nombres en blanc (la file, la
  partie, les amis, le kit, les replays) ; les messages de party et d'amis sont entre deux traits
  rouges, et le chat de la party commence par « Party » en rouge.
- **Le jaune** ne sert plus qu'à l'XP Network, au ping moyen (vert, jaune, rouge) et au palier Gold
  des titres ; le Bed Wars garde ses couleurs de jeu (les équipes, la boutique d'objets).
- **Les récompenses** gardent leurs couleurs : l'XP Network en jaune, les Rago Points en or.
- **Les catégories** : Practice `&b`, RBW Modes `&6`, Duels `&a`, Arenas `&c`. Le nom de chaque mode
  et de chaque groupe prend la couleur de sa catégorie : c'est aussi la ligne du mode en tête du
  sidebar en partie.
- **Les rangs** : VIP `&b`, PREMIUM+ `&d` ; le rose ne sert à rien d'autre.
- **Un seul préfixe** pour les messages du serveur : `&c&lRAGO &8» `, puis le texte en blanc, court,
  en anglais, qui commence par un verbe (les astuces, `/discord`, `/site`, `/store`, un rang acheté,
  un pack reçu).
- **Le slogan** : « Train. Clutch. Climb. » (s'entraîner, gagner ses duels, monter en Ranked), sous
  le nom dans le Tab et dans l'accueil.
- **La liste des serveurs** : la ligne 1 dit ce qu'est le serveur (« RAGO PRACTICE • Bed Wars
  Practice, Ranked & Duels »), la ligne 2 ce qui est nouveau : « NEW » en rouge gras, puis la
  nouveauté, à changer à chaque mise à jour. Une ligne fait 270 pixels au plus (une lettre 6, un
  espace 4, un i 2, 1 de plus en gras) : plus longue, elle passe à la ligne et cache la suivante, et
  le plugin le dit au démarrage. L'icône est le `server-icon.png` (64x64) à la racine du serveur :
  celui du serveur de test (tools/local-server) est le logo, un R blanc sur un hexagone rouge.

## Catégories, PNJ et classements

- `categories:` range les modes par zone du lobby : la boussole montre les catégories (Practice à
  gauche, RBW Modes avec un lit, Duels, Arenas à droite : les modes sans file, FFA, Top Bridge FFA,
  Lobby Fight FFA, Bridge fight, Block placement arena et Arena Free, où l'on entre tout de suite ;
  Lobby Fight 1v1 est dans Duels),
  puis les jeux de celle qu'on choisit (7 catégories, 28 objets par catégorie au plus, un groupe
  comptant pour un). Chaque mode dit sa `categorie:`. Le `slogan:` d'une catégorie (40 caractères au
  plus) dit ce qu'on y fait : sous son nom dans la boussole, sous son PNJ et dans l'accueil.
- `groupes:` réunit les tailles d'un même jeu en un seul objet de sa catégorie, comme les jeux de
  Hypixel : Ranked Bedwars ouvre 1v1, 2v2, 3v3 et 4v4 côte à côte, Top Bridge ouvre 1v1, 2v2 et
  FFA, etc. Un mode y entre avec `groupe:` ; l'objet du groupe montre chaque taille avec sa file.
  Un mode `cache: true` ne paraît pas dans la boussole et ne se joue qu'en partie privée.
- `pnj:` place un PNJ sur un point de `/lobby point` (la clé) : un joueur dans son skin, tourné
  vers le spawn, qui tient l'icône du mode, avec son nom et le nombre de joueurs en file au-dessus
  de la tête. Un clic rejoint la file du mode, ou ouvre un menu quand le PNJ a plusieurs modes ;
  dans ce menu, les tailles d'un même groupe ne font qu'un objet (Top Bridge, une échelle qui ouvre
  1v1, 2v2 et FFA), sauf si le PNJ n'a que ce groupe. Un PNJ avec `categorie:` ouvre toute sa
  catégorie, comme la boussole. Au centre, quatre PNJ seulement, un par façon de jouer, pour qu'un
  nouveau joueur ne s'y perde pas : Practice, RBW Modes, Duels et Arenas (les points `practice`,
  `rbw`, `duels` et `arenas`), plus un PNJ **Private game** (point `prive`) pour les parties
  privées, qui restent aussi dans RBW Modes. Les PNJ d'un mode (Ranked Bedwars, FFA,
  Clutch Scenario, Attack and Defense, Sky Rise, First rush, Invis, Bridge fight,
  Block placement) et ceux des zones Practice et Duels sont en commentaire dans `pnj:` ; leurs
  modes restent dans la boussole et dans les menus des quatre. Un PNJ de catégorie montre le
  slogan de sa catégorie sous son nom, un autre PNJ une ligne à lui avec `sous-titre:`. Les PNJ
  réapparaissent quand un point bouge.
- `skin:` sous un PNJ lui donne un skin : `skins/<nom>.png` dans le dossier du plugin (64x64, le
  vôtre), sinon celui de ce nom dans le jar. Le jar en a un par mode, originaux : `ranked` (armure
  de diamant et trophée), `prive` (costume et lunettes noires), `ffa` (bandeau rouge, épées
  croisées), `skyrise` (lunettes d'aviateur, nuage), `rush` (casquette, éclair), `invis` (capuche
  violette, potion), `clutch` (end stone, laine, lit), `attaque` (chevalier de fer au tabard
  rouge), `bridge` (bleu et blanc), `arena` (gladiateur vert), `blocs` (casque de chantier, laine
  de toutes les couleurs), `duels` (moitié rouge moitié bleu, casque de diamant), `practice`
  (survêtement de coach), `shop` (marchand au tablier vert, émeraude) et `stats` (robe de savant,
  lunettes, livre). Sans `skin:`, Steve. Un client 1.8 ne montre le skin d'un PNJ que signé par
  Mojang : au démarrage, le serveur envoie une fois chaque nouveau PNG à MineSkin
  (api.mineskin.org), garde la réponse dans `skins.yml` (avec l'empreinte du PNG : un PNG changé
  est signé à nouveau) et replace les PNJ ; en attendant, Steve. Si MineSkin refuse sans clé, le
  log le dit : une clé gratuite (account.mineskin.org/keys) va dans `cle-mineskin:`.
- `pnj:` avec `type: stats` place un PNJ de stats : un joueur (skin `stats`) qui tient un livre, avec au-dessus
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
  (`tableaux: [[niveau, elo, fkdr, kills, parties]]`, celui du mur `top`, qui montre
  d'abord le niveau Network, sur le tableau et sur le podium, puis l'ELO, le FKDR, les kills et les
  parties jouées) :
  un clic droit passe au classement suivant, un clic gauche change la période ou le mode classé, et un clic sur la ligne
  « Click here: next leaderboard » ou « Click here: change the period » fait ce qu'elle dit. Un
  clic gauche atteint un tableau de 16 blocs, n'importe où sur son texte : le plugin suit le
  regard du joueur. Un clic droit ne l'atteint que de près : plus loin, l'objet en main (boussole,
  livre...) garde le clic droit. Le tableau `niveau` est
  classé par XP et montre le niveau. Le mur `joueurs` montre les kills du jour, de la semaine et
  à vie, entre les meilleures séries de victoires (à gauche) et le niveau Network (à droite).
  `periode: semaine` (ou `jour`, `toujours`) fixe la période des tableaux d'un mur : un clic ne fait
  plus que passer au classement suivant. C'est le mur `semaine`, dont chaque tableau dit sous la
  place du joueur ce que son top gagne le lundi.
- `podium:` (dans un mur de `classements:`) : jusqu'à 5 marches, la 1re place d'abord. Sur chacune
  se tient le joueur à cette place du premier tableau du mur, avec son skin, et deux lignes
  au-dessus : la place, son rang et son pseudo dans sa couleur (« #1 [PREMIUM+] Pseudo »), puis son
  score (« Gold 245 »). Chaque joueur voit le top de ce qu'il lit sur le tableau : ELO, kills
  ou niveau, la période, le mode classé. Une marche est un point `/lobby point`, ou `"x y z"` (là où
  vont les pieds). Un clic sur une marche ouvre les stats de ce joueur. Celui du mur `top` est sur
  les points `top1`, `top2` et `top3`. Le skin d'un
  joueur est gardé dans ses stats à chaque connexion ; pour un joueur pas revenu depuis, le lobby
  le demande une fois aux serveurs de Mojang (serveur en online-mode ou derrière BungeeCord).

Les PNJ n'existent que dans les paquets envoyés aux joueurs (plugin PacketEvents, installé à côté
de RagoLobby) : le serveur ne les voit pas, ils ne bougent pas et ne prennent pas de coups. Ils
quittent la liste des joueurs (Tab) deux secondes après être apparus, et le scoreboard du lobby
cache leur nom au-dessus de la tête (équipe `pnj`).

## Panneaux publicitaires

`panneaux:` affiche vos images dans le lobby, comme les grands panneaux de Hypixel : la chaîne
YouTube, le Discord, la boutique... Un panneau est un mur de cadres, une carte dans chacun ; une
carte tenue par un cadre n'a pas de bordure, et les cartes côte à côte font une seule image.

- L'image (PNG ou JPG) va dans `plugins/RagoLobby/panneaux/` (le dossier est créé au premier
  démarrage), `image:` dit son nom. Elle est lue au démarrage, hors du thread principal.
- Chaque bloc du mur est une carte de 128 x 128 pixels : un panneau de `largeur: 8` et
  `hauteur: 8` montre 1024 x 1024 pixels, un panneau de 16 x 9 en montre 2048 x 1152 (une image
  de 1920 x 1080 le remplit). 16 blocs de côté au plus, 8 panneaux au plus.
- Un client 1.8 ne dessine sur une carte que 144 couleurs : le plugin y ramène l'image, avec des
  points de couleurs voisines (le tramage, ou dithering) pour les dégradés. Vue de quelques
  blocs, elle est presque identique à l'originale. L'image est montrée entière, centrée, avec du
  noir autour si elle n'a pas la forme du panneau.
- `lieu: "x y z"` place le panneau : le bloc du milieu de sa rangée du bas (celui juste à droite
  du milieu, vu de face, pour une largeur paire), le panneau tourné vers le spawn ; `"x y z nord"`
  (`sud`, `est`, `ouest`) le tourne de ce côté. Pour le placer en jeu : se mettre à cet endroit
  (en vol au besoin), regarder d'où on doit le voir et taper `/lobby point panneau-<nom>`. Ce
  point passe avant `lieu:`, et le panneau bouge tout de suite.
- Les cadres n'existent que dans les paquets des joueurs (comme les PNJ) : rien n'est posé dans
  le monde, on ne peut ni les casser ni les tourner. Ils se voient de 128 blocs. Chaque joueur
  reçoit les cartes une seule fois (4 par tick : un panneau de 8 x 8 en moins d'une seconde),
  et de nouveau seulement quand son client les oublie (changement de monde, connexion).

## Les stats

Les plugins de jeu donnent le résultat de chaque partie au lobby (`recordMatch`, voir plus bas) :
victoires, défaites, série de victoires en cours et meilleure série, par mode, à vie, du jour et
de la semaine (heure de Paris : le jour repart à minuit, la semaine le lundi à minuit). Un abandon
compte comme une défaite. Les kills et les morts aussi (`recordKill`) : au sumo, chaque point est
un kill pour celui qui le marque et une mort pour celui qui tombe. Chaque joueur garde aussi son
XP Network. Une partie d'un mode classé trouvée par la file fait aussi bouger l'ELO des deux
camps (voir « L'ELO classé ») ; un duel ou une partie privée ne touche ni l'ELO ni les séries.

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
`lobby.giveXp("aim-trainer", joueur, xp, "aim trainer")` (rien dans une partie privée). Un joueur qui quitte une partie qu'il joue encore
(un abandon) est signalé avec `lobby.forfeited("sumo", joueur)` avant d'être rendu au lobby : après
une partie classée, les files classées lui sont fermées un moment. À la fin, `lobby.recordMatch("sumo", gagnants, perdants)` compte la partie
dans les stats, les classements et l'XP Network.

Un entraînement peut envoyer son joueur directement dans un autre : `lobby.openModesOf(joueur,
mode)` ouvre le menu de la catégorie du mode (en partie aussi), et un clic sur un mode joué seul y
emmène le joueur sans repasser par le lobby, si l'hôte de sa partie le laisse partir
(`MatchHost.letGo(joueur)`, faux par défaut). Un hôte qui répond vrai à `MatchHost.canLetGo(joueur)`
(faux par défaut) garde aussi la place de ses joueurs dans les files : ils peuvent en rejoindre
depuis la partie, et la partie trouvée les en sort avec `letGo`. Sinon, entrer dans la partie fait
quitter les files.

Pour `/spectate`, le lobby vérifie qui peut regarder qui, compte le spectateur dans la partie du
joueur, puis appelle `MatchHost.spectate(spectateur, joueur)` (faux par défaut : la partie ne se
regarde pas). L'hôte le met dans la partie du joueur, invisible, et le rend avec
`lobby.sendToLobby(joueur)` à la fin ; `abandon` est appelé à son `/spawn`, et `letGo` quand une
partie trouvée dans une file l'en sort (un hôte qui accepte des spectateurs répond vrai à
`canLetGo` pour eux).

Le plugin des entraînements donne son défi du jour avec `lobby.setDailyChallenge("clutch",
"Block clutch: Island")` (`null` : aucun) : la boussole le montre au bout de « Popular now » tant
que son mode est joué, et un clic lance `/challenge play` pour le joueur.

Un entraînement dit chaque score avec `lobby.trainingScored(joueur, "clutch", record)` (`record`
vrai pour un nouveau record personnel, pas pour le premier score) : c'est ce qui fait avancer les
étapes « Play a training » et « Beat one of your records » de Getting started, et les quêtes des
réussites et des records. Un score au défi du jour se dit avec `lobby.dailyChallengePlayed(joueur,
mode)`, et chaque minute d'entraînement où le joueur a fait quelque chose avec
`lobby.trainedMinute(joueur, mode)` : les quêtes du défi et des minutes avancent (rien dans une
partie privée).

Un plugin pose son propre tableau sur un point du lobby avec `lobby.setLobbyBoard(plugin, "records",
lignes, texte, clic)` : chaque joueur lit ses lignes (demandées deux fois par seconde), et un clic
gauche ou droit va au plugin (le mur des records du practice). Il ajoute un classement de la
semaine, récompensé avec ceux du lobby, avec `lobby.addWeeklyBoard(plugin, "Biggest clutches",
semaine -> classement)` (la semaine « 2026-W46 », le meilleur d'abord, chacun avec le score
affiché), et lit les amis d'un joueur avec `lobby.friends(joueur)`. `unregisterHosts` reprend
tout cela quand le plugin s'arrête.

Pour `/replay`, un jeu enregistre ses parties avec `lobby.replays()` (`null` quand les replays sont
coupés) : `record(ReplaySetup.ofBlocks(...))` pour une arène dont les blocs tiennent dans le replay
(un duel : la zone de la partie et ses blocs au départ), ou `ReplaySetup.ofStage(...)` pour une
partie rejouée dans une copie de sa map, que le plugin fournit avec
`replays.registerStages(plugin, "bedwars", stages)`. Le `ReplayRecording` rendu suit les joueurs
tout seul ; le jeu lui donne ses joueurs (`actor(joueur, couleur)`), les lignes de son chat
(`chat(ligne)`), puis `finish(résultat)` à la fin (qui rend le code du replay), ou `cancel()` si
la partie n'a pas lieu.

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
- Des cosmétiques pour le lobby (chapeaux, familiers).

## Construire et tester

```bash
./gradlew :rago-lobby:build
```

Les tests unitaires lisent le `config.yml` fourni et des configurations invalides, `spawn.yml`, la
disposition du sélecteur, l'horloge du scoreboard, les rangs, les divisions ELO, la récompense du
jour, les durées et le fichier des sanctions. Le comportement en jeu est testé par deux
bots sur un vrai serveur : voir [tools/lobby-check](../../tools/lobby-check/README.md).
