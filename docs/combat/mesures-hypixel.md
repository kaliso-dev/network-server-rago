# Mesures du knockback d'Hypixel

Ce document garde les mesures dont vient le profil `hypixel-approx-v1` et le réglage
`hit-delay-ticks: 9` du moteur. Une nouvelle mesure ajoute une section et, si elle change une
valeur, un nouveau profil (`hypixel-approx-v2`) : on ne modifie pas un profil déjà mesuré.

## Mesure 1 : 7 novembre 2026, Housing, 1v1

### Conditions

- Deux joueurs, qRago et godemy, se battent à l'épée, chacun avec le mod kb-recorder 0.1.0
  (tools/kb-recorder). Les deux enregistrent en même temps : chaque coup est vu des deux côtés.
- Hypixel, en Housing. Le Bedwars n'est pas encore mesuré.
- Ping mesuré entre l'envoi d'une attaque et l'animation de coup reçu de l'adversaire : 163 ms
  pour qRago, 140 ms pour godemy. Hypixel affiche un ping de 1 dans le tab, inutilisable.
- Même combat sur notre serveur de test (PandaSpigot 226, RagoCombatEngine en `override` avec
  `vanilla`), pour étalonner la méthode : qRago en local (0 ms), godemy par Tailscale (46 ms).

| | Hypixel | Notre serveur |
| --- | --- | --- |
| Durée (qRago / godemy) | 266 s / 277 s | 220 s / 220 s |
| Paquets de vitesse reçus | 261 + 283 = 544 | 254 + 264 = 518 |
| Coups retrouvés dans l'enregistrement de l'attaquant | 258 + 271 | 252 + 260 |
| Lignes perdues | 0 | 0 |

### Méthode

- Un coup reçu est un paquet de vitesse (0x12) pour le joueur, avec l'animation de coup reçu
  (statut 2, paquet 0x1A) reçue à moins de 100 ms. Les 1 062 paquets de vitesse ont tous la leur,
  à 12 ms au plus.
- L'état au sol de la victime vient de sa dernière ligne de tick avant le paquet.
- Le sprint de l'attaquant tel que le serveur l'a utilisé vient de l'enregistrement de
  l'attaquant : ses paquets de sprint (0x0B, actions 3 et 4) rejoués dans l'ordre, avec la règle
  vanilla qui arrête le sprint après un coup en sprint qui compte.
- Les deux horloges sont recalées sur les animations de coup reçu vues des deux côtés.
- La poussée horizontale de sprint est retrouvée à partir du regard de l'attaquant, lu dans son
  dernier paquet de regard envoyé avant l'attaque (0x05 ou 0x06).
- Les chiffres viennent de l'outil d'analyse kb-analyzer (tools/kb-analyzer, PR séparée), lancé
  sur les quatre enregistrements ; une première analyse par scripts avait donné les mêmes valeurs.

### Résultats : vertical

Valeurs du paquet (8 000 unités = 1 bloc par tick), les plus fréquentes, avec le sprint de
l'attaquant tel que le serveur l'a utilisé :

| Coup | Hypixel | Notre serveur (vanilla) |
| --- | --- | --- |
| Sans sprint, victime au sol | 2 886 (85 coups sur 135) | 2 886 (167 sur 235) |
| Sprint, victime au sol | **3 446** (127 sur 191) | 3 686 (131 sur 175) |
| Sans sprint, victime en l'air | 3 200, le plafond (37 sur 94) | 1 982 (20 sur 63) ; 3 200 (6) |
| Sprint, victime en l'air | **3 760** (30 sur 109) | 3 077 (8 sur 39) ; 4 000 (4) |

Chaque valeur courante sans sprint a sa jumelle en sprint 560 unités plus haut sur Hypixel
(2 886/3 446, 2 898/3 458, 2 590/3 150, 3 200/3 760), contre 800 unités chez nous. Le sprint
ajoute donc 0,07 bloc par tick à la vitesse verticale sur Hypixel, après le plafond, au lieu de
0,1 en vanilla. Les deux valeurs les plus fréquentes encadrent cette poussée entre 0,069999994 et
0,070075. Sans sprint, Hypixel donne exactement les valeurs vanilla : friction 2, poussée 0,4,
plafond 0,4.

