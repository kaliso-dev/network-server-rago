---
type: code
tags: [code, rago-lobby]
maj: 2026-11-28
code: bukkit/rago-lobby
---
# Code de rago-lobby

Le plugin **RagoLobby**, le plus gros (128 classes, ~43 000 lignes) : spawn et protection, barre d'objets et gadgets, boussole et menus, PNJ et hologrammes (par paquets, PacketEvents), files et matchmaking, parties privées et duels entre parties, stats, classements, niveau, quêtes, Daily Chest, rangs, boutique, styles, nicks, party, amis, staff, replays, spectateurs, import Hypixel, RagoBot, outils Owner. Il publie l'API des plugins de jeu (package `gg.rago.lobby.api` : `LobbyService`, `MatchHost`…). **Points d'entrée** : `RagoLobby` (onEnable, l'implémentation de `LobbyService`), `LobbySettings` (lecture de `config.yml`), `QueueController` (files), `ModeMenu` (boussole), `StatsService` / `StatsBook` (stats et ELO), `ReplayService`. Fonctions : [[Lobby]], [[Boussole et menus]], [[Statistiques et classements]]… README : `bukkit/rago-lobby/README.md` (1 055 lignes).

Packages : `gg.rago.lobby`, `gg.rago.lobby.api`. **128 classes, 42969 lignes** (branche de la PR #47, `3316b1f`, 28/11). Aucune classe ajoutée depuis le 25/11 ; la PR #61 (Block Method 1v1) a changé `BotBrain` (niveau `SOLO` : pont accroupi au bord, sortie d'une full box), `BotBody` (accroupi), `BotSettings` (section `seul`), `RagoBots` (`joinFor`) et `QueueController` (`joinAgainstBot`) ; la PR #54 (liste d'Abel du 27/11) a changé `ModeMenu` (boussole de 5 lignes, nombre de joueurs en partie sous chaque objet), `QueueController` (`playingAll`, `playingPrivate`), `LobbySettings`, `ModeCategory` et `ModeDisplay` (`icone:` avec une couleur, `regard: point`), `LobbyNpcs` et `DailyChest` (sens du point), `FirstChoice` et `Titles` (2012 Bedwars). Retour à [[Architecture]].

## Les classes
Description = la première phrase de la javadoc de la classe, **en anglais comme le code** (le code et ses commentaires sont en anglais ; les README sont en français).

| Classe | Lignes | Ce qu'elle fait |
|---|---|---|
| `AccountSkins` | 170 | The skins of the NPCs that wear the skin of a Minecraft account (skin-joueur: under an NPC of config.yml, plan 5.6). |
| `Billboard` | 160 | A billboard of the lobby as config.yml gives it (panneaux:): an image of the folder panneaux/ on a wall of maps, its size in blocks and where it stands. |
| `Billboards` | 254 | The billboards of the lobby (panneaux: of config.yml): their images, read from the folder panneaux/ of the plugin and drawn on maps at the start, off the main thread (MapArt); their walls of frames, placed with the rest  |
| `BoardStat` | 101 | What a leaderboard ranks (config.yml, classements: ... |
| `Books` | 54 | Opens a written book on a player's screen, as Hypixel does, without giving it: the client is told the book is in their hand, then to open it (MC/BOpen); a tick later it is told the real item again. |
| `BotBody` | 745 | The body of RagoBot on the server: a player of the server with no client behind it. |
| `BotBrain` | 1521 | The mind of RagoBot, without Bukkit: each tick it gets what a player sees (Sight) and gives what a player presses (Controls): forward, strafe, jump, sprint, where it looks, its clicks, its hotbar slot, eating. |
| `BotSettings` | 163 | RagoBot as config.yml sets it (ragobot:): a player of the server, invited into a party with /p invite RagoBot, who plays the party's games with it, as a real player, at one fixed skill. |
| `Brand` | 34 | The look of Rago Practice, the same everywhere (the README of the module, "Les couleurs du serveur"): red, white and grey, the colours of the logo, with red kept for the signature (the owner's ask of 22/11: less red). |
| `ClickMenu` | 127 | A chest menu whose items each run an action when clicked: the shop, the profile, the friends, the settings. |
| `Cosmetic` | 165 | A cosmetic of the shop, bought with Rago Points once a player reaches its Network level: a trail behind their projectiles, an effect where their victims die, or an effect around them when they win. |
| `CosmeticEffects` | 362 | The cosmetics at work in the games: the trail behind a player's projectiles, the effect where their victim dies, the effect around them when they win. |
| `DailyChest` | 482 | The Daily Chest of the spawn, as on Hypixel: a chest on its point ("daily", /lobby point daily, where the chest goes; point-coffre-du-jour: in config.yml names another one), with lines above it that tell each player whet |
| `DailyRewards` | 369 | What the Daily Chest gives (config.yml, coffre-du-jour:): a list of rewards, each with its rarity, what it gives (Rago Points, Network XP, or a cosmetic of the shop the player does not own yet) and its chance. |
| `DuelRequests` | 226 | /duel: a player challenges a friend in the lobby to a 1v1 mode, without the queue. |
| `EloLadder` | 138 | The ELO of the ranked modes, as on the Ranked Bedwars servers (config.yml, elo:): everyone starts at 0, and every 100 ELO is a division (Coal, Iron, Gold...). |
| `EndOfGame` (api) | 110 | The end of a game, as on Hypixel: a few seconds to see who won (the winners' victory effect) before everyone goes back to the lobby, with the Play again paper and the Leave bed in the hotbar, and the same two buttons in  |
| `FakeBillboard` | 77 | A billboard: an image on a wall of item frames, a map in each, as on Hypixel. |
| `FakeEntities` | 309 | Entities that exist only in the packets sent to each player, through PacketEvents: the NPC bodies (players with the Steve skin, which Bukkit 1.8.8 cannot spawn) and the holograms whose text differs from one player to the |
| `FakeHologram` | 112 | Lines of text floating in the air, each player reading their own: a column of small invisible armor stands whose names are the lines, first line on top. |
| `FakePlayer` | 84 | An NPC body: a player in its skin (Steve without one), hands empty (the owner's ask of 22/11), facing one way. |
| `FireballAim` (api) | 87 | Aims a fireball as the game does, for the plugins that throw them. |
| `FirstChoice` | 88 | A choice of the menu of the first arrival, "What do you want to do?" (premiere-venue: in config.yml): a mode to play at once (its queue; a training starts straight away), or a category of the compass to open. |
| `FirstJoinMenu` | 108 | The menu of the first arrival on the server, "What do you want to do?": a few choices, each a click away from playing (premiere-venue: in config.yml: a mode, whose queue it joins, a training starting at once; or a catego |
| `GettingStarted` | 183 | "Getting started", the quests of a new player, once each: play a training, beat one of your records, play a duel, import your Hypixel quick buy, find our Discord. |
| `GiftRequests` | 120 | The ranks offered and not answered yet: a player of PREMIUM (rago.perk.gift) offers a rank, the one who gets it says YES or NO in a book, within a minute; the giver pays at YES. |
| `HeadLines` | 376 | A line of text above the name of each lobby player, which they choose: their Network level (by default, plan 3.5), their ranked ELO, wins, kills or best streak, the title they wear, a message of their own (PREMIUM), or n |
| `HostWatcher` | 20 | A game plugin that is disabled closes the queues of its modes. |
| `HotbarItem` | 109 | The items of the lobby hotbar: the selector, the kit editor, a free slot (2), the gadget, the fireball, a free slot (5), the leave or play again item (6, just before the shop), the shop and the profile (which also opens  |
| `HypixelMenu` | 165 | Shows the Bed Wars settings a player imported from Hypixel: the quick buy as in the Hypixel shop (three rows of seven), and the hotbar manager in the bottom row. |
| `HypixelProfile` | 178 | A player's Bed Wars settings copied from Hypixel: the quick buy of the shop (21 slots, three rows of seven, Hypixel's item ids such as "wool" or "stone_sword") and the hotbar manager (the category of each of the 9 hotbar |
| `HypixelProfiles` | 335 | The Bed Wars settings players copy from Hypixel (the shop's quick buy and the hotbar manager), one YAML file per player in plugins/RagoLobby/hypixel. |
| `Kit` | 187 | The items a mode gives (config.yml, kit: under the mode): 36 slots, the hotbar (0 to 8) then the inventory (9 to 35), from the top row. |
| `KitEditor` | 728 | The kit editor, opened by the book of the lobby hotbar. |
| `KitItem` | 111 | One stack of a kit: "IRON_SWORD", "WOOL 64", "WOOL:14 64" in config.yml. |
| `KitLayouts` | 139 | How each player arranged the kit of each mode in the kit editor, one YAML file per player in plugins/RagoLobby/kits. |
| `LevelMenu` | 292 | The brewing stand of the lobby hotbar (/level): the player's Network level and how far the next one is, the tier they are in and the next, the rewards of the levels (got, next, to come: 21 of them around the next one), t |
| `LevelRewards` | 125 | The rewards of the Network levels (config.yml, niveau.recompenses, then niveau.prestiges): one every 5 levels up to 100 by default, then one at each prestige (200, 300... |
| `LobbyCommands` | 438 | /spawn, /queue (alias /file), /duel, /hypixel and /lobby. |
| `LobbyGadgets` | 875 | The toys of the lobby hotbar. |
| `LobbyListener` | 389 | The lobby rules. |
| `LobbyNpc` | 301 | What stands on a lobby point (config.yml, pnj: and classements:): a mode NPC that opens its modes or a whole category, a stats NPC that shows each player their own stats, the Shop, the leaderboard selector, a person of t |
| `LobbyNpcs` | 467 | The NPCs and leaderboards of the lobby, standing on the /lobby points named in config.yml. |
| `LobbyPoints` | 88 | Named spots of the lobby, placed with /lobby point while the map is built and kept in points.yml: where the mode NPCs, the leaderboards and the other decorations will stand once they exist. |
| `LobbyScoreboard` | 741 | The lobby sidebar: players online, the player's Network level and rank, the queue one waits in and for how long. |
| `LobbyService` (api) | 384 | The lobby as the game plugins see it, from Bukkit's services manager: getServer().getServicesManager().load(LobbyService.class). |
| `LobbySettings` | 1489 | The lobby's config.yml, read and checked once. |
| `LobbyState` | 227 | Where each player is, for the lobby: the lobby world and its spawn, who belongs to a game (which mode, against whom), and who is building in the lobby. |
| `LobbyText` | 79 | A text of the lobby as config.yml gives it (textes:): lines floating above its point, the same for everyone, as a hologram (FakeHologram); a click does nothing. |
| `LobbyWelcome` | 191 | The welcome of the lobby, so that a player sees at once what they can do here: at each arrival, the name and the slogan of the server, then the ways to play in the chat, one line per category of the compass (its name and |
| `MapArt` | 173 | An image drawn on the maps of a billboard (FakeBillboard): brought to columns x rows maps of 128 x 128 pixels, whole and centred (black around it when its shape is not the board's), in the colours a 1.8 client draws on a |
| `MatchHost` (api) | 130 | What a game plugin gives the lobby for each mode it plays. |
| `MineSkin` | 187 | Signs a skin through MineSkin (api.mineskin.org, v2): the PNG goes up, a Minecraft account of theirs wears it, and Mojang's signed textures come back, the only ones a 1.8 client shows on an NPC. |
| `ModeCategory` | 101 | A category of the selector, matching a zone of the lobby; or a group of a category (groupes: in config.yml), the sizes of one game shown as one item. |
| `ModeDisplay` | 119 | A mode of the catalogue with how the selector shows it. |
| `ModeMenu` | 1071 | The mode selector, a chest menu. |
| `ModeRecord` (api) | 30 | The record of a mode with records (a training), as the menus of the lobby show it under the mode in the place of its Daily Streak (LobbyService#setModeRecords): what the record is, its best players and the viewer's best. |
| `ModeStats` | 252 | One player's stats in one mode: ever, and for the current day and week (the counters of a day that is over count as zero, and start again at the next game). |
| `NameLooks` | 312 | How a player's name looks: their rank, the colour of their name (a gradient of NameStyle, or the colour of the rank) and their badge. |
| `NameStyle` | 343 | A style of a player's name: a colour of the name, one colour or a gradient along its letters (made of the 16 colours of 1.8), a badge, a short word after the name ("§c§lRAGO"), or the colour of the + of [PREMIUM+] (white |
| `NetworkCommands` | 932 | The commands of a network: the shop and the profile, friends and private messages, the leaderboards in the chat, the staff's ranks, points and sanctions, the links. |
| `NetworkLevel` | 271 | The Network level (config.yml, niveau:): every game gives XP to everyone in it (more for a win), every kill a little more. |
| `NetworkRanks` | 97 | The Network level, the title of a mode and the rank in the chat: "§6[12✫] §8[§eSumo Gold III§8] §c[ADMIN] Sofiene§f: hello", the name in the main colour of the player (light blue for VIP, pink for PREMIUM, the middle of  |
| `NextGame` | 119 | What comes after a game, as on Hypixel and Minemen: back in the lobby, [PLAY AGAIN] and the Play again paper of the hotbar queue the same mode again, and after a 1v1 that is not ranked, [REMATCH] challenges the same oppo |
| `Nicks` | 131 | /nick: a player of PREMIUM (rago.perk.nick) plays under another name. |
| `NpcSkins` | 263 | The skins of the NPCs (skin: under an NPC of config.yml). |
| `OwnerRules` | 123 | The rules of the Owner's test tools (plan 7.1, outils-owner: of config.yml), without Bukkit: who the Owner is, the reach the tool allows, where a hit lands, where the scaffold puts a block. |
| `OwnerTools` | 432 | The Owner's test tools (plan 7.1, outils-owner: of config.yml), to test the PvP and the protections of the server: Reach (the Owner's hits land up to 6 blocks away, as a reach cheat's) and Scaffold (a block of the one in |
| `PacketWarmup` | 95 | Loads, on the main thread at enable, every PacketEvents class the lobby's code names. |
| `Parties` | 701 | /party (/p), /pc and /chat, as on Hypixel: invites that last a minute and are clicked to accept, accept, leave, list, kick, transfer, disband, warp, the party chat. |
| `PartyBook` | 320 | The parties of the lobby, as on Hypixel: a player invites others (the party starts with the first invite), each invite lasts a minute, the leader kicks, transfers or disbands. |
| `PartyDuels` | 396 | Party duels (the owner's ask of 25/11: « System dans la category party, ou si on fait /duel en étant le chef d'une party avec 2 ou + on peut voir toutes les party présentes sur le server et les duel dans n'importe quel m |
| `PearlGuard` | 93 | The 1.8 pearl glitch, for every mode of the server: a pearl still in the air when its thrower dies, respawns or is sent back to a spawn still lands, and takes them from their spawn (or the spectators' point) to where it  |
| `Pedestal` | 74 | The block a figure of the lobby stands on, as on Hypixel: polished andesite, or the block its NPC has in config.yml (pnj: ... |
| `PlayerMenu` | 97 | A right click on a player in the lobby opens their menu, as on Hypixel: their head with their rank, level and ranked ELO, then a duel, their stats, a party invite, a friend request and a rank to gift them. |
| `PlayerStats` | 742 | One player's stats, mode by mode, and their profile: Rago Points, ranks, cosmetics, friends, settings and daily reward. |
| `PodiumPlace` | 125 | A step of a podium: the player at this place of what the viewer reads on the leaderboard, in their skin, facing the spawn. |
| `PrivateSetting` (api) | 87 | A setting of a private game that the party leader chooses in the lobby before the game (Bedwars Normal: Speed, Max Diamond...), as the game plugin offers it for a mode (MatchHost#privateSettings): its id, its name and ic |
| `ProfileMenu` | 319 | "My profile" (the head of the hotbar, /profile): the player's level, rank, title, points and ranked divisions, and the way to their stats, friends, settings, titles, the shop and the daily reward. |
| `Profiles` | 707 | What a player owns beyond their stats: their ranks and the permissions they give, their Rago Points, the cosmetics they bought and equipped, their daily reward. |
| `Punishments` | 479 | The sanctions of the staff: bans and mutes (for a time or for good), kicks and warnings, and the history of each player, in plugins/RagoLobby/sanctions.yml. |
| `Quests` | 353 | The quests (config.yml, quetes:): daily ones and weekly ones, longer and worth more Network XP. |
| `QuestsNpc` | 213 | The quests NPC of the spawn: a figure in the skin quetes beside the daily reward chest, facing the spawn, with lines above its head that tell each player how many of the daily and weekly quests they have done. |
| `QueueController` | 1261 | The local queues: the lobby's matchmaker and the game plugins that play each mode. |
| `RagoBots` | 466 | The RagoBots online: one per party that invited it (/p invite RagoBot), each a player of the server (BotBody) with its mind (BotBrain). |
| `RagoLobby` | 2188 | RagoLobby: the main lobby of Rago Practice. |
| `RankDef` | 271 | A rank of config.yml (rangs:): its prefix before the name ("§c[ADMIN] "), the colour of the name and of the messages, its permissions (with those of the rank it inherits), and for a rank of the shop its price in Rago Poi |
| `RankedBans` | 66 | The ranked queues closed for a while to a player who left a ranked game they still played (a forfeit: /spawn, Play again, leaving the server), as on the ranked servers: the game counts as lost, and the queues of the rank |
| `Ranks` | 279 | Every rank of config.yml (rangs:), the highest first: the order of the list is the order of the tab list, and the rank a player shows is the highest of theirs. |
| `ReplayData` | 890 | A replay as a file, and as the viewer reads it back. |
| `ReplayHook` | 84 | Hears every block that changes in a world, whoever changes it (a player, an explosion, flowing water, a plugin): a listener of the 1.8 world itself (its IWorldAccess, through which the server sends the changes to the pla |
| `ReplayRecorder` | 449 | One game being recorded: each tick, where its players are, how they look and what they hold, the blocks that changed in its part of the world and its projectiles; with the hits, swings, explosions, sounds, broken blocks  |
| `ReplayRecording` (api) | 27 | One game being recorded (see Replays#record). |
| `Replays` (api) | 23 | The replays of the games: a game plugin records each game it plays, and /replay shows it again to a player of the lobby (the players, their hits, their blocks and projectiles, the game's messages). |
| `ReplayService` | 1304 | The replays: each game a game plugin records (#record) is kept in plugins/RagoLobby/ replays/(id).replay, and /replay shows a player their last games, or any game by its code. |
| `ReplaySetup` (api) | 129 | What a replay records and where it is shown again: the mode and the map, the world of the game and the part of it that belongs to the game, and how to build the map again for the replay. |
| `ReplayStages` (api) | 22 | The worlds a game plugin makes for the replays of its games: a world holding the map as at the start of a game, used by one replay at a time, the positions of the replay being its own. |
| `ReplayViewer` | 847 | One player watching a replay: the game's players are bodies sent to their client alone (through PacketEvents), moved each tick as they moved, with their items, hits and swings; the blocks change in the stage world as the |
| `Season` | 143 | The ranked season (config.yml, saison:), as on the Ranked Bedwars servers: its number, its name and the day it ends. |
| `ServerInfo` | 278 | What a real server shows around the game: the header and footer of the tab list (the players online, the player's ping), the message of the server list, the announcements of the lobby, the arrival of the players in the l |
| `ServerSettings` | 913 | What makes the lobby a network, from config.yml: the ranks (rangs), the colours of name and the badges (styles), the ELO divisions (elo), the Rago Points (rago-points), the prices of the shop (boutique), the chat rules ( |
| `ShopMenu` | 419 | The Rago Shop (the emerald of the hotbar, /shop): the ranks and the cosmetics, bought with Rago Points once the player reaches their Network level. |
| `Skin` | 105 | A player's skin as Mojang signs it: the "textures" property of their profile. |
| `Skins` | 109 | The skins the podiums wear. |
| `Social` | 410 | Friends and private messages, as on Hypixel: /friend add, accept, deny, remove, list; /msg and /r; who may message, invite or challenge a player (their settings in the profile menu). |
| `SpawnPoint` | 88 | The lobby spawn, kept in spawn.yml in the lobby world's folder, so that it travels with the map. |
| `Spectating` | 99 | /spectate: watching any game, open to every player (only the game of a player under a nick stays for the staff and that player's friends, so that the nick keeps who they are hidden). |
| `StaffPanel` | 214 | The staff panel (/staff, or the Staff button of a player's menu): the players online, then for one of them a click to teleport to them or bring them, freeze them, mute, kick, warn or ban them with a reason and a length,  |
| `StaffTools` | 306 | The staff's tools and the guard of the chat. |
| `StarterPack` | 175 | The starter pack of the web store (section pack-depart of config.yml): a lot of Rago Points, XP and coin boosters and cosmetics without their level, once per player, the cheapest thing sold. |
| `StatsBook` | 720 | Every player's stats, in memory: what the games record, what the stats NPCs and the leaderboards read. |
| `StatsDisplays` | 789 | What the stats show in the lobby, each player reading their own:  a stats NPC: a body in its skin, with the player's profile floating above its head (Network level and rank, wins, losses, kills, best streak, wins of the  |
| `StatsFiles` | 383 | One player's stats as a YAML file (plugins/RagoLobby/stats/&lt;uuid&gt;.yml), and the team win streaks of each mode (plugins/RagoLobby/stats/equipes/&lt;mode&gt;.yml), until the network's database exists. |
| `StatsMenu` | 465 | The menu of a stats NPC: the player's head with their totals, then the categories of the selector (Practice, RBW Modes, Duels, Arenas), each with the player's totals in it; a click on one shows its modes with the player' |
| `StatsPeriod` | 92 | The periods of the stats and leaderboards, in the time of Paris: the day starts at midnight, the week on Monday at midnight. |
| `StatsService` | 821 | The stats on the local test server: the StatsBook in memory, one YAML file per player in plugins/RagoLobby/stats. |
| `StoreMenu` | 217 | The Shop NPC of the centre of the lobby (and /pack): the store of a player who starts, on one page. |
| `StyleMenu` | 330 | The colours of name and the badges (/style, the profile, the shop): how the player looks in the chat, then every colour, those they own to wear (a click), the others with how to get them: the PREMIUM rank, the store, Rag |
| `TeamRoom` | 166 | The teams a party chooses before its private game of Bedwars Normal (the owner's answers of 22/11): the teams of the mode, in the order of the map's colours (8 teams of 2 on a map of 2s, 4 of 4 on a map of 4s), each play |
| `TeamRooms` | 1067 | The private games whose players choose their teams (parties-privees-equipes: Bedwars Normal, the owner's answers of 22/11). |
| `TeamStreak` | 83 | The win streak of one team in one mode (one player in a 1v1, two in a 2v2...): its wins in a row, and the longest run it reached ever, during the day and during the week. |
| `Titles` | 322 | The titles of the modes, earned with wins ("Sumo Gold III"): each title counts the wins of its modes together (Bed Fight: its 1v1, its 2v2 and the top bed fight), and climbs steps (Bronze to Mythic) of five levels each ( |
| `TitlesMenu` | 187 | /titles: every title of the modes, with the player's wins in it, the title they have and the next one; a click wears one of theirs, their best (by default) or none. |
| `Utf8Config` | 69 | A YAML file of the plugin read as UTF-8, whatever the charset of the system. |
| `Visibility` | 67 | Who sees whom: everyone sees everyone, but a vanished member of the staff stays hidden from everyone but the staff, in the lobby and in the games. |
| `VoidGenerator` (api) | 40 | Generates nothing: every chunk is air. |
| `WeeklyRewards` | 517 | The leaderboards of the week, rewarded. |
| `XpBoosters` | 317 | The boosters (/booster), each for 30 minutes of play, the time running only while the player is in a game. |

## Les tests (58 fichiers)
`BillboardTest`, `BoostersTest`, `BotBrainTest`, `BotBrainTravelTest`, `BotSettingsTest`, `ConfigAsWrittenTest`, `DailyRewardsTest`, `DuelRequestsTest`, `EndOfGameTest`, `FireballAimTest`, `GameSidebarTest`, `GettingStartedTest`, `GiftRequestsTest`, `HeadLineNickTest`, `HypixelMenuTest`, `HypixelProfileTest`, `HypixelProfilesTest`, `KitEditorTest`, `KitTest`, `LayoutTest`, `LevelQuestTest`, `LobbyGadgetsTest`, `LobbyPointsTest`, `LobbySettingsTest`, `LobbyTextTest`, `LobbyWelcomeTest`, `MapArtTest`, `MineSkinTest`, `NameStyleTest`, `NetworkFeaturesTest`, `NetworkLevelTest`, `NextGameTest`, `OwnerRulesTest`, `OwnerSettingsTest`, `PacketWarmupTest`, `PartyBookTest`, `PedestalTest`, `PersonNpcTest`, `PingCommandTest`, `PodiumTest`, `PrestigeTest`, `PrivateSettingTest`, `ProductNpcTest`, `QuestDrawTest`, `QuestsNpcTest`, `RankedBansTest`, `ReplayDataTest`, `SeasonTest`, `SpawnPointTest`, `SpectatingTest`, `StarterPackTest`, `StatsBookTest`, `StatsDisplaysTest`, `StatsMenuTest`, `StatsPeriodTest`, `TeamRoomTest`, `TitlesTest`, `Utf8ConfigTest`, `WeeklyRewardsTest`

Lancer : `./gradlew :rago-lobby:test` ([[Construire et tester]]).
