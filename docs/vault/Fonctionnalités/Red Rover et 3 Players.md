---
type: fonctionnalité
tags: [red-rover, 3-players, 1v2]
maj: 2026-11-28
code: game/rago-game-model (RedRoverRotation), bukkit/rago-practice (RedRover, RedRoverTurns, Duel.asRedRover), bukkit/rago-bedwars (RedRoverTopBridgeGame)
---
# Red Rover et 3 Players

Deux choses pour jouer **à trois** (plan 6.5 et 6.6). Détail : `bukkit/rago-practice/README.md` (« Red Rover »). Retour à [[Catalogue des modes]].

## Red Rover (plan 6.5, « solution 1 »)
Trois joueurs (une party de 3 ou la file) en **1v1 chacun leur tour** ; celui qui attend regarde en spectateur. Chaque 1v1 est une manche du mode, une seconde de décompte entre deux, la map reste comme elle a été construite. Les quatre modes sont un seul objet **Red Rover** (un groupe) :

| Mode (id) | Plugin | Un 1v1 se gagne… |
|---|---|---|
| Red Rover Sumo (`red-rover-sumo`) | RagoPractice | le premier qui tombe perd (maps du sumo) |
| Red Rover Block Edit (`red-rover-block-edit`) | RagoPractice | un edit propre au mur de laine (cage en blocs barrière depuis le 28/11, comme le Block edit : PR #55) |
| Red Rover Block Placement (`red-rover-block-placement`) | RagoPractice | premier à `points-par-1v1-block-placement` (10) points (coup +1, vide +10) |
| Red Rover Top Bridge (`red-rover-top-bridge`) | RagoBedwars | premier à `points-par-1v1-top-bridge` manches d'une vie (1) |

Réglages communs dans `red-rover:` du `config.yml` de RagoPractice (RagoBedwars les lit là) : `variante` `points` (premier à `points-pour-gagner` 1v1 gagnés, 3) ou `elimination` ; `reste` `gagnant` ou `perdant` (qui reste pour le 1v1 suivant) ; `secondes-par-1v1` 90 (au bout, le plus de coups gagne). Celui qui quitte est éliminé. À la fin : **top 3** (« 1st - Alice - 3 1v1 won »), calculé par `RedRoverRotation.top`. Kits : ceux du block edit, du block placement 1v1 et du Top Bridge 1v1 rangés par le joueur (`kit-de:`). Valeurs **à régler avec Sofiene**. [[RagoBot]] peut être le 3e joueur.

## La catégorie 3 Players (plan 6.6)
`trois-joueurs`, **dans la boussole seulement, sans PNJ** (choix du 21/11), sur la ligne du haut. Elle n'a pas de modes à elle : par `aussi:` elle montre Red Rover, Bed Break Simulator 1v2, Invis practice 1v2, Clutch scenario 1v2 et Bed fight 1v2 ([[Bed Wars]], [[Duels]]). Ces modes restent dans leur catégorie et y gardent leurs stats. Son nom, son slogan et sa description sont **à confirmer** (README de RagoLobby).

Voir aussi [[Boussole et menus]], [[Party, amis et messages]].