Effet sur la hauteur d'un coup en sprint au sol : 1,31 bloc sur Hypixel, 1,49 bloc en vanilla
(13 % de plus), et 12 ticks en l'air au lieu de 13.

### Coups en l'air pendant un combo : l'effet du ping

En l'air, la valeur verticale dépend de la vitesse verticale que le serveur garde pour la victime.
Sur les coups d'un combo (8,5 à 12,5 ticks après le coup précédent), on peut retrouver depuis le
paquet le moment où le serveur a remis cette vitesse à zéro avant de la refaire tomber, compté en
ticks après le coup précédent :

| Victime | Ping (attaque vers animation) | Remise à zéro, la plus fréquente |
| --- | --- | --- |
| qRago, notre serveur | 0 ms | 6 ticks (24 sur 28 au premier combat, 11 sur 17 au second) |
| godemy, notre serveur | 46-48 ms | 7 ticks (30 sur 66, puis 15 sur 24) |
| godemy, Hypixel | 140 ms | 8 ou 9 ticks, ou plus (65 sur 110) |
| qRago, Hypixel | 163 ms | 9 ticks ou plus (49 sur 87) |

La remise à zéro arrive à peu près 6 ticks plus le ping après le coup précédent. Quand elle arrive
au moment du coup suivant ou plus tard, ce coup prend le plafond (3 200, ou 3 760 en sprint) :
c'est le cas d'un tiers des coups en l'air des combos sur Hypixel, et de presque aucun chez nous.
Ce n'est donc pas une règle différente d'Hypixel mais l'effet du ping, que notre serveur reproduit
de la même façon : avec 150 ms, un joueur comboté monte plus haut, sur Hypixel comme ici. Le test
avec du ping ajouté sur notre serveur, plus bas, le confirme.

Conséquence : la sensation d'Hypixel dépend du ping de celui qui joue. Un joueur européen à 30 ms
sur Rago aura des combos en l'air plus bas que ceux qu'il connaît sur Hypixel à 150 ms, avec
exactement les mêmes réglages.

### Résultats : horizontal

- Sans sprint : 0,400 bloc par tick exactement pour 158 coups sur 229 (et 204 sur 298 chez nous) ;
  les autres sont des coups en combo, où s'ajoute la moitié de la vitesse d'avant.
- Sprint : la poussée en plus dans la direction du regard de l'attaquant vaut 0,50 à 0,005 près
  pour 203 des 300 coups en sprint sur Hypixel (68 %), et pour 152 sur 214 chez nous (71 %), où
  elle vaut 0,5 par construction. Les autres coups sont des combos ou des sprints mal classés,
  aussi nombreux des deux côtés : rien n'indique une autre valeur sur Hypixel.

L'horizontal d'Hypixel est donc le vanilla.

### Résultats : délai entre deux coups

Intervalle entre deux animations de coup reçu sur le même joueur, vu par les deux clients :

| Intervalle | Hypixel | Notre serveur |
| --- | --- | --- |
| moins de 425 ms | 18 | 2 |
| 425 à 474 ms (9 ticks) | **271** | 29 |
| 475 à 524 ms (10 ticks) | 177 | **308** |
| 525 à 574 ms (11 ticks) | 69 | 174 |

Côté attaquant, part des attaques qui comptent selon le temps écoulé depuis le coup précédent qui
a compté, dans l'horloge de l'attaquant :

| Depuis le coup précédent | Hypixel (les deux joueurs) | Notre serveur, godemy (46 ms) | Notre serveur, qRago (0 ms) |
| --- | --- | --- | --- |
| moins de 400 ms | 1 sur 1 437 | 0 sur 779 | 0 sur 648 |
| 400 à 424 ms | 7 sur 104 | 1 sur 60 | 0 sur 90 |
| 425 à 449 ms | **45 sur 49** | 0 sur 25 | 2 sur 24 |
| 450 à 474 ms | **95 sur 108** | 0 sur 77 | 12 sur 104 |
| 475 à 499 ms | 16 sur 18 | **32 sur 32** | 18 sur 26 |
| 500 à 524 ms | 69 sur 72 | 51 sur 53 | 54 sur 69 |

qRago jouait sur la machine qui faisait tourner le serveur : sa courbe est plus floue, sans
explication sûre pour l'instant (le client et le serveur se partagent le processeur).

