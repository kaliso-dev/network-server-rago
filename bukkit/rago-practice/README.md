# RagoPractice : les modes d'entraînement

Un plugin Bukkit 1.8.8 qui joue les modes d'entraînement du lobby (`bukkit/rago-lobby`) : les
duels, les entraînements en solo et les arènes ouvertes (block placement, bridge fight).
Chaque mode prend son kit dans l'éditeur de kit du lobby, rangé comme le joueur l'a laissé. Rien ne
s'use dans une partie : les épées, les armures, les cisailles et les arcs ne perdent jamais de
durabilité.

## Les duels

Deux joueurs (ou deux équipes de deux), par la file ou par `/duel`. Le résultat compte dans les
stats du lobby. Quitter le serveur ou taper `/spawn` pendant le duel, c'est perdre par abandon.
Le nom de chaque joueur prend la couleur de son équipe (rouge ou bleu) au-dessus de sa tête et dans
le Tab, avec sa vie en dessous et son niveau Network à côté. À la fin d'un duel gagné, tout le
monde reste 3 secondes dans l'arène pour voir le gagnant et son effet de victoire, avec le papier
**Play again** et le lit **Leave** qui marchent tout de suite (`fin-de-partie-secondes` du lobby).
Comme les « match details »
des serveurs practice, chacun reçoit le **récap du duel** : pour chaque joueur, ses coups qui sont
passés (pas ceux du délai entre deux coups) et son meilleur combo, les coups d'affilée sans en
prendre un (« Ann: 34 hits, best combo 7 »), puis une seconde ligne avec ce que le duel a eu : la
vie qui lui restait (dès qu'un coup ou une chute a fait mal), les pommes d'or mangées, les blocs
posés et les lits cassés, chacun quand quelqu'un l'a fait (« 7.5❤ left, 2 golden apples »).
**[View]** au bout de la ligne d'un joueur montre ce qu'il avait à la fin (ou quand il est sorti de
la manche) : son inventaire, la barre en bas comme dans le sien, son armure, sa vie, ses effets et
ses chiffres du récap, dans un coffre où rien ne bouge (`/matchinv`, gardé 10 minutes). Au sumo
aussi (sans [View]) ; pas dans les events.
Ceux qui regardent un duel ou un entraînement (`/spectate`, voir le
[README du lobby](../rago-lobby/README.md)) lisent ses messages et son récap, voient le sidebar du
joueur qu'ils sont venus voir, ne s'éloignent pas de l'arène et reviennent au lobby à la fin.
Chaque duel, sumo compris, est enregistré : `/replay` le montre à nouveau depuis le lobby, avec
**[REPLAY]** dans la ligne d'après-partie (voir le [README du lobby](../rago-lobby/README.md)).

- **Sumo** : sur les maps sumo du serveur (Ponsen, Fort Royale, White Crystal, Space Mine, Sumo,
  dans le dossier des mondes), tirées au hasard à chaque duel ; sans elles, une plateforme ronde.
  Des coups sans dégâts, en une seule manche : le premier qui tombe (ou touche l'eau) perd
  (`sumo.points-pour-gagner` pour en jouer plus). La manche commence par 3 secondes immobiles, face
  à face.
  Les maps sont copiées dans les zones du sumo au démarrage, quelques milliers de blocs par tick ;
  elles ne sont pas dans le dépôt.
- **Boxing**, comme sur Minemen : sur les arènes Minemen de 2022 du propriétaire (Cobble, Shy Guy,
  Redstone, Cave, Fishtank, Goku, Lollipop, Pokemon : les mondes `Minemen …` du dossier des
  mondes, `boxing.cartes`), tirées au hasard, chacun sur son bloc de diamant ; sans elles, un ring
  fermé. Speed II, une épée sans dégâts ; le premier à 100 coups gagne. Le scoreboard compte les
  coups et le combo. `/speed` enlève la Speed au joueur qui la tape, ou la lui rend (tout de
  suite en partie, sinon pour ses prochains boxings, jusqu'au redémarrage). Avec un nombre, c'est
  toujours la vitesse de vol des builders (`/vitesse` de RagoBuildTools). Les arènes sont copiées
  comme les maps du sumo et ne sont pas dans le dépôt.
- **KB displace**, comme un vrai top fight de Bed Fight, sur la map KbDisplace du propriétaire
  (section `kb-displace` de `config.yml`, copiée une fois au démarrage) : son pont d'un bloc de
  large, rien sur les côtés : l'attaquant part du bloc d'or, le défenseur se tient dans le creux du
  pont, entre ses deux blocs surélevés, 9 blocs devant lui.
  Sans le monde, le pont du plugin : le défenseur en haut de la défense, un bloc plus bas que le
  pont, le pont devant lui et un bloc derrière. L'attaquant court sur le pont et, dans le tick du coup, tourne la
  tête d'un côté : le knockback du sprint envoie le défenseur sur le côté, dans le vide. Seul un
  coup donné de face compte (depuis le pont, jamais d'un côté ni de derrière), sprint ou pas. Des
  murs invisibles (barrières) bordent le pont devant le défenseur : l'attaquant reste au milieu du
  pont et ne peut pas aller frapper sur le côté. Contre le bot, le réglage « Bot KB » de la hotbar
  dit quelle part du knockback il prend (60 à 100 %, 80 % par défaut) : un zombie part plus loin
  qu'un joueur qui tient sa place. Chaque coup est un
  essai : défenseur tombé, un point pour l'attaquant ; les rôles changent à chaque coup (ou après
  15 secondes sans coup). Le défenseur qui retombe sur le pont a tenu, dès que le recul est fini
  (une demi-seconde après le coup). Le défenseur reste à sa place, coups sans dégâts, pas de blocs. Premier
  à 3.
- **Block placement 1v1 et 2v2**, sur les maps de block placement du propriétaire
  (`block-fight.cartes` dans `config.yml` : chaque camp part de son bloc d'or, en 2v2 côte à
  côte), copiées comme celles du sumo : épée en bois Sharpness I, pioche et hache Efficiency I,
  cisailles, deux stacks de laine, 12 end stone, 16 échelles, armure en fer Protection IV.
  Construire, clutch, casser leurs blocs, faire tomber. Comme sur Minemen, les points sont les
  coups : chaque coup donné à l'autre camp vaut un point (le coup ne fait pas de dégâts, seulement
  son recul), et faire tomber un adversaire dans le vide donne 10 points à son camp ; seul celui
  qui est tombé revient tout de suite sur son bloc d'or, avec tout son kit à nouveau (deux stacks
  de laine), les autres continuent et les blocs restent. Premier à
  `points-pour-gagner.block-fight` (100). Les cœurs disent le score : un joueur a toute sa vie
  tant que l'autre camp n'a aucun point, et plus rien quand il en a 100 (20 PV à 0 coup, 0 PV à
  100 coups, une demi-vie au moins : le score ne tue pas) ; la vie ne remonte pas toute seule.
  Sans ces mondes, deux petites plateformes au-dessus du vide.
- **Fireball fight** : chaque camp a son île et son lit sous une défense de bois et de laine. Le
  stuff des maps du propriétaire : épée en pierre, pioche et hache Efficiency I, 6 boules de feu,
  un stack de laine, 8 end stone, 2 TNT, cisailles, 8 échelles, armure en cuir. Tant que le lit
  tient, on revient au bout de 3 secondes ; ensuite, une mort est définitive. Le dernier camp
  debout gagne. Le bois et l'end stone qu'on casse tombent par terre, comme un vrai bloc. Une
  boule de feu qui explose près d'une TNT allumée l'écarte de deux blocs environ. Les boules de feu et la
  TNT poussent (la TNT comme celle du jeu, sans ses dégâts) et
  cassent les blocs posés et la défense des lits (l'end stone seulement à la TNT), sans faire de
  dégâts ; pas de dégâts de chute non plus. Sur une map de `fireball.cartes` (`config.yml`), chaque
  camp part de son départ et les lits sont ceux de la map (un lit est au camp dont le départ est le
  plus proche) ; la défense que la map a sur chaque lit (bois, end stone, laine... collés au lit, à
  4 blocs au plus) se casse à la main et saute comme si des joueurs l'avaient posée, et revient à
  chaque partie ; `lits` remet ceux qu'une map a perdus. Fournie : la map Fireball Fight du propriétaire (son lit de
  l'île ouest, perdu, revient dans sa défense d'end stone).
- **Bow duel** (`bow-duel`) : un 1v1 à l'arc sur la place du bow training (la base rouge et le
  nord du milieu d'Archway) : rouge sur le devant de la base, bleu au bord du milieu, face à face.
  Un arc (Infinity) et de la laine ; chaque flèche qui touche l'autre est un point (son
  knockback, pas de dégâts), une chute dans le vide donne un point à l'autre et relance la manche.
  Épées et poings ne font rien. Premier à 10 (`points-pour-gagner.bow`).
