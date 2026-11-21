# Rago Practice : suivi du développement jusqu'à la sortie publique

Document de suivi du plan complet de Sofiene (21/11/2026). Chaque demande du plan y est une
tâche ; aucune n'est retirée parce qu'elle a commencé. Une tâche n'est **TESTÉ** que lorsque son
fonctionnement a été vérifié (tests automatiques, bots en CI, ou essai en jeu signalé comme tel).

Statuts : **À FAIRE**, **EN COURS**, **IMPLÉMENTÉ** (code écrit, compilé et testé
automatiquement, essai en jeu à faire), **TESTÉ**, **BLOQUÉ** (il manque une information ou une
décision). Priorités : **P0** bug critique, **P1** avant la sortie, **P2** souhaité pour la
sortie, **P3** après la sortie ou étude.

Ordre d'exécution imposé : Étape 1 audit, Étape 2 bugs critiques, puis les étapes 3 à 9 dans
l'ordre. Les nouveaux modes (Étape 7) ne commencent qu'une fois les systèmes communs stabilisés,
et chaque concept est d'abord proposé.

---

## 1. Audit (Phase 0)

### 1.1 Architecture

Un serveur PandaSpigot 1.8.8 (build 226, joué en 1.8.9), plugins du dépôt :

| Module | Rôle | Taille |
| --- | --- | --- |
| `combat/rago-combat-model` | calcul du knockback 1.8.8 et de la trajectoire client, sans dépendance | — |
| `combat/rago-combat-engine` | plugin RagoCombatEngine : profils de KB (`hypixel-approx-v1` verrouillé), `/kb` | — |
| `game/rago-game-model` | modes, files, matchmaking (ELO classé), grille d'arènes, défense de lit | ~1 300 lignes |
| `bukkit/rago-lobby` | RagoLobby : lobby, PNJ, files, stats, leaderboards, ranks, quêtes, replays, parties, spectate, kits, cosmétiques, shop | ~35 000 lignes, 110 classes |
| `bukkit/rago-practice` | RagoPractice : duels, trainings, arènes ouvertes, events `/host` | ~25 000 lignes, 66 classes |
| `bukkit/rago-bedwars` | RagoBedwars : Ranked Bedwars, Bed Fight, Top Bridge, FFA, Lobby Fight, Invis, Bed Break, Clutch Scenario, Attack/Defense, Fast Shop | ~20 000 lignes, 50 classes |
| `tools/build-tools` | RagoBuildTools : mondes de build, créatif, protection, sauvegarde toutes les 2 min (**serveur de test seulement**) | — |
| `tools/lobby-check`, `kb-control` | tests sur un vrai serveur avec des bots, lancés par la CI | — |
| `site/` | site web (Netlify, `netlify.toml`) | — |

Dépendances externes : PacketEvents 2.14.0 (PNJ, hologrammes, lignes au-dessus des têtes,
nicks, replays), WorldEdit 6.1 (serveur de test).

Données sur ton PC (serveur `rago-serveur-de-test (19)`, mondes dans `C:\Users\rgabe\Rago\mondes`) :
`plugins/RagoLobby/stats/` (stats des joueurs), `kits/` (kits arrangés), `replays/`,
`semaine.yml`, `skins.yml`, `sanctions.yml`, `panneaux/` (visuels publicitaires),
`mondes/lobby/points.yml` (points des PNJ placés par `/lobby point`), records de RagoPractice.

### 1.2 Systèmes partagés (à corriger une seule fois)

| Système commun | Où | Utilisé par |
| --- | --- | --- |
| Reset des arènes **practice** : `Arena` garde chaque bloc changé (pose, casse, explosion) et le remet à la fin de la manche et de la partie ; le monde `arenes` est recréé à chaque démarrage | `rago-practice/Arena.java`, `PracticeGames.java` (onPlaced, onBroken, onExplode), `PracticeGame.restoreArena`, `MapZones`, `MapCopy` | Block placement 1v1/2v2, Block edit (duel et bot), Block In, Clutch, Fast defense, Extend, Bridge, Fireball fight, toutes les trainings |
| Reset des maps **Bed Wars** : une copie de la map par monde (`Instance`), blocs posés et lits remis après chaque partie | `rago-bedwars/Instance.java`, `Instances.java` | Ranked Bedwars, Bed Fight, Top Bridge, Lobby Fight, Invis, Bed Break, Clutch Scenario, Fast Shop |
| Spectateurs (`/spectate`) | `rago-lobby/Spectating.java` (droits, état), `rago-practice/Spectators.java`, `rago-bedwars/Watchers.java` | tous les modes |
| Statistiques | `rago-lobby/StatsService.java`, `PlayerStats.java`, `ModeStats.java`, `StatsFiles.java`, `StatsBook.java`, `StatsPeriod.java` ; appels `recordMatch` / `recordKill` des modes | tous les modes, leaderboards, menus, quêtes, niveau |
| Leaderboards et podiums | `StatsDisplays.java`, `BoardStat.java`, `PodiumPlace.java`, `addWeeklyBoard` | lobby |
| Records practice | `rago-practice/Records.java`, `RecordBook.java`, `RecordCatalog.java`, `RecordsMenu.java` | toutes les trainings |
| Kits | `rago-lobby/KitEditor.java`, `KitLayouts.java`, `Kit.java` ; `rago-bedwars/RoundKit.java`, `KitMenu.java` | tous les modes |
| Visibilité, noms, lignes au-dessus des têtes | `Visibility.java`, `Nicks.java`, `HeadLines.java`, `LobbyScoreboard.java`, `NameStyle.java` | lobby et parties |
| Files et matchmaking | `QueueController.java`, `rago-game-model` (`ModeQueue`, `Matchmaker`) | tous les modes |
| PNJ, hologrammes, skins | `LobbyNpcs.java`, `LobbyNpc.java`, `FakePlayer.java`, `FakeHologram.java`, `NpcSkins.java`, `MineSkin.java`, `points.yml` | lobby |
| Fireballs et TNT | `rago-practice/Fireballs.java`, `PracticeGame.tntBlast` ; `rago-bedwars/FireballBlast.java`, `BedwarsGame` ; `rago-lobby/api/FireballAim.java` | Bed Wars, Fireball fight, trainings, Lobby Fight |
| Replays | `ReplayService.java`, `ReplayRecorder.java`, `ReplayViewer.java`, `ReplayData.java` | duels, Bed Wars |
| Events `/host` | `rago-practice/Events.java`, `EventGame.java`, `PracticeHost.java`, `rago-lobby/HostWatcher.java` | Sumo, Brackets, LMS |

### 1.3 Données à ne pas supprimer

Stats des joueurs, kits arrangés, records (sauf ceux de Block In, dont la remise à zéro est
demandée, après sauvegarde), replays, ranks et sanctions, `points.yml` du lobby, skins en cache,
mondes du dossier `mondes`. Le monde `arenes` est, lui, refait à chaque démarrage par
RagoPractice (fichier `rago-arenas.txt`) : rien n'y est à garder.

Avant chaque installation sur ton PC : copie du dossier `plugins/` du serveur.

### 1.4 Conflits et points à trancher dans le plan

