# RagoPractice : les modes d'entraînement

Un plugin Bukkit 1.8.8 qui joue les modes d'entraînement du lobby (`bukkit/rago-lobby`) : les
duels, les entraînements en solo, les arènes ouvertes (block placement, bridge fight) et les
scénarios classés.
Chaque mode prend son kit dans l'éditeur de kit du lobby, rangé comme le joueur l'a laissé.

## Les duels

Deux joueurs (ou deux équipes de deux), par la file ou par `/duel`. Le résultat compte dans les
stats du lobby. Quitter le serveur ou taper `/spawn` pendant le duel, c'est perdre par abandon.
Le nom de chaque joueur prend la couleur de son équipe (rouge ou bleu) au-dessus de sa tête et dans
le Tab, avec sa vie en dessous et son niveau Network à côté.

- **Sumo** : sur les maps sumo du serveur (Ponsen, Fort Royale, White Crystal, Space Mine, Sumo,
  dans le dossier des mondes), tirées au hasard à chaque duel ; sans elles, une plateforme ronde.
  Des coups sans dégâts, le premier qui tombe (ou touche l'eau) donne un point ; premier à 3.
  Chaque manche commence par 3 secondes immobiles, face à face. Les maps sont copiées dans les
  zones du sumo au démarrage, quelques milliers de blocs par tick ; elles ne sont pas dans le dépôt.
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
  coup donné de face compte (depuis le pont, jamais d'un côté ni de derrière). Chaque coup est un
  essai : défenseur tombé, un point pour l'attaquant ; les rôles changent à chaque coup (ou après
  15 secondes sans coup). Le défenseur reste à sa place, coups sans dégâts, pas de blocs. Premier
  à 3.
- **Block placement 1v1 et 2v2**, sur les maps de block placement du propriétaire
  (`block-fight.cartes` dans `config.yml` : chaque camp part de son bloc d'or, en 2v2 côte à
  côte), copiées comme celles du sumo : épée en bois Sharpness I, pioche et hache Efficiency I,
  cisailles, deux stacks de laine, 12 end stone, 16 échelles, armure en fer Protection IV.
  Construire, clutch, casser leurs blocs, faire tomber. Comme sur Minemen, les points sont les
  coups : chaque coup donné à l'autre camp vaut un point (le coup ne fait pas de dégâts, seulement
  son recul), et faire tomber un adversaire dans le vide donne 20 points à son camp
  et relance la manche, chacun sur son bloc d'or, la map comme au départ. Premier à
  `points-pour-gagner.block-fight` (100). Sans ces mondes, deux petites plateformes au-dessus du
  vide.
- **Top fight 2v2** (et 1v1 en partie privée) : une île dont le lit a une défense de trois couches
  (end stone, bois, laine). On commence directement en haut, face à face (en 2v2 le second de
  chaque équipe une marche plus bas derrière), en armure complète en diamant Protection IV, avec
  Absorption II (huit cœurs de plus) à chaque départ, une épée en fer, 64 laines, 5 pommes d'or et
  une perle. Les noms prennent la couleur de l'équipe. Premier à 3.
- **Attack and defense 1v1 et 2v2**, comme un rush en solo sur Hypixel : le défenseur est chez
  lui, à côté de son lit sans défense, l'attaquant déjà sur un pont, à trois blocs de vide de
  l'île. Le défenseur doit couvrir son lit tout de suite et tenir 60 secondes (il revient au bout
  de 3 secondes s'il meurt) ; l'attaquant doit le casser, et s'il meurt, son rush est raté.
  L'attaquant a le stuff des premières minutes (épée en pierre, 48 laine, cisailles, pioche en
  bois), le défenseur une épée en bois et 32 laine. On change de côté à chaque manche ; le plus
  de lits cassés gagne, et à égalité le plus rapide. L'île est l'île rouge de Lotus
  (`attaque-defense` dans la config : le monde, la zone, le lit, le départ du défenseur et le
  pont) ; sans ce monde, une île simple.
- **Fireball fight** : chaque camp a son île et son lit sous une défense de bois et de laine. Le
  stuff des maps du propriétaire : épée en pierre, pioche et hache Efficiency I, 6 boules de feu,
  un stack de laine, 8 end stone, 2 TNT, cisailles, 8 échelles, armure en cuir. Tant que le lit
  tient, on revient au bout de 3 secondes ; ensuite, une mort est définitive. Le dernier camp
  debout gagne. Sur une map de `fireball.cartes` (`config.yml`), chaque camp part de son départ et
  les lits sont ceux de la map (un lit est au camp dont le départ est le plus proche) ; `lits`
  remet ceux qu'une map a perdus. Fournie : la map Fireball Fight du propriétaire (son lit de
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