- **Block edit 1v1** : la station du block edit avec une pièce de chaque côté du mur de laine.
  Casser un bloc du mur, frapper l'autre par le trou, et remettre un bloc dans le mur avant qu'il
  ne frappe : un point. S'il frappe en retour d'abord, le point passe de son côté. Coups sans
  dégâts, que le knockback. Premier à 5 (`points-pour-gagner.block-edit`).

## Les entraînements

Un joueur seul. La fin de la hotbar garde les commandes, côte à côte, le kit prend les cases
d'avant : la poudre verte **Next level** passe au niveau suivant et la poudre rouge **Previous
level** à celui d'avant (seulement quand l'entraînement en a plusieurs ; après le dernier, le
premier, et avant le premier, le dernier), la boussole **Trainings** ouvre le menu Practice du lobby (un clic sur un autre entraînement y
emmène directement, sans repasser par le lobby), le comparateur **Settings** ouvre les réglages
(seulement quand l'entraînement en a), et la blaze rod **Restart** recommence (seulement là où
elle sert : pas dans block extend, block clutch, block edit, combo bot, extend sauf la chaîne,
fireball, ladder et pearl, où une chute ou un raté relance l'essai tout seul). Pas de lit :
`/spawn` ramène au lobby.

Le joueur range son inventaire comme il veut, une fois : en le fermant, la place des objets du kit
est gardée par le lobby pour cet entraînement, et celle des commandes (poudres, boussole,
comparateur, blaze rod) dans `plugins/RagoPractice/reglages.yml` (`_barre`), pour tous les
entraînements (« Hotbar saved »). Un objet du kit qui tombe sur la case d'une commande va dans la
première case libre. L'entraînement attend tant qu'un menu est ouvert (les réglages, les
entraînements de la boussole) ou que le joueur range son inventaire (un clic dedans, jusqu'à ce
qu'il le ferme, 15 secondes au plus) : aucun coup, aucun nouvel essai qui redonnerait le kit. Si une
chute commencée avant recommence l'essai, les réglages se rouvrent aussitôt.
Le menu des réglages a une ligne pour le mode (chaque variante, avec le meilleur score du joueur et
le défi du jour), puis une ligne par réglage : − et + autour de sa valeur (avec sa valeur par
défaut), ou un clic pour un réglage oui/non. En bas, **Reset to default**, les préréglages de
l'entraînement s'il en a, et **Close**. L'essai recommence avec les nouveaux réglages quand le menu
se ferme. Le block clutch a trois préréglages : **Hypixel** (un coup d'un combat, de face : KB 3,
1 coup), **RBW** (des coups en sprint, de n'importe quel côté : KB 5, 2 coups, côté aléatoire) et
**Hard** (KB 5, 3 coups, KB stick, côté aléatoire).

Pendant un entraînement, le joueur garde sa place dans les files qu'il attendait au lobby, et peut
en rejoindre depuis la boussole **Trainings** (la flèche ramène aux catégories) ou `/queue` : le
scoreboard montre l'attente sur l'avant-dernière ligne, et la partie trouvée le sort de
l'entraînement directement.

Chaque entraînement a ses variantes, et chaque variante son record : le meilleur score du joueur et celui du serveur, gardés dans
`plugins/RagoPractice/records.yml`. Le premier score et chaque nouveau record font avancer
« Getting started » dans le lobby (« Play a training », « Beat one of your records »), et les
quêtes du lobby comptent les réussites, les records, les scores au défi du jour et les minutes
d'entraînement (une minute où le joueur a cliqué ou posé un bloc). Le scoreboard montre le top 3 du serveur, une ligne vide, le
meilleur score du joueur et son rang, et le dernier score à côté du ping (12 lignes au plus :
quand l'essai en prend beaucoup, la ligne du mode, puis la 3e et la 2e place, puis la ligne vide
sous le top s'effacent avant le reste). L'extend practice et le block extend n'ont pas de
classement dans le scoreboard, seulement l'essai et le ping. Après une réussite, on repart en
moins d'une seconde, et au block clutch comme au block extend le coup arrive moins d'une seconde
après le retour au départ (au block clutch, après une chute, un peu plus tard : 0,75 à 1,3 seconde,
le temps de se replacer). Au block clutch, Random side est désactivé par défaut. Chaque joueur retrouve, dans chaque entraînement, la variante et les
réglages qu'il avait la dernière fois (gardés dans `plugins/RagoPractice/reglages.yml`, sauf la
variante quand c'est le défi du jour qui la choisit) ; **Reset to default** (ou shift et un clic
droit sur la blaze rod, quand il y en a une) remet les réglages par défaut.

Les entraînements et les arènes libres (block clutch, bridge fight) donnent de l'XP Network :
un peu par minute de jeu, un peu par essai raté, plus par réussite (un temps, une série qui
continue), avec un plafond par minute (`xp-entrainement` dans `config.yml` : 1, 1, 3, 8 au plus).
Elle part au lobby toutes les deux minutes et quand le joueur sort, en un message. Les duels
donnent la leur à la fin ; l'aim trainer garde la sienne.

**Les records (`/records`).** `/records` ouvre tous les entraînements du serveur, avec le meilleur
score du joueur et sa place sur chaque record (une variante, une vitesse du bot du block edit, ou
un parcours du timed bridge),
et le résumé : combien de records, de premières places et de places dans un top 10. Un
entraînement ouvre ses records, chacun avec son top 10, la place du joueur (sous le top quand il
est plus loin) et sa place parmi ses amis (`/friend`). Un clic sur un record le joue : dans cet
entraînement, il y passe et recommence ; depuis le lobby ou un autre entraînement, le lobby y
emmène et l'entraînement commence dessus. `/records <joueur>` montre ceux d'un autre.

**Le mur des records.** Au lobby, sur le point `records` (`/lobby point records`,
`mur-des-records` dans `config.yml`, `""` pour aucun), un tableau comme ceux du lobby montre le
top 10 d'un record après l'autre (ceux qui ont au moins un score), toutes les 8 secondes, avec la
place de celui qui regarde et le défi du jour. Un clic gauche passe au record suivant (gardé
20 secondes, le temps de le lire), un clic droit ouvre `/records`.

**Le défi du jour (`/challenge`).** Chaque jour (heure de Paris), un entraînement pour tout le
serveur, le suivant de `defi-du-jour.epreuves` à tour de rôle (le même toute la journée, même après
un redémarrage) : Block-In côté bois, island clutch, aim trainer, fast defense, ladder, combo bot
difficile, extend en chaîne... Le meilleur score de chaque joueur du jour fait le classement du
jour ; à minuit, le 1er, le 2e et le 3e gagnent 750, 500 et 300 XP Network (`xp-podium`), annoncés à
tout le serveur (même si le serveur était éteint à minuit). Le premier score du jour d'un joueur
lui donne 100 XP, plus 25 par jour d'affilée où il a joué le défi (7 jours au plus : 250). À
l'arrivée, un joueur qui ne l'a pas encore joué aujourd'hui reçoit le défi du jour avec un
**[PLAY]**, entre deux lignes dorées pour qu'on le voie (comme le nouveau défi à minuit et le podium) ; `/challenge` montre l'entraînement, le top 5 du jour, sa place, sa série de jours et
ce qui se gagne ; `/challenge play` (ou le bouton) lance l'entraînement directement sur la bonne
variante. Le défi est aussi au bout de « Popular now », la ligne du haut de la boussole du lobby et
du menu Trainings : un clic le lance. Depuis un autre entraînement, `/challenge play` y emmène tout
droit ; dans l'entraînement du défi, il passe à sa variante et recommence. Pendant le défi, le
scoreboard de l'entraînement porte « ✦ Daily ». Gardé dans
`plugins/RagoPractice/defi.yml`. `actif: false` le coupe ; sans la section, il tourne avec ces
réglages. Un entraînement de la liste que le serveur ne joue pas est laissé de côté.

- **Block clutch** : le joueur est au bord d'une petite île de Bed Wars, son lit et sa défense
  à côté ; un coup le jette dans le vide, il pose sa laine pour se rattraper. L'île est celle du
  propriétaire : l'île seule de sa map BlockClutchNew, avec son lit et sa défense en end stone
  (section `clutch` de `config.yml`), copiée une fois au démarrage. Le joueur est au bord sud,
  face au lit : le coup vient de devant lui et le jette en arrière. Sans le monde, l'île du plugin
  (herbe, lit, défense end stone et laine). Les îles des joueurs sont côte à côte (28 blocs) :
  chacun voit les autres clutcher. La hotbar
  règle le knockback (1 à 5), le nombre de coups à la suite (1 à 3), le KB stick (bien plus loin)
  et un côté au hasard. Série de clutchs (un seul record pour tous les réglages). Chaque clutch
  réussi a une taille : la distance en blocs à laquelle le coup a envoyé le joueur de sa place
  avant qu'il se rattrape (« Clutch! Streak: 3 | 4.3 blocks »). Le top 3 du scoreboard et, au-dessus
  de chaque île devant le joueur, le tableau « BIGGEST CLUTCHES » (les dix premiers) montrent les
  plus gros clutchs de la semaine, le meilleur de chaque joueur, les mêmes pour tout le monde et mis
  à jour chez tous dès qu'un joueur bat son record. Ils repartent à zéro chaque lundi à minuit
  (heure de Paris) : le top 3 de la semaine finie gagne alors les Rago Points et la couronne des
  classements de la semaine du lobby (« Biggest clutches », et « Biggest top fight clutches » pour
  le top fight), et la semaine d'avant reste gardée jusque-là.
- **Block clutch : top fight** : le même clutch sur une plateforme de top fight en laine bleue et
  rouge, à la limite de construction : aucun bloc au-dessus de la plateforme, il faut se
  rattraper à son niveau ou plus bas.
- **Block in** : tomber sur la défense d'un lit, s'enfermer, puis creuser jusqu'au lit. Chrono dès
  le premier bloc. Le temps ne compte pour le classement que si le joueur s'est enfermé : une fois,
  les quatre côtés à ses pieds et à sa tête fermés, dont au moins un bloc à lui (sinon casser le lit
  au plus vite, sans bloc, serait premier). Sans ça, « Not counted ».
- **Fast defense** : couvrir son lit le plus vite possible, avec les défenses du propriétaire. La
  défense à construire se montre 3 secondes au premier essai de chaque défense ; les essais
  suivants partent tout de suite, le chrono au premier bloc.
  Ranked : end stone (le dessus du lit, deux rangées de chaque côté, un bloc à chaque bout) puis
  du verre par-dessus, les rangées du bord restant nues (26 blocs). Duo : celle de la map « Defense
  Claude » du propriétaire, le lit pointé vers le joueur, end stone autour du lit et dessus, avec
  un bloc de plus derrière la tête, puis de la laine sur le tout (35 blocs ; c'est aussi la défense
  du Clutch Scenario). Chrono.
- **Block extend**, comme dans un vrai bed fight, sur la map BlockClutchExtend du propriétaire
  (`block-extend` dans `config.yml`) : le joueur part d'une place exacte d'un pont, figé jusqu'au
  coup (il ne peut ni bouger ni tourner : il doit se retourner, comme en vrai, et le coup vient
  toujours du même axe), puis se sauve avec sa laine bleue. Trois niveaux : Or, un gros coup de
  face, plus haut et plus fort (0.80, 0.55), droit en arrière, il clutch ; Diamant, un coup en
  sprint (0.80, 0.43) par la droite, il se retourne et extend jusqu'aux blocs de diamant ;
  Émeraude, depuis le bloc d'émeraude au bout du pont, un coup de face long et bas (0.85, 0.12),
  il clutch sur les blocs d'émeraude plus bas. Les blocs d'or, de diamant et
  d'émeraude de la map sont de la laine dans le jeu. Chaque niveau donne sa place de départ (F3),
  le bloc où se sauver, la force du coup et d'où il vient (`coup` : devant, droite, gauche,
  derriere, ou `vers` un point). Le kit a des échelles avec la laine (un réglage les retire). Série.
- **Extend practice** : les sauts du Clutch Training de Flarefin, un vide trop long pour sauter
  entre deux pads de quartz : on court, on saute et on extend sa laine en l'air (double extend
  5+1 et 6+0 avec 2 blocs au plus, triple extend 6+1 et 7+0, sauts en descente 7-1 et 7-2 avec
  3). Mode **Chain** : dix pads à la suite avec tous les sauts entre eux, sans arrêt ni
  téléportation d'un saut à l'autre, au chrono. Des échelles viennent avec la laine (un réglage
  les retire) ; elles ne comptent pas dans les blocs du saut, une échelle sur le côté du pad
  rattrape un saut trop court.
- **Timed bridge** (`bridge-chrono`) : bridger d'un pad blanc jusqu'au pad vert, au chrono, avec
  n'importe quelle technique (speed bridge, ninja, breezily, god bridge, telly...) : 16, 32 ou 64
  blocs de vide, en ligne droite ou en diagonale (de coin à coin, la même distance), à plat ou en
  montée (le pad vert plus haut d'un quart de la distance : 4, 8 ou 16 blocs). Le chrono part au
  premier bloc posé et s'arrête au premier pas sur le pad vert (sa ligne d'or) ; les blocs ne se
  posent qu'entre les deux pads, et un essai a 3 secondes par bloc de distance au plus. La fin dit
  le temps, les blocs posés et les blocs par seconde. Laine et sandstone, sans fin. La distance se
  choisit dans la ligne du mode des réglages, la diagonale et la montée sont deux réglages oui/non.
  Les douze parcours sont côte à côte dans l'arène, chacun avec son record (`/records` les montre
  en deux rangées : à plat, puis en montée). Pour en faire un défi du jour : `bridge-chrono.16`,
  `.32` ou `.64` (droit et à plat) dans `defi-du-jour.epreuves`.