| # | Point | Proposition |
| --- | --- | --- |
| C1 | 6.1 demande un nouveau **Fireball Fight**, mais un mode `fireball` existe déjà (`FireballFight.java`, map « Fireball Fight »). | Améliorer le mode existant (arène plus grande que Sumo, KB fireball plus fort, souffles de vent) plutôt qu'en créer un second. À confirmer. |
| C2 | 2.1 Lobby Fight : les PR #43/#44 ont réduit le kit FFA à « une épée, 3 fireballs, 5 pommes ». Le plan ajoute 12 end stone, une pioche en bois et Haste, et « 3 manches ». | Confirmé : le même kit pour le 1v1 et le FFA ; les manches ne concernent que `lobby-fight-1v1`, gagné par le premier à 3 manches (« Bo3, le premier à 3 win »). |
| C3 | 2.3 Bed Break : « réalistes, style Ranked Bedwars » et « sur les côtés, pas en diagonale comme en Ranked Bedwars ». | Confirmé : « droit, pas en diagonale ». Fait : le bridge de l'attaquant, un bloc de large, droit, un seul virage au plus. |
| C4 | 3.5 Network Level par défaut au-dessus des joueurs : aujourd'hui la ligne par défaut est l'ELO (« Unranked »). 3.6 garde l'ELO en Ranked. | Défaut Network Level partout, ELO en partie classée. |
| C5 | 1.2 « Spectator pour tous les joueurs » et les nicks (qui cachent qui joue). | Fait : tout le monde peut regarder toute partie, sauf celle d'un joueur sous nick (staff et amis seulement). |
| C6 | 4.5 `/discord` et `/store` existent déjà, avec `discord.gg/ragooptimizer` et `rago-optimizer.com`. | Il manque les liens officiels de Rago Practice. |
| C7 | 5.6 « jncl : nom à afficher : directemeent ». | Je lis « affiché directement : jncl ». À confirmer. |
| C8 | 7.1 Reach et Scaffold sont des triches. | Réservés au compte Owner (UUID), désactivés par défaut, jamais en partie classée ni comptée. |

### 1.5 Ce qui manque (à fournir, rien n'est inventé)

- Lien officiel du Discord de Rago Practice, et du Store (4.5, 5.7, 8.2).
- Liens officiels de Rago Client et de Rago Optimizer, et les skins voulus pour leurs PNJ (5.6 A).
- Pseudo exact du compte « Woryes » pour le skin de `nativ` (5.6 C).
- L'image de référence du lobby (5.2) : elle n'est pas dans les fichiers du projet.
- Les spawns Hypixel de référence pour First Rush (1.7) : tu as donné les maps (« Gelato, Nebuc
  etc. ») ; il manque la position de référence de chaque spawn (capture F3 sur Hypixel, ou
  coordonnées).
- Ce qui ne marche pas sur la forge de Bed Break Simulation sur Invasion (1.6) : la map est
  donnée, ses 4 points de forge sont justes (voir le journal).
- ~~Les étapes exactes du bug de reset (1.1)~~ : répondu (« Block Placement, Block edit, après
  quelques games, même avec un redémarrage ») ; bug reproduit et corrigé.
- ~~Pour la visibilité au lobby (1.3)~~ : répondu (« quand le joueur rejoint le serveur, et
  seulement de mon point de vue ») ; bug reproduit par un test réel et corrigé.
- Probabilités et cosmétiques du Daily Chest (5.5) : je proposerai des valeurs, à valider.
- Hébergement (8.1) : budget 30 €/mois confirmé ; achat du domaine (8.2) : à faire par toi.

Les PNJ nommés dans le plan ont bien un point dans `mondes/lobby/points.yml` : `newquest`,
`newshop`, `newdailychest`, `newleaderboardselector`, `ragoclient`, `ragooptimizer`, `qrago`,
`kalisodev`, `jncl`, `nativ`, `funmod2`, `fpsbenchmark`, `textremerciement`. Ils ne sont pas
encore reliés à un PNJ dans `config.yml`.

---

## 2. Checklist globale

Colonnes : n° du plan, tâche, catégorie, priorité, statut, fichiers, dépendances, tests,
informations manquantes.

### Étape 2 : bugs critiques (Phase 1)

| # | Tâche | Cat. | Prio | Statut | Fichiers | Dépend de | Tests | Manque |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | Reset des arènes (Block Placement 1v1, Block Edit, autres) | Arènes | P0 | IMPLÉMENTÉ | `Arena`, `PracticeGames`, `PracticeGame`, `MapZones`, `MapCopy` ; `Instance` (Bed Wars) | — | test réel en CI (`run-arena-test.sh`, 2 bots) ; en jeu sur tes maps | — |
| 1.2 | Spectator : jamais en Survival, intouchable, invisible, retour au lobby, ouvert à tous, message aux joueurs | Social | P0 | IMPLÉMENTÉ | `BuildListener` (build-tools), `Spectators` (practice), `Watchers` (bedwars), `Spectating`, `LobbyService`, `RagoLobby` | — | tests unitaires ; en jeu : /spectate d'un duel et d'une partie Bed Wars, se faire viser, /kill, /spawn | — |
| 1.3 | Joueurs invisibles au spawn (seul le nametag « UNRANKED » visible) | Lobby | P0 | IMPLÉMENTÉ | `LobbyListener` | — | test réel en CI (`visibility-bots.mjs`, 2 bots, 5 arrivées) ; en jeu : rejoindre le serveur pendant qu'un autre attend au spawn | — |
| 1.4 | Audit complet des statistiques (kills, games played, double comptage, par mode) | Stats | P0 | IMPLÉMENTÉ | `StatsBook`, `StatsService`, `LobbyService`, `RagoLobby`, `BedwarsGame`, `Lineup`, `BedBreakGame`, `InvisGame`, `FfaGame`, `FastShopGame`, `TopBridgeGame`, `Duel`, `BlockFight`, `FireballFight` | — | tests unitaires ; en jeu : voir le journal | — |
| 1.5 | Kit Bedfight réinitialisé à la mort dans le vide | Kits | P0 | IMPLÉMENTÉ | `BedFightGame`, `BedwarsGame.kill` | — | tests unitaires ; en jeu : ranger sa barre en partie, mourir par un coup puis dans le vide | — |
| 1.6 | Forges de Bed Break Simulation sur certaines maps | Bed Wars | P0 | EN COURS | `BedBreakGame`, `Generator`, `BedwarsMap`, `bedwars.yml` des maps | — | vérifier chaque map | la map est Invasion ; il manque ce qui ne marche pas sur sa forge |
| 1.7 | Spawns First Rush trop en avant | Bed Wars | P0 | BLOQUÉ (cause trouvée) | `bedwars.yml` des maps (Gelato, Nebuc…), `tools/bedwars-maps` | — | en jeu sur chaque map | le point Hypixel sur une île par map (`/bw spawn red`), recopié ensuite sur les 7 autres |
| 1.8 | Block In réaliste (casser, frapper seulement par le trou, recasser) + reset des records | Practice | P0 | IMPLÉMENTÉ | `BlockInEnemy`, `BlockInTraining`, `Records`, `RagoPractice` | 1.1 | tests unitaires (`BlockInEnemyTest`, `RecordsResetTest`) ; test réel en CI (`blockin-bots.mjs`) ; en jeu sur ta map | — |

### Étape 3 : modes existants (Phase 2)

