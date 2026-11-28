---
type: fonctionnalité
tags: [duels, practice]
maj: 2026-11-28
code: bukkit/rago-practice (Duel, PracticeGame, SumoHost…), bukkit/rago-bedwars (Bed Fight, Top Bridge…)
---
# Duels

La catégorie **Duels** (`duels`) : des 1v1 et 2v2 par la file ou par `/duel <joueur> [mode]`. Une partie lancée sans la file (un `/duel`, une partie privée, un duel entre parties) n'est **pas classée** : ni ELO ni série, mais victoires et XP comptent. Quitter ou `/spawn` pendant un duel = défaite par abandon. Détail complet : `bukkit/rago-practice/README.md` (« Les duels ») et `bukkit/rago-bedwars/README.md`. Liste exacte, dans l'ordre des menus (celui d'Abel depuis le 27/11 : Bed fight, Top BedFight, Block placement, Bridge fight, Top Bridge, Block edit 1v1, Lobby Fight 1v1, Red Rover, Boxing, Sumo, Fireball fight, Bow duel) : [[Catalogue des modes]]. Retour à [[Projet]].

## Commun à tous les duels
- Décompte 3, 2, 1 immobile, « Fight! » en titre ; nom en rouge ou bleu au-dessus de la tête et dans le Tab, vie en dessous, niveau à côté ; sidebar avec l'adversaire et le **ping** des deux.
- **Fin** : 3 secondes dans l'arène pour voir le gagnant et son effet de victoire, avec **Play again** et **Leave** qui marchent tout de suite (`fin-de-partie-secondes: 3`), puis retour au lobby avec [PLAY AGAIN], revanche après un 1v1 non classé, et [REPLAY] ([[Replays et spectateurs]]).
- **Récap du duel** (« match details ») : coups passés et meilleur combo de chacun, vie restante, pommes d'or, blocs posés, lits cassés ; **[View]** montre l'inventaire de fin (`/matchinv <code>`, gardé 10 minutes).
- **Le ding** (22/11) : chaque point, manche ou partie gagnés sonne (`ORB_PICKUP`).
- Quatre duels de RagoPractice ont une **version classée** jouée par le même hôte sur les mêmes maps, avec sa file et son ELO : `ranked-sumo`, `ranked-boxing`, `ranked-block-fight-1v1`, `ranked-bridge-fight-1v1` ; RagoBedwars joue `ranked-bed-fight-1v1` et `ranked-top-bridge-1v1` ([[Files, Ranked et ELO]]).

## Les duels de RagoPractice
- **Sumo** (`sumo`) : maps Ponsen, Fort Royale, White Crystal, Space Mine, Sumo (dossier des mondes), au hasard ; coups sans dégâts, **une manche** (`sumo.points-pour-gagner`).
- **Boxing** (`boxing`) : arènes Minemen 2022 du propriétaire (Cobble, Shy Guy, Redstone, Cave, Fishtank, Goku, Lollipop, Pokemon), Speed II, épée sans dégâts, premier à **100 coups** ; `/speed` coupe ou rend la Speed.
- **Block placement 1v1 / 2v2** (`block-fight-1v1`, `block-fight-2v2`) : maps de block placement du propriétaire (`block-fight.cartes`), départ sur un bloc d'or ; épée en bois Sharpness I, pioche et hache Efficiency I, cisailles, 2 stacks de laine, 12 end stone, 16 échelles, fer Protection IV. Chaque coup = 1 point (recul sans dégâts), le vide = 10 points pour l'autre camp, premier à **100** ; la vie suit le score ; blocs cassés qui tombent par terre ; **anti full box** (impossible d'enfermer un joueur) ; les blocs du point de retour sont enlevés avant chaque retour (23/11).
- **Bridge fight 1v1 / 2v2** (`bridge-fight-1v1`, `bridge-fight-2v2`) : une plateforme du bridge fight, pas de blocs, un kill ou une chute = 1 point, premier à 5.
- **Block edit 1v1** (`block-edit-duel`) : le mur de laine, une pièce de chaque côté ; casser, frapper par le trou, refermer avant d'être frappé = 1 point ; premier à 5. La cage autour est en **blocs barrière** (invisibles) depuis le 28/11, plus en verre (PR #55, demande d'Abel du 27/11 : « Oui » pour toute la cage ; installé le 28/11, pas encore vu en jeu).
- **Fireball fight** (`fireball`, avec lits, à ne pas confondre avec le Fireball Fight de [[Fun Practice]]) : île et lit sous une défense bois et laine ; épée en pierre, 6 boules de feu, laine, 8 end stone, 2 TNT… ; on revient tant que le lit tient ; explosions et chutes sans dégâts.
- **Bow duel** (`bow-duel`) : arc Infinity sur Archway, chaque flèche qui touche = 1 point, premier à 10.

## Les duels de RagoBedwars
- **Bed fight 1v1 / 2v2 / 1v2** (`bed-fight-*`) : deux îles face à face (map Bed Fight du propriétaire), un lit chacune sous sa défense (bois et end stone) ; on revient tant que son lit tient ; kit épée, pioche, hache en bois, cisailles, 64 laine ; pas de dégâts de chute ; **protection au respawn 1,5 s** coupée dès qu'on frappe (durée de Minemen, TESTÉ EN JEU) ; chaque mort fait sonner le ding pour toute la partie. **1v2** (22/11) : le joueur seul, tiré au sort, a un meilleur stuff (`stuff-ameliore:` : jambières et bottes en fer, épée en pierre, à confirmer).
- **Top BedFight 1v1** (`top-bed-fight-1v1`) : sur le pont en haut de la map Bed Fight, à la limite de hauteur ; manches d'une vie, premier à 5. Le 2v2 attend la map de Sofiene.
- **Top Bridge 1v1 / 2v2** (`top-bridge-*`) : la plateforme du top bridge d'Antenna (17 blocs de large) posée au-dessus du milieu des 20 maps RBW ; pas de lit, pas de forge, manches d'une vie, premier à 5 points ; kit épée en pierre et 64 pommes d'or.
- **Lobby Fight 1v1** (`lobby-fight-1v1`) : dans le lobby d'attente d'Archway FFA avec le kit du Lobby Fight, manches d'une vie, premier à 3 ([[Arènes ouvertes]]).
- Red Rover : [[Red Rover et 3 Players]].

## Prévu : Block Method 1v1 (pas commencé)
Un duel contre [[RagoBot]] qui **imite un vrai joueur** (preshot, full box, pose de blocs), tiré de la vidéo « Block Method » d'Abel ; plan validé par Abel le 27/11 (« Oui je suis d'accord ») : d'abord les 10 premières techniques en entraînement (Block Pattern Practice, PR #58, [[Entraînements]]), puis Block Method 1v1 dans Duels. **Pas commencé** : questions à Abel sur la condition de victoire, le kit, la map, et l'**anti full box** du Block placement (`FullBox`, impossible d'enfermer un joueur), qui contredit le « full box » demandé pour RagoBot ([[Questions ouvertes]]).

## /duel
`/duel <joueur> [mode]` défie un joueur du lobby ([Accept] / [Deny], une minute, `DuelRequests`) ; le chef d'une party de 2+ qui tape `/duel` sans nom voit les autres parties ([[Party, amis et messages]]). `/duel RagoBot` renvoie vers la party ([[RagoBot]]).