- **Ladder practice** : le block-ladder clutch de la map de lavy. Le joueur saute d'une
  plateforme de 4 à 24 blocs et, en tombant, pose une laine sur le pad de pierre et une échelle
  sur son côté, puis tombe dans l'échelle. De haut la chute va vite : l'échelle posée au dernier
  moment arrive au serveur juste après l'atterrissage, alors l'atterrissage est jugé 5 ticks plus
  tard, échelle comprise, au lieu de renvoyer le joueur tout de suite. Série.
- **Pearl practice** : en boucle sur les maps de Bed Wars, rien à choisir : chaque essai sur la
  map suivante de `pearl.cartes` (`config.yml`) que le serveur a (son monde dans le dossier des
  mondes, avec le `bedwars.yml` du plugin Bed Wars), à un autre endroit, dans une autre direction.
  De chaque map, le côté d'une équipe tirée à chaque démarrage est copié une fois : sa base, ses
  îles aux diamants et le milieu. Les situations viennent de la forme des îles (`PearlMaps`) :
  une perle d'une île à l'autre (6 à 45 blocs de vide), un coup qui envoie dans le vide puis une
  perle pour revenir, et surtout le pearl block clutch : sur un bloc de pont à 10-14 blocs d'une
  île et 3-5 blocs sous son sol, un coup envoie le joueur dans le vide, il lance sa perle contre le
  flanc de l'île et pose aussitôt des blocs sous lui contre le mur. La boucle alterne perle,
  clutch, save, clutch ; les niveaux Pearls, Saves et Pearl clutches ne jouent qu'un genre. Une
  perle et un stack de laine par essai ; les blocs seulement après la perle, et jamais plus de
  16 blocs sous l'île la plus basse, comme sur Hypixel. L'essai compte quand le joueur tient
  debout sur l'île visée (ou sur ses blocs contre elle). Série. La zone garde une map à la fois :
  à chaque changement, ses blocs sont écrits directement dans les chunks (`FastBlocks`, une dizaine
  de ms au lieu de 2 à 3 s de lag), éclairés à fond, sans coffre rempli ni panneau écrit. Sans
  aucune des maps, des îles simples du plugin.