| # | Tâche | Cat. | Prio | Statut | Fichiers | Dépend de | Manque |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2.1 | Lobby Fight : 12 end stone, pioche bois, Haste, 5 pommes, blocs qui partent après 10 s (pas ceux du lobby), 3 manches | Modes | P1 | IMPLÉMENTÉ | `LobbyFight`, `LobbyFightGame`, `FfaGame`, `BlockDecay`, config du lobby | 1.1 | tests unitaires (`LobbyFightTest`, `LobbySettingsTest`) ; en jeu sur Archway FFA (voir le journal) |
| 2.2 | Bedfight : protection au respawn de la durée exacte de Minemen, coupée dès un combat ; kit gardé | Modes | P1 | BLOQUÉ | `BedFightGame`, `BedwarsGame`, `BedwarsHost` | 1.5 | durée Minemen (non publiée) : une partie enregistrée avec kb-recorder |
| 2.3 | Bed Break Simulation : bridges refaits, pas de blocs en haut de la map, cisailles + hache, défense RBW (end stone + verre), forges | Modes | P1 | FAIT sauf forges (1.6) | `BedBreakGame`, `Bridges`, `Defense`, `game-model/BedDefense` | 1.6 | C3 |
| 2.4 | Clutch Scenario : bridges réalistes jusqu'à la base ennemie ; rôles au hasard (leader aussi Clutcher) | Modes | P1 | IMPLÉMENTÉ | `ClutchScenarioGame`, `Bridges` | — | tests unitaires (`BridgesTest`) ; simulation sur Archway et Sky Rise ; en jeu | — |
| 2.5 | Au moins 10 spectateurs (Block Clutch et autres practice) | Social | P1 | IMPLÉMENTÉ | `tools/local-server/server.properties` (50 places), `spectate-bots.mjs` | 1.2 | test réel en CI : 10 bots regardent un Block clutch en même temps |
| 2.6 | Fireballs fidèles à Hypixel (KB, explosion, direction, fireball jump, blocs) | PvP | P1 | EN COURS (mesure prête, à faire sur Hypixel) | `Fireballs`, `FireballBlast`, `FireballAim`, `PracticeGame` | — | mesures Hypixel (kb-recorder 0.2) |
| 2.7 | TNT (timing, KB, TNT jump, environnement), réf. Hypixel / Minemen | PvP | P1 | À FAIRE | `PracticeGame.tntBlast`, `BedwarsGame` | 2.6 | mesures |
| 2.8 | Fast Shop et API Hypixel : étude des données réellement disponibles | Modes | P2 | À FAIRE | `FastShopGame`, `HypixelProfiles`, `HypixelProfile` | — | clé API |

### Étape 4 : statistiques et progression (Phase 3)

| # | Tâche | Cat. | Prio | Statut | Fichiers | Dépend de | Manque |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 3.2 | FKDR avec décimales partout (« 6.42 FKDR ») | Stats | P1 | À FAIRE | `BoardStat`, `StatsBook`, `StatsMenu`, `StatsDisplays`, `HeadLines` | 1.4 | — |
| 3.3 | Records practice : Lifetime d'abord, Weekly, Daily plus tard ; visibles ≥ 1 mois | Stats | P1 | À FAIRE | `Records`, `RecordBook`, `RecordsMenu` | 1.4 | — |
| 3.4 | Menus practice : TOP 3 Lifetime du record du mode au survol | Stats | P1 | À FAIRE | `ModeMenu`, `ModeDisplay`, `RecordCatalog` | 3.3 | — |
| 3.1 | Trois leaderboards Daily / Weekly / Lifetime liés à une même stat | Stats | P1 | À FAIRE | `StatsDisplays`, `StatsBook`, `StatsPeriod`, `BoardStat` | 1.4 | méthode pour ratios et ELO à valider |
| 5.4d | Sélecteur `NewLeaderboard Selector` (Win Streak, Level, ELO, Wins, Kills, FKDR, WWLR…) | Lobby | P1 | À FAIRE | `LobbyNpcs`, `StatsDisplays`, point `newleaderboardselector` | 3.1 | — |
| 3.5 | Network Level au-dessus des joueurs par défaut (lobby, practice, autres) | Lobby | P1 | À FAIRE | `HeadLines`, `NameStyle`, `LobbyScoreboard` | 1.3 | C4 |
| 3.6 | ELO des adversaires visible en Ranked | Ranked | P1 | À FAIRE | `HeadLines`, scoreboard des parties | 3.5 | — |
| 3.8 | Nombre de joueurs en file pour chaque mode | Files | P1 | À FAIRE | `QueueController`, `ModeMenu`, `LobbyNpcs` | — | — |
| 3.7 | Organisation Solo / Party / Ranked (ELO par mode) : proposition avant tout code | Files | P2 | À FAIRE | `ModeCategory`, `ModeMenu`, `QueueController`, `Matchmaker`, `EloLadder` | 3.8 | validation de la proposition |

### Étape 5 : expérience joueur (Phase 4)

| # | Tâche | Cat. | Prio | Statut | Fichiers | Manque |
| --- | --- | --- | --- | --- | --- | --- |
| 4.1 | Party Replay : les membres de la party rejoignent le replay | Replays | P2 | À FAIRE | `ReplayService`, `ReplayViewer`, `Parties` | — |
| 4.1b | Sons dans les replays : étude de faisabilité | Replays | P3 | À FAIRE | `ReplayRecorder`, `ReplayData` | — |
| 4.2 | `/host` : annonce cliquable toutes les 10 s ; inscription gardée sans téléporter ; départ quand assez de joueurs | Events | P1 | À FAIRE | `Events`, `EventGame`, `PracticeHost`, `HostWatcher` | — |
| 4.3 | Kit Editor plus clair et trié | Kits | P2 | À FAIRE | `KitEditor`, `KitLayouts` | — |
| 4.4 | Menu Stats : présentation, catégories, navigation | Stats | P2 | À FAIRE | `StatsMenu`, `ProfileMenu`, `StatsBook` | — |
| 4.5 | `/discord` et `/store` avec les liens officiels | Cmd | P1 | BLOQUÉ | `config.yml` (`liens`), `ServerSettings` | liens officiels (C6) |

### Étape 6 : lobby (Phase 5)