Un joueur seul. Chaque entraînement a ses variantes (dans la hotbar, le colorant rouge baisse et le vert monte, le mode puis les réglages, le nombre sur le colorant dit où on en est ; la blaze rod recommence),
et chaque variante son record : le meilleur score du joueur et celui du serveur, gardés dans
`plugins/RagoPractice/records.yml`. Le scoreboard montre le top 3 du serveur, le meilleur score
du joueur et son rang, et le dernier score à côté du ping (12 lignes au plus : quand l'essai en
prend beaucoup, la ligne du mode puis la 3e et la 2e place s'effacent avant le reste).

Les entraînements et les arènes libres (block clutch, bridge fight) donnent de l'XP Network :
un peu par minute de jeu, un peu par essai raté, plus par réussite (un temps, une série qui
continue), avec un plafond par minute (`xp-entrainement` dans `config.yml` : 1, 1, 3, 8 au plus).
Elle part au lobby toutes les deux minutes et quand le joueur sort, en un message. Les duels
donnent la leur à la fin ; l'aim trainer garde la sienne.

- **Block clutch** : le joueur est au bord d'une petite île de Bed Wars, son lit et sa défense
  à côté ; un coup le jette dans le vide, il pose sa laine pour se rattraper. L'île est celle du
  propriétaire : l'île en herbe de BlockClutchExtend, avec son lit et sa défense en end stone
  (section `clutch` de `config.yml`), copiée une fois au démarrage. Le joueur est au bord nord,
  face au lit : le coup vient de devant lui et le jette en arrière. Sans le monde, l'île du plugin
  (herbe, lit, défense end stone et laine). Les îles des joueurs sont côte à côte (28 blocs) :
  chacun voit les autres clutcher. La hotbar
  règle le knockback (1 à 5), le nombre de coups à la suite (1 à 3), le KB stick (bien plus loin)
  et un côté au hasard. Chaque réglage a son propre record. Série de clutchs.
- **Block clutch : top fight** : le même clutch sur une plateforme de top fight en laine bleue et
  rouge, à la limite de construction : aucun bloc au-dessus de la plateforme, il faut se
  rattraper à son niveau ou plus bas.
- **Block in** : s'enfermer le plus vite possible (les quatre côtés aux pieds et à la tête, et
  au-dessus), sur le sol, sur une défense de lit ou sur un bloc au-dessus du vide. Chrono.
- **Fast defense** : couvrir son lit le plus vite possible, avec les défenses du propriétaire.
  Ranked : end stone (le dessus du lit, deux rangées de chaque côté, un bloc à chaque bout) puis
  du verre par-dessus, les rangées du bord restant nues (26 blocs). Solo : le lit pointé vers le
  joueur, end stone dessus, à chaque bout et sur chaque côté, les côtés d'un bloc plus longs du
  côté du joueur, puis de la laine sur le tout (32 blocs). Chrono.
- **Bridge** : 31 blocs de vide par technique (god bridge, breezily, ninja, telly, speed bridge),
  dont les règles sont vérifiées. Chrono.
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
- **Ladder practice** : le block-ladder clutch de la map de lavy. Le joueur saute d'une
  plateforme de 4 à 24 blocs et, en tombant, pose une laine sur le pad de pierre et une échelle
  sur son côté, puis tombe dans l'échelle. De haut la chute va vite : l'échelle posée au dernier
  moment arrive au serveur juste après l'atterrissage, alors l'atterrissage est jugé 5 ticks plus
  tard, échelle comprise, au lieu de renvoyer le joueur tout de suite. Série.
- **Pearl practice** : sur un morceau de Sky Rise (l'île rouge, l'île aux diamants en face, l'île
  aux émeraudes du milieu), copié du monde `SkyRise` du dossier des mondes (`pearl` dans
  `config.yml`), sinon des îles simples aux mêmes places. Une perle et un stack de laine par
  essai : vers l'île aux diamants, vers le milieu, ou pour revenir sur son île après un coup qui
  envoie dans le vide. Une perle trop courte se rattrape avec des blocs (pearl block clutch), mais
  aucun bloc ne se pose sous la limite basse (`limite-basse`, y 55), comme sur Hypixel. L'essai
  compte quand le joueur tient debout sur l'île visée. Série.
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
  un record par vitesse. La station est construite par le plugin, la map n'est pas copiée.