- **Fireball practice** : le saut à la boule de feu sur une falaise de 4 blocs, au-dessus de 10
  blocs de vide, ou renvoyer les boules d'un canon. Série.
- **Bow training** (`bow-training`) : sur une vraie map de Bed Wars, la base rouge et le nord du
  milieu d'Archway copiés une fois (`bow-training` dans `config.yml` : le monde, la zone, le départ
  du joueur et les ponts des bots). Le joueur est sur le devant de sa base avec un arc (Infinity,
  Power I en réglage) ; des bots bridgent du milieu vers sa base, une laine à chaque pas, comme un
  rusher. Une flèche pousse le bot à l'opposé du tireur : il faut le prendre de côté pour le faire
  tomber de son pont, c'est un kill, et un nouveau bot repart sur ce pont reconstruit à neuf. Un bot
  qui arrive à la base termine l'essai ; le score est le nombre de kills. La barre de côté montre
  les touches, la précision et le tir le plus long. Réglages : la vitesse des bots (2,6 à 5 blocs
  par seconde) et combien viennent à la fois (1 à 3). Sans le monde, des plateformes aux mêmes
  endroits.
- **Aim trainer** : une cible de la taille d'un joueur qui strafe, saute ou tourne autour ; 30
  secondes, les coups qui touchent et la précision. Chaque seconde où le viseur reste sur la
  cible (12 ticks sur 20) ou la touche donne de l'XP, avec un son qui monte avec la série (et un
  carillon toutes les 5 secondes d'affilée) ; une seconde à côté ne donne rien. Le niveau de la
  barre d'XP montre l'XP de l'essai, la barre le temps qui reste. À la fin, le joueur reçoit
  cette XP en XP Network, plus 1 pour 10 coups (`aim-trainer` dans `config.yml`), et voit le top 5
  de la variante. `/aimtop [strafe|jump|circle]` montre le top de partout.