| # | Tâche | Cat. | Prio | Statut | Fichiers / points | Manque |
| --- | --- | --- | --- | --- | --- | --- |
| 5.1 | Direction artistique : moins de rouge, identité Rago gardée | DA | P2 | À FAIRE | `Brand`, `config.yml`, menus, scoreboard | palette à valider |
| 5.2 | Build monumental « RAGO PRACTICE » (rocheux, angélique, stone, void), sans détruire le spawn : plan précis d'abord | Build | P2 | BLOQUÉ | monde `lobby` | image de référence |
| 5.3 | Blocs sous PNJ : Jouer quartz, Stats fer, Shop émeraude | Lobby | P1 | À FAIRE | `LobbyNpcs`, points `practice`/`statsjoueur*`/`shop` | — |
| 5.4a | `NewQuest` relié aux quêtes | Lobby | P1 | À FAIRE | `QuestsNpc`, point `newquest` | — |
| 5.4b | `NewShop` sur émeraude | Lobby | P1 | À FAIRE | `LobbyNpcs`, point `newshop` | — |
| 5.4c | `NewDailyChest` sur diamant | Lobby | P1 | À FAIRE | `DailyChest`, point `newdailychest` | — |
| 5.5 | Daily Chest : animation à suspense, Rago Points 100–500, XP 100–2000, cosmétiques, probabilités configurables, jamais de doublon | Lobby | P2 | À FAIRE | `DailyChest`, `LobbyPoints`, `Cosmetic` | probabilités |
| 5.6A | `RagoClient` et `RagoOptimizer` : nom, skin, diamant, liens au clic, texte « OUR OTHER PRODUCTS », visuels publicitaires existants derrière | Lobby | P2 | BLOQUÉ | `LobbyNpcs`, `Billboards`, `panneaux/` | liens, skins |
| 5.6B | `qRago` (Owner) et `Kalisodev` (kaliso_dev, Dev) sur obsidienne, surélevés d'un bloc | Lobby | P2 | À FAIRE | `LobbyNpcs`, `NpcSkins` | — |
| 5.6C | `jncl` et `nativ` (affiché woryes, skin Woryes), rôle Mod en jaune clair | Lobby | P2 | BLOQUÉ | `LobbyNpcs`, `RankDef` | pseudo Woryes, C7 |
| 5.7 | Texte de remerciement en anglais, fidèle à ton histoire, avec le Discord, à valider avant intégration | Lobby | P2 | À FAIRE | point `textremerciement`, `FakeHologram` | lien Discord |
| 5.8 | Revoir les skins des PNJ du lobby | Lobby | P2 | À FAIRE | `NpcSkins`, `skins.yml` | — |

### Étape 7 : nouveaux modes (Phase 6) — proposition avant tout code

| # | Tâche | Prio | Statut | Manque |
| --- | --- | --- | --- | --- |
| 6.1 | Catégorie FUN PRACTICE, PNJ `FunMod2` sur quartz | P2 | À FAIRE | — |
| 6.1a | Snowball Spleef (style Minemen) | P2 | À FAIRE | validation du concept |
| 6.1b | Fireball Fight (voir C1) | P2 | À FAIRE | C1 |
| 6.1c | Fast Bedwars (Bedwars 2018 simplifié) | P2 | À FAIRE | concept |
| 6.1d | Autres modes FUN connus : propositions | P3 | À FAIRE | — |
| 6.2 | Capture the Flag, version Bedwars et seconde version : études | P3 | À FAIRE | concepts |
| 6.3 | Find Your Sensitivity : faisabilité et protocole | P3 | À FAIRE | — |
| 6.4 | Block Aim Practice (patterns de la communauté) | P3 | À FAIRE | — |
| 6.5.1 | Red Rover (Sumo, Block Placement, Block Edit, Top Bridge…) | P2 | À FAIRE | — |
| 6.5.2 | Modes 2v1 asymétriques (Clutch Scenario, stuff amélioré) | P3 | À FAIRE | — |
| 6.5.3 | Bot IA : étude technique | P3 | À FAIRE | — |
| 6.6 | Catégorie 3 joueurs (Red Rover, Clutch Scenario, Bed Break Scenario, Invis Practice) | P2 | À FAIRE | PNJ ou boussole à choisir |

### Étape 8 : administration et benchmark (Phase 7)

| # | Tâche | Prio | Statut | Fichiers | Manque |
| --- | --- | --- | --- | --- | --- |
| 7.1 | Outils Owner (Reach, Scaffold…) réservés au compte Owner | P3 | À FAIRE | `StaffTools`, `StaffPanel` | C8 |
| 7.2 | Rejoindre une partie en cours comme participant (admin), sans fausser les stats | P2 | À FAIRE | `Spectating`, `PracticeGame`, `BedwarsGame` | — |
| 7.3 | AntiCheat compatible KB custom, fireballs, TNT, practice ; Owner exempté | P1 | À FAIRE | à choisir (Grim, Vulcan…) | choix de l'anticheat |
| 7.4 | FPSbenchmark : PNJ `fpsbenchmark` sur `159:14`, hors boussole, scénarios reproductibles | P3 | À FAIRE | point `fpsbenchmark` | — |

### Étape 9 : release (Phases 8 et 9)

| # | Tâche | Prio | Statut | Manque |
| --- | --- | --- | --- | --- |
| 8.1 | Hébergeur européen ≤ 30 €/mois : comparatif d'offres réelles | P1 | À FAIRE | — |
| 8.2 | Site : vérifier, liens Store/Discord, Netlify, domaine | P1 | À FAIRE | achat du domaine (toi), liens |
| 9 | Sécurité, tests finaux (modes, stats, lobby, social, performances) | P1 | À FAIRE | — |

---

## 3. Journal des corrections

### 1.2 Spectator — IMPLÉMENTÉ (21/11)

**Constat dans tes logs** (`logs/2026-11-15-4.log`) : `godemy spectates qRago (lobby-fight-1v1)`
à 17:26:06, puis `godemy was slain by Fazzzee` à 17:26:10 ; la même chose quatre fois de suite.

**Cause réelle** : RagoBuildTools, installé sur ton serveur de test, remettait **en Survival**
tout joueur qui change de monde, dans tous les mondes qui ne sont pas des mondes de build. Le
spectateur passe en mode spectateur dans le lobby, puis est téléporté dans le monde de la partie
(`arenes` ou la copie de la map Bed Wars) : RagoBuildTools le remettait aussitôt en Survival. Au
premier mouvement, RagoPractice et RagoBedwars voyaient un « spectateur » qui n'était plus en
mode spectateur, l'oubliaient (plus aucune protection), et il pouvait frapper, être frappé et
mourir ; sa réapparition, que plus aucun plugin ne gérait, le mettait au spawn du monde principal.

**Corrections** :
- RagoBuildTools ne change plus le mode de jeu que dans ses propres mondes de build (et remet en
  Survival un builder qui quitte un monde de build en créatif). Le lobby et les modes gardent le
  mode qu'ils donnent.
- RagoPractice et RagoBedwars : le spectateur est compté avant la téléportation ; tant qu'il
  regarde, tout changement de son mode de jeu est refusé (« You are spectating a game: /spawn to
  leave it. ») ; une mort forcée (/kill) le fait réapparaître au point des spectateurs de la
  partie, toujours spectateur.
- `/spectate` est ouvert à tous les joueurs, pour toute partie (sauf celle d'un joueur sous nick :
  staff et amis seulement, pour ne pas révéler qui se cache derrière).
- Les joueurs de la partie lisent « <name> is now spectating your game. » (pas pour un membre du
  staff en vanish, qui vérifie une partie sans être vu).

**Tests** : tests unitaires (`BuildListenerTest`, `SpectatingTest`), compilation et suite de tests
par la CI. **Pas encore testé en jeu.** À tester en jeu : `/spectate` d'un duel practice et
d'une partie Bed Wars (Lobby Fight), essayer de frapper le spectateur et de le faire frapper,
`/gamemode survival` pendant le spectate (refusé), `/kill` du spectateur (il reste spectateur),
`/spawn` (retour au lobby), fin de la partie (retour au lobby).

### 1.1 Reset des arènes — IMPLÉMENTÉ (21/11)

