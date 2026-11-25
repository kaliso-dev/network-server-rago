---
type: référence
tags: [modes, catalogue]
maj: 2026-11-25
source: bukkit/rago-lobby/src/main/resources/config.yml (modes:, categories:) sur la branche de la PR #47
---
# Catalogue des modes

Tous les modes du jeu au 25/11/2026, lus dans la section `modes:` du `config.yml` fourni avec RagoLobby (branche de la PR #47) : **90 modes**, dont 9 cachés (ils ne se lancent pas seuls : le parent d'un groupe dont seuls les formats se jouent, les tailles cachées de Bedwars Normal, le jeu interne du FFA Archway, et les trois events lancés par `/host`). Chaque mode a un id (utilisé dans les commandes, les stats et les configs), un nom en jeu, une catégorie, un nombre d'équipes et de joueurs par équipe, et un plugin qui le joue. Retour à [[Projet]] ; la boussole qui les montre : [[Boussole et menus]].

**Catégories** (`categories:`) : Practice (`entrainement`, bleu clair), Bedwars Modes (`ranked`, or ; s'appelait « RBW Modes » jusqu'au 23/11), Duels (`duels`, vert), Arenas (`arenes`, rouge), Fun Practice (`fun`, rose), 3 Players (`trois-joueurs`, jaune, qui reprend des modes d'autres catégories par `aussi:`), Party (`party`, bleu, ce que la party joue contre elle-même), Ranked (`classe`, or). Un **groupe** (`groupes:`) montre plusieurs tailles d'un même jeu comme un seul objet du menu (ex. `bed-fight` : 1v1, 2v2, 1v2).

**Retirés des menus** (code et records gardés, plus jouables) : `kb-displace` et KB displace contre un bot, Pearl practice, Fireball practice (23/11) ; Combo bot, Find Your Sensitivity (22/11). Voir [[Décisions]].

Le détail de chaque jeu : [[Entraînements]], [[Duels]], [[Arènes ouvertes]], [[Bed Wars]], [[Fun Practice]], [[Red Rover et 3 Players]], [[Parties privées]], [[Events host]]. Les modes classés et l'ELO : [[Files, Ranked et ELO]].

## Ranked (`classe`)

| Id | Nom en jeu | Format | Plugin | Ce que c'est (texte du jeu) |
|---|---|---|---|---|
| `ranked-1v1` | Ranked Bedwars 1v1 | 1v1 ; groupe `ranked`, classé (ELO) | RagoBedwars | Ranked Bedwars in 1v1, on the 20 maps of the RBW queue: your ELO goes up or down after every game. |
| `ranked-2v2` | Ranked Bedwars 2v2 | 2v2 ; groupe `ranked`, classé (ELO) | RagoBedwars | Ranked Bedwars, two against two, on the RBW maps. |
| `ranked-3v3` | Ranked Bedwars 3v3 | 3v3 ; groupe `ranked`, classé (ELO) | RagoBedwars | Ranked Bedwars, three against three, on the RBW maps. |
| `ranked-4v4` | Ranked Bedwars 4v4 | 4v4 ; groupe `ranked`, classé (ELO) | RagoBedwars | Ranked Bedwars, four against four, on the RBW maps. |
| `ranked-bed-fight-1v1` | Ranked Bed Fight 1v1 | 1v1 ; classé (ELO) | RagoBedwars | Bed fight in 1v1, ranked: your ELO goes up or down after every game. |
| `ranked-top-bridge-1v1` | Ranked Top Bridge 1v1 | 1v1 ; classé (ELO) | RagoBedwars | Top Bridge in 1v1, ranked: the first to 5 points wins, your ELO follows. |
| `ranked-bridge-fight-1v1` | Ranked Bridge Fight 1v1 | 1v1 ; classé (ELO) | RagoPractice | Bridge fight in 1v1, ranked: first to 5, your ELO follows. |
| `ranked-block-fight-1v1` | Ranked Block Placement 1v1 | 1v1 ; classé (ELO) | RagoPractice | Block placement in 1v1, ranked: first to 100, your ELO follows. |
| `ranked-sumo` | Ranked Sumo | 1v1 ; classé (ELO) | RagoPractice | The sumo duel, ranked: knock them off, your ELO follows. |
| `ranked-boxing` | Ranked Boxing | 1v1 ; classé (ELO) | RagoPractice | Boxing, ranked: first to 100 hits, your ELO follows. |

## Practice (`entrainement`)

| Id | Nom en jeu | Format | Plugin | Ce que c'est (texte du jeu) |
|---|---|---|---|---|
| `clutch` | Block clutch | solo / ouvert ; groupe `clutch` | RagoPractice | Solo, on a small island next to the islands of the others: a hit throws you off, place your wool to save yourself. Set the knockback, the hits, a KB stick in the hotbar. |
| `clutch-top-fight` | Block clutch: top fight | solo / ouvert ; groupe `clutch` | RagoPractice | The block clutch on a top fight platform, at the build limit: no block above it. |
| `block-in` | Block in practice | solo / ouvert | RagoPractice | Drop on a bed defense, block yourself in and dig to the bed: the time of each layer. |
| `fast-defense` | Fast defense | solo / ouvert | RagoPractice | Build a bed defense as fast as you can: Ranked (end stone, glass) or duo (end stone, wool). |
| `block-edit` | Block edit | solo / ouvert | RagoPractice | Break a block of the wall, hit the bot behind it, put the block back before it hits you. |
| `extend-practice` | Extend practice | solo / ouvert | RagoPractice | Double and triple extends: jump a gap too long, extend your wool in the air (5+1, 6+0, 6+1, 7+0, 7-1, 7-2). Chain: 10 pads in a row, no stop. Ladders included. |
| `block-extend` | Block extend | solo / ouvert | RagoPractice | As in a real bed fight: take a hit on the bridge, save yourself with your wool. Gold, Diamond, Emerald: the hit comes from your look. Wool and ladders. |
| `bridge-chrono` | Timed bridge | solo / ouvert | RagoPractice | Bridge 16, 32 or 64 blocks against the clock, with any technique: straight or diagonal, flat or uphill, or with a turn. A record for each course. |
| `ladder-practice` | Ladder practice | solo / ouvert | RagoPractice | Block-ladder clutch: jump from 4 to 24 blocks, wool and a ladder before the ground. |
| `shop-practice` | Fast Shop | solo / ouvert | RagoBedwars | Buy lists of items as fast as you can, with your own Hypixel quick buy (copy it in one click: the book). |
| `bow-training` | Bow training | solo / ouvert | RagoPractice | On Archway: bots bridge from the middle to your base, knock them off with your bow. Hits, accuracy, the longest shot. |
| `aim-trainer` | Aim trainer | solo / ouvert | RagoPractice | Aim training, solo, moving targets. XP for each second on the target, and a top of the best. |
| `block-aim` | Block aim | solo / ouvert | RagoPractice | A face lights up: under your feet, on the side of the bridge or on the bed. A block on it, faster at each level. |

## Bedwars Modes (`ranked`)

| Id | Nom en jeu | Format | Plugin | Ce que c'est (texte du jeu) |
|---|---|---|---|---|
| `skyrise-1v1` | RBW Sky Rise 1v1 | 1v1 ; groupe `skyrise` | RagoBedwars | A solo Bedwars, RBW style, on Sky Rise. |
| `skyrise-2v2` | RBW Sky Rise 2v2 | 2v2 ; groupe `skyrise` | RagoBedwars | A duo Bedwars, RBW style, on Sky Rise. |
| `skyrise-resources-1v1` | RBW Sky Rise Resources 1v1 | 1v1 ; groupe `skyrise` | RagoBedwars | Sky Rise 1v1 with the forge and the item shop of every island: go and take their iron and gold. Slow forges. |
| `skyrise-resources-2v2` | RBW Sky Rise Resources 2v2 | 2v2 ; groupe `skyrise` | RagoBedwars | Sky Rise 2v2 with the forge and the item shop of every island: go and take their iron and gold. Slow forges. |
| `first-rush` | First rush solo | 1v1 ; groupe `first-rush` | RagoBedwars | A real Bedwars 1v1, RBW style, on two islands side by side: the first rush decides it. Choose the map. |
| `first-rush-duo` | First rush duo | 2v2 ; groupe `first-rush` | RagoBedwars | A real Bedwars 2v2, RBW style, on two islands side by side: the first rush decides it. Choose the map. |
| `first-rush-full` | First rush full map solo | 1v1 ; groupe `first-rush` | RagoBedwars | First rush 1v1 with the whole map: diamonds, emeralds and the forges of the empty islands. Choose the map. |
| `first-rush-full-duo` | First rush full map duo | 2v2 ; groupe `first-rush` | RagoBedwars | First rush 2v2 with the whole map: diamonds, emeralds and the forges of the empty islands. Choose the map. |
| `invis-practice` | Invis practice | solo / ouvert ; caché | RagoBedwars | The 20 RBW maps, 3-minute rounds: an attacker with an invis potion, from mid, against the defenders of the bed, each player in turn: a hit shows them. |
| `invis-practice-1v1` | Invis practice 1v1 | 1v1 ; groupe `invis` | RagoBedwars | An invisible attacker against one defender: 2 players, each attacks twice, in turn. |
| `invis-practice-1v2` | Invis practice 1v2 | 1v2 ; groupe `invis` | RagoBedwars | An invisible attacker against two defenders: 3 players, each attacks twice, in turn. |
| `invis-practice-1v3` | Invis practice 1v3 | 1v3 ; groupe `invis` | RagoBedwars | An invisible attacker against three defenders: 4 players, each attacks once, in turn. |
| `invis-practice-1v4` | Invis practice 1v4 | 1v4 ; groupe `invis` | RagoBedwars | An invisible attacker against four defenders: 5 players, each attacks once, in turn. |
| `clutch-scenario-1v1` | Clutch scenario 1v1 | 1v1 ; groupe `clutch-scenario` | RagoBedwars | A real RBW game: one of you has no bed and must clutch, the other's bed is defended. |
| `clutch-scenario-1v2` | Clutch scenario 1v2 | 1v2 ; groupe `clutch-scenario` | RagoBedwars | A real RBW game: the player alone has no bed and must clutch, the bed of the 2 others is defended. |
| `clutch-scenario-1v3` | Clutch scenario 1v3 | 1v3 ; groupe `clutch-scenario` | RagoBedwars | A real RBW game: the player alone has no bed and must clutch, the bed of the 3 others is defended. |
| `clutch-scenario-1v4` | Clutch scenario 1v4 | 1v4 ; groupe `clutch-scenario` | RagoBedwars | A real RBW game: the player alone has no bed and must clutch, the bed of the 4 others is defended. |
| `attack-defense-1v1` | Attack and defense 1v1 | 1v1 ; groupe `attack-defense` | RagoBedwars | Bio Hazard: the attacker on the pink bridge, one life; the defender with forge and shop. First to 3 rounds, the roles swap at each round; 3 kits to attack, 3 to defend. |
| `attack-defense-2v2` | Attack and defense 2v2 | 2v2 ; groupe `attack-defense` | RagoBedwars | Bio Hazard: one team attacks from the pink bridge, the other defends with forge and shop. First to 3 rounds, the roles swap at each round; 3 kits to attack, 3 to defend. |
| `bed-break-simulator` | Bed Break Simulator | solo / ouvert ; caché | RagoBedwars | Archway or Invasion: attack from the top with one of 3 kits and break the bed; the defenders have their forge full. 3-minute rounds, each in turn. |
| `bed-break-simulator-1v1` | Bed Break Simulator 1v1 | 1v1 ; groupe `bed-break` | RagoBedwars | One attacker against one defender: 2 players, each attacks twice, in turn. |
| `bed-break-simulator-1v2` | Bed Break Simulator 1v2 | 1v2 ; groupe `bed-break` | RagoBedwars | One attacker against two defenders: 3 players, each attacks twice, in turn. |
| `bed-break-simulator-1v3` | Bed Break Simulator 1v3 | 1v3 ; groupe `bed-break` | RagoBedwars | One attacker against three defenders: 4 players, each attacks once, in turn. |
| `bed-break-simulator-1v4` | Bed Break Simulator 1v4 | 1v4 ; groupe `bed-break` | RagoBedwars | One attacker against four defenders: 5 players, each attacks once, in turn. |
| `bedwars-normal` | Bedwars Normal | 2 to 16 ; groupe `prive` | RagoBedwars | A Bed Wars game with your party, as on Hypixel: choose a map of 4s or 2s, then everyone their team. 2 to 16 players. |
| `bedwars-normal-4s` | Bedwars Normal | 4s ; caché | RagoBedwars |  |
| `bedwars-normal-2s` | Bedwars Normal | 2s ; caché | RagoBedwars |  |
| `ffa-archway` | FFA Archway | 2 to 16 ; groupe `prive` | RagoBedwars | The FFA of Archway with your party alone, each for themselves: 30 minutes, then the kills leaderboard. 2 to 16 players. |
| `ffa-archway-partie` | FFA Archway | 2 to 16 ; caché | RagoBedwars |  |

## Duels (`duels`)

| Id | Nom en jeu | Format | Plugin | Ce que c'est (texte du jeu) |
|---|---|---|---|---|
| `bed-fight-1v1` | Bed fight 1v1 | 1v1 ; groupe `bed-fight` | RagoBedwars | Two islands face to face, a bed on each: bridge over, break their bed and kill them. You come back while yours stands. |
| `bed-fight-2v2` | Bed fight 2v2 | 2v2 ; groupe `bed-fight` | RagoBedwars | Bed fight, two against two: break their bed, then the last team standing wins. |
| `bed-fight-1v2` | Bed fight 1v2 | 1v2 ; groupe `bed-fight` | RagoBedwars | Bed fight, one against two: the player alone, drawn at random, has a better stuff than the two others. |
| `top-bed-fight-1v1` | Top BedFight 1v1 | 1v1 | RagoBedwars | On the bridge at the top of Bed fight: kill them or knock them off, rounds of one life, the first to 5 points wins. |
| `top-bridge-1v1` | Top Bridge 1v1 | 1v1 ; groupe `top-bridge` | RagoBedwars | On top of the top bridge of Antenna, on the 20 RBW maps: no bed, rounds of one life, the first to 5 points wins. |
| `top-bridge-2v2` | Top Bridge 2v2 | 2v2 ; groupe `top-bridge` | RagoBedwars | Top Bridge, two against two: the first team to 5 points. |
| `bridge-fight-1v1` | Bridge fight 1v1 | 1v1 ; groupe `bridge-fight` | RagoPractice | A real 1v1 on a platform of its own, no blocks: a kill or a fall is a point. First to 5. |
| `bridge-fight-2v2` | Bridge fight 2v2 | 2v2 ; groupe `bridge-fight` | RagoPractice | The bridge fight, two against two on one platform: when a whole team is out, the other scores. First to 5. |
| `block-fight-1v1` | Block placement 1v1 | 1v1 ; groupe `block-placement` | RagoPractice | On the block placement maps, as on Minemen: each hit is a point, the void costs 10. Build, clutch, knock them off: first to 100. |
| `block-fight-2v2` | Block placement 2v2 | 2v2 ; groupe `block-placement` | RagoPractice | Block placement, two against two: each hit is a point, the void costs 10. First to 100. |
| `lobby-fight-1v1` | Lobby Fight 1v1 | 1v1 | RagoBedwars | In the waiting lobby of Archway FFA, with the kit of Lobby Fight: rounds of one life, knock them out; the first to 3 points wins. |
| `sumo` | Sumo duel | 1v1 | RagoPractice | Knock the other player off the platform: great for working on knockback. |
| `red-rover-sumo` | Red Rover Sumo | 1v1v1 ; groupe `red-rover` | RagoPractice | Three players, 1v1 in turn on the sumo platform: who waits watches the 1v1. |
| `red-rover-block-edit` | Red Rover Block Edit | 1v1v1 ; groupe `red-rover` | RagoPractice | Three players, 1v1 in turn at the wall of wool: a clean edit wins the 1v1. |
| `red-rover-block-placement` | Red Rover Block Placement | 1v1v1 ; groupe `red-rover` | RagoPractice | Three players, 1v1 in turn on the block placement maps: a hit +1, the void +10, first to 10 wins the 1v1. |
| `red-rover-top-bridge` | Red Rover Top Bridge | 1v1v1 ; groupe `red-rover` | RagoBedwars | Three players, 1v1 in turn on the top bridge: a round of one life wins the 1v1. |
| `boxing` | Boxing | 1v1 | RagoPractice | First to 100 hits, to work on your combos. /speed: Speed II off or on. |
| `block-edit-duel` | Block edit 1v1 | 1v1 | RagoPractice | A wall of wool between you: open, hit, close before they hit you back. First to 5. |
| `fireball` | Fireball fight | 1v1 | RagoPractice | Fireballs and TNT: break their bed, the last one standing wins. Blasts and falls do no damage. |
| `bow-duel` | Bow duel | 1v1 | RagoPractice | Bow 1v1 on Archway, the base against the middle: each arrow that hits is a point. First to 10. |
| `event-sumo` | Sumo event | solo / ouvert ; caché | RagoPractice | Hosted with /host: one 1v1 at a time, the last one in wins. |
| `event-brackets` | Brackets event | solo / ouvert ; caché | RagoPractice | Hosted with /host: a 1v1 tournament with a Bed Wars kit. |
| `event-lms` | Last Man Standing event | solo / ouvert ; caché | RagoPractice | Hosted with /host: everyone at once, the last one standing wins. |

## Arenas (`arenes`)

| Id | Nom en jeu | Format | Plugin | Ce que c'est (texte du jeu) |
|---|---|---|---|---|
| `top-bridge-ffa` | Top Bridge FFA | solo / ouvert | RagoBedwars | The top bridge of Invasion, free for all: come and go when you like, diamond armour, and back on top at each death. |
| `ffa` | FFA Ranked Bedwars | solo / ouvert | RagoBedwars | Free for all on Archway FFA, in diamond armour: spawn on the top bridge, low, mid or at random; come and go when you like. A kill heals you and gives your whole kit back. |
| `lobby-fight-ffa` | Lobby Fight FFA | solo / ouvert | RagoBedwars | The FFA in the waiting lobby of Archway FFA, a sword, 5 golden apples (back to 5 at each kill), 3 fireballs, blocks, a pickaxe, a pearl and shears: come and go when you like, fall out of it and you die. |
| `free-ffa` | Arena Free | solo / ouvert | RagoPractice | Free for all in the arena, a sword only and Resistance: nobody dies, it is a practice. The slime of the black pit throws you out. |
| `bridge-fight` | Bridge fight | solo / ouvert | RagoPractice | Free for all, no blocks: the hits only push, knock them into the void. Real damage on the middle platform only. |
| `block-fight-arena` | Block placement arena | solo / ouvert | RagoPractice | Block placement in an arena, with everyone: wool and shears, no damage, knock them off. The blocks break after 15 seconds. |

## Fun Practice (`fun`)

| Id | Nom en jeu | Format | Plugin | Ce que c'est (texte du jeu) |
|---|---|---|---|---|
| `fireball-knockback` | Fireball Fight | 1v1v1 | RagoPractice | Three players on a big platform over the void: knock them off with fireballs. Double jump, and wool that goes fast. No hits, no damage. |
| `snowball-spleef` | Snowball Spleef | 1v1 | RagoPractice | Break the snow under them: each block gives a snowball that breaks the snow too, or pushes them. Two floors, no hand hits: don't fall! |
| `parkour-duel` | Parkour Duel | 1v1 | RagoPractice | Race them on your own lane: five sections, four checkpoints. A fall takes you back to the last one. First to the finish wins! |
| `ctf-bridge-1v1` | CTF The Bridge 1v1 | 1v1 ; groupe `ctf-bridge` | RagoBedwars | The Bridge with a flag: take theirs at the back of their base and bring it home over the bridge to capture it. |
| `ctf-bridge-2v2` | CTF The Bridge 2v2 | 2v2 ; groupe `ctf-bridge` | RagoBedwars | CTF The Bridge, two against two: one takes their flag, the other guards yours. |
| `ctf-castle-2v2` | CTF Castle 2v2 | 2v2 ; groupe `ctf-castle` | RagoBedwars | Castle against castle, a mid between: Bed Wars with a flag in each keep in place of the bed. Bring theirs to yours. |
| `ctf-castle-4v4` | CTF Castle 4v4 | 4v4 ; groupe `ctf-castle` | RagoBedwars | CTF Castle, four against four: defend your keep, raid theirs. |
| `fast-bedwars-1v1` | 2017 Bedwars 1v1 | 1v1 ; groupe `fast-bedwars` | RagoBedwars | Bed Wars made fast: faster forges, earlier events, the beds gone at 15 minutes, a smaller shop, no upgrades. |
| `fast-bedwars-2v2` | 2017 Bedwars 2v2 | 2v2 ; groupe `fast-bedwars` | RagoBedwars | 2017 Bedwars, two against two: rush fast, defend fast. |
