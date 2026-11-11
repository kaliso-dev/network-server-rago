# RagoBedwars : les modes Bed Wars

Un plugin Bukkit 1.8.8 qui joue les modes Bed Wars du lobby (`bukkit/rago-lobby`) : **Ranked
Bedwars 1v1, 2v2, 3v3 et 4v4**, **RBW Sky Rise** en solo et en duo, avec les règles d'une partie
classique d'Hypixel, **First Rush** en solo et en duo (la même partie, sur deux îles voisines), les
**parties privées** d'une party (le lobby choisit le 1v1, 2v2, 3v3 ou 4v4 selon sa taille), et
six jeux à part sur des maps de Bed Wars : **Top Bridge**, **Attack and Defense**, **Clutch
Scenario**,
**FFA** (dont le Top Bridge FFA), **Invis practice** et **Fast Shop**. Chaque mode a ses maps.

## La partie

- Chaque équipe apparaît sur son île, avec une épée en bois et une armure en cuir à sa couleur.
  Compte à rebours de 3 secondes, immobile, puis « Fight! ». Sur une map qui a plus d'îles que la
  partie n'a d'équipes, les équipes prennent des îles éloignées (un duel sur une map à 4 se joue sur
  deux îles opposées, rouge contre vert), et les lits des îles vides sont enlevés.
- **Forges** : chaque île a sa forge de fer et d'or, à la vitesse de sa map comme sur Hypixel
  (débits mesurés sur Hypixel). Un « nœud » de fer donne 30 lingots par minute ; une forge a 1 nœud
  sur les maps lentes, 2 sur les moyennes, 3 sur les rapides, et un de plus sur les maps à 4
  équipes ; le fer tombe par paquets de 2 ou 4, comme sur Hypixel. Ça fait environ 90 fers par
  minute sur une map rapide à 8 îles et 120 sur les maps à 4 îles des Ranked. L'or donne 8 lingots
  par minute, 12 sur les maps de 4 îles ou moins ; au plus 48 fers et 16 ors par terre ; deux fois
  plus vite pendant les 30 premières secondes. Toutes les maps sont rapides, sauf Lighthouse et Lotus (lentes en solo,
  moyennes en duo), comme sur Hypixel (réglable dans `forge:`). Le RBW 1v1 a la forge d'un solo
  d'Hypixel sur toutes ses maps : 90 fers et 8 ors par minute (`forge.modes`, réglable par mode),
  au lieu des 120 et 12 d'un 4v4 pour un seul joueur. Les générateurs de diamant et
  d'émeraude affichent leur tier et le temps avant le prochain objet. Ils passent tier II puis III
  pendant la partie.
- **Événements** (réglables) : Diamond II à 6 min, Emerald II à 12, Diamond III à 18, Emerald III
  à 24, tous les lits détruits à 30, fin de la partie (égalité) à 45. Le scoreboard montre le
  prochain, les équipes (✔ tant que le lit tient, sinon le nombre de joueurs encore en vie), et
  les kills, final kills et lits cassés du joueur.
