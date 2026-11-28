---
type: code
tags: [code, rago-bedwars]
maj: 2026-11-28
code: bukkit/rago-bedwars
---
# Code de rago-bedwars

Le plugin **RagoBedwars** (60 classes, ~21 600 lignes) : tout ce qui se joue sur une map de Bed Wars. **Points d'entrée** : `RagoBedwars` (onEnable), `BedwarsHost` (le `MatchHost` de tous les modes, choix de la map et de la copie), `BedwarsGame` (la partie classique, 2 950 lignes), `BedwarsSettings` (`config.yml`), `BedwarsMap` et `MapCommands` (`bedwars.yml`, `/bw`), `Instances` / `Instance` (les copies `bw_<map>_<n>`). Chaque autre jeu a sa classe (`FfaGame`, `InvisGame`, `BedBreakGame`, `CaptureTheFlagGame`…). Fonction : [[Bed Wars]]. README : `bukkit/rago-bedwars/README.md` (808 lignes).

Packages : `gg.rago.bedwars`. **60 classes, 21649 lignes** (branche de la PR #47, `03beb92`, 28/11 ; `Bedwars2012Game` ajoutée par la PR #54, avec `ShopCatalog.clayForWool` et le lit caché d'une équipe dans `BedwarsGame`). `FastBedwarsGame` (2017 Bedwars) reste, caché des menus depuis le 27/11, pour les bots de la CI. Retour à [[Architecture]].

## Les classes
Description = la première phrase de la javadoc de la classe, **en anglais comme le code** (le code et ses commentaires sont en anglais ; les README sont en français).

| Classe | Lignes | Ce qu'elle fait |
|---|---|---|
| `ArmourHider` | 76 | The armour of an invisible player, hidden from the others as on Hypixel, where an invisibility potion makes a player wholly invisible. |
| `AttackDefenseGame` | 751 | Attack and Defense, on the owner's maps of it (Bio Hazard first): one base and its bed, the defenders' (gray on Bio Hazard), and the bridge the attackers come by (pink), nothing else. |
| `BedBreakGame` | 969 | Bed Break Simulator, a situation of Ranked Bedwars: 2 to 5 players on the map chosen (Archway or Invasion), in rounds of three minutes, on two opposite islands. |
| `BedFightGame` | 355 | Bed Fight, 1v1 or 2v2, as on Minemen: two islands face to face, each with its bed under the defense the map has on it (wood and end stone on the owner's Bed Fight map), which the players break as in a game. |
| `Bedwars2012Game` | 325 | 2012 Bedwars (the owner's ask of 27/11), as a rush of 2012: two great bases far apart, the emeralds in the middle and the diamonds on an island on each side (the version with them), or the forges of the bases only (the classic version). |
| `BedwarsGame` | 2950 | One Bed Wars game in one game world: the teams on their islands, their beds, generators and shops, the deaths, the beds broken and the final kills, the timed events (the diamond and emerald generators going up a tier, th |
| `BedwarsHost` | 1873 | Plays the Bed Wars modes of the lobby: takes a free copy of a map for each match (or has one made, and the match waits in the queue for a second), starts the game, and applies the Bed Wars rules in the game worlds: block |
| `BedwarsMap` | 1049 | A Bed Wars map: a world of the server's world folder, with the points of the game kept in #FILE_NAME inside the world's own folder, placed in game with /bw. |
| `BedwarsSettings` | 1012 | The settings of config.yml, checked. |
| `BlockDecay` | 59 | The blocks placed in an open game go after a while, oldest first, so that the map never fills up: what the map had there comes back. |
| `Bridges` | 402 | The bridges a Clutch Scenario game starts with, as a real game has them at 18 minutes: from the base of each team, one to its diamond island and one to mid, and the one it rushed the enemy base by, from mid; one block wi |
| `CaptureFlags` | 193 | The flags of a Capture the Flag game and its score, without Bukkit: each team has its flag, at its base, carried by an enemy, or dropped where its carrier died (back at its base when one of its team touches it, or after  |
| `CaptureTheFlagGame` | 591 | Capture the Flag (plan 6.2), in its two kinds, the owner's two of 22/11: « 1. |
| `ClutchScenarioGame` | 274 | Clutch Scenario: a real Ranked Bedwars game (1v1, 1v2, 1v3, 1v4, as Invis Practice and the Bed Break Simulator: the owner's choice, 22/11) on Sky Rise or Archway (the maps of the mode in config.yml), the two teams on opp |
| `CtfMaps` | 452 | The maps of Capture the Flag, made by the plugin the first time a mode plays them (as the demo map), red in the west and blue in the east, over the void:     CtfBridge, for the CTF of The Bridge: two bases of quartz face |
| `Defense` | 274 | A bed defense built by the game, as players build theirs: layer after layer around the bed, the first layer touching it (the 8 blocks around and on top), each next one around the last (26 blocks for two layers, 58 for th |
| `DemoMap` | 100 | A small map of our own, made by the plugin the first time it starts, so that a game can be played before any real map is set up: two islands facing each other (red in the west, blue in the east), each with its spawn, bed |
| `Disk` | 94 | Files of the plugin and of the game worlds. |
| `FastBedwarsGame` | 59 | Fast Bedwars (plan 6.1: « un Bedwars simplifié, inspiré du Bedwars de 2018, rapide, simple et amusant »), as proposed on 21/11: a game of Bed Wars on the maps of Bed Wars, made fast by its forges (forge.modes in config.y |
| `FastShopGame` | 503 | Fast Shop: buying in the item shop as fast as possible, with the player's own quick buy (copied from Hypixel in the lobby: the emerald of the kit editor, or /hypixel import name). |
| `FastShopRecords` | 219 | The best times of Fast Shop, list by list: each player's best, and the name they had then, kept in fastshop.yml of the plugin's folder, written away from the main thread. |
| `FfaGame` | 979 | A free for all on a Bed Wars map, open to everyone: players join and leave when they like, each for themselves, and come back at once after a death (in the arena, three seconds after it), on the island farthest from the  |
| `FfaSpawnMenu` | 84 | The menu of the FFA where a player chooses where they spawn: each spawn of the map (apparitions: in its bedwars.yml: "Top bridge", "Low", "Mid"...), then Random, among all of them, the bases and the diamonds. |
| `FireballBlast` | 66 | The blast of a fireball that breaks blocks. |
| `Generator` | 179 | Drops an item on its spot every so many ticks, unless enough of them lie there already. |
| `Instance` | 228 | A copy of a map, loaded as a world of its own, where one game is played at a time. |
| `Instances` | 337 | The game worlds: copies of the maps, named bw_&lt;map&gt;_&lt;n&gt;, each playing one game at a time and made ready again after it (see Instance). |
| `InvisGame` | 825 | Invis practice: from 1v1 to 1v4, 2 to 5 players on a Bed Wars map (the 20 maps of the RBW queue), in rounds of three minutes. |
| `KillBoard` | 84 | The board of the players with the most kills ever in a mode, floating in the air of an FFA beside the fight, where everyone sees it: a title, then the ten first, each with their rank and their kills. |
| `KillLines` | 40 | The lines of a final kill and of a bed broken that show the author's own count, every game together: "vaggww78 was qAbel's final #41,000. |
| `KitMenu` | 108 | The menu of the kits of a game of rounds (Attack and Defense, the Bed Break Simulator), before a round: the kits of the player's side this round (attack or defense) on the middle row, each with what it gives; the one cho |
| `Lineup` | 104 | How many players a game in rounds is for (the invis practice, the Bed Break Simulator), one of them attacking the others in turn. |
| `LobbyFight` | 285 | Lobby Fight, the fight in the waiting lobby of a Bed Wars map (salle-attente: in its bedwars.yml, see BedwarsMap.WaitingLobby): in rounds of one life (LobbyFightGame, 1v1), or for everyone (FfaGame, the FFA). |
| `LobbyFightGame` | 146 | Lobby Fight 1v1: the rounds of Top Bridge in the waiting lobby of the map (salle-attente: in its bedwars.yml; Archway FFA has one, its glass floor high over the middle), with the kit of Lobby Fight (lobby-fight-ffa: in t |
| `MapCommands` | 514 | /bw: sets up the Bed Wars maps, in the map's own world. |
| `MapDraw` | 59 | The map of the next "any map" game of each mode, drawn at random among the mode's maps. |
| `MapLeftovers` | 41 | What a map captured on a server kept of the game played there when it was saved: the iron, gold and emeralds lying on its forges and generators, the invisible armour stands of its holograms (ITEM SHOP, Tier I Diamond, Sp |
| `MapPoint` | 144 | A place in a map: a position and where it looks. |
| `MapStore` | 175 | The Bed Wars maps of the server: every folder of the world folder that holds a BedwarsMap#FILE_NAME, read when the plugin starts. |
| `NpcSpot` | 141 | Where a shop or upgrades villager really stands. |
| `PrivateRules` | 276 | The settings the party leader chose for a private game of Bedwars Normal (reglages-prives in config.yml); each is as in a normal game until set. |
| `PrivateWaitingLobby` | 82 | The waiting lobby of a private game of Bedwars Normal (the owner's ask of 23/11: « ça doit directement TP tous les joueurs au lobby d'attente bedwars de base et c'est là-bas que le host doit faire les settings de la map  |
| `RagoBedwars` | 350 | RagoBedwars: the Bed Wars modes played from the lobby (Ranked Bedwars 1v1 to 4v4, RBW on Sky Rise, First Rush), each on its own maps, set up with /bw or the map tool, each game in a copy of its map. |
| `RedRoverTopBridgeGame` | 404 | Red Rover Top Bridge (plan 6.5, solution 1), as the Red Rovers of RagoPractice: three players or more (one team of the lobby's queue) on the top bridge, in 1v1, in turn; who waits watches above the bridge. |
| `ReplayWorlds` | 69 | The worlds of the replays of the Bed Wars games (/replay of the lobby): a free copy of the game's map, taken as a game takes one, and given back to the games when the replay is over. |
| `RoundKit` | 176 | A kit a player chooses before a round of Attack and Defense or of the Bed Break Simulator: its name, its icon, a few words, and its items, those of the item shop by their id, with their amount ("wool 64", "fireball 3", g |
| `ShopCatalog` | 309 | What the item shop sells, by category, at the usual Bed Wars prices of a solo or doubles game. |
| `ShopItem` | 305 | Something the item shop sells: what it gives, for how much, in which category, and how it is given (a block in the team's colour, a sword that replaces the wooden one, armour kept until the end, a tool with tiers...). |
| `ShopMenu` | 167 | The item shop, laid out as on Hypixel: the Quick Buy and the categories along the top row, a row of glass under them (green under the one open), then three rows of seven items. |
| `SpawnProtection` | 27 | The protection of a player who has just respawned (Bed Fight, as on Minemen): for a moment, no enemy can hurt them; it ends at once when they hit an enemy themselves (they engaged a fight). |
| `TeamColor` | 91 | The teams a map can have, in the order they are listed: their id in bedwars.yml and in the commands, their name, their chat colour, the data value of their wool, clay and glass, and the colour of their leather armour. |
| `TopBedFightGame` | 177 | Top Bed Fight, the owner's mode on his Bed Fight map: the two players face each other on the bridge he built on top, at the build height limit, each with a block of their colour at their back. |
| `TopBridge` | 326 | The top bridge of a Top Bridge game, as the owner built it on Antenna: high above the middle of the map, a platform of wool 17 blocks wide, each half in the colour of the team on its side and a white block in the middle; |
| `TopBridgeGame` | 474 | Top Bridge, as the owner plays it: no bed, no forge, no shop, only the fight on the platform of the top bridge (the one he built on Antenna, on any map). |
| `Unbreakable` | 65 | Nothing wears out in the games: every sword, tool, bow and piece of armour of a player carries the Unbreakable tag (its line hidden). |
| `UpgradeMenu` | 212 | The menu of the upgrades NPC, laid out as on Hypixel: the team upgrades on the left, the traps on the right, a row of glass, then the three places of the trap queue. |
| `Upgrades` | 207 | The team upgrades and traps of a team, bought with diamonds at the upgrades NPC, as on Hypixel: Sharpened Swords (Sharpness I on the swords and axes), Reinforced Armor (Protection I to IV), Maniac Miner (Haste I and II), |
| `Utf8Config` | 69 | A YAML file of the plugin read as UTF-8, whatever the charset of the system. |
| `Utilities` | 427 | The special items of the shop, as on Hypixel. |
| `Watchers` | 317 | Those who watch a game without playing it (the lobby's /spectate): in the spectator mode, which the players do not see, flying in the game's world and touching nothing, not even a chest. |

## Les tests (35 fichiers)
`AttackDefenseGameTest`, `BedBreakGameTest`, `BedFightGameTest`, `Bedwars2012GameTest`, `BedwarsMapTest`, `BedwarsModesTest`, `BedwarsSettingsTest`, `BridgesTest`, `CaptureFlagsTest`, `CaptureTheFlagGameTest`, `ClutchScenarioGameTest`, `CtfMapsTest`, `DemoMapTest`, `FastBedwarsGameTest`, `InstanceTest`, `InvisGameTest`, `KillLinesTest`, `LineupTest`, `LobbyFightTest`, `MapDrawTest`, `MapLeftoversTest`, `MapStoreTest`, `NpcSpotTest`, `PrivateRulesTest`, `QuickDepositTest`, `RedRoverTopBridgeTest`, `ShopCatalogTest`, `SidebarModeTest`, `SpawnProtectionTest`, `TopBedFightGameTest`, `TopBridgeTest`, `UpgradesTest`, `Utf8ConfigTest`, `UtilitiesTest`, `WatchersTest`

Lancer : `./gradlew :rago-bedwars:test` ([[Construire et tester]]).
