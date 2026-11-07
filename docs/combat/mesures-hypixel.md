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

### Limites

- Housing seulement. Le Bedwars peut avoir d'autres réglages : à vérifier par un enregistrement
  en partie de Bedwars avant d'en faire le réglage du Ranked.
- Deux joueurs, environ 4 minutes et demie. Non mesurés : l'enchantement Knockback, la canne à
  pêche, les projectiles, les explosions et boules de feu.
- Le délai est mesuré sur des coups d'épée ; le moteur l'applique à tous les dégâts, comme le fait
  le cooldown de Minecraft.
- La vitesse « côté serveur » de la victime avant un coup en combo ne se voit pas dans les
  paquets ; les valeurs en combo ne servent qu'à vérifier la poussée de sprint.