- **Combo bot** : une IA qui se bat comme un joueur 1.8 (`ComboAi`), sans dégâts ; le plus long
  combo. Elle sprinte vers le joueur, strafe une fois proche, clique à son CPS avec sa précision et
  sa reach (mesurée jusqu'à la hitbox), et ses coups poussent comme ceux d'un joueur (un coup en
  sprint pousse plus loin). Après un coup elle peut w-tap, touchée au sol elle peut jump reset, et
  elle vise là où le joueur était il y a quelques ticks (son temps de réaction). Quatre niveaux :
  Easy (5 CPS, 2,7 de reach, pas de sprint), Normal (8 CPS, 2,9), Hard (11 CPS, 3,0), Expert
  (14 CPS, 3,0, w-tap et jump reset presque à chaque fois). Le réglage « Bot hits back » le rend
  passif, pour travailler un combo seul. Le zombie ne marche pas lui-même (lenteur) : le jeu le
  déplace chaque tick avec la physique d'un joueur. Le scoreboard compte aussi les coups de chacun
  et le plus long combo du bot. Sur les arènes du boxing : le joueur sur le premier bloc de
  diamant, le bot 8 blocs devant lui.
- **KB displace contre un bot** : le même pont (celui de la map KbDisplace quand le serveur l'a),
  le joueur sur le bloc d'or, le bot à la place du défenseur, dans le creux. Après chaque coup
  on revient tout de suite au départ, pour reprendre de l'élan, jusqu'à ce que le bot tombe ; seul
  un coup de face compte. Le score est le temps, et le scoreboard compte les essais.
- **Block edit**, comme sur la map Block Edit Practice du propriétaire : une cage de verre, un mur
  de laine rouge de 5 sur 3, et derrière, un bot dans une petite pièce en bois. Casser un bloc du
  mur, frapper le bot par le trou, remettre le bloc avant qu'il ne frappe. Le bot frappe comme un
  joueur, à 3 blocs, une fois qu'il a vu le trou pendant son temps de réaction ; la hotbar règle
  sa vitesse (500, 350 ou 200 ms). Chaque edit propre ajoute à la série, un coup du bot la finit ;
  un record par vitesse. Le défi du jour du block edit se joue à la vitesse par défaut (350 ms) :
  [PLAY] la donne, et une autre vitesse ne compte pas pour le défi. La station est construite par
  le plugin, la map n'est pas copiée.

## L'arène de block placement

Ouverte à tous : on entre et on sort quand on veut, avec une épée en bois, deux stacks de laine
à sa couleur (qui ne s'épuisent jamais) et des cisailles, et Résistance V à chaque vie : les
coups font reculer mais n'enlèvent pas de vie, on gagne en faisant tomber les autres dans le vide. Elle
se joue sur l'arène du propriétaire (`block-fight-arena.cartes` dans `config.yml`, le monde
`Arena`) : l'arène verte dans ses murs de barrières, copiée une fois. On arrive n'importe où sur
l'herbe ; le bloc de slime du cube noir, dans le coin, projette celui qui monte dessus 3 blocs en
haut et 2 devant lui (là où il regarde), pour sortir du trou. Le bloc d'or du milieu ne sert que de
repère : il n'est pas copié. Sans ce monde, une grande plateforme et quatre îles au-dessus du vide.
On revient tout de suite après une chute. Les blocs posés se fissurent de plus en plus
(les fissures d'un bloc qu'on casse) et se cassent au bout de 15 secondes, avec le bruit et les
éclats d'un bloc cassé. Les kills comptent dans les stats du mode.

**Arena Free** (`free-ffa`) se joue dans la même arène du propriétaire, avec son slime, pour un
FFA de combat pour s'entraîner : une épée en fer, rien d'autre, sans armure, et Résistance V à
chaque vie, si bien que personne ne meurt des coups (seul le vide renvoie au départ). `/center`
ramène au centre de l'arène (sur le sol le plus près du milieu où l'on tient debout), sauf dans
les 5 secondes qui suivent un coup : ce n'est pas une façon d'échapper à une chute.

## Le bridge fight

Un FFA ouvert à tous, comme l'arène de block placement, en stuff de Ranked Bedwars (armure de fer
Protection II, épée en bois, pommes d'or : les coups de la plateforme du milieu font moins mal),
sans poser de bloc. Les coups ne font que repousser (Résistance V,
aucun dégât : on fait tomber les autres dans le vide), sauf sur la **plateforme du milieu**, celle
dont le milieu est le plus près du `centre` de l'arène (sur la map, le H rouge et jaune au sud de la
cabine du milieu), où les coups font de vrais dégâts ; un kill sur cette plateforme remet le tueur
à fond de vie. En changeant de plateforme, un message dit
laquelle des deux règles s'applique. Sans la map, pas de plateforme du milieu : Résistance partout. Il se joue sur la map du
propriétaire (`bridge-fight.cartes` dans `config.yml`, le monde `Bridge Fight Plateform`) : son
étage du bas, copié une fois, avec ses cinq plateformes (deux bleu et jaune, deux noir et vert, le
H rouge et jaune) et les trois portails entre elles. Les plateformes sont ses sols de laine ou
d'argile à la hauteur du `centre` ; un portail, c'est la cabine autour d'une lanterne aquatique
posée au sol (7 sur 7, 9 de haut) : ce sont les trois îlots de l'arène, en ligne au centre. Un
clic sur un portail (clic droit en le visant, ou clic gauche sur un de ses blocs) fait juste
passer d'un îlot à l'autre, du côté où l'on regarde, sur la lanterne de l'îlot suivant : de celui
du milieu à celui de gauche ou de droite, et de ceux-ci au milieu. Pas de portail pendant un
combat. Les barrières de la map ne
sont pas copiées : celui qui tombe tombe dans le vide. Sans ce monde, quatre plateformes de laine
côte à côte au-dessus du vide (bleu et jaune, rouge et blanc, orange et noir, vert et vert clair) :
un pont relie chaque bout de plateforme à un petit carré, et une passerelle le long de chaque bout
relie les carrés, donc on passe d'une plateforme à l'autre à pied.