## L'arène de block placement

Ouverte à tous : on entre et on sort quand on veut, avec une épée en bois, deux stacks de laine
à sa couleur (qui ne s'épuisent jamais) et des cisailles. Elle
se joue sur l'arène du propriétaire (`block-fight-arena.cartes` dans `config.yml`, le monde
`Arena`) : l'arène verte dans ses murs de barrières, copiée une fois. On arrive n'importe où sur
l'herbe ; le bloc de slime du cube noir, dans le coin, projette celui qui monte dessus 3 blocs en
haut et 2 devant lui (là où il regarde), pour sortir du trou. Le bloc d'or du milieu ne sert que de
repère : il n'est pas copié. Sans ce monde, une grande plateforme et quatre îles au-dessus du vide.
On revient tout de suite après une chute ou une mort. Les blocs posés se fissurent de plus en plus
(les fissures d'un bloc qu'on casse) et se cassent au bout de 15 secondes, avec le bruit et les
éclats d'un bloc cassé. Les kills comptent dans les stats du mode.

**Arena Free** (`free-ffa`) se joue dans la même arène du propriétaire, avec son slime, pour un
FFA de combat pour s'entraîner : une épée en fer, rien d'autre, sans armure, et Résistance V à
chaque vie, si bien que personne ne meurt des coups (seul le vide renvoie au départ).

## Le bridge fight

Un FFA ouvert à tous, comme l'arène de block placement, en stuff de Ranked Bedwars (armure de fer,
épée en pierre, pommes d'or), vrais dégâts, sans poser de bloc. Il se joue sur la map du
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
la boussole emmène sur n'importe laquelle (pas pendant un combat). En arrivant, et après une mort ou
une chute, on apparaît dans la cabine la plus proche de sa plateforme (la petite île blanche en
quartz), sur sa lanterne au milieu du losange de quartz, face à la plateforme ; l'armure prend la
couleur du bout de la plateforme côté cabine. Sans la map, on revient à un bout de la plateforme. Trois secondes sans dégâts au retour, sauf si on
frappe le premier. Les kills comptent dans les stats du mode.

`bridge-fight-1v1` est le vrai 1v1, à part : une seule plateforme (rouge à l'ouest, bleu à l'est)
avec ses carrés et ses ponts, le même stuff avec Protection II sur l'armure
(`bridge-fight.protection-1v1`, 0 pour aucune), aucun bloc. Une mort ou une chute donne le point à
l'autre ; le premier à `points-pour-gagner.bridge-fight` (5) gagne.

## Les scénarios classés

`scenarios-ranked` : les moments qui décident une partie de Ranked Bedwars, rejoués encore et
encore, chacun avec son record.

- **Bed defense 1v2** : seul sur son île, avec une épée en fer, de la laine et l'armure de Ranked
  Bedwars, contre deux rushers qui traversent le pont pour casser le lit (end stone sous de la
  laine). Près du joueur, ils se battent ; sinon, ils creusent la défense depuis l'endroit où ils
  sont, couche par couche (la laine vite, l'end stone plus lentement, puis le lit : environ deux
  secondes), et un coup arrête leur creusage. On peut reboucher le trou avec sa laine. Les tuer ou
  les faire tomber dans le vide avant que le lit tombe : le temps est le score. Lit cassé, mort,
  chute ou 90 secondes sans en finir : l'essai est perdu.
- **Final clutch, 2 hearts** : le dernier combat, à 2 cœurs, sans lit et sans régénération,
  contre un adversaire en pleine vie (aussi rapide qu'un joueur qui marche) sur une île au-dessus
  du vide ; deux coups et c'est fini. Chaque combat gagné, à l'épée ou par le vide, ajoute un à la
  série.

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
le monde, le centre de la plateforme, la boîte copiée), la map du block in, et l'île et le temps de
l'attack and defense.

## Construire et tester

```bash
./gradlew :rago-practice:build
```

Le score des duels, les records, les formes des arènes (les couches d'une défense, le souffle des
boules de feu), les entraînements, les scénarios classés (où et dans quel ordre les rushers
creusent) et la vérification de la configuration ont des tests unitaires. Un duel sumo complet
est joué par deux bots sur un vrai serveur : voir
[tools/lobby-check](../../tools/lobby-check/README.md).