**Fonctionnement** : toutes les arènes practice passent par `Arena` : chaque pose
(`BlockPlaceEvent`, état remplacé), casse et explosion est notée (`placed` / `removed` /
`touch`), et `restore` remet tout à la fin de chaque manche, de la partie, d'un abandon et de
l'arrêt du plugin. Le monde `arenes` est refait à chaque démarrage. Bed Wars a son propre système
(`Instance`, une copie de la map par partie, remise par positions) : il n'a pas ce bug.

**Cause principale, reproduite par un test réel** : un nouveau test (`tools/lobby-check/run-arena-test.sh`,
dans la CI à chaque pull request) lance un vrai serveur PandaSpigot 1.8.8 avec deux bots qui
enchaînent les parties de Block placement 1v1, Block edit 1v1 et l'entraînement Block edit sur la
même zone, posent et cassent des blocs (laine, échelle, end stone), finissent les parties de
toutes les façons, et vérifient bloc par bloc, au début de chaque partie, que la zone est
exactement comme construite. Son premier passage a échoué : après **Play again** (ou Leave, `/l`)
cliqué sur l'écran de fin, la partie suivante dans la même zone retrouvait les blocs de la partie
d'avant. Les joueurs partis, le serveur décharge les chunks de la zone avant le reset (3 s après
la fin) ; les blocs gardés depuis les événements pointent encore vers ces chunks déchargés, ce
qui est posé à travers eux est perdu, et les chunks reviennent du disque tels que la partie les a
laissés. Cela explique « après quelques games, même après un redémarrage » : il suffit que tout
le monde quitte l'écran de fin avant les 3 s. **Correction** : le reset pose chaque bloc à
travers le monde (`Arena.setBack`), qui recharge le chunk. Après la correction, le test passe :
10 parties commencées dans une zone déjà jouée, chacune exactement comme construite, aucune
erreur dans le journal du serveur.

**Deux autres causes, trouvées sur tes maps Block Placement** (lecture des fichiers de la map) :
les trappes, portillons et portes ouverts d'un clic restaient ouverts pour les parties suivantes
(aucun événement ne dit qu'ils ont changé) : ils sont maintenant notés au clic et refermés au
reset (`Arena.clicked`). Et l'herbe sous un bloc posé devenait de la terre (plaques de terre qui
s'accumulent) : plus rien ne change tout seul dans le monde `arenes` (herbe, glace, neige,
feuilles ; le feu s'éteint toujours).

**Autres modes à blocs** : tous les modes practice utilisent le même `Arena` (Block placement 2v2,
Block In, Clutch, Fast defense, Extend, Bridge, Fireball fight, Block Arena, trainings) : la
correction vaut pour eux. Le test réel ne couvre que Block placement 1v1, Block edit 1v1 et
l'entraînement Block edit.

**Tests** : test réel en CI (ci-dessus), tests unitaires `ArenaRestoreTest` (trappe, portillon,
porte, zone quittée). **Pas testé en jeu sur tes maps** (le serveur de test de la CI n'a pas tes
maps : les trappes et l'herbe sont couvertes par les tests unitaires seulement). À tester :
plusieurs parties de Block placement sur tes maps en cliquant Play again sur l'écran de fin, en
ouvrant les trappes et portillons, et en laissant des blocs posés une minute sur l'herbe.

Une faille liée, fermée plus tôt : un joueur qui n'est pas dans une partie (spectateur, admin)
ne peut plus poser ni casser dans le monde `arenes`.

### 1.3 Joueurs invisibles à l'arrivée — IMPLÉMENTÉ (21/11)

**Cause, reproduite par un test réel** : un nouveau test (`tools/kb-control/bots/visibility-bots.mjs`,
lancé par `run-arena-test.sh` dans la CI) connecte deux bots à un vrai serveur PandaSpigot 1.8.8 et
enregistre, dès le premier paquet, tout ce que le serveur leur dit des joueurs (entrée dans la tab
list, corps, mode de jeu, invisibilité). Son premier passage a échoué dès la première arrivée :
le joueur qui attendait au spawn recevait le **corps** du nouveau joueur **avant son entrée dans
la tab list**. Un client 1.8.9 ne dessine pas le corps d'un joueur qu'il ne connaît pas encore :
il ne reste que la ligne au-dessus de la tête (« Unranked »), exactement ce que tu voyais, et
seulement chez ceux déjà présents. Pourquoi : à la connexion, le lobby envoyait le joueur au spawn
par une téléportation **pendant** l'événement de connexion ; quand le joueur arrivait d'un autre
monde (le monde par défaut à la première connexion, le monde `arenes` s'il avait quitté en
partie), le serveur l'ajoutait au monde du lobby tout de suite, avant d'envoyer aux autres son
entrée dans la tab list (envoyée juste après l'événement).

**Correction** : le joueur apparaît directement au spawn du lobby quand il se connecte
(`PlayerSpawnLocationEvent`, `LobbyListener.onSpawnLocation`) ; la téléportation de la connexion
reste dans le même monde, et le corps arrive après l'entrée dans la tab list. Rien d'autre ne
change à l'arrivée (même spawn qu'avant).

**Tests** : après la correction, le test réel passe pour les 5 cas : première connexion, retour
après avoir quitté au lobby, après avoir quitté pendant un entraînement (Block edit), après avoir
quitté pendant un duel (Block placement 1v1), et l'arrivée de celui qui attendait. Pour chacun,
les deux bots se voient (corps après l'entrée dans la tab list, ni spectateur ni invisible, et
toujours là une seconde après). **Pas testé avec un vrai client 1.8.9** : les bots lisent les
mêmes paquets qu'un client, mais à vérifier en jeu (quelqu'un au spawn, un autre qui se connecte).

### 1.8 Block In réaliste — IMPLÉMENTÉ (21/11)

**Avant** : environ toutes les 1,1 s, l'ennemi frappait par n'importe quel côté ouvert, ou
faisait disparaître d'un coup un bloc posé au hasard autour du joueur.

**Maintenant** (`BlockInEnemy`) : l'ennemi se tient d'**un** côté du joueur, comme un joueur de
Bed Wars.
- Côté ouvert (au niveau des pieds ou de la tête) : il frappe par là (recul à l'opposé, comme
  avant).
- Côté fermé par un bloc du joueur : il le casse d'abord, avec les fissures sur le bloc, dans le
  temps d'un joueur 1.8 au sol avec l'outil du kit (laine aux cisailles : 5 ticks, 0,25 s ;
  calcul vanilla : dureté 0,8, cisailles ×5), puis frappe par le trou.
- Un bloc remis referme le côté : il doit le casser à nouveau avant de frapper.
- Il ne frappe jamais par un autre côté ouvert, et ne casse ni la défense ni la map : un côté
  fermé par ceux-là, il passe à un autre côté qu'il peut atteindre (s'il n'y en a aucun, il
  attend).
- Son rythme reste celui d'avant : un geste (frapper, ou commencer à casser) environ toutes les
  1,1 s (± 0,3 s) ; il arrive toujours 2 s après le départ, et le réglage « Enemy » le coupe.

**Records** : remis à zéro une seule fois au premier démarrage de cette version (tableaux
`block-in.*`), après une copie de `records.yml` dans `records-sauvegarde-block-in-2026-11.yml` ;
c'est noté dans `records-remis-a-zero.txt` pour ne jamais le refaire. Les autres records ne
bougent pas.

