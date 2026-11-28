---
type: code
tags: [code, rago-practice]
maj: 2026-11-28
code: bukkit/rago-practice
---
# Code de rago-practice

Le plugin **RagoPractice** (81 classes, ~28 300 lignes) : duels (sumo, boxing, block placement, bridge fight, block edit, fireball, bow), entraînements (une sous-classe de `Training` chacun), arènes ouvertes, events `/host`, Fun Practice, Red Rover, records et défi du jour. **Points d'entrée** : `RagoPractice` (onEnable, enregistrement des `MatchHost`), `PracticeHost`, `PracticeGames` / `PracticeGame` (le moteur commun des duels), `Training` (le moteur commun des entraînements), `Arena` (remise à zéro des blocs), `Records`. Fonctions : [[Entraînements]], [[Duels]], [[Arènes ouvertes]], [[Fun Practice]], [[Events host]]. README : `bukkit/rago-practice/README.md` (630 lignes).

Packages : `gg.rago.practice`. **81 classes, 28282 lignes** (branche de la PR #47, `03beb92`, 28/11 ; `BowPractice` ajoutée par la PR #56, le niveau RBW de `BlockInTraining` par la #57, la cage barrière d'`EditStation` par la #55). Retour à [[Architecture]].

**Règle d'enregistrement des modes** (`RagoPractice.registerModes`) : chaque mode reçoit sa zone dans le monde `arenes` dans l'ordre où il est enregistré ; un mode enregistré au milieu décale les zones de tous ceux d'après. **Un nouveau mode s'enregistre toujours à la fin** (commentaire du code depuis la PR #58). Sur `03beb92` (installé le 28/11), `bow-practice` est enregistré au milieu, juste après `bow-duel` : bug corrigé dans la PR #58, pas encore installé ([[Bugs et problèmes connus]]).

## Les classes
Description = la première phrase de la javadoc de la classe, **en anglais comme le code** (le code et ses commentaires sont en anglais ; les README sont en français).

| Classe | Lignes | Ce qu'elle fait |
|---|---|---|
| `AimTop` | 56 | /aimtop: the best scores of the aim trainer. |
| `AimTrainer` | 443 | Aim trainer: a target the size of a player moves in front of the player as a player would in a fight, and every click that lands on it counts. |
| `Arena` | 364 | The zone of one game in the arena world: its centre, the blocks the mode builds there once (the platform, the islands, the bed...), and every block changed since, so that a round starts again on the arena as it was built |
| `BlockAim` | 237 | The targets of Block aim, without Bukkit: the scene (an island with its bed, a bridge out of it), its faces that light up one at a time (under the feet at the end of the bridge, on the side of the bridge, on the bed to c |
| `BlockAimTraining` | 220 | Block aim (plan, step 6.4, the placement on a target of its proposal): an island with its bed and a short bridge out of it. |
| `BlockArena` | 513 | The block placement arena, open to everyone: one arena where the players join and leave when they like, endless sandstone, the block fight kit. |
| `BlockCracks` | 93 | The cracks of a block being broken, shown on a placed block as it gets old: 1.8 has no API for them, so the server's own packet (block break animation), found by reflection as on any 1.8 server (v1_8_R3). |
| `BlockEditDuel` | 136 | Block edit, one against one: the station of the block edit with a room on each side of the wall of wool. |
| `BlockEditTraining` | 312 | Block edit against a bot, as on the owner's Block Edit Practice map: a wall of wool between the player and a bot in a small wooden room. |
| `BlockExtendTraining` | 403 | Block extend, as in a real bed fight, on the owner's map (BlockClutchExtend): the player stands at an exact place of a bridge, frozen (they can neither move nor turn) until a hit, and saves themselves with their wool: th |
| `BlockFight` | 311 | Block placement fight, 1v1 or 2v2, on the owner's block placement maps (block-fight.cartes in config.yml: each side starts on its gold block) or, without them, two small platforms over the void. |
| `BlockInEnemy` | 190 | The enemy of the Block in training, as a Bed Wars player standing at one side of the player. |
| `BlockInTraining` | 500 | Block in practice, as on the Ultimate Block-In Practice map: the player stands on a ring of glass ten blocks over a bed and its defense (end stone, glass, then wool on one side and wood on the other), drops on it, blocks |
| `Bots` | 67 | The bots of the trainings: a zombie the size of a player, in leather of its colour with a sword, that walks to the player and hits them. |
| `BowDuel` | 129 | The bow 1v1, on the place of the bow training (the red base of Archway and the north of its middle): red on the front of the base, blue on the edge of the middle, facing each other. |
| `BowPractice` | 414 | Hypixel Bow Practice (the owner's ask of 27/11), on his map BowPractice (bow-practice in config.yml): a zombie comes straight at the player along a bridge of wool over the void. |
| `BowTraining` | 399 | Bow training on a real Bed Wars map: the red base of Archway and the north of its middle (bow-training in config.yml), copied into the zone. |
| `Boxing` | 169 | Boxing, as on Minemen: on one of the Minemen arenas of the server (see MapZones), or a closed ring without them, with Speed II and a sword that does no damage, the first to land 100 hits wins. |
| `BridgeArena` | 1104 | The bridge fight, a free for all open to everyone like the block placement arena, in Ranked Bedwars gear (iron armour with Protection II, a wooden sword, golden apples), and nobody places blocks. |
| `BridgeDuel` | 99 | The bridge fight 1v1 or 2v2, apart from the open arena: one platform of the bridge fight, red on the west half and blue on the east one, with its pads and the bridges from the pads to its tips. |
| `BridgeTraining` | 477 | Timed bridge: from a pad to the green one over 16, 32 or 64 blocks of void, as fast as possible, with any technique (speed bridge, ninja, breezily, god bridge, telly...). |
| `CenterCommand` | 34 | /center: back to the centre of Arena Free (see BlockArena#centre). |
| `Challenges` | 393 | The challenge of the day on the server: /challenge (its training, the top of the day, the player's place and days in a row, a [PLAY] button), the XP of each first score of the day and of the podium when the day turns (an |
| `ClutchTraining` | 464 | Block clutch: the player stands at the edge of a small Bed Wars island, its bed and defense beside them, and takes a hit that throws them off over the void; they place their wool to save themselves. |
| `ComboAi` | 289 | The mind of the combo bot, without Bukkit: it plays as a 1.8 player would. |
| `ComboBot` | 298 | Combo bot: sparring against a bot that plays as a player would (ComboAi), on the arenas of the boxing (or a ring without them). |
| `DailyChallenge` | 452 | The challenge of the day: one training for everyone each day (Paris time), in turn from a list of record boards ("block-in.wood", "clutch.island"...). |
| `Duel` | 367 | A duel of two sides, red and blue, in rounds: a player who falls into the void or dies is out of the round (watching from above); when a whole side is out, the other side gets the point and the next round starts on the a |
| `DuelScore` | 44 | The score of a duel between two sides, 0 and 1: first to target points wins. |
| `EditStation` | 101 | The station of the block edit, as on the owner's Block Edit Practice map: a case of barrier blocks (glass until 27/11: the owner's ask, nothing in the view), a wall of wool five wide and three high across it, the player on one side and their enemy on the other. |
| `EndInventories` | 265 | The [View] of the recap of a duel: what a player had at its end (or when they went out of it), in a chest that nothing leaves: their inventory as they had it, the hotbar under the rest as in their own, their armour, thei |
| `EventGame` | 649 | An event a PREMIUM player hosts with /host, as on Minemen: the whole server is told every 10 seconds ([Click to join], /join) and its players keep playing (see Events); once it starts, they come to one of the sumo maps,  |
| `Events` | 902 | The events PREMIUM players host, as on Minemen: /host opens the menu of the events (Sumo, Brackets, Last Man Standing), and the whole server is told every 20 seconds, a message between two lines with its [CLICK TO JOIN], |
| `ExtendTraining` | 407 | Extend practice, the jumps of Flarefin's Clutch Training: a gap too long to jump, between two pads of quartz and wool. |
| `FastBlocks` | 252 | Many blocks of a map at once, written straight into the chunks of the server (1.8, v1_8_R3, found by reflection as BlockCracks does): no light worked out and no update block by block, which costs some 30 microseconds eac |
| `FastDefenseTraining` | 300 | Fast defense: a bed on an island, and the player covers it as fast as they can, as in a game. |
| `FindYourSensitivity` | 503 | Find Your Sensitivity (plan, step 7, as its proposal describes it): the player says the sensitivity of their Minecraft options (the comparator, 100% by default as in the game), then plays the same series at it and at two |
| `FireballFight` | 370 | Fireball fight: each side has an island and a bed under a defense of wood and wool. |
| `FireballKnockback` | 625 | The Fireball Fight of FUN PRACTICE (plan 6.1): three players at once on a big round platform over the void (the owner's ask of 22/11), each for themselves; knock the others off with fireballs that push harder than in Bed |
| `Fireballs` | 151 | The Bed Wars fireball, with the values of Hypixel (those of BedWars1058, as in RagoBedwars): thrown at speed 1 where the player looks, speeding up by 0.1 each tick (see FireballAim), it pushes the players up to 3 blocks  |
| `FireballTraining` | 651 | Fireball practice on the owner's Bed Wars maps, in a loop as the pearl practice: each attempt on the next map, at another place, in another direction, a situation of a real game found on the bridges between its islands ( |
| `FullBox` | 47 | The anti full box of Block Placement (the owner's choice of 22/11, "Interdire de s'enfermer"): a block is refused when it would close a player in completely, the four sides of their feet and of their head and the block a |
| `GameMenu` | 31 | A chest menu a game opens for one of its players (the platforms of the bridge fight...): its items can't be taken, and a click on one goes to PracticeGame#menuClick. |
| `KbDisplace` | 511 | KB displace as in a real Bed Fight top fight: a bridge one block wide, and the defender on the top of the defense, one block below the bridge, the bridge in front of them and a block behind them, nothing on the sides. |
| `KbDisplaceBot` | 208 | KB displace against a bot, the drill of the KbDisplace duel, on the same place: the bot holds its notch on the bridge, nothing on its sides, and the player starts on the gold block of the owner's map. |
| `KillBoard` | 126 | The board of the players with the most kills ever in a mode, floating in the air of the bridge fight, where everyone sees it: a title, then the ten first, each with their rank and their kills (as the board of the FFA of  |
| `LadderTraining` | 263 | Ladder practice, the block-ladder clutch of lavy's map: like a water bucket from high up, but with a block and a ladder. |
| `MapCopy` | 414 | A build copied from a world of the server into the zones of a training: a map of the owner's own (in the folder of the worlds), read once at the start. |
| `MapDefense` | 74 | The defense a map has on its beds (the owner's fireball fight map: wood, then end stone): the blocks of a game (wool, end stone, wood, glass, clay, obsidian, ladders) joined to a bed, from its height up and within #REACH |
| `MapFinder` | 937 | Finds the situations of a part of a map (see MapParts): its islands (the base, its diamond islands, the middle), the edge of each towards the others, the straight bridges through the void between them, and on them the si |
| `MapLoopTraining` | 439 | A training in a loop on the owner's Bed Wars maps (the pearl practice, the fireball practice): each attempt on the next map of the loop, at another place, in another direction, the kind of situation its level asks for (s |
| `MapParts` | 618 | Where the pearl practice and the fireball practice play: a part of each of the owner's Bed Wars maps, read once from its world, around the points of its bedwars.yml (a team's base, the one or two diamond islands nearest  |
| `MapZones` | 158 | Maps copied into the zones of a duel mode, so that each duel plays on one of them: zone n holds map n modulo their number. |
| `MatchRecap` | 202 | The recap of a duel, told to its players at its end, as the "match details" of the practice servers: for each one, the hits they landed and their best combo (hits in a row without being hit back); then, when the duel had |
| `ParkourCourse` | 226 | The course of the Parkour Duel (FUN PRACTICE, the owner's choice of 22/11): one lane per player, the same for both, side by side along x. |
| `ParkourDuel` | 174 | Parkour Duel, the race of FUN PRACTICE (the owner's choice of 22/11, "dans une map stylée"): each side on its own lane of the same course (ParkourCourse), red and blue side by side. |
| `PearlTraining` | 309 | Pearl practice on the owner's Bed Wars maps, in a loop: each attempt on the next map (each server start takes the side of one team of each map), at another place and in another direction, the kinds of situation in turn ( |
| `Ping` | 47 | The ping of a player (the 1.8 API has none: the server's own field), for the sidebars. |
| `PracticeGame` | 1344 | One game of a practice mode in its arena: its players by side, the 3-2-1 countdown, the rounds, the sidebar, and the way back to the lobby. |
| `PracticeGames` | 944 | Every practice game running, by player and by entity, and the rules every mode shares: a game's players stand still during the countdown, fall into the void under its floor, never hurt the players of other games, only pl |
| `PracticeHost` | 292 | Plays one practice mode of the lobby: each match takes a free zone of the mode's row in the arena world, built the first time it is used, and a new game of the mode in it. |
| `PracticeXp` | 122 | The Network XP of the trainings and the free arenas, where nobody wins a game: a little for each minute played, a little for each attempt, more for each success, never more than a cap a minute (falling on purpose earns n |
| `RagoPractice` | 1362 | RagoPractice: the practice modes played from the lobby. |
| `RecordBook` | 270 | The personal bests of the trainings, board by board ("clutch.single", "bridge.ninja"...): the best score of each player and the best of all, a time (lower is better) or a count (higher is better). |
| `RecordCatalog` | 197 | Every training with records, in the order of /records and of the wall of the practice records: its mode, its name (that of the lobby's menus), an icon close to the lobby's, and its boards, one per variant (the block edit |
| `Records` | 260 | The record book of the trainings in plugins/RagoPractice/records.yml: read once when the plugin starts, written after each new best, off the main thread (the text is made on the main thread, the disk is written by an asy |
| `RecordsMenu` | 669 | The records of the trainings. |
| `RedRover` | 178 | Red Rover Sumo (plan 6.5, solution 1): three friends (a party, or three players of the queue) on the sumo platform, in 1v1, in turn; the one who waits watches. |
| `RedRoverTurns` | 255 | The turns of a Red Rover (plan 6.5, solution 1), whatever its 1v1 is (a round of the sumo, a clean edit of the block edit...): who fights whom (RedRoverRotation), what the players read, the sidebar, the time limit of a 1 |
| `Sensitivity` | 272 | The measures of Find Your Sensitivity, without Bukkit: the sensitivities played around the player's own, how a flick went (an overshoot, short of the target, clean) and its corrections, a series summed up, the most preci |
| `Shapes` | 140 | The shapes the practice arenas are built from: floors, discs, beds and the layers of a bed defense. |
| `SnowballSpleef` | 322 | Snowball Spleef, the spleef of FUN PRACTICE (plan 6.1): floors of snow over the void, one under the other (two, the owner's choice of 22/11), a diamond shovel that breaks the snow at once, and a snowball for each block b |
| `Spectators` | 305 | The players who watch a game without playing it (the lobby's /spectate): in the spectator mode, which the players of the game do not see, flying near the game and touching nothing. |
| `SpeedCommand` | 57 | /speed: the Speed II of the boxing off, or back on. |
| `SumoHost` | 331 | Plays the "sumo" mode of the lobby, and its ranked twin "ranked-sumo" (plan 3.7: the same duel on the same maps, with its own queue and ELO): takes an arena slot for each match, on one of the sumo maps of the server (see |
| `SumoMatch` | 410 | One sumo duel in one arena slot: two players on a round platform, the first one off it gives a point to the other, first to N points wins (one round by default). |
| `Titles` | 40 | Title timings. |
| `Training` | 1244 | A solo training: one player, attempts one after the other (each on the arena as built), a personal best and the server's best for each of its variants, kept in records.yml. |
| `TrainingPrefs` | 197 | What each player last chose in each training, in plugins/RagoPractice/reglages.yml: the variant and the value of each setting, given back the next time they play it; and where they put the controls of the hotbar (_barre: |
| `Unbreakable` | 65 | Nothing wears out in the games: every sword, tool, bow and piece of armour of a player carries the Unbreakable tag (its line hidden). |
| `Utf8Config` | 69 | A YAML file of the plugin read as UTF-8, whatever the charset of the system. |

## Les tests (42 fichiers)
`AimTrainerTest`, `ArenaRestoreTest`, `BlockAimTest`, `BlockEditTest`, `BlockFightRulesTest`, `BlockInEnemyTest`, `BlockInRbwTest`, `BlockMapsTest`, `BowPracticeTest`, `BowTrainingTest`, `BoxingSpeedTest`, `BridgeArenaTest`, `BridgeTrainingTest`, `ClutchTrainingTest`, `ComboAiTest`, `DailyChallengeTest`, `DuelScoreTest`, `EndInventoriesTest`, `EventsTest`, `FindYourSensitivityTest`, `FireballFightTest`, `FireballKnockbackTest`, `FireballMapsTest`, `FullBoxTest`, `KbDisplaceTest`, `MapPartsTest`, `MapTrainingsTest`, `MatchRecapTest`, `ParkourCourseTest`, `PracticeXpTest`, `RecordBookTest`, `RecordsMenuTest`, `RecordsResetTest`, `RecordsWeeksTest`, `RedRoverTest`, `SettingsCheckTest`, `ShapesTest`, `SnowballSpleefTest`, `SpectatorsTest`, `SumoMapsTest`, `TrainingsTest`, `Utf8ConfigTest`

Lancer : `./gradlew :rago-practice:test` ([[Construire et tester]]).

## En attente : PR #58 (ni fusionnée ni installée)
Branche `claude/block-pattern-practice`, commits `1aa63ca` (le code) et `88ec47e` (un bot joue Block Pattern Practice dans le test des arènes : `pattern-bots.mjs`), 28/11, vers la branche de la PR #47 : trois classes en plus (Block Pattern Practice, [[Entraînements]]), et `bow-practice` puis `block-pattern-practice` enregistrés à la fin de `registerModes`.

| Classe | Lignes | Ce qu'elle fait |
|---|---|---|
| `BlockPatternPractice` | 427 | Block Pattern Practice (the owner's ask of 27/11): the techniques of his video Block Method, one level each (`BlockPatterns`), the first ten for now. |
| `BlockPatterns` | 139 | The techniques of Block Pattern Practice (the owner's ask of 27/11): the first ten of his video Block Method (#1 to #10), simplest first. |
| `PatternBot` | 129 | How RagoBot runs in Block Pattern Practice, without Bukkit: the way of its technique, then at the player, sprinting, with the 1.8 physics of a player (`ComboAi#push`). |

Test en plus : `BlockPatternsTest`.