La plateforme d'un joueur suit l'endroit où il est (sauf dans une cabine, entre deux plateformes) ;
la boussole emmène sur n'importe laquelle (pas pendant un combat). En arrivant, on apparaît dans la
cabine la plus proche de sa plateforme (la petite île blanche en quartz), sur sa lanterne au milieu
du losange de quartz, face à la plateforme ; l'armure prend la couleur du bout de la plateforme côté
cabine. Après une mort ou une chute, on revient tout de suite dans la cabine de la plateforme où
l'on était, sur sa lanterne (sans la map : sur la plateforme, au bout le plus éloigné des autres
joueurs). Les cabines sont les spawns : on n'y frappe personne, et on ne frappe pas depuis une
cabine. Trois secondes sans dégâts ni recul au retour, sauf si on frappe le premier. Les kills comptent dans les stats du mode. La sidebar montre le **top 3 des
kills** de la partie en cours (les joueurs présents), et un **classement des kills** flotte
au-dessus de l'îlot du milieu, 12,5 blocs au-dessus de son sol, au-dessus du toit de sa cabine, vu
de toutes les plateformes : « TOP KILLS », puis les 10 joueurs qui ont le plus de kills à vie dans
le mode (les stats du lobby, relues toutes les 5 secondes).

`bridge-fight-1v1` est le vrai 1v1, à part : une seule plateforme (rouge à l'ouest, bleu à l'est)
avec ses carrés et ses ponts, une épée en bois, des pommes d'or et l'armure en fer avec Protection
II (`bridge-fight.protection-1v1`, 0 pour aucune), le tout incassable, aucun bloc. Une mort ou une chute donne le point à
l'autre (un son de victoire pour qui gagne la manche, un autre pour qui la perd) ; le premier à `points-pour-gagner.bridge-fight` (5) gagne.
`bridge-fight-2v2` est le même en 2v2 (avec la même épée en bois) : chaque équipe côte à côte sur son carré, le point quand
toute l'équipe d'en face est tombée ou morte.