**Tests** : tests unitaires `BlockInEnemyTest` (joueur à découvert, joueur enfermé : casse puis
frappe, bloc remis : recasse sans frapper, autre côté ouvert jamais utilisé, côté de défense,
fissures retirées) et `RecordsResetTest` (remise à zéro unique, copie, autres records gardés,
nouveaux temps conservés aux démarrages suivants), et un test réel en CI
(`tools/kb-control/bots/blockin-bots.mjs`, lancé par `run-arena-test.sh`) : sur un vrai serveur
PandaSpigot, un bot joue l'entraînement sur l'île du plugin, s'enferme dans la laine depuis son
départ sur l'anneau de verre, puis vérifie que l'ennemi ne le frappe pas tant qu'il est enfermé,
casse un bloc d'un seul côté avec les fissures (250 ms entre les premières fissures et la casse,
5 étapes vues), le frappe par ce trou 1,2 s plus tard en le repoussant à l'opposé, casse à nouveau
le bloc remis trois fois de suite, au même côté et sans frapper, et ne casse rien d'autre (autres
côtés, verre). Le test passe. **Pas testé en jeu sur ta map** « Ultimate Block-In Practice » (le
serveur de test ne l'a pas) ni avec un vrai client : à essayer, en t'enfermant puis en remettant
le bloc cassé.

### 1.5 Kit Bedfight — IMPLÉMENTÉ (21/11)

**Fonctionnement actuel** : à chaque mort (un coup, le vide, tout passe par `BedwarsGame.kill`),
l'inventaire est vidé, puis `BedFightGame.giveKit` redonne le kit tel qu'il est rangé dans
l'éditeur du lobby. Tout ce que le joueur avait déplacé pendant la partie était donc perdu à chaque
vie ; c'est surtout visible après une chute dans le vide, la mort la plus fréquente en Bed Fight.

**Correction** : juste avant de vider l'inventaire, le mode note la case de chaque sorte d'objet
(`layoutOf`) ; la vie suivante redonne le kit dans ces cases (`arranged`), deux stacks de laine
compris. Une sorte épuisée (toute la laine posée) garde la case de la vie d'avant. Un objet
ramassé en partie (end stone d'une défense) ne prend la place d'aucun objet du kit. La première
vie garde le rangement du lobby. Je lis ta demande ainsi : « le kit modifié pendant la partie est
gardé après chaque mort ». Dis-moi si tu voulais autre chose.

**Tests** : tests unitaires (`BedFightGameTest`) ; compilation et tests par la CI. **Pas testé
en jeu.** À tester : déplacer l'épée et la laine en partie, mourir d'un coup, puis dans le vide,
puis avec l'inventaire ouvert.

### 1.4 Statistiques — IMPLÉMENTÉ (21/11)

**Audit.** Toutes les stats passent par le lobby (`LobbyService`) et sont comptées dans
`StatsBook`, puis écrites dans un fichier par joueur (`plugins/RagoLobby/stats/<uuid>.yml`).

| Stat | Calcul | Mise à jour | Modes |
| --- | --- | --- | --- |
| Wins, Losses | +1 à la fin de la partie (un abandon est une défaite) | `recordMatch` | duels et 2v2 practice (`PracticeGame.finish`), sumo (`SumoMatch.end`), toutes les parties Bed Wars à équipes (`BedwarsGame.end`), Attack and Defense, Bed Break Simulator, Invis practice |
| Games played | Wins + Losses + égalités, par mode, du jour, de la semaine et depuis toujours | `recordMatch`, `recordDraw` | idem |
| Kills, Deaths | +1 kill au tueur, +1 death à la victime | `recordKill` à chaque mort | tous les modes à combat, FFA compris ; sumo : un point = un kill |
| Final kills, Final deaths, Beds broken, FKDR | +1 à chaque élimination / lit cassé ; FKDR = final kills / final deaths | `recordFinalKill`, `recordFinalDeath`, `recordBedBroken` | Bed Wars |
| Network XP, Rago Points, quêtes | donnés avec les parties et les kills | `recordMatch`, `recordKill` | tous |

Non comptés, volontairement : les entraînements, les parties privées d'une party, les FFA et
events dans Wins/Losses (pas de fin de partie). Affichage : le menu des stats, le profil au-dessus
du PNJ, les leaderboards et `/stats` lisent les mêmes compteurs et calculent tous Games played de
la même façon. Écriture : à chaque changement, sur un seul fil d'écriture, dans l'ordre. Vérifié
sans problème : chaque fin de partie n'est comptée qu'une fois (toutes protégées par l'état
ENDED) ; un joueur à la fois gagnant et perdant n'est compté qu'une fois ; jour et semaine
repartent de zéro à l'heure de Paris.

**Problèmes trouvés et corrigés :**
1. **Kills Bed Wars comptés deux fois.** Un final kill comptait +1 kill **et** +1 final kill (et
   la victime +1 death **et** +1 final death), alors que le tableau de fin de partie les sépare.
   Corrigé : un final kill ne compte plus que dans les final kills, une final death que dans les
   final deaths, comme sur Hypixel. Le tueur garde l'XP, les Rago Points, la quête de kills,
   l'effet de kill et le compte du MVP.
2. **Morts sans tueur jamais comptées.** Tomber dans le vide sans avoir été frappé juste avant ne
   comptait aucune death (le K/D était gonflé), alors que les final deaths, elles, étaient
   comptées. Corrigé dans les parties contre d'autres joueurs : duels practice (Duel, Block fight,
   Fireball fight) et toutes les parties Bed Wars, sauf FFA, Fast Shop et Top Bridge en practice.
   Les FFA et events practice (Block arena, Bridge arena) ne changent pas : une chute y reste
   sans death. Se tuer soi-même compte une death, sans kill.
3. **Abandons non comptés en Bed Break Simulator et Invis practice.** Seuls les joueurs encore là
   à la fin étaient comptés : celui qui partait avant n'avait ni défaite ni partie jouée,
   contrairement à tous les autres modes. Corrigé : chaque joueur du premier round parti avant la
   fin compte une défaite. Si celui qui avait le plus de points est parti, le meilleur des joueurs
   restants gagne.
4. **Stats remises à zéro par un `/reload`.** Au `/reload`, le lobby recréait un profil vide pour
   chaque joueur en ligne avant d'avoir fini de lire les fichiers ; le fichier lu était alors
   ignoré et le profil vide l'écrasait au changement suivant (skin, /fly, partie). Même risque pour
   un joueur connecté dans les tout premiers instants du démarrage. Corrigé : le fichier remplace
   le profil vide et il est réécrit aussitôt.
5. **Leaderboard FKDR en retard** après une final death sans tueur (cache non vidé). Corrigé.

6. **Égalités** (ta réponse du 21/11 : « toute game compte ») : une partie sans gagnant (temps
   écoulé, plus personne debout, aucun point en Bed Break / Invis) compte maintenant une partie
   jouée pour chacun de ses joueurs (compteur `egalites` dans le fichier, sans reset), avec l'XP,
   les Rago Points et la quête d'une partie jouée. Ni victoire, ni défaite, ni ELO, ni série. Un
   joueur parti pendant une partie qui finit en égalité a l'égalité, comme son équipe.
7. **Reset des kills et deaths Bed Wars** (ta réponse : « reset, on repart de zéro ») : au premier
   démarrage avec la nouvelle version, RagoBedwars demande au lobby de remettre à 0 les kills et
   les deaths (à vie, du jour, de la semaine) de **tous ses modes**, FFA compris, pour tous les
   joueurs. Le reste (victoires, défaites, final kills, final deaths, lits, XP, ELO) ne bouge pas.
   Avant, le dossier `plugins/RagoLobby/stats/` est copié tel quel dans
   `plugins/RagoLobby/stats-sauvegarde-kills-bedwars-2026-11/`. Le reset n'est fait qu'une fois
   (noté dans `plugins/RagoLobby/stats-remises-a-zero.txt`).

**Pas modifié :** les deaths des duels practice étaient aussi trop basses (morts dans le vide sans
tueur). Elles ne sont pas remises à zéro, puisque tu as parlé des kills Bed Wars ; dis-le si tu le
veux aussi.

**Tests** : tests unitaires (`StatsBookTest` : morts sans tueur, final kills, fichier lu après un
profil vide, égalités, reset des kills ; `LineupTest` : résultat avec un joueur parti) ;
compilation et tests par la CI. **Pas testé en jeu.** À tester : au premier démarrage, la ligne
« Kills and deaths of [...] back to 0 » dans la console et le dossier de sauvegarde ; un final kill
en Bed Wars (Kills inchangé, Final kills +1), une chute dans le vide sans coup dans un duel
(Deaths +1), quitter une partie de Bed Break Simulator en cours (Losses +1), une partie Bed Wars
qui finit au temps (Games played +1), un `/reload` avec un joueur connecté (stats intactes).

### 1.7 Spawns First Rush — CAUSE TROUVÉE, en attente du point Hypixel

**Cause** (lue dans les `bedwars.yml` et les mondes de Gelato et Nebuc) : l'outil de maps
(`tools/bedwars-maps`) place le spawn entre le coffre et l'ender chest de l'équipe. Sur Gelato et
Nebuc, ces deux coffres sont posés de chaque côté de la porte de la base : le joueur apparaît sur
le seuil, tourné vers le lit, à environ 9 blocs de lui. Les 8 autres maps First Rush (Acropolis,
Ambush, Aqil, Cascade, Crogorm, Frosted, Lighthouse, Lotus) ont la même disposition : spawn 2 ou 3
blocs derrière la porte.

**Ce qui manque** : le point exact d'Hypixel n'est ni dans les fichiers de la map (aucun repère :
pas de panneau, de bloc ou d'entité au spawn) ni dans les sources publiques consultées (wiki
Hypixel). Aucun point n'est inventé.

**Prêt** : un seul `/bw spawn red` par map, au point Hypixel, suffit. Il est recopié sur les 7
autres îles dans le repère de chaque île (son générateur, son lit, son shop). Vérifié sur Gelato et
Nebuc : à partir du spawn rouge actuel, la recopie redonne exactement les 8 spawns actuels.

### 2.4 Clutch Scenario — IMPLÉMENTÉ (21/11)

**Rôles** : l'équipe sans lit (les Clutchers) est déjà tirée au sort parmi les deux équipes
(`ThreadLocalRandom`, une chance sur deux), sans regarder qui est le leader de la party. C'est le
cas depuis le 11/11, et le jar installé chez toi (14/11) l'a déjà. Le code n'a pas été changé.

**Bridges** (`Bridges`, `ClutchScenarioGame`) :
- **Droits, comme ceux des joueurs** : tout droit le long d'un axe quand les deux îles se font
  face ; sinon tout droit, **un seul virage**, tout droit. Le pont en diagonale (un bloc à chaque
  pas en biais) ne sert plus qu'en dernier recours, quand aucun chemin droit ne passe. Sur Archway,
  le bridge vers le diamant est maintenant en L au lieu d'un escalier en diagonale.
- **Jusqu'à la base ennemie** : chaque équipe a en plus le bridge de son rush, de mid (le
  générateur d'émeraude du côté de l'ennemi) jusqu'au lit ennemi, dans sa couleur. Il arrive par
  le côté de mid, là où la défense de l'équipe sans lit a déjà son trou.
