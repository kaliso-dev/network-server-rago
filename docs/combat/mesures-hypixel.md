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
| Lignes perdues | 0 | 0 |

### Méthode

- Un coup reçu est un paquet de vitesse (0x12) pour le joueur, avec l'animation de coup reçu
  (statut 2, paquet 0x1A) reçue à moins de 20 ms. Les 1 062 paquets de vitesse ont tous la leur.
- L'état au sol de la victime vient de sa dernière ligne de tick avant le paquet.
- Le sprint de l'attaquant tel que le serveur l'a utilisé vient de l'enregistrement de
  l'attaquant : ses paquets de sprint (0x0B, actions 3 et 4) rejoués dans l'ordre, avec la règle
  vanilla qui arrête le sprint après un coup en sprint qui compte.
- Les deux horloges sont recalées sur les animations de coup reçu vues des deux côtés.
- La poussée horizontale de sprint est retrouvée à partir du regard de l'attaquant, lu dans son
  dernier paquet de position envoyé avant l'attaque.
- Les chiffres viennent d'une première analyse par scripts. L'outil d'analyse versionné qui les
  recalcule arrive dans une PR séparée.

### Résultats : vertical

Valeurs du paquet (8 000 unités = 1 bloc par tick), les plus fréquentes :

| Coup | Hypixel | Notre serveur (vanilla) |
| --- | --- | --- |
| Sans sprint, victime au sol | 2 886 (98 coups) | 2 886 (181 coups) |
| Sprint, victime au sol | **3 446** (148 coups) | 3 686 (139 coups) |
| Sans sprint, au plafond (victime qui monte) | 3 200 (47 coups) | 3 200 |
| Sprint, au plafond | **3 760** (32 coups) | 4 000 |

Chaque valeur courante sans sprint a sa jumelle en sprint 560 unités plus haut sur Hypixel
(2 886/3 446, 2 898/3 458, 2 590/3 150, 3 200/3 760), contre 800 unités chez nous. Le sprint
ajoute donc 0,07 bloc par tick à la vitesse verticale sur Hypixel, après le plafond, au lieu de
0,1 en vanilla. Les deux valeurs les plus fréquentes encadrent cette poussée entre 0,069999994 et
0,070075. Sans sprint, Hypixel donne exactement les valeurs vanilla : friction 2, poussée 0,4,
plafond 0,4.

Effet sur la hauteur d'un coup en sprint au sol : 1,31 bloc sur Hypixel, 1,49 bloc en vanilla
(13 % de plus), et 12 ticks en l'air au lieu de 13.

### Résultats : horizontal

- Sans sprint : 0,400 bloc par tick exactement pour 186 coups sur 544 (et 217 sur 518 chez nous) ;
  les autres sont des coups en combo, où s'ajoute la moitié de la vitesse d'avant.
- Sprint : la poussée en plus dans la direction du regard de l'attaquant vaut 0,50 à 0,005 près
  pour 203 des 300 coups en sprint sur Hypixel (68 %), et pour 107 sur 162 chez nous (66 %), où
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
a compté (horloge de l'attaquant ; les attaques de qRago chez nous, à 0 ms de ping, sont laissées
de côté) :

| Depuis le coup précédent | Hypixel | Notre serveur |
| --- | --- | --- |
| moins de 400 ms | 2 sur 1 438 | 0 sur 779 |
| 400 à 424 ms | 7 sur 104 | 1 sur 60 |
| 425 à 449 ms | **51 sur 55** | 0 sur 25 |
| 450 à 474 ms | **107 sur 120** | 0 sur 77 |
| 475 à 499 ms | 17 sur 19 | **41 sur 41** |

Sur Hypixel, un coup compte 9 ticks (450 ms) après le précédent ; en vanilla, 10 ticks (500 ms).
Les paquets d'Hypixel arrivent par lots espacés de 50 ms, ce qui situe son serveur à 20 ticks par
seconde pendant la mesure : 450 ms font bien 9 ticks. Sur aucun des deux serveurs une attaque plus
rapprochée ne compte : il n'y a pas de « double coup » sur Hypixel, seulement un délai plus court.

### Ce qu'on en tire

- Profil intégré `hypixel-approx-v1` : vanilla, avec `extra-vertical: 0.07`.
- Réglage `hit-delay-ticks: 9` : le moteur donne aux joueurs un cooldown de dégâts de 18 ticks,
  dont la moitié fait le délai entre deux coups.

### Limites

- Housing seulement. Le Bedwars peut avoir d'autres réglages : à vérifier par un enregistrement
  en partie de Bedwars avant d'en faire le réglage du Ranked.
- Deux joueurs, environ 4 minutes et demie. Non mesurés : l'enchantement Knockback, la canne à
  pêche, les projectiles, les explosions et boules de feu.
- Le délai est mesuré sur des coups d'épée ; le moteur l'applique à tous les dégâts, comme le fait
  le cooldown de Minecraft.
- La vitesse « côté serveur » de la victime avant un coup en combo ne se voit pas dans les
  paquets ; les valeurs en combo ne servent qu'à vérifier la poussée de sprint.
