---
type: technique
tags: [maps, mondes, bedwars.yml]
maj: 2026-11-28
code: bukkit/rago-bedwars (MapCommands, BedwarsMap, MapStore, Instances), bukkit/rago-practice (MapFinder, MapCopy, MapParts), tools/bedwars-maps, tools/build-tools
---
# Maps et mondes

**Les maps ne sont pas dans le dépôt** : elles sont dans le dossier des mondes du [[Serveur de test]] (`C:\Users\rgabe\Rago\mondes`, sur le PC d'Abel) : maps d'Hypixel, maps construites ou reçues. Les réglages de maps en jeu (`/bw…`) sont faits par Abel. Retour à [[Reprendre le développement]].

## Les mondes du serveur
- `lobby` (le lobby, avec `spawn.yml` et `points.yml`), `world` (monde plat du test de knockback), `arenes` (les plateformes du practice, refait à chaque démarrage), `rago_replays` (replays de duels, effacé à chaque démarrage), `bw-demo` (map de démo créée par RagoBedwars), `bw_<map>_<n>` (copies des parties de Bed Wars, supprimées à l'arrêt), mondes de build de RagoBuildTools, et un dossier par map.
- Une map doit venir de Minecraft 1.8 à 1.12 (`/monde import` refuse 1.13+). Les `.schem` (WorldEdit 7) ne se chargent pas.

## Les maps attendues par les modes
| Usage | Maps |
|---|---|
| Ranked Bedwars, Invis practice, Top Bridge, 2017 Bedwars caché (20 maps RBW) | Antenna, Aquarium, Archway, Artemis, Boletum, Carapace, Catalyst, Chained, Eastwood, Extinction, Invasion, Katsu, Lectus, Obelisk, Paladin, Planet98, Rise, Stonekeep, Swashbuckle, Treenan (chacune avec sa `hauteur-max` de la file) |
| RBW Sky Rise, Clutch Scenario | SkyRise (`hauteur-min: 63`) ; Clutch Scenario aussi Archway |
| First Rush | Acropolis, Ambush, Aqil, Cascade, Crogorm, Frosted, Gelato, Lighthouse, Lotus, Nebuc (spawns corrigés sur Nebuc et Gelato le 21/11 ; sur les autres, pas encore posés : à faire par Abel, 26/11) |
| Bed Break Simulator | Archway, Invasion (escalier et plateforme `bed-break:`) |
| Attack and Defense | BioHazard (`attaquants:`) |
| FFA, Lobby Fight, FFA Archway, salle d'attente privée | ArchwayFFA (`salle-attente: {centre: {x: 0.5, z: 0.5}, y: 117, rayon: 11.5}`) ; c'est la seule map qui a une `salle-attente:` (26/11) : à poser par Abel sur chaque map de Bedwars Normal ([[Parties privées]]) |
| Top Bridge FFA | le top bridge d'Invasion |
| 2012 Bedwars (27/11) | **Bedwars2012** : la map « Rush » de Dono9999, adaptée (2 bases gardées, mid à émeraudes, 2 îles à diamants ; le lobby en redstone retiré), dans `C:\Users\rgabe\Rago\mondes\Bedwars2012` ; prête au démarrage du 28/11 (« Game world bw_Bedwars2012_1 ready ») ([[Bed Wars]]) |
| Bed Fight, Top BedFight | map Bed Fight du propriétaire |
| CTF | CtfBridge et CtfCastle (construites par le plugin, à valider) |
| Practice | BlockClutchNew, BlockClutchExtend, KbDisplace, Block Edit Practice, Bridge Fight Plateform (Bridge fight 1v1 et 2v2 ; le FFA est fermé depuis le 27/11), Arena (l'arène verte), arènes Boxing Minemen 2022 (Cobble, Shy Guy, Redstone, Cave, Fishtank, Goku, Lollipop, Pokemon), sumo (Ponsen, Fort Royale, White Crystal, Space Mine, Sumo), maps de block placement (`block-fight.cartes`), map du ladder de lavy, Ultimate Block-In Practice (Block in) |
| Hypixel Bow Practice (28/11) | **BowPractice**, la map d'Abel (section `bow-practice`), copiée une fois dans `arenes` : « Copied for Hypixel Bow Practice from the map "BowPractice": 5923 blocks » au démarrage du 28/11 ; les marqueurs de la map (blocs d'or, de diamant, d'émeraude des 3 niveaux) remplacés par laine, herbe, terre et andésite sur le PC d'Abel ([[Entraînements]]) |
| Block in, niveau RBW defense (28/11) | **Block In Practice RBW**, la map d'Abel (section `block-in.rbw`), copiée une fois à côté de l'île du Block in : « Copied for the RBW level of the block in from the map "Block In Practice RBW": 1374 blocks » au démarrage du 28/11 ([[Entraînements]]) |

Une map absente est sautée (la console le dit). Une map sert à un mode si elle a au moins autant d'équipes complètes (spawn, lit, générateur, shop).

## Régler une map de Bed Wars
`/bw` (`rago.bedwars.admin`) écrit le `bedwars.yml` du dossier de la map : `/monde import <map>`, `/bw edit <map>`, puis par équipe `spawn`, `bed`, `generator`, `shop`, `upgrades` ; `diamond`, `emerald`, `spectators`, `height`, `void`, `floor`, `lobbyfight`, `info`, `enable`. Clés en plus à la main : `retirer:` (blocs enlevés de chaque copie, ex. la plateforme de laine d'Antenna), `attaquants:`, `bed-break:`, `iles:`, `apparitions:`, `classement:`, `hauteur-max`, `hauteur-min`.
**`tools/bedwars-maps`** (Python, numpy/scipy/pillow) trouve tout seul ces points sur une map d'Hypixel et écrit le `bedwars.yml` (`bwsetup.py`, `runall.py`) ; essayé sur 54 maps solo/doubles et 4 équipes. Reste à vérifier en jeu.

## Copies
`parties-max: 8` copies en même temps ; une copie libre d'une autre map est retirée pour faire de la place ; la première copie se fait quelques secondes après le démarrage. Les practice copient des morceaux de maps dans `arenes` (`MapCopy`, `MapParts`).