- **Séparés** : un bridge ne touche jamais un bridge déjà posé (au moins une colonne de vide entre
  les deux) ; le rush longe donc le bridge de l'ennemi vers mid au lieu de se confondre avec lui.

**Testé** : 11 tests unitaires de `Bridges` passent (dont 4 nouveaux : le virage unique, la
diagonale en dernier recours, deux bridges côte à côte, le départ du rush). Simulation avec le vrai
code de `Bridges` sur tes mondes Archway et Sky Rise : chaque équipe a ses 3 bridges, aucun n'est
refusé. **Pas encore testé en jeu.**

### 2.1 Lobby Fight — IMPLÉMENTÉ (21/11)

Tes réponses : le même kit pour le 1v1 et le FFA, les 3 manches en 1v1 seulement, « Bo3, le
premier à 3 win ». Je l'ai lu comme le premier à 3 manches gagnées (donc jusqu'à 5 manches).

- **Kit** : `lobby-fight-ffa:` dans le `config.yml` du lobby : une épée en pierre, 5 pommes en
  or, 3 fireballs, 12 end stone, une pioche en bois. Le 1v1 joue ce kit (`kit-de:
  lobby-fight-ffa`) : un seul rangement dans l'éditeur de kit pour les deux. Avant, le 1v1 avait
  le kit du FFA (64 pommes, pearl, laine, cisailles). Un rangement fait pour l'ancien kit garde
  ses objets à leur place, les nouveaux vont dans les cases libres.
- **Haste I** donné avec le kit, à chaque vie, dans les deux modes ; le retour au lobby enlève
  tous les effets.
- **Blocs** : en 1v1 aussi, ils partent 10 secondes après avoir été posés (avant : seulement à
  la manche suivante). Seuls les blocs posés se cassent, jamais ceux de la salle d'attente.
- **Manches** : le premier à 3 gagne (avant : 5).