## Les events (/host)

Comme sur Minemen, un joueur PREMIUM (`rago.perk.host`) lance un event pour tout le serveur avec
`/host` : un menu avec Sumo, Brackets (un tournoi en 1v1 avec épée et armure) et Last Man
Standing (tout le monde en même temps, le dernier debout gagne), ou directement
`/host sumo|brackets|lms`. Tout le serveur hors partie est prévenu avec un **[Click to join]**
(ou `/join`), puis encore à 45, 30, 15 et 5 secondes. Pendant l'attente, les joueurs sont sur la
plateforme sans kit et sans coups ; ensuite, au Sumo et aux Brackets, un 1v1 à la fois pendant
que les autres regardent, par manches (un joueur sans adversaire passe), 90 secondes au plus par
combat (celui qui a touché le plus passe). Le gagnant est annoncé à tout le serveur et gagne
`xp-gagnant` XP Network, chaque autre joueur `xp-participation`. Un event à la fois, une pause
entre deux, et un délai pour chaque hôte (le staff n'attend pas). `/event` dit où en est l'event,
`/event leave` le quitte (comme `/spawn`), `/event cancel` l'annule (son hôte ou le staff).
Les events se jouent sur les maps du sumo, dans les modes cachés `event-sumo`, `event-brackets` et
`event-lms` du lobby. Tout se règle dans `evenements:`.

**Les events automatiques.** Quand personne n'en lance, le serveur en lance lui-même, au nom de
« Rago » : dès qu'il y a `joueurs-lobby` joueurs hors partie (6 par défaut) et qu'aucun event n'a
eu lieu depuis `minutes` minutes (20), il annonce le suivant de `types` (sumo, brackets, lms, à
tour de rôle) avec son **[Click to join]**. Le premier qui le rejoint le lance, et il se joue
comme un event lancé par un joueur ; sans personne pendant l'attente, l'annonce tombe. Un joueur
PREMIUM peut toujours lancer le sien. Dans `evenements.automatiques` (`actif: false` les coupe ;
sans la section, ils tournent avec ces réglages).

## Les arènes

Les parties se jouent dans un monde vide (`arenes`, créé s'il n'existe pas). Chaque mode a sa
rangée, chaque partie sa zone, à `espacement` blocs (200) de la suivante : deux parties ne se
voient jamais. Une zone est construite la première fois qu'elle sert, puis réutilisée ; chaque
manche la remet comme elle était construite. Au-delà de `arenes-max` parties à la fois, les
joueurs gardent leur place dans la file. Un nouveau mode prend la rangée après les autres, pour
que les zones déjà construites ne bougent pas.

## Configuration

`plugins/RagoPractice/config.yml` : le monde des arènes, l'espacement, le nombre maximal de
parties, les points pour gagner de chaque duel, le rayon du sumo et ses maps (`sumo.cartes` :
le monde, le centre de la plateforme, la boîte copiée), la map du block in et le point du mur des
records (`mur-des-records`). L'attack and defense
est dans le plugin bedwars, sur les maps du propriétaire (Bio Hazard).

## Construire et tester

```bash
./gradlew :rago-practice:build
```

Le score des duels, les records, les formes des arènes (les couches d'une défense, le souffle des
boules de feu), les entraînements et la vérification de la configuration ont des tests
unitaires. Un duel sumo complet
est joué par deux bots sur un vrai serveur : voir
[tools/lobby-check](../../tools/lobby-check/README.md).