Sur Hypixel, un coup compte 9 ticks (450 ms) après le précédent ; en vanilla, 10 ticks (500 ms).
Les paquets d'Hypixel arrivent par lots espacés de 50 ms, ce qui situe son serveur à 20 ticks par
seconde pendant la mesure : 450 ms font bien 9 ticks. Presque aucune attaque plus rapprochée ne
compte (8 sur 1 541 sous 425 ms sur Hypixel, ce qui reste dans les écarts du réseau) : il n'y a pas
de « double coup » sur Hypixel, seulement un délai plus court.

### Ce qu'on en tire

- Profil intégré `hypixel-approx-v1` : vanilla, avec `extra-vertical: 0.07`.
- Réglage `hit-delay-ticks: 9` : le moteur donne aux joueurs un cooldown de dégâts de 18 ticks,
  dont la moitié fait le délai entre deux coups.

### Contrôle sur notre serveur

Le même jour, un second combat d'environ 80 secondes sur notre serveur avec `hypixel-approx-v1` et
`hit-delay-ticks: 9` (159 coups) redonne les valeurs d'Hypixel : 2 886 sans sprint et 3 446 en
sprint au sol, un pas de sprint de 560, la poussée horizontale de 0,4 et de 0,5 en plus en sprint,
et 44 % des intervalles entre deux coups à 9 ticks (45 % sur Hypixel). Les attaques envoyées au
moins 425 ms après la précédente ont compté 69 fois sur 71.

### Test avec du ping ajouté sur notre serveur

Le même soir, sur notre serveur avec `hypixel-approx-v1` et 9 ticks, trois joueurs ont enchaîné
des combats avec le journal des hits du moteur, dans trois conditions : avec clumsy (75 ms de
retard dans chaque sens sur le port du serveur, environ 150 ms de ping en plus pour tous), avec la
première version de `/kb pingsim` à 160 (le mouvement plus ancien seulement, sans retard du kb), et
sans rien.

| Condition | Coups des combos en l'air au plafond | Coups rendus dans les 300 ms |
| --- | --- | --- |
| Clumsy, environ 150 ms de plus | 40 sur 92 (43 %) | 45 sur 118 (38 %) |
| `/kb pingsim 160`, mouvement seulement | 16 sur 50 (32 %) | 23 sur 78 (29 %) |
| Sans rien | 13 sur 95 (14 %) | 9 sur 42 (21 %) |

Les coups des combos sont ceux qui arrivent 8 à 13 ticks après le coup précédent sur la même
victime, tous joueurs confondus ; sur Hypixel, un tiers d'entre eux prennent le plafond. Les coups
rendus sont ceux d'une même paire de joueurs, présente dans les trois conditions : un coup est
rendu quand la victime touche son attaquant dans les 6 ticks qui suivent.

Le ping réel ajouté donne donc les combos d'Hypixel, et le mouvement plus ancien s'en approche. Le
ressenti des joueurs : avec clumsy, « vraiment Hypixel », mais tout le jeu lagge (blocs,
déplacements) ; avec le ping simulé, peu de différence. Ce qui manque au ping simulé, ce sont les
trades : avec du vrai ping, la victime ne reçoit son kb qu'après le trajet de l'attaque jusqu'au
serveur et celui du kb jusqu'à elle, et peut rendre le coup jusque-là. D'où la seconde version du
réglage, qui retarde aussi le paquet de kb (README du moteur).

Le journal montre aussi que la première version prenait parfois un mouvement plus récent d'un tick
que prévu : quand l'attaque arrive avant le mouvement de la victime dans le même tick du serveur.
La seconde version en tient compte. Les échantillons restent petits, une centaine de coups par
condition.

### Limites

- Housing seulement. Le Bedwars peut avoir d'autres réglages : à vérifier par un enregistrement
  en partie de Bedwars avant d'en faire le réglage du Ranked.
- Deux joueurs, environ 4 minutes et demie. Non mesurés : l'enchantement Knockback, la canne à
  pêche, les projectiles, les explosions et boules de feu.
- Le délai est mesuré sur des coups d'épée ; le moteur l'applique à tous les dégâts, comme le fait
  le cooldown de Minecraft.
- La vitesse « côté serveur » de la victime avant un coup en combo ne se lit pas directement : elle
  est déduite du paquet, en supposant la gravité vanilla depuis la remise à zéro. Le lien avec le
  ping repose sur deux joueurs et deux pings par serveur.
