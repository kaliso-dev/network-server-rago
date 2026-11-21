# Mesurer les boules de feu et les TNT d'Hypixel

Les valeurs actuelles des boules de feu de RagoBedwars et du practice (vitesse, rayon, poussée,
dégâts, blocs cassés) sont celles de BedWars1058, jamais mesurées sur Hypixel. Cette séance donne
les mesures pour les rendre fidèles (plan 2.6 Fireballs et 2.7 TNT). Rien ne sera changé dans le
jeu sans elles.

## Matériel

- Le mod **kb-recorder 0.2.0** (artefact `rago-kb-recorder` de la CI, à la place du 0.1.0 dans le
  dossier `mods`). Le 0.1.0 n'enregistre pas les boules de feu ni les TNT.
- Deux comptes, qRago et godemy comme pour la mesure du knockback, **chacun avec le mod**. La
  partie B demande les deux ; les autres peuvent se faire seul.
- Une partie de Bed Wars où l'on peut acheter des boules de feu, des TNT et des blocs : une partie
  privée si possible, sinon une vraie partie (en solo, assez d'argent pour la partie C).

## Déroulé

`/kbrec start explosions` au début, `/kbrec stop` à la fin (ou quitter le jeu). Avant chaque essai,
un repère avec `/kbrec mark <texte>` : l'outil d'analyse recopie le dernier repère avant chaque
explosion, c'est lui qui dit ce qu'on testait. Le nombre entre parenthèses est le nombre d'essais.

### A. Fireball jump (seul)

1. `/kbrec mark jump immobile` : debout sur un sol plat, regarder droit vers le bas, lancer (5).
2. `/kbrec mark jump sprint` : en sprintant tout droit, lancer à ses pieds comme en partie (5).
3. `/kbrec mark jump en l air` : sauter, lancer à ses pieds en l'air (5).

### B. Boule de feu sur un autre joueur (à deux)

godemy reste immobile sur un sol plat, qRago lance.

1. `/kbrec mark sol a 1 bloc` : la boule de feu touche le sol à 1 bloc de godemy (3).
2. Pareil à 2 blocs puis à 3 blocs : `sol a 2 blocs`, `sol a 3 blocs` (3 chacun).
3. `/kbrec mark direct` : la boule de feu touche godemy (3).

### C. Blocs

Pour chaque bloc : construire un petit mur (3 de large, 2 de haut) sur un sol du même bloc, se
reculer à 5 blocs et lancer une boule de feu au milieu du mur.

`/kbrec mark fireball laine`, puis `fireball bois`, `fireball argile`, `fireball end stone`,
`fireball verre`, `fireball obsidienne` (2 chacun, l'obsidienne seulement si elle est achetable).

### D. TNT (pour 2.7)

1. `/kbrec mark tnt jump` : poser une TNT et rester à côté, comme pour un TNT jump (5).
2. `/kbrec mark tnt laine`, puis `tnt bois`, `tnt end stone`, `tnt verre` : une TNT contre un mur
   du bloc, puis se reculer (1 chacun).

### E. Trajet

`/kbrec mark trajet` : lancer une boule de feu à l'horizontale vers le vide, loin de tout (3).

## Ce que la mesure donne

L'outil d'analyse (`--explosions`, voir [kb-analyzer](../../tools/kb-analyzer/README.md)) sort,
pour chaque explosion : le délai entre le clic et l'apparition, la vitesse de la boule de feu, le
temps avant l'explosion (la mèche des TNT), la puissance de l'explosion, la poussée reçue et sa
direction par rapport au centre, la hauteur et la longueur du saut, et les blocs cassés ou laissés
par type. Ce sont ces valeurs qui remplaceront celles de BedWars1058, et elles iront dans
[mesures-hypixel.md](mesures-hypixel.md) comme une nouvelle mesure.