- **Shop** : le villageois « ITEM SHOP » de chaque île (son nom flotte au-dessus de lui sur deux
  lignes, « ITEM SHOP » et « RIGHT CLICK », comme sur Hypixel, et il regarde droit devant). Le Quick Buy et les sept catégories en
  haut (Blocks, Melee, Armor, Tools, Ranged, Potions, Utility), trois rangées de sept objets, aux
  prix d'Hypixel en solo et en duo. Le Quick Buy est celui que le joueur a copié d'Hypixel
  (`/hypixel import`, ou l'émeraude du livre du lobby), sinon un Quick Buy par défaut. Les objets
  achetés vont dans la case que leur donne le hotbar manager Hypixel du joueur, si elle est libre.
  L'armure achetée reste jusqu'à la fin ; la pioche et la hache montent tier par tier et perdent un
  tier à chaque mort ; les cisailles restent. TNT allumée en la posant, seau d'eau à usage unique.
- **Boules de feu** : lancées au clic droit, comme sur Hypixel (les valeurs de BedWars1058, la copie
  ouverte du Bed Wars d'Hypixel). Leur souffle pousse les joueurs à 3 blocs ou moins : loin de lui
  à l'horizontale (1,0) et vers le haut (0,65 × 1,5), ce qui fait le saut à la boule de feu ; il
  fait 1 cœur, armure ou pas, à celui qui la lance et aux autres équipes, rien à ses coéquipiers.
  Réglable dans `boule-de-feu:`.
- **Elo** : dans un mode classé (Ranked), la barre d'expérience affiche l'elo du joueur dans ce
  mode pendant toute la partie, comme sur les serveurs RBW.
- **Morts** : un coup qui tuerait, une chute dans le vide ou une chute mortelle. Le kill revient au
  dernier joueur qui a frappé dans les 10 secondes, avec le fer, l'or, les diamants et les
  émeraudes du mort. Tant que son lit tient, le joueur revient au bout de 5 secondes (il regarde la
  partie en spectateur en attendant) ; sans lit, c'est un **final kill** et il est éliminé. Le
  message donne le total de final kills du tueur, toutes parties confondues, comme sur Hypixel :
  « vaggww78 was qAbel's final #41,000. FINAL KILL! » (sans total dans une partie privée, qui ne
  compte pas dans les stats).
- **Lits** : on ne casse pas le sien. Casser celui d'une autre équipe l'annonce à tous, avec le
  total de lits cassés du joueur (« Red Bed was destroyed by qAbel's bed #1,250! »), et ses joueurs
  ne reviendront plus.
- **Ce que la map a gardé d'une partie** : une map capturée sur un serveur garde parfois ce qui
  traînait au moment de la capture (les lingots et émeraudes posés sur les forges, les hologrammes
  « ITEM SHOP » ou « Tier I Diamond », des mobs). Tout ça est retiré de chaque copie de la map
  avant la partie : une forge commence vide.
- **Blocs** : on ne casse que les blocs posés pendant la partie (et les lits). Pas de bloc posé à
  moins de 3 blocs d'un spawn, à moins de 2 d'un générateur, ni au-dessus de la hauteur maximale.
  Les explosions (TNT et boules de feu) ne cassent que les blocs posés, jamais le verre
  « blast-proof », l'obsidienne ni les lits ; l'end stone résiste aux boules de feu, pas à la TNT,
  comme sur Hypixel. Pas de tir ami.
- **Fin** : la dernière équipe en vie gagne. Le résultat va dans les stats du lobby (victoires,
  défaites, ELO du mode classé, kills), puis tout le monde revient au lobby. Quitter le serveur ou
  taper `/spawn` pendant la partie, c'est l'abandonner (une défaite).

## First Rush

`first-rush` (1v1) et `first-rush-duo` (2v2) sont de vraies parties, comme RBW Sky Rise, mais les
deux équipes jouent sur des **îles voisines** au lieu d'îles opposées (`iles-voisines:` du
config.yml) : le premier rush décide souvent de la partie. Le joueur choisit sa map parmi les dix
maps duo.

`first-rush-full` (1v1) et `first-rush-full-duo` (2v2), « First rush full map », sont la même
partie avec toute la map : les diamants et les émeraudes, et les forges des îles où personne ne
joue tournent aussi, leur fer et leur or pour qui va les prendre (`forges-partout:` du config.yml).

## Top Bridge

`top-bridge-1v1` et `top-bridge-2v2` (le jeu `top-bridge`) se jouent sur les 20 maps de la file,
sur le **top bridge** construit sur Antenna : au début de la partie, le plugin pose sur la copie
une plateforme de laine de 17 blocs de large au-dessus du milieu, chaque moitié à la couleur de
l'équipe de son côté (un bloc blanc au centre), et depuis le bord de chaque île un escalier de laine
d'un bloc de large qui monte d'un bloc tous les deux blocs. Sur Antenna, c'est exactement la
construction d'origine : 16 blocs au-dessus des lits (y 99), 79 blocs de long, 1445 blocs. Sur les
autres maps, la plateforme reste à 16 blocs au-dessus des lits si rien de la map n'est dedans ni
juste au-dessus, sinon elle prend la hauteur la plus proche où elle est dégagée ; elle est plus
basse quand les îles sont proches, pour garder au moins 16 blocs de long. Entre deux lits qui ne
sont pas alignés (Invasion, Lectus), tout suit la ligne d'un lit à l'autre. On construit jusqu'à
10 blocs au-dessus de la plateforme ; elle ne se casse pas, et la copie l'enlève après la partie.
Les modes qui l'ont sont listés dans `top-bridge:` du config.yml.

Ni lit ni forge ni shop : seul le combat sur le pont compte. Chaque équipe démarre en haut, sur sa
moitié, à 12 blocs de la ligne blanche du milieu, côte à côte en 2v2, face à l'autre moitié, avec
le stuff de combat du propriétaire : une épée en pierre et un stack de pommes d'or (arrangeables
dans l'éditeur de kit), une armure complète en diamant Protection IV, sans effet d'absorption. Celui qui tombe à 10
blocs sous la plateforme est mort, comme dans le vide (on a le temps d'un clutch). Ceux qui
attendent regardent depuis le dessus du milieu.

- **Dans la file** : des manches d'une vie. Une manche est gagnée quand tous les joueurs de l'autre
  équipe sont morts ; la première équipe à 5 points gagne. Entre deux manches, la copie redevient
  la map (les blocs posés partent), le pont est reposé et tout le monde repart d'en haut après un
  compte à rebours de 3 secondes. Une party qui fait la file joue dans la même équipe. Au bout de
  30 minutes, l'équipe en tête gagne.
- **En partie privée** (une party qui remplit le match, ou le mode **Private top bridge** du PNJ
  des parties privées) : un practice sans fin. Personne n'est éliminé : on revient en haut 2
  secondes après une mort, les blocs posés partent au bout de 45 secondes, et la partie dure 30
  minutes au plus. Rien ne compte dans les stats.

`top-bridge-ffa` est le FFA libre sur le pont, sur Invasion seulement : on entre et on sort quand on veut,
chacun pour soi, avec le même stuff de combat à chaque vie. Le top bridge relie les
deux îles les plus éloignées ; on apparaît et on revient 3 secondes après une mort sur la
plateforme, à l'endroit le plus loin des autres joueurs, et celui qui tombe en dessous est mort.
Un kill soigne.

## Attack and Defense

`attack-defense-1v1` et `attack-defense-2v2` (le genre `attaque-defense`), sur les maps d'attack
and defense du propriétaire : d'abord **BioHazard** (Bio Hazard, dont il ne reste que la base
grise et le pont rose qui y mène). Deux manches : chaque camp attaque une fois, les rôles
s'échangent à la deuxième, la map redevient comme au départ.

- **Les attaquants** partent du bout du pont (les `attaquants:` du `bedwars.yml` de la map), avec
  une seule vie, une armure en fer et le kit d'attaque qu'ils ont choisi. Ils posent des blocs et
  lancent des boules de feu comme dans une vraie partie.
- **Les défenseurs** partent de leur base avec le stuff d'un début de partie (épée en bois), en
  armure de fer comme les attaquants, avec leur forge et leur shop, sans améliorations, plus leur
  kit de défense à leur première vie de la manche (son épée remplace celle en bois ; sa pioche,
  sa hache ou ses cisailles restent comme achetées). Ils reviennent tant que leur lit tient ;
  après, une mort est un final kill, comme dans une vraie partie.

**Les kits.** Avant chaque manche, un menu s'ouvre tout seul avec les kits du rôle du joueur ; le
décompte dure 10 secondes, 3 dès que tout le monde a choisi. `/kit` (ou le bouton [CHANGE KIT] du
chat) le rouvre ; un choix pendant la manche vaut pour la prochaine manche dans ce rôle. Le
dernier kit choisi est retenu. Les objets suivent la place que le joueur leur a donnée dans
l'éditeur de kit du lobby, sinon sa hotbar Hypixel.

| Attaque | Objets |
|---|---|
| Rusher | épée en pierre, 64 laines, 3 boules de feu, pioche en or (Efficacité III, Tranchant II), cisailles |
| Breaker | épée en pierre, 32 laines, 2 TNT, pioche en diamant (Efficacité III), cisailles |
| Ninja | épée en pierre, 32 laines, potion d'invisibilité (30 s), ender pearl, pioche en fer, cisailles |

| Défense | Objets |
|---|---|
| Guardian | épée en pierre, bâton Recul I, 16 laines, pomme d'or |
| Archer | arc, 24 flèches, 16 laines |
| Builder | 24 end stone, 32 laines, cisailles |

Les kits se changent dans `attaque-defense:` du `config.yml` (de 1 à 7 par camp, les objets du
shop par leur id, avec leur nombre).

La défense que la map a sur le lit (sur Bio Hazard, l'end stone et la laine capturées avec la map)
est celle des défenseurs, comme dans une vraie partie : on peut la casser, et elle revient entière
à chaque manche.

Les attaquants gagnent la manche en cassant le lit et en tuant tous les défenseurs ; les
défenseurs en tuant tous les attaquants, ou en tenant 5 minutes. Le camp qui gagne le plus de
manches gagne ; à une manche partout, la plus rapide des deux attaques réussies gagne, sinon c'est
une égalité.

## Clutch Scenario

`clutch-scenario-1v1`, `-2v2`, `-3v3` et `-4v4` (le genre `clutch-scenario`) : une vraie partie de
Ranked Bedwars sur n'importe quelle map (les 20 maps de la file, Sky Rise, les maps solo et duo),
les deux équipes sur des îles opposées. L'une, tirée au sort, a déjà perdu son lit : une seule vie
chacun, elle doit clutch. L'autre garde le sien, sous une défense duo déjà posée, celle de la map
« Defense Claude » du propriétaire : de l'end stone autour du lit et dessus (avec un bloc de plus
derrière la tête), puis de la laine de l'équipe par-dessus (12 end stone, 23 laines). Tout le monde part de sa base en armure de fer avec une épée en pierre, sans
enchantement ni émeraude, les forges déjà pleines de fer et d'or. La partie est déjà à moitié
jouée, comme une vraie à 18 minutes : générateurs de diamant au Tier III, d'émeraude au Tier II,
puis Emerald III dans 6 minutes et la fin des lits dans 12. Ensuite, c'est une partie comme
les autres : shop, améliorations, diamants et émeraudes, jusqu'à ce qu'il ne reste qu'une équipe.

## Bed Fight

`bed-fight-1v1` et `bed-fight-2v2` (le genre `bed-fight`), comme le Bed Fight de Minemen, sur la
map BedFight du propriétaire : deux îles face à face, chaque lit sous la défense que la map a déjà
(bois et end stone), que les joueurs cassent comme dans une partie. Ni forge, ni shop, ni
amélioration, ni diamant : chacun a le kit du mode (l'éditeur de kit du lobby ; sinon une épée en
pierre, une pioche et une hache en bois, des cisailles et deux stacks de laine) en armure de cuir
de la couleur de son équipe, à chaque vie. On revient tant que son lit tient ; la dernière équipe
en vie gagne. Les lits partent à 10 minutes, égalité à 15.

La map n'est pas dans le dépôt : son `bedwars.yml` donne à chaque équipe son spawn et son lit ; le
générateur et le shop, que le mode n'utilise pas, sont posés sur le spawn.

## Améliorations et pièges

Le villageois « TEAM UPGRADES » de chaque île ouvre « Upgrades & Traps », comme sur Hypixel. Tout
se paie en diamants, pour toute l'équipe, aux prix d'Hypixel (ceux du 3v3 et du 4v4 dès trois
joueurs par équipe) :

- **Sharpened Swords** (4, ou 8) : Tranchant I sur les épées et les haches de l'équipe, celles
  qu'elle a et celles qu'elle achètera.
- **Reinforced Armor I à IV** (2, 4, 8, 16, ou 5, 10, 20, 30) : Protection sur toutes les armures.
- **Maniac Miner I et II** (2, 4, ou 4, 6) : Haste pour toujours.
- **Forge** (2, 4, 6, 8, ou 4, 8, 12, 16) : Iron Forge +50 %, Golden Forge +100 %, Emerald Forge
  (une émeraude par minute à la forge de l'île), Molten Forge +200 %.
- **Heal Pool** (1, ou 3) : Régénération I dans la base.

**Les pièges** se mettent en file, trois au plus, pour 1, 2 puis 4 diamants. Le premier ennemi qui
entre dans la base déclenche le premier : **It's a Trap!** (Cécité et Lenteur 8 secondes),
**Counter-Offensive** (Speed II et Jump Boost II 15 secondes pour l'équipe dans sa base),
**Alarm** (l'intrus perd son invisibilité) ou **Miner Fatigue** (10 secondes). L'équipe le voit en
grand et l'entend ; un autre piège ne part pas avant 15 secondes. La base est le cercle autour du
spawn qui va jusqu'au lit, au générateur et aux villageois, plus 6 blocs (de 12 à 22 blocs).

## Objets spéciaux

Les objets de la catégorie Utility du shop, comme sur Hypixel :

- **Bedbug** : la boule de neige libère un poisson d'argent de l'équipe, 15 secondes, qui va sur
  l'ennemi le plus proche.
- **Dream Defender** : un golem de fer de l'équipe, 4 minutes, qui attaque les ennemis à 16 blocs
  (3 cœurs par coup). Les deux portent le nom de leur équipe et leur temps restant, ne touchent
  jamais leur équipe, et un kill de leur part revient au joueur qui les a sortis.
- **Bridge Egg** : l'œuf pose la laine de l'équipe sous lui tant qu'il vole (27 blocs au plus).
- **Magic Milk** : 30 secondes sans déclencher de piège.
- **Compact Pop-up Tower** : posée, elle monte en un instant autour du joueur, tournée vers où il
  regarde : quatre murs de 5 blocs, une échelle à l'intérieur, un toit plus large avec ses créneaux.
  Elle ne remplace aucun bloc et ne se pose jamais dans un joueur.

## Coffres, noms, chat et invisibilité

- **Noms** : dans une partie classique, au-dessus de la tête et dans la liste Tab, le pseudo montre
  l'équipe comme sur Hypixel (« **R** Steve » en rouge) ; le rang revient au retour au lobby.
- **Coffres** : le coffre d'une île est le même pour tous ceux qui l'ouvrent (on peut piller celui
  d'un ennemi), l'ender chest est à chacun. Un clic gauche sur un coffre ou un ender chest avec du
  fer, de l'or, des diamants ou des émeraudes en main les y met directement, tout le stack
  (« You deposited x16 Iron into the Chest! ») ; le clic droit l'ouvre.
- **Chat** : chaque partie a son chat, à part du lobby. Dans un mode à équipes, on parle à son
  équipe ; un message qui commence par `!` va à toute la partie, en « [SHOUT] » ; les éliminés
  parlent entre eux.
- **Invisibilité** : un joueur sous potion d'invisibilité l'est tout entier, comme sur Hypixel :
  les autres ne voient plus son armure (elle le protège toujours) ni les particules de la potion.
  C'est le paquet d'équipement du serveur, trouvé par réflexion ; s'il manque, l'armure se voit et
  la console le dit une fois.

## Les jeux à part

Dans `jeux:` du config.yml, un mode peut jouer autre chose qu'une partie classique :

- **FFA** (`ffa`, sur Archway FFA seulement, pas de choix de map) : chacun pour soi, ouvert,
  on entre et on sort quand on veut. Le kit du mode (rangé dans l'éditeur de kit du lobby) est le
  stuff de combat du propriétaire, une épée en pierre et un stack de pommes d'or, avec 3 boules de
  feu, une perle et 2 stacks de laine, et à chaque vie une armure complète en diamant Protection IV
  (sans effet d'absorption) ; on revient 3 secondes après une mort ; un kill soigne ; les
  blocs posés partent au bout de 10 secondes ; les kills comptent dans les stats. Archway FFA (le
  dossier `ArchwayFFA`) est l'Archway du propriétaire : des ponts de laine entre les îles, une
  défense d'end stone et de verre sur les lits rouge et vert pour les top fights, et depuis ces deux
  îles un escalier jusqu'au top bridge au-dessus du milieu (sa plateforme à y 85) ; on construit
  jusqu'à y 95. En arrivant, un menu demande où apparaître : **Top bridge** (la plateforme du
  top bridge), **Low** (sous l'arche du milieu, près de l'émeraude du bas), **Mid** (sur l'arche)
  ou **Random** (n'importe lequel de ces endroits, les bases et les diamants). Le choix vaut pour
  chaque vie ; `/ffaspawn` (ou [CHOOSE] dans le chat) le change, tout de suite si on vient
  d'apparaître sans être touché. Parmi les points du choix, on prend au hasard un endroit sans
  joueur à moins de 12 blocs, sinon le plus éloigné des autres. Ces points sont les `apparitions:`
  du `bedwars.yml` de la map (un nom, puis sa liste de points ; 8 au plus). Une map sans
  `apparitions:` fait revenir sur l'île la plus éloignée des autres.
  **Où poser des blocs** : sur Archway FFA, seulement sur les îles, jamais sur les ponts. Les îles
  sont les `iles:` du `bedwars.yml` de la map, chacune une boîte (`de:` et `a:`, deux coins, comme
  `retirer:`) ou un disque (`centre: {x, z}`, `rayon`, de y `bas` à y `haut`) : le milieu est un
  disque de rayon 27,5 qui s'arrête à y 80, 5 blocs sous le top bridge (rien sur le top bridge,
  ni pour y monter), puis les 4 bases et les 4 îles à diamants. Une map sans `iles:` laisse poser
  partout sous sa hauteur max. Aucun bloc non plus près d'un endroit où l'on apparaît : les spawns
  des bases (comme en Bed Wars), les points des `apparitions:` et les diamants (Random y fait
  apparaître), 3 blocs autour et 3 au-dessus.
  Dans le FFA et le Top Bridge FFA, un **classement des kills** flotte en l'air à côté du
  combat : « TOP KILLS », puis les 10 joueurs qui ont le plus de kills à vie dans le mode, avec
  leur rang et leurs kills (les stats du lobby, relues toutes les 5 secondes). Sur Archway FFA, il
  y en a deux : un à côté du top bridge, 5 blocs après son bord est, sa ligne du haut à y 90 (à
  hauteur des yeux sur la plateforme), et un au low, devant l'entrée nord de l'arche, sa ligne du
  haut à y 73,5, au-dessus des têtes (`classement:` dans le
  `bedwars.yml` de la map, un point `{x, y, z}` ou une liste de points) ; une map sans
  `classement:` en met un haut au-dessus du milieu des îles. En Top Bridge FFA, il est à côté du
  milieu de la plateforme, 8 blocs après son bord et 7 blocs au-dessus. La sidebar montre le
  **top 3 des kills** de la partie en cours (les joueurs présents), sous ses kills, morts et
  série.
- **Free FFA** (`arene`) : la même chose avec les forges, les diamants et les émeraudes, un shop
  à lui (les diamants achètent aussi), l'armure et les outils gardés comme en Bed Wars, et sur
  chaque lit une défense de trois couches prête pour les top fights, remise quand personne n'est à
  côté. Aucun mode ne le joue pour l'instant : Arena Free (`free-ffa`) se joue sur l'Arena du
  propriétaire, dans le plugin practice ; il suffit d'un mode sous `jeux:` en `arene` pour le
  rouvrir.
- **Invis practice** (`invis`) : sur les 20 maps de la file, de 2 à 6 joueurs, des manches
  de 3 minutes. À chaque manche, un joueur attaque, au mid (le générateur d'émeraudes en face de
  la base à attaquer), tourné vers le lit, avec le kit du mode (rangé dans l'éditeur de
  kit du lobby) : une potion d'invisibilité (30 s) et une de Jump V (45 s), 16 planches, une pioche
  en or, une fireball, une hache en fer, des cisailles, une épée en pierre, 2 Bridge Eggs, 2 stacks
  de laine, en armure de fer Protection I (cachée tant qu'il est invisible). Il laisse des traces
  de pas au sol (une tous les 0,8 bloc, pied gauche puis pied droit, qui restent quelques
  secondes) et les tourbillons gris de la potion autour de lui : les défenseurs peuvent le
  repérer, et un coup d'un défenseur lui fait perdre son invisibilité pour le reste de la manche.
  Les autres modes gardent l'invisibilité complète d'Hypixel. Les autres, quatre au plus,
  défendent le lit de l'île voisine sous une défense de ranked sans sa couche de laine : end stone
  sur le lit, puis verre et bois (les couches que l'île a la place de porter) ; cisailles, outils en bois et Haste,
  épée en fer Sharpness, 2 stacks de laine, armure de fer Protection IV.
  L'attaquant gagne la manche en cassant le lit, les défenseurs en le tuant (une vie) ou en tenant
  3 minutes. Chacun attaque à son tour ; le plus de points gagne. La partie part 20
  secondes après l'arrivée du deuxième joueur, 5 secondes quand ils sont six.
- **Fast Shop** (`fast-shop`) : acheter le plus vite possible avec son propre quick buy (copié
  d'Hypixel dans le lobby). Sept listes, chacune avec ses records : le premier achat, l'armure de
  fer, les outils, un fireball fight, une défense de lit, un rush, et une liste au hasard. Un clic
  sur un objet qui n'est pas sur la liste compte comme une erreur.

## Les maps

Une map est un monde du dossier des mondes du serveur, avec un fichier `bedwars.yml` dans son
dossier, qui garde ses points. Les points se placent en jeu avec `/bw` (opérateurs, permission
`rago.bedwars.admin`), en se tenant à l'endroit voulu. Dans l'ordre, pour une map à deux équipes
red et blue :

1. `/monde import NomDeLaMap` si la map vient d'ailleurs (le dossier du monde copié dans
   `mondes`), puis `/bw edit NomDeLaMap` pour y aller (en créatif).
2. Sur l'île rouge : `/bw spawn red` là où les joueurs apparaissent, en regardant vers où ils
   doivent regarder ; `/bw bed red` en visant le lit ; `/bw generator red` sur le générateur de fer
   et d'or ; `/bw shop red` là où se tient le villageois du shop, tourné vers où il doit regarder ;
   `/bw upgrades red` pour le villageois des améliorations (facultatif).
3. La même chose pour `blue` (et `green`, `yellow`, `aqua`, `white`, `pink`, `gray` pour une map à
   plus d'équipes).
4. `/bw diamond` sur chaque générateur de diamant, `/bw emerald` sur chaque générateur
   d'émeraude.
5. `/bw spectators` là d'où les morts regardent la partie (sinon au-dessus du milieu des spawns).
6. Si besoin, `/bw height 100` (hauteur maximale de construction ; sinon 25 blocs au-dessus du
   spawn le plus haut) et `/bw void 40` (la hauteur où l'on meurt ; sinon 30 blocs sous le spawn le
   plus bas).
7. `/bw info` dit ce qui manque ; `/bw enable` ouvre la map aux parties.
8. Facultatif, à écrire dans le `bedwars.yml` : `retirer:`, des boîtes de blocs que chaque copie
   enlève en étant faite, toutes ou seulement certains blocs. Antenna s'en sert pour laisser de
   côté la plateforme de laine construite dessus (le mode Top Bridge pose la sienne) :
   ```yaml
   retirer:
   - de: {x: -8, y: 81, z: -73}
     a: {x: 8, y: 120, z: 73}
     blocs: [WOOL]
   ```
9. Pour une map d'attack and defense, à écrire aussi dans le `bedwars.yml` : une seule équipe
   complète (la base défendue) et `attaquants:`, la couleur des attaquants (une couleur que la map
   n'a pas) et leurs départs, le premier attaquant au premier :
   ```yaml
   attaquants:
     equipe: pink
     departs:
     - {x: 94.5, y: 77, z: 20.5, yaw: 27}
     - {x: 94.5, y: 77, z: 18.5, yaw: 27}
   ```

`/bw maps` liste les maps, `/bw remove red|diamond|emerald|diamonds|emeralds` enlève une équipe ou
un générateur (le plus proche, à 5 blocs), `/bw disable` ferme la map. Chaque changement est
enregistré tout de suite ; les parties en cours gardent la map comme elle était.

**Les maps de chaque mode** : dans `modes:` du config.yml, chaque mode a sa liste de maps (les noms
de leurs dossiers), ou `toutes`. Par défaut : les Ranked sur les 20 maps de la file RBW (Antenna,
Aquarium, Archway, Artemis, Boletum, Carapace, Catalyst, Chained, Eastwood, Extinction, Invasion,
Katsu, Lectus, Obelisk, Paladin, Planet98, Rise, Stonekeep, Swashbuckle, Treenan), chacune avec la
hauteur limite de la file (`hauteur-max` dans son `bedwars.yml`) ; RBW Sky Rise sur SkyRise ; First Rush sur Acropolis, Ambush, Aqil, Cascade,
Crogorm, Frosted, Gelato, Lighthouse, Lotus et Nebuc. Une map absente du serveur est sautée (la
console le dit au démarrage). Quand un mode a deux maps prêtes ou plus, le joueur **choisit sa
map** en cliquant le mode dans le sélecteur (ou `/queue first-rush Gelato`), ou prend « Random
map » pour attendre moins ; il ne joue qu'avec ceux qui ont choisi la même map ou n'importe
laquelle. C'est pareil pour les modes classés (Ranked). Avec « Random map », la map est tirée au
hasard à chaque partie, une autre que la dernière du mode (sa copie est préparée à l'avance, pour
ne pas attendre).

Une map sert à un mode si elle a au moins autant d'équipes complètes que le mode a d'équipes,
qu'elle en ait exactement autant ou plus (les équipes prennent alors des îles éloignées, ou
voisines en First Rush) : Antenna, à deux équipes, est une des 20 maps des Ranked, pas la seule. Une équipe est complète avec son spawn, son lit, son générateur et son shop. Une map
d'attack and defense compte deux camps : sa base et ses attaquants.

Pour une map d'Hypixel, [tools/bedwars-maps](../../tools/bedwars-maps/README.md) trouve tous ces
points à partir des lits, des coffres et des îles, et écrit le `bedwars.yml` : il ne reste qu'à
les vérifier en jeu.

**La map de démo** : au premier démarrage, le plugin crée `bw-demo`, une map à lui (deux îles
rouge et bleue face à face, un milieu avec un générateur d'émeraude et deux îles de diamant), pour
jouer tout de suite. Elle ne sert que tant qu'aucune des maps du mode ne peut l'accueillir.
`carte-demo: false` dans le config.yml l'empêche de la créer.

## Les copies

Chaque partie se joue dans une copie de sa map (`bw_<map>_<n>`), jamais dans la map elle-même.
La première copie est faite quelques secondes après le démarrage, les suivantes quand il n'y en a
plus de libre (pendant ce temps, les joueurs gardent leur place dans la file). Après une partie, la
copie retire les blocs posés, remet les lits et les blocs que l'eau ou les blocs avaient
remplacés, et sert à la partie suivante. Les copies sont supprimées quand le serveur s'arrête (ou,
s'il a été coupé net, au démarrage suivant).
`parties-max` limite le nombre de copies, donc de parties en même temps. À la limite, une partie
qui attend une map sans copie libre fait de la place : une copie libre d'une autre map est
retirée.

## Configuration

`plugins/RagoBedwars/config.yml` : les modes joués (leurs ids dans le config.yml du lobby) avec
leurs maps, les modes joués sur des îles voisines, `parties-max`, `carte-demo`, le temps de
réapparition, la forge (le fer d'un nœud, l'or, les maximums, la vitesse de chaque map), la vitesse
et le maximum des générateurs de diamant et d'émeraude (trois tiers), les minutes des événements,
la vitesse, le recul et les dégâts des boules de feu. L'ancienne forme de `modes:` (une
simple liste) se lit encore : chaque mode sur toutes les maps.

## Construire et tester

```bash
./gradlew :rago-bedwars:build
```

Les maps (lecture et écriture de `bedwars.yml`, les maps de chaque mode, les îles d'un duel et
de First Rush), la forge de chaque map, le recul des boules de feu, le catalogue du shop et la
lecture des ids Hypixel, les améliorations et les pièges (prix, noms, vitesse de la forge, la
base, le menu), les objets spéciaux (la Pop-up Tower et son échelle, le Bridge Egg), la
configuration et la map de démo ont des tests unitaires. Une partie de Ranked Bedwars 1v1 et un
First Rush sur la map de démo sont joués par deux bots sur un vrai serveur : voir
[tools/lobby-check](../../tools/lobby-check/README.md).