**Testé** : tests unitaires (le kit, Haste I, le premier à 3, les 10 secondes, le `kit-de:` du
1v1). **Pas encore testé en jeu** (le serveur de test n'a pas la map Archway FFA) : à faire, une
partie de Lobby Fight 1v1 et une de FFA, poser de l'end stone et la voir partir, la casser avec
la pioche, gagner 3 manches.

### 2.5 Au moins 10 spectateurs — IMPLÉMENTÉ (21/11)

Aucune limite de spectateurs dans le code. La vraie limite était `max-players=8` du
`server.properties` du serveur local : 8 places, trop peu pour une partie et dix spectateurs. Tu
as choisi 50 places.

**Testé** : test réel en CI sur un vrai serveur PandaSpigot (`spectate-bots.mjs`) : un bot joue
le Block clutch, dix autres le regardent en même temps avec `/spectate`, chacun en mode
spectateur près du joueur, le joueur est prévenu de chacun et continue de jouer, les dix sont
toujours là 4 secondes plus tard, puis `/spawn` les ramène au lobby. Le test joue avec le
`server.properties` livré (50 places). Sur ton serveur actuel, mets `max-players=50` dans son
`server.properties` (ou prends le prochain paquet).

### 2.3 Bed Break Simulation — IMPLÉMENTÉ (21/11), sauf les forges (1.6)

- **Bridges** : le top bridge (la plateforme de laine de 17 blocs de large, 16 blocs au-dessus
  des lits, tracée de lit à lit, donc en diagonale sur Invasion) est remplacé par le bridge de
  l'attaquant, comme un joueur le pose en Ranked Bedwars : un bloc de large, en laine de sa
  couleur, de son île jusqu'au lit défendu, à la hauteur des îles. Il suit le même tracé que les
  bridges de Clutch Scenario (`Bridges`) : droit le long d'un axe, sinon un seul virage, jamais en
  diagonale quand un chemin droit passe. Sur Invasion (îles aux coins), il longe le bord ouest
  puis le bord nord de la map, par l'île jaune ; sur Archway, il passe par mid. Les défenseurs
  peuvent le casser ; il est reposé à chaque manche. L'attaquant part de son milieu, tourné vers
  la suite du chemin. Images : `sortie-publique/bed-break/Invasion-bridge.png` et
  `Archway-bridge.png`.
- **Bord arrondi des îles d'Invasion** : le bord des îles descend en escalier sous l'île. Le
  tracé partait du bas de cet escalier, 4 blocs sous le sol de l'île (y 89 au lieu de 93), et
  arrivait de même sous le bord de l'île d'en face, avec une marche de 2 blocs impossible à
  sauter. Le bridge part maintenant du haut du bord, par-dessus, et arrive au haut de l'autre :
  tout à y 93. Les bridges de Clutch Scenario sur Archway et Sky Rise ne changent pas (vérifié
  bloc par bloc avec la simulation).
- **Placement en haut de la map** : sans le top bridge, la limite de hauteur redevient celle de
  la map (`hauteur-max:` de son `bedwars.yml` : 116 sur Invasion, 87 sur Archway). Avant, le top
  bridge la montait à 10 blocs au-dessus de sa plateforme (120 sur Invasion, 92 sur Archway).
- **Kits** : cisailles et une hache du même tier que la pioche dans les trois kits (ton choix) :
  Classic, Golden Pickaxe (tier III) et Iron Axe (tier III) ; Fireball, Iron Pickaxe (tier II) et
  Stone Axe (tier II) ; Fighter, Wooden Pickaxe et Wooden Axe (tier I). Dans l'éditeur de kit du
  lobby, la hache et les cisailles sont ajoutées en dernier au kit `bed-break-simulator` : les
  rangements déjà enregistrés gardent leurs objets à leur place. **Sur ton serveur**, le
  `config.yml` de RagoBedwars a ses propres `bed-break.kits` : ajoute-y les objets (ou enlève
  `kits:` pour prendre ces trois kits), et `IRON_AXE, SHEARS` à la fin du kit
  `bed-break-simulator` du `config.yml` de RagoLobby.
- **Défense** : celle de Ranked Bedwars, end stone sous le verre (celle de l'entraînement Fast
  defense : 12 end stone, 14 verre teinté de la couleur de l'équipe), à la place de la défense duo.

**Testé** : tests unitaires (`BridgesTest` : 12 dont le nouveau du bord en escalier, qui échoue
avec l'ancien tracé ; `BedDefenseTest` ; `BedBreakGameTest` : les kits, le départ au milieu du
bridge). Simulation avec le vrai code de `Bridges` sur tes mondes Invasion et Archway. **Pas
encore testé en jeu.**

### 1.6 Forges de Bed Break Simulation — EN COURS

Bed Break Simulator joue sur Archway et Invasion. Les points des forges (`generateur`) des deux
maps sont au bon endroit sur l'île, et la forge des défenseurs est remplie à chaque round
(`forge.fill()`). Le réglage des forges du serveur est correct.

**Invasion (21/11)**, la map donnée : vérifié dans ses fichiers (`region/`), pas en jeu.
- Les 4 points de forge (rouge, bleu, vert, jaune) sont au centre de la fosse de la forge : sur la
  vitre (y 92), avec l'air au-dessus (y 93 à 95), une lanterne marine dessous. Les lingots tombent
  donc bien sur la vitre.
- La map ne garde aucune entité (aucun lingot ni hologramme laissé par la capture).
- En Bed Break, les deux îles jouées sont rouge (défense) et vert (attaque), à l'opposé ; seule
  la forge rouge tourne, à la vitesse moyenne (celle de la file RBW 2v2 à 4v4), remplie au début
  de chaque manche (48 fer, 16 or).
- Les journaux de ton serveur ont deux copies d'Invasion (14/11 et 15/11), sans aucune erreur.

Je n'ai pas trouvé ce qui ne marche pas : il me faut ce que tu vois (forge vide, trop lente,
lingots qui partent ailleurs, une forge d'une autre île qui ne tourne pas…).

### 2.6 Fireballs — MESURE PRÊTE (21/11), en attente de la séance sur Hypixel

**Analyse** : les valeurs des boules de feu de RagoBedwars (`BedwarsSettings.Fireball` : vitesse
1, rayon 3, recul horizontal 1, vertical 0,65, 1 cœur de dégâts) et du practice (`Fireballs`) sont
celles de BedWars1058 ; aucune n'a été mesurée sur Hypixel. `mesures-hypixel.md` le dit aussi :
explosions et boules de feu non mesurées. Le mod kb-recorder 0.1.0 ne pouvait pas les mesurer : il
gardait le paquet d'explosion, mais pas la boule de feu ni la TNT, ni le clic droit, ni les blocs.
Rien n'est changé dans le jeu sans mesure (pas de valeur inventée).

**Fait** :
- **kb-recorder 0.2.0** : suit les TNT allumées et les boules de feu de leur apparition à leur
  disparition (position, vitesse), enregistre les changements de blocs et le clic droit, et, juste
  avant que le client applique une explosion, écrit tous les blocs autour d'elle (ligne `blocks`) :
  on sait ce qu'elle casse et ce qu'elle laisse, bloc par bloc.
- **kb-analyzer `--explosions`** : pour chaque explosion, la source, le délai clic → apparition →
  explosion, la vitesse de la boule de feu, la puissance, la poussée (celle de l'explosion ou un
  paquet de vitesse envoyé à part), sa direction par rapport au centre, la hauteur et la longueur du
  saut, les blocs cassés et laissés par type.
- **Protocole** de la séance : `docs/combat/protocole-explosions.md` (fireball jumps, boule de feu
  sur un autre joueur à 1, 2, 3 blocs, murs de laine, bois, argile, end stone, verre, obsidienne,
  TNT pour 2.7).

**Testé** : tests unitaires du mod, de kb-analyzer (dont un fireball jump inventé) et de la
vérification, en local et en CI. Test de bout en bout en CI avec un vrai client Minecraft 1.8.9 et
le mod, sur PandaSpigot : les 24 coups passent comme avant, et les deux explosions sont retrouvées
par kb-analyzer : la TNT (puissance 4, 119 blocs détruits, 1 156 blocs lus avant, 51 remis à l'air
par le serveur) et la boule de feu (puissance 1, 9 blocs détruits, 147 lus avant, 3 remis à
l'air), chaque bloc détruit encore présent dans la lecture d'avant. Le clic droit n'est pas couvert
(personne ne joue le client). Rien n'est encore mesuré sur Hypixel.

**Reste** : la séance sur Hypixel avec le mod 0.2.0, puis les nouvelles valeurs (RagoBedwars,
practice, Lobby Fight) à partir des mesures.
