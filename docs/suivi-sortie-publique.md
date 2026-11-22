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
| C1 | 6.1 demande un nouveau **Fireball Fight**, mais un mode `fireball` existe déjà (`FireballFight.java`, map « Fireball Fight »). | Analyse du 21/11 : le mode existant se joue avec des lits (catégorie Duels), le mode demandé est un mode de knockback sur une arène ronde type Sumo. Ce sont deux jeux différents : proposé de créer le nouveau en FUN PRACTICE et de garder l'actuel sous un autre nom (noms à choisir, question 2 du document Étape 7). |
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
- ~~Ce qui ne marche pas sur la forge de Bed Break Simulation sur Invasion (1.6)~~ : répondu (« les
  minerais fer et or spawn au milieu de la base là où le joueur spawn au lieu d'être correctement
  dans la forge ») ; cause trouvée et `bedwars.yml` corrigé.
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
| 1.6 | Forges de Bed Break Simulation sur certaines maps | Bed Wars | P0 | TESTÉ (en jeu par toi, 21/11) | `bedwars.yml` d'Invasion, `tools/bedwars-maps` | — | en jeu : une manche de Bed Break sur Invasion | — |
| 1.7 | Spawns First Rush trop en avant | Bed Wars | P0 | IMPLÉMENTÉ sur Nebuc et Gelato (ton spawn rouge recopié sur les 7 autres îles), à vérifier en jeu | `bedwars.yml` des maps (Gelato, Nebuc…), `tools/bedwars-maps` | — | en jeu sur chaque map | le point Hypixel sur une île par map (`/bw spawn red`), recopié ensuite sur les 7 autres |
| 1.8 | Block In réaliste (casser, frapper seulement par le trou, recasser) + reset des records | Practice | P0 | TESTÉ (en jeu par toi, 21/11) | `BlockInEnemy`, `BlockInTraining`, `Records`, `RagoPractice` | 1.1 | tests unitaires (`BlockInEnemyTest`, `RecordsResetTest`) ; test réel en CI (`blockin-bots.mjs`) ; en jeu sur ta map | — |

### Étape 3 : modes existants (Phase 2)

| # | Tâche | Cat. | Prio | Statut | Fichiers | Dépend de | Manque |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2.1 | Lobby Fight : 12 end stone, pioche bois, Haste, 5 pommes, blocs qui partent après 10 s (pas ceux du lobby), 3 manches | Modes | P1 | CORRIGÉ après l'essai en jeu (pearl, laine et cisailles remises), à retester | `LobbyFight`, `LobbyFightGame`, `FfaGame`, `BlockDecay`, config du lobby | 1.1 | tests unitaires (`LobbyFightTest`, `LobbySettingsTest`) ; en jeu sur Archway FFA (voir le journal) |
| 2.2 | Bedfight : protection au respawn de la durée exacte de Minemen, coupée dès un combat ; kit gardé ; puis les autres modes Bed Wars (demandé après l'essai) | Modes | P1 | TESTÉ en Bed Fight (en jeu par toi) ; autres modes IMPLÉMENTÉ | `BedFightGame`, `BedwarsGame`, `BedwarsHost`, `SpawnProtection` | 1.5 | tests unitaires (`SpawnProtectionTest`) ; en jeu : frapper un joueur qui vient de réapparaître |
| 2.3 | Bed Break Simulation : bridges refaits, pas de blocs en haut de la map, cisailles + hache, défense RBW (end stone + verre), forges | Modes | P1 | IMPLÉMENTÉ : tes escaliers rouges relevés (65 laines par map) ; plateforme du Top Bridge (ton choix du 21/11) codée, CI entière passée ; ajoutée aux `bedwars.yml` d'Invasion et d'Archway, tes 65 blocs enlevés des maps (le jeu les pose à chaque manche) ; plugins du build 339 installés le 21/11 ; forges testées ; plateforme à tester en jeu | `BedBreakGame`, `Bridges`, `Defense`, `game-model/BedDefense` | 1.6 | C3 |
| 2.4 | Clutch Scenario : bridges réalistes jusqu'à la base ennemie ; rôles au hasard (leader aussi Clutcher) | Modes | P1 | IMPLÉMENTÉ | `ClutchScenarioGame`, `Bridges` | — | tests unitaires (`BridgesTest`) ; simulation sur Archway et Sky Rise ; en jeu | — |
| 2.5 | Au moins 10 spectateurs (Block Clutch et autres practice) | Social | P1 | IMPLÉMENTÉ | `tools/local-server/server.properties` (50 places), `spectate-bots.mjs` | 1.2 | test réel en CI : 10 bots regardent un Block clutch en même temps |
| 2.6 | Fireballs fidèles à Hypixel (KB, explosion, direction, fireball jump, blocs) | PvP | P1 | EN COURS (mesure prête, à faire sur Hypixel) | `Fireballs`, `FireballBlast`, `FireballAim`, `PracticeGame` | — | mesures Hypixel (kb-recorder 0.2) |
| 2.7 | TNT (timing, KB, TNT jump, environnement), réf. Hypixel / Minemen | PvP | P1 | À FAIRE | `PracticeGame.tntBlast`, `BedwarsGame` | 2.6 | mesures |
| 2.8 | Fast Shop et API Hypixel : étude des données réellement disponibles | Modes | P2 | BLOQUÉ : Hypixel refuse la clé du serveur (« Invalid API key ») | `FastShopGame`, `HypixelProfiles`, `HypixelProfile` | — | un vrai `/hypixel import` sur ton serveur |

### Étape 4 : statistiques et progression (Phase 3)

| # | Tâche | Cat. | Prio | Statut | Fichiers | Dépend de | Manque |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 3.2 | FKDR avec décimales partout (« 6.42 FKDR ») | Stats | P1 | IMPLÉMENTÉ | `BoardStat`, `StatsBook`, `StatsMenu`, `StatsDisplays`, `HeadLines` | 1.4 | — |
| 3.3 | Records practice : Lifetime d'abord, Weekly, Daily plus tard ; visibles ≥ 1 mois | Stats | P1 | IMPLÉMENTÉ (Weekly ajouté, gardé 5 semaines ; Daily plus tard) | `Records`, `RecordBook`, `RecordsMenu` | 1.4 | — |
| 3.4 | Menus practice : TOP 3 Lifetime du record du mode au survol | Stats | P1 | IMPLÉMENTÉ (les entraînements de RagoPractice, et Fast Shop : sa première liste) | `ModeMenu`, `ModeDisplay`, `RecordCatalog` | 3.3 | — |
| 3.1 | Trois leaderboards Daily / Weekly / Lifetime liés à une même stat | Stats | P1 | IMPLÉMENTÉ (CI passée : build, tests unitaires et tests réels) | `StatsDisplays`, `StatsBook`, `ModeStats`, `PlayerStats`, `StatsFiles`, `BoardStat` | 1.4 | méthode proposée le 21/11 (XP et ELO gagnés, ratios avec 5 / 15 parties), à valider ; murs : leaderboards Daily, joueurs Weekly, semaine Lifetime (ton choix du 21/11) |
| 5.4d | Sélecteur `NewLeaderboard Selector` (Win Streak, Level, ELO, Wins, Kills, FKDR, WWLR…) | Lobby | P1 | IMPLÉMENTÉ (CI passée) | `LobbyNpc`, `LobbyNpcs`, `LobbySettings`, `StatsDisplays`, point `newleaderboardselector` | 3.1 | config de ton serveur modifiée le 21/11 (`serveur-de-test/install-selecteur/appliquer-config.py`, ancienne copie dans `sauvegarde-avant-maj-21-11c`) ; à voir en jeu |
| 3.5 | Network Level au-dessus des joueurs par défaut (lobby, practice, autres) | Lobby | P1 | IMPLÉMENTÉ (tests unitaires passés en CI ; à voir en jeu) | `HeadLines`, `RagoLobby`, `LobbyScoreboard`, `ProfileMenu` | 1.3 | C4 |
| 3.6 | ELO des adversaires visible en Ranked | Ranked | P1 | IMPLÉMENTÉ (tests unitaires passés en CI ; à voir en jeu) | `LobbyScoreboard` (pseudo et Tab en partie) | 3.5 | — |
| 3.8 | Nombre de joueurs en file pour chaque mode | Files | P1 | TESTÉ (test réel en CI, Java 8 et 21 : les bots lisent 0, 1, 0 sous le PNJ Duels et dans le menu) | `QueueController`, `ModeMenu`, `LobbyNpcs`, `lobby-bots.mjs` | — | — |
| 3.7 | Organisation Solo / Party / Ranked (ELO par mode) : proposition avant tout code | Files | P2 | BLOQUÉ (proposition faite le 21/11, à valider) | `ModeCategory`, `ModeMenu`, `QueueController`, `Matchmaker`, `EloLadder` | 3.8 | validation de la proposition |

### Étape 5 : expérience joueur (Phase 4)

| # | Tâche | Cat. | Prio | Statut | Fichiers | Manque |
| --- | --- | --- | --- | --- | --- | --- |
| 4.1 | Party Replay : les membres de la party rejoignent le replay | Replays | P2 | IMPLÉMENTÉ : `/p warp` du chef dans un replay ; test réel à 2 bots passé en CI (Java 8) ; à voir en jeu | `ReplayService`, `ReplayViewer`, `Parties` | — |
| 4.1b | Sons dans les replays : étude de faisabilité | Replays | P3 | TESTÉ en CI : faisable ; sons envoyés par le serveur et blocs cassés enregistrés et rejoués ; test unitaire et test réel à 2 bots passés (run 339) ; à écouter en jeu | `ReplayRecorder`, `ReplayData` | — |
| 4.2 | `/host` : annonce cliquable toutes les 10 s (20 s depuis ta demande du 21/11) ; inscription gardée sans téléporter ; départ quand assez de joueurs ; poudre rouge pour quitter ou annuler | Events | P1 | Ta demande du 21/11 (poudre rouge « Leave the event » / « Cancel the event » au dernier slot, annonce centrée entre deux lignes avec [CLICK TO JOIN], toutes les 20 s) : IMPLÉMENTÉ le 22/11 dans cette PR (la PR #48 partait de l'ancienne version des events) ; TESTÉ par les bots en CI le 22/11 (Java 8 : l'annonce, le [CLICK TO JOIN], la poudre Leave puis Cancel ; le premier essai échouait côté test, le clic « en l'air » d'un bot qui vient de poser un bloc étant ignoré par le serveur : le bot clique maintenant le sol, comme un joueur) ; pas encore en jeu. Avant : ton choix « Les sortir de leur partie » : au lancement, les inscrits encore en partie la quittent comme avec /spawn et vont à l'event ; test réel à 3 bots passé ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `Events`, `EventGame`, `PracticeHost`, `HostWatcher` | — |
| 4.3 | Kit Editor plus clair et trié | Kits | P2 | TESTÉ en CI (21/11, run 342, Java 8 et 21) : kits rangés par catégorie et par nom, « Also for », Save et Cancel ramènent à la liste ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `KitEditor`, `KitLayouts` | — |
| 4.4 | Menu Stats : présentation, catégories, navigation | Stats | P2 | TESTÉ en CI (21/11, run 343, Java 8 et 21) : une page des catégories du sélecteur avec les totaux de chacune, un clic montre ses modes, Go back ramène aux catégories ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `StatsMenu` | — |
| 4.5 | `/discord` et `/store` avec les liens officiels | Cmd | P1 | BLOQUÉ | `config.yml` (`liens`), `ServerSettings` | liens officiels (C6) |

### Étape 6 : lobby (Phase 5)

| # | Tâche | Cat. | Prio | Statut | Fichiers / points | Manque |
| --- | --- | --- | --- | --- | --- | --- |
| 5.1 | Direction artistique : moins de rouge, identité Rago gardée | DA | P2 | À FAIRE | `Brand`, `config.yml`, menus, scoreboard | palette à valider |
| 5.2 | Build monumental « RAGO PRACTICE » (rocheux, angélique, stone, void), sans détruire le spawn : plan précis d'abord | Build | P2 | BLOQUÉ | monde `lobby` | image de référence |
| 5.3 | Blocs sous PNJ : Jouer quartz, Stats fer, Shop émeraude | Lobby | P1 | TESTÉ en CI (21/11, runs 345 et 346, Java 8 et 21) : `bloc:` sous chaque PNJ dans config.yml ; quartz sous les 5 PNJ pour jouer, fer sous les PNJ de stats, émeraude sous le Shop ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `Pedestal`, `LobbyNpcs`, `LobbySettings`, `config.yml` (`pnj:`) | — |
| 5.4a | `NewQuest` relié aux quêtes | Lobby | P1 | TESTÉ en CI (21/11, runs 345 et 346 : compilation, tests unitaires, test du lobby) : le PNJ des quêtes (clic = `/quests`) va sur le point choisi par `point-pnj-quetes:` (`newquest` pour ton serveur) ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `QuestsNpc`, `LobbySettings`, point `newquest` | — |
| 5.4b | `NewShop` sur émeraude | Lobby | P1 | TESTÉ en CI (21/11, runs 345 et 346 : compilation, tests unitaires, test du lobby) : le PNJ Shop passe du point `shop` au point `newshop` (config de ton serveur), sur ton bloc d'émeraude ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `LobbyNpcs`, `Pedestal`, point `newshop` | — |
| 5.4c | `NewDailyChest` sur diamant | Lobby | P1 | TESTÉ en CI (21/11, runs 345 et 346 : compilation, tests unitaires, test du lobby) : le coffre du jour va sur le point choisi par `point-coffre-du-jour:` (`newdailychest` pour ton serveur), sur ton bloc de diamant ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `DailyChest`, `LobbySettings`, point `newdailychest` | — |
| 5.5 | Daily Chest : animation à suspense, Rago Points 100–500, XP 100–2000, cosmétiques, probabilités configurables, jamais de doublon | Lobby | P2 | À FAIRE | `DailyChest`, `LobbyPoints`, `Cosmetic` | probabilités |
| 5.6A | `RagoClient` et `RagoOptimizer` : nom, skin, diamant, liens au clic, texte « OUR OTHER PRODUCTS », visuels publicitaires existants derrière | Lobby | P2 | BLOQUÉ | `LobbyNpcs`, `Billboards`, `panneaux/` | liens, skins |
| 5.6B | `qRago` (Owner) et `Kalisodev` (kaliso_dev, Dev) sur obsidienne, surélevés d'un bloc | Lobby | P2 | TESTÉ en CI (21/11, runs 345 et 346, Java 8 et 21), sauf le skin lu chez Mojang : PNJ `type: personne` sur tes points `qrago` et `kalisodev`, « [OWNER] qRago » et « [DEV] kaliso_dev » (les préfixes de `rangs:`), skin de leur compte Minecraft lu chez Mojang (`skin-joueur:`), sur obsidienne un bloc plus haut ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `LobbyNpc`, `LobbyNpcs`, `AccountSkins`, `LobbySettings`, `config.yml` | — |
| 5.6C | `jncl` et `nativ` (affiché woryes, skin Woryes), rôle Mod en jaune clair | Lobby | P2 | BLOQUÉ | `LobbyNpcs`, `RankDef` | pseudo Woryes, C7 |
| 5.7 | Texte de remerciement en anglais, fidèle à ton histoire, avec le Discord, à valider avant intégration | Lobby | P2 | À FAIRE | point `textremerciement`, `FakeHologram` | lien Discord |
| 5.8 | Revoir les skins des PNJ du lobby | Lobby | P2 | À FAIRE | `NpcSkins`, `skins.yml` | — |
| 5.9 | Boussole comme sur Minemen (ta demande du 22/11) : les catégories en haut, un espace, puis les modes de la catégorie choisie | Lobby | P1 | IMPLÉMENTÉ le 22/11, pas encore testé (CI en cours) : les 6 catégories sur la ligne du haut (slots 1 à 6), une ligne vide, les jeux de la catégorie choisie à partir du slot 19 ; un clic sur une autre catégorie change les jeux dans le même menu, celle choisie brille (« Its modes are below ») ; s'ouvre sur Practice, ou sur la catégorie du PNJ, de `/play` ou du groupe dont on revient ; « Popular now » passe sur la ligne du bas ; le menu garde la taille de la plus grande catégorie (Practice, 15 jeux : 6 lignes) | `ModeMenu`, `lobby-bots.mjs` | les catégories restent celles d'aujourd'hui ; d'autres (Duo, Ranked…, voir 3.7) sont à choisir |

### Étape 7 : nouveaux modes (Phase 6) — proposition avant tout code

Les propositions sont dans le document « Étape 7 : propositions des nouveaux modes » (21/11), avec
les 13 questions à trancher. Ta demande du 21/11 (« commence par ces 3 mods ») : Fireball Fight,
Snowball Spleef et Red Rover sont codés avec des valeurs de départ à confirmer (détail plus bas,
« Étape 7 : les trois premiers modes ») ; les autres attendent tes réponses.

| # | Tâche | Prio | Statut | Manque |
| --- | --- | --- | --- | --- |
| 6.1 | Catégorie FUN PRACTICE, PNJ `FunMod2` sur quartz | P2 | IMPLÉMENTÉ (21/11 ; tests unitaires et test du lobby en CI passés) | le skin du PNJ (Steve pour l'instant), le nom de la catégorie à confirmer ; poser le point `funmod2` sur ton serveur |
| 6.1a | Snowball Spleef (style Minemen) | P2 | TESTÉ par les bots en CI (21/11), pas encore en jeu | valeurs de départ à confirmer : rayon 12, 1 étage, écart 6, 1 boule par bloc, 1 round |
| 6.1b | Fireball Fight (voir C1) | P2 | TESTÉ par les bots en CI (21/11), pas encore en jeu | valeurs de départ à confirmer : rayon 10, recul 1,5 / 1,2, fireballs illimitées, souffle 3 s, 1 round ; le nom (« Fireball Fight », FUN PRACTICE) |
| 6.1c | Fast Bedwars (Bedwars 2018 simplifié) | P2 | BLOQUÉ (proposition écrite le 21/11, attend ta validation) | ce que « 2018 » veut dire (shop, améliorations, équipes, durée), maps |
| 6.1d | Autres modes FUN connus : propositions | P3 | BLOQUÉ (proposition écrite le 21/11, attend ta validation) | ton choix parmi Spleef, Bow Spleef, Parkour Duels, Pearl Fight, Battle Rush |
| 6.2 | Capture the Flag, version Bedwars et seconde version : études | P3 | BLOQUÉ (proposition écrite le 21/11, attend ta validation) | version à faire d'abord, équipes, captures, durée, maps, kit |
| 6.3 | Find Your Sensitivity : faisabilité et protocole | P3 | BLOQUÉ (proposition écrite le 21/11, attend ta validation) | durée d'une série, nombre de sensibilités, historique |
| 6.4 | Block Aim Practice (patterns de la communauté) | P3 | BLOQUÉ (proposition écrite le 21/11, attend ta validation) | patterns d'abord, parcours ou cibles, map |
| 6.5.1 | Red Rover (Sumo, Block Placement, Block Edit, Top Bridge…) | P2 | Sumo, Block Edit, Block Placement, Top Bridge : TESTÉ par les bots en CI (21/11) ; rien encore en jeu | question 10 (qui reste, points ou élimination : codé « points, le gagnant reste, premier à 3 », à confirmer) ; 90 s par 1v1 à confirmer  |
| 6.5.2 | Modes 2v1 asymétriques (Clutch Scenario, stuff amélioré) | P3 | Clutch Scenario : IMPLÉMENTÉ le 22/11, build et tests unitaires passés en CI (le partage du joueur seul, la party gardée ensemble) ; pas encore joué, ni par les bots ni en jeu ; en 1v1, 1v2, 1v3 et 1v4 comme Invis Practice et le Bed Break Simulator (ta demande du 22/11, à la place des 2v2, 3v3 et 4v4 ; les victoires de ceux-ci comptent toujours pour le titre Clutch Scenario) ; à partir du 1v2, le joueur seul, tiré au sort, part sans lit avec une vie, les autres gardent le leur (ton choix du 21/11) ; une party reste ensemble, le joueur seul est tiré parmi les autres (ton choix du 22/11). Stuff amélioré : BLOQUÉ | mode du stuff amélioré |
| 6.5.3 | Bot IA : étude technique | P3 | À FAIRE | l'étude d'abord, sans code (question 13) |
| 6.6 | Catégorie 3 joueurs (Red Rover, Clutch Scenario, Bed Break Scenario, Invis Practice) | P2 | IMPLÉMENTÉ le 22/11, CI verte (tests unitaires ; les bots du lobby ouvrent la boussole à 6 catégories et y prennent Duels au slot 12 ; tests du site) : « 3 Players » dans la boussole seulement, sans PNJ (ton choix du 21/11), après Fun Practice ; elle montre Red Rover (un objet), Bed Break Simulator 1v2, Invis Practice 1v2 et Clutch Scenario 1v2 (`aussi:` de `categories:`), avec les mêmes files : ces modes restent dans leur catégorie, leurs stats aussi ; le retour d'un groupe ouvert depuis elle y ramène ; aussi dans l'accueil, `/play 3 players` et le site (tests du site passés en local) | son nom, son slogan, sa description et son icône (une tête) sont à confirmer ; la boussole passe à 6 catégories, côte à côte des slots 10 à 15 (sur la ligne du haut, slots 1 à 6, depuis 5.9) |

### Étape 8 : administration et benchmark (Phase 7)

Proposition écrite le 22/11, rien n'est codé : document « Étape 8 : administration et benchmark —
propositions » (Claude Docs), avec 9 questions à trancher.

| # | Tâche | Prio | Statut | Fichiers | Manque |
| --- | --- | --- | --- | --- | --- |
| 7.1 | Outils Owner (Reach, Scaffold…) réservés au compte Owner | P3 | BLOQUÉ (proposition du 22/11 : UUID dans `config.yml`, Reach à la distance que tu choisis, Scaffold, jamais en Ranked, partie non comptée) | `StaffTools`, `StaffPanel`, `LobbyService` | C8 ; questions 1, 2 et 5 |
| 7.2 | Rejoindre une partie en cours comme participant (admin), sans fausser les stats | P2 | BLOQUÉ (proposition du 22/11 : `/join <joueur>`, l'admin ne garde pas son équipe en vie, partie non comptée) | `Spectating`, `PracticeGame`, `BedwarsGame`, `LobbyService` | questions 3, 4 et 5 |
| 7.3 | AntiCheat compatible KB custom, fireballs, TNT, practice ; Owner exempté | P1 | BLOQUÉ (proposition du 22/11 : Grim, gratuit, Java 17 ou plus ; alertes seules pendant un essai) | à choisir (Grim, Vulcan…) | questions 6, 7 et 8 |
| 7.4 | FPSbenchmark : PNJ `fpsbenchmark` sur `159:14`, hors boussole, scénarios reproductibles | P3 | BLOQUÉ (proposition du 22/11 : faux joueurs par paquets, caméra fixe, graine fixe) | point `fpsbenchmark` | question 9 : nom affiché, skin, lieux, nombres, durées |

### Étape 9 : release (Phases 8 et 9)

| # | Tâche | Prio | Statut | Manque |
| --- | --- | --- | --- | --- |
| 8.1 | Hébergeur européen ≤ 30 €/mois : comparatif d'offres réelles | P1 | À FAIRE | — |
| 8.2 | Site : vérifier, liens Store/Discord, Netlify, domaine | P1 | À FAIRE | achat du domaine (toi), liens |
| 9 | Sécurité, tests finaux (modes, stats, lobby, social, performances) | P1 | À FAIRE | — |

---

## 3. Journal des corrections

### 5.6B qRago et Kalisodev — TESTÉ EN CI (21/11), sauf le skin Mojang

**Demande** : « qRago : skin : mon skin Minecraft qRago, nom affiché : qRago, rôle : Owner, bloc :
obsidienne, position : surélevé d'un bloc » et « Kalisodev : skin : kaliso_dev, nom affiché :
kaliso_dev, rôle : Dev, bloc : obsidienne, position : surélevé d'un bloc ».

**Analyse** (avant le changement) :
- Tes points `qrago` (-0.5, 70, -41.6) et `kalisodev` (2.5, 70, -41.5) sont dans points.yml, sur le
  sol de quartz de la case Owner (y 69). Ton config.yml n'a aucun PNJ sur ces points : rien n'y
  apparaît aujourd'hui (pas de doublon possible).
- Les PNJ du lobby ne savaient qu'ouvrir des modes, des stats, le Shop ou le sélecteur, et ne
  portaient que des skins de PNG signés par MineSkin : ni personne sans clic, ni skin d'un compte.
- Les rôles existent déjà dans `rangs:` : `owner` « [OWNER] » en rouge foncé, `dev` « [DEV] » en
  bleu clair, les préfixes du chat, du tab et du dessus de la tête.
- Le skin d'un compte : le lobby le lit déjà chez Mojang pour les podiums (UUID du pseudo, puis le
  serveur de sessions, qui le donne signé : un client 1.8 l'affiche sans MineSkin).

**Lectures choisies** :
- « Rôle » : le préfixe du rang devant le nom, comme au-dessus de la tête d'un joueur :
  « [OWNER] qRago », « [DEV] kaliso_dev ».
- « Surélevé d'un bloc sur obsidienne » : le bloc d'obsidienne posé sur le point, le PNJ dessus, un
  bloc plus haut que le sol (comme les autres PNJ du lobby).
- Un clic sur eux ne fait rien (le plan n'en demande pas).

**Fait** :
- `type: personne` dans `pnj:` : un PNJ les mains vides, avec au-dessus son nom après le préfixe de
  son rang (`rang:`, un rang de `rangs:` ; un rang inconnu est refusé au démarrage). Un clic ne fait
  rien.
- `skin-joueur:` (pour tout PNJ, à la place de `skin:`) : le skin d'un compte Minecraft, lu chez
  Mojang hors du fil principal la première fois qu'un PNJ posé le porte depuis le démarrage, gardé
  dans `skins-joueurs.yml` (porté tout de suite au démarrage suivant, même si Mojang ne répond
  pas) ; un skin changé replace les PNJ ; un pseudo sans compte est dit dans le log, le PNJ reste
  en Steve. Un PNJ en attente porte Steve.
- Le config.yml fourni place `qrago` (nom qRago, rang owner, skin du compte qRago) et `kalisodev`
  (nom kaliso_dev, rang dev, skin du compte kaliso_dev), `bloc: OBSIDIAN` tous les deux.
- Pour ton serveur : les mêmes lignes ajoutées à ton config.yml (installé le 21/11, voir 5.3 et 5.4).

**Tests** : `PersonNpcTest` (qRago et kaliso_dev du config.yml fourni : leur
genre, leur compte, leur nom « §4[OWNER] §4qRago » et « §b[DEV] §bkaliso_dev », l'obsidienne ;
sans rang, le nom seul ; les refus : rang inconnu, pseudo invalide, `skin:` et `skin-joueur:`
ensemble, des modes ; `skin-joueur:` sur un autre PNJ ; `skins-joueurs.yml` écrit puis relu). Le
test du lobby sur un vrai serveur place qRago près du spawn et vérifie qu'il se tient sur de
l'obsidienne sous « [OWNER] qRago », les mains vides, et qu'un clic n'ouvre rien. Ce test retire
`skin-joueur:` (le serveur de test ne demande rien à Mojang) : **le skin lu chez Mojang n'est pas
testé**, il est à voir en jeu, comme l'aspect des deux PNJ. **Passés** en CI : runs 345
(commit 4923f7d) et 346 (commit 503eb0a), compilation, tests unitaires et test du lobby sur Java 8
et Java 21.

### 5.3 et 5.4 Blocs sous les PNJ, NewQuest, NewShop, NewDailyChest — TESTÉ EN CI (21/11)

**Demande** : « Blocs sous les PNJ principaux : PNJ Jouer = Quartz, PNJ Stats du Joueur = Fer, PNJ
Shop = Émeraude » et « NewQuest : PNJ des quêtes. Le relier au système de quêtes. NewShop : PNJ du
Shop. Le faire apparaître sur un bloc d'émeraude. NewDailyChest : Coffre des récompenses
quotidiennes. Le placer sur un bloc de diamant. »

**Analyse** (avant le changement) :
- Chaque PNJ du lobby (sauf les murs de classement) se tient un bloc plus haut que son point, sur un
  bloc d'andésite polie que le plugin pose à la place de ses pieds. Le bloc était fixe dans le code :
  aucun moyen d'en choisir un autre.
- Dans ton monde `lobby` (points.yml du 16/11 et fichiers de région) : l'andésite du plugin est sous
  `practice`, `rbw`, `duels`, `arenas`, `prive`, `shop` (3, 65, 11), `profil2` et
  `newleaderboardselector`. Le point `profil` n'existe plus (tu l'as retiré).
- Tes nouveaux points : `newshop` (13.7, 66, 19.5) avec des blocs d'émeraude posés à la main à
  y 64 et 65 ; `newdailychest` (13.5, 66, 23.5) avec un bloc de diamant à y 65 ; `newquest`
  (7.5, 65, 10.6) sur le sol de pierre.
- Le coffre du jour était lié au point `daily` (aujourd'hui en (-2, 65, 11)) et le PNJ des quêtes
  au point `quests` (à côté du coffre sans ce point), tous deux fixés dans le code. Le PNJ des
  quêtes est déjà relié au système de quêtes (un clic ouvre `/quests`).

**Lectures choisies** :
- « PNJ Jouer » : les 5 PNJ pour jouer (Practice, RBW Modes, Duels, Arenas et Private game).
- NewShop, NewDailyChest et NewQuest remplacent le Shop, le coffre et le PNJ des quêtes actuels
  (ils changent de place, sans doublon) : un seul Shop, un seul coffre, un seul PNJ des quêtes.

**Fait** :
- **5.3** : `bloc:` sous un PNJ dans config.yml choisit le bloc sur lequel il se tient (un nom ou un
  id, avec la data : `QUARTZ_BLOCK`, `"STONE:6"`, `"159:14"`) ; sans `bloc:`, l'andésite polie
  d'avant. Si ce bloc est déjà sous le point, seul (posé à la main, sans bloc du même genre à côté :
  pas un sol), le PNJ se tient dessus et rien n'est posé : c'est le cas de ton émeraude sous
  `newshop`. Le config.yml fourni met du quartz sous les 5
  PNJ pour jouer, du fer sous les PNJ de stats (`profil`, `profil2`) et de l'émeraude sous le Shop.
  Un bloc qui n'est pas un bloc plein (`DIAMOND`, `TORCH`, `AIR`, data > 15) est refusé au démarrage
  comme les autres erreurs de config.yml (RagoLobby se désactive), avec un message qui nomme
  `pnj.<point>.bloc`.
- **5.4** : `point-coffre-du-jour:` et `point-pnj-quetes:` dans config.yml choisissent les points du
  coffre et du PNJ des quêtes (`daily` et `quests` sans eux, comme avant).
- Pour ton serveur (**installé le 21/11**, build 346 ; ton ancien config.yml est dans
  `sauvegarde-avant-maj-21-11d\plugins\RagoLobby\`) : `serveur-de-test/install-pnj-blocs/appliquer-config.py`
  ajoute ces blocs à ton config.yml, passe le PNJ Shop sur le point `newshop` avec l'émeraude, et
  met `point-coffre-du-jour: newdailychest` et `point-pnj-quetes: newquest`. Rien d'autre ne
  change dans ton config.yml.
- **Restes à casser en jeu** après l'installation : l'ancien coffre en (-2, 65, 11) et l'andésite de
  l'ancien Shop en (3, 65, 11). Le plugin ne les retire pas (il ne casse pas de blocs qu'il ne
  pose plus). Les points `shop` et `daily` restent dans points.yml, sans effet.

**Tests** : `PedestalTest` (un bloc par son nom ou son id avec sa data ; les refus ; un bloc choisi
déjà sous le point, seul, est gardé, un sol de ce bloc non ; les blocs du config.yml fourni ; les
points du coffre et du PNJ des quêtes, et les erreurs nommées). Le test du lobby sur un vrai serveur
PandaSpigot vérifie en plus, à l'étape 1, que le PNJ Duels se tient sur du quartz et le PNJ de stats
sur du fer.
- CI run 344 (commit c3e8846) : compilation et tests unitaires passés ; le test du lobby a échoué
  (Java 8 et 21) : le PNJ Duels était à y 65 au lieu de 66. Cause : le monde de test est neuf, sa
  plateforme est en quartz, et la règle « bloc déjà sous le point » le prenait pour un socle posé à
  la main ; un sol de quartz sous les PNJ pour jouer aurait fait pareil dans un vrai lobby.
  Corrigé : le bloc n'est gardé que s'il est seul (aucun bloc du même genre au nord, au sud, à l'est
  ou à l'ouest), comme ton émeraude sous `newshop`.
- CI runs 345 (commit 4923f7d) et 346 (commit 503eb0a) : compilation, tests unitaires et test du
  lobby **passés** sur Java 8 et Java 21 (le PNJ Duels sur le quartz, le PNJ de stats sur le fer).
- Installé sur ton serveur le 21/11 (build 346, plugins et config.yml du lobby vérifiés octet par
  octet, anciens dans `sauvegarde-avant-maj-21-11d`) ; pas encore vu avec un vrai client.

### 4.4 Menu Stats — TESTÉ EN CI (21/11)

**Demande** : « Menu Stats : Améliorer la présentation, les catégories et la navigation. »

**Analyse** (avant le changement) : le menu « Your stats » (PNJ de stats, profil, clic sur un
joueur) montrait la tête du joueur puis tous les modes d'un coup, dans l'ordre du config.yml, ceux
joués d'abord : 66 modes visibles avec le config.yml fourni, sur 2 pages (45 + 21), l'entraînement,
le ranked, les duels et les arènes mélangés, sans aucune catégorie. Pour trouver un mode, il
fallait survoler les cases une par une ; aucun total par catégorie (combien de victoires en Duels,
en RBW…).

**Fait** :
- **Catégories** : la première page montre la tête (les totaux, comme avant) et les catégories du
  sélecteur dans son ordre (Practice, RBW Modes, Duels, Arenas), avec leur icône, une case sur
  deux au milieu, comme le sélecteur. « Other modes » s'ajoute seulement si le joueur a joué un
  mode d'aucune catégorie (aucun avec le config.yml fourni : les 71 modes ont la leur).
- **Présentation** : chaque catégorie montre son slogan et les totaux du joueur dans ses modes :
  parties, victoires, défaites, WLR, meilleure série, kills, morts, K/D, final kills, final deaths,
  FKDR et lits quand il y en a, victoires du jour et de la semaine, et « Modes played: x/y » ;
  « No game yet » si rien n'est joué.
- **Navigation** : un clic sur une catégorie montre ses modes (titre « Your stats: Duels »), ceux
  joués d'abord, chacun avec ses stats comme avant ; « Go back » (la flèche des autres menus, en haut à gauche) ramène aux
  catégories ; les flèches de page passent en haut à droite (cases 7 et 8).
- Sans `categories:` dans config.yml, le menu reste celui d'avant (tous les modes d'un coup).
  Rien ne change dans les fichiers de stats.

**Tests** (CI, run 343 du 21/11, commit 625a39d) : la compilation et les tests unitaires passent
(`StatsMenuTest` : les cases des catégories, chaque mode du config.yml fourni dans sa catégorie,
les totaux d'une catégorie) ; le test du lobby sur un vrai serveur PandaSpigot passe sous Java 8
et Java 21 : à l'étape 4, RagoUn ouvre le menu du PNJ de stats, lit dans la catégorie Duels sa
victoire et ses 3 kills du Sumo, clique dessus, lit les stats du Sumo dans « Your stats: Duels »,
puis revient aux catégories avec Go back. Pas encore vérifié avec un vrai client (l'aspect des
icônes et du texte), ni installé sur ton serveur.

### 4.3 Kit Editor — TESTÉ EN CI (21/11)

**Demande** : « Plus clair. Mieux organisé. Mieux trié. Plus facile à utiliser. »

**Analyse** (avant le changement) : le livre ouvrait une seule liste de tous les modes qui ont un
kit (24 dans le config.yml fourni), dans l'ordre du config.yml : Block edit, Invis practice,
Attack and defense, Bed Break, puis les duels et les arènes mélangés, sans catégorie. Les modes
cachés qui prêtent leur kit (`invis-practice`, `bed-break-simulator`) n'y disaient pas qu'ils
servent aux formats 1v1 à 1v4, ni `lobby-fight-ffa` qu'il sert au Lobby Fight 1v1. Après Save, le
livre se fermait : pour ranger un autre kit, il fallait le rouvrir. Rien n'expliquait qu'un kit
rangé pendant une partie est gardé aussi.

**Fait** :
- **Mieux trié, mieux organisé** : les kits par catégorie, dans l'ordre du sélecteur (Practice, RBW
  Modes, Duels, Arenas), triés par nom dans chacune (Bed Fight 1v1 avant Bed Fight 2v2) ; une ligne
  par catégorie, son icône et son nom en première case, puis ses kits (8 par ligne). Avec le
  config.yml fourni, tout tient sur une page (5 lignes) ; au-delà, des flèches de page en bas.
- **Plus clair** : chaque kit dit quels autres modes le jouent (« Also for: Invis Practice 1v1,
  … ») et s'il est rangé par le joueur ; un livre « How it works » en bas explique le rangement,
  aussi pendant une partie.
- **Plus facile** : Save et Cancel ramènent à la liste (à la même page), pour ranger plusieurs kits
  à la suite ; fermer avec Échap enregistre et ferme, comme avant. L'import Hypixel et la boutique
  restent en bas (mêmes cases).
- Rien ne change dans les rangements enregistrés (`plugins/RagoLobby/kits/`).

**Tests** (CI, run 342 du 21/11, commit 846ced9) : la compilation et les tests unitaires passent
(`KitEditorTest` : l'ordre par catégorie puis par nom, une catégorie de plus de 8 kits, les pages,
le texte « Also for ») ; le test du lobby sur un vrai serveur PandaSpigot passe sous Java 8 et
Java 21 : à l'étape 1, RagoUn trouve le kit Sumo dans la nouvelle liste, déplace son bâton,
clique Save, lit « Kit saved for Sumo duel », retrouve la liste « Kit editor » avec la barre du
lobby, puis retrouve le bâton là où il l'a mis. Pas encore vérifié avec un vrai client (l'aspect
des lignes et des icônes), ni installé sur ton serveur.

### 4.2 Events /host — TESTÉ EN CI (21/11, ton choix)

**Ton choix** (carte de décision, 21/11) : « Quand un événement /host se lance, que faire des
inscrits encore en partie ? » → **« Les sortir de leur partie »** : « Au lancement, les inscrits
encore en partie l'abandonnent et sont amenés dans l'événement. » Cela remplace l'option
recommandée que j'avais codée en attendant (décrite plus bas : départ quand assez d'inscrits sont
hors partie, les autres prévenus qu'il a commencé sans eux).

**Changé** :
- **Lancement** : après `attente-secondes`, l'event commence dès qu'il a `joueurs-min` inscrits
  en ligne, **en partie ou non**. Chaque inscrit encore en partie lit « [Event] The Sumo event
  starts: you leave your game for it. », puis quitte sa partie **exactement comme avec `/spawn`**
  (le même chemin du lobby, `goToSpawn`), et tous partent ensemble à l'event par la file de son
  mode, comme avant.
- Conséquence voulue par « l'abandonnent » : c'est l'abandon habituel de `/spawn`. Dans un duel
  ou une partie, l'adversaire gagne comme si le joueur avait tapé `/spawn` ; après une partie
  **classée**, la file classée lui est fermée `file.abandon-classe-minutes` (5 min sur ton
  serveur), avec son message. Je n'ai pas ajouté d'exception : dis-le si tu veux que l'event
  l'épargne.
- L'état de l'event dans le message public : « 3/40 players (1 in a game), starting » à la fin de
  l'attente ; sous `joueurs-min` inscrits, « needs N more players to start » comme avant. La ligne
  « starts when N more are out of a game » disparaît.
- Les events automatiques (« Rago ») suivent la même règle : à la fin de l'attente, ils partent
  avec `joueurs-min` inscrits, en partie ou non, et tombent sinon.
- Code : `LobbyService.leaveGame` (nouveau, le `/spawn` du lobby), `Events` (`online`,
  `launches`, `launch`, `status`). Docs : `rago-practice/README.md` (Les events), commentaire de
  `evenements:` du config.yml, `tools/lobby-check/README.md` (étape 11).

**Tests** : `EventsTest` adapté (l'état « starting » avec des inscrits en partie, le lancement
après l'attente avec des inscrits en partie ou non). Le test réel à 3 bots (étape 11) est adapté :
RagoDeux, en Block clutch, doit lire « The Sumo event starts: you leave your game for it. »,
puis les trois « You joined the Sumo event of RagoTrois. » et « The Sumo event starts with 3
players! » ; au premier combat, RagoTrois puis RagoDeux font `/spawn`, RagoUn doit gagner et les
trois revenir au lobby. **Passés** en CI (run 346, commit 503eb0a, Java 8 et Java 21). Ce que
les bots ont lu (Java 21) :
- Pendant l'attente, rien ne change : RagoDeux s'inscrit depuis son Block clutch et continue d'y
  jouer ; le message revient 10,0 s après le premier (« 3/40 players (1 in a game), starts in 5s »).
- À la fin de l'attente, RagoDeux, en plein Block clutch, lit « [Event] The Sumo event starts: you
  leave your game for it. » (puis le gain de son entraînement, « +7 Network XP (practice) », comme
  avec `/spawn`) ; les trois lisent « [Event] You joined the Sumo event of RagoTrois. », puis
  « [Event] The Sumo event starts with 3 players! » et « [Event] RagoTrois vs RagoUn ».
- RagoTrois quitte le combat (`/spawn`) : « RagoTrois left the fight. », « RagoTrois was
  eliminated. » ; RagoDeux, qui attendait son tour, quitte l'event : « RagoDeux left the event. » ;
  « [Event] ★ RagoUn won the Sumo event of RagoTrois! » chez les trois, +250 XP pour RagoUn ; les
  trois reviennent au lobby.

#### Avant ton choix — TESTÉ EN CI (21/11, run 341)

**Demande** : « Lorsqu'un événement est créé avec /host, envoyer un message public dans le chat
toutes les 10 secondes jusqu'au lancement de l'événement », cliquable pour le rejoindre, comme sur
Minemen ; l'event ne commence que lorsqu'il y a assez de joueurs ; avant : ne pas téléporter les
joueurs, les laisser dans leurs parties et leurs modes Practice, garder leur inscription.

**Analyse** (avant le changement) : `/host` mettait l'hôte tout de suite dans la partie de l'event
(la file du mode caché `event-sumo`…), `/join` y téléportait aussi chaque joueur, et il fallait
avoir quitté sa partie pour rejoindre. L'annonce partait à la création puis à 45, 30, 15 et 5 s,
seulement aux joueurs hors partie. À la fin de `attente-secondes`, l'event était annulé sous
`joueurs-min` joueurs.

**Fait** (option recommandée de la carte de décision, en attendant ta réponse) :
- `/host` n'envoie plus personne nulle part : il **annonce** l'event, l'hôte inscrit en premier.
- Le message public part à **tous les joueurs en ligne** (en partie aussi) **toutes les 10
  secondes jusqu'au lancement** : « [Event] RagoTrois is hosting Sumo! One 1v1 at a time on the
  platform: knock your opponent off. The last one in wins. », puis « [Click to join] or /join:
  3/40 players (1 in a game), starts in 5s » (le bouton lance `/join`). Un inscrit lit « You are
  in (…). Keep playing: you go to it when it starts. /event leave to leave. » à la place du bouton.
- `/join` (ou le bouton) **inscrit sans téléporter** : le joueur continue sa partie ou son
  entraînement, reste dans son mode Practice, garde son inscription ; `/event leave` la retire.
- **Lancement** : après `attente-secondes`, dès que `joueurs-min` inscrits sont hors partie, ceux-là
  partent ensemble à l'event ; les inscrits encore en partie lisent « The Sumo event started
  without you: you were in a game. ». L'event attend ses joueurs 10 s au plus, puis se joue comme
  avant. Un event de joueur attend tant qu'il n'a pas assez d'inscrits (son hôte ou le staff peut
  l'annuler avec `/event cancel`) ; il tombe si son hôte quitte le serveur sans autre inscrit.
- Les events automatiques du serveur (« Rago ») sont annoncés de la même façon ; sans
  `joueurs-min` inscrits hors partie à la fin de l'attente, ils tombent, comme avant.
- Docs : `rago-practice/README.md` (Les events) et le commentaire de `evenements:` du config.yml.

**Tests** : tests unitaires (`EventsTest` : le texte de l'état, le moment du lancement, le bouton)
et un test réel à 3 bots dans le test du lobby (`lobby-bots.mjs`, étape 11). **Passés** sur un
vrai serveur PandaSpigot en CI (run 341, Java 8 et Java 21). Ce que les bots ont lu (Java 8) :
- RagoTrois, opérateur, tape `/host sumo` : les trois lisent « [Event] RagoTrois is hosting Sumo!
  One 1v1 at a time on the platform: knock your opponent off. The last one in wins. » ; RagoUn
  (au lobby) et RagoDeux (en Block clutch) ont le bouton « [Click to join] or /join: 1/40 players,
  starts in 15s », dont le clic lance bien `/join` ; RagoTrois lit « You are in (1/40 players,
  starts in 15s) ».
- RagoDeux tape `/join` dans son Block clutch : « You joined the Sumo event of RagoTrois (2/40
  players (1 in a game), starts in 15s) » et reste dans son entraînement (il continue de tomber et
  de recommencer) ; RagoUn s'inscrit et ne bouge pas du lobby.
- Le message revient **9,9 s** après le premier : « You are in (3/40 players (1 in a game),
  starts in 5s) » pour les trois inscrits.
- À la fin de l'attente (15 s dans le test), RagoDeux, toujours en Block clutch, lit « The Sumo
  event started without you: you were in a game. » ; RagoUn et RagoTrois partent à l'event :
  « The Sumo event starts with 2 players! », « RagoTrois vs RagoUn ».
- RagoTrois quitte le combat avec `/spawn` : « ★ RagoUn won the Sumo event of RagoTrois! » chez
  les trois, +250 XP pour RagoUn, et chacun revient au lobby.

Pas encore essayé en jeu par un joueur.

### 4.1 Party Replay — IMPLÉMENTÉ (21/11), test réel passé

**Demande** : « Lorsqu'un joueur lance un replay en étant dans une Party, les autres membres
doivent pouvoir être warp dans ce replay. »

**Analyse** : un replay est montré à un seul joueur (`ReplayViewer`) : les corps des joueurs sont
des paquets envoyés à lui seul, les blocs changent dans le monde du replay (un emplacement de
`rago_replays` pour un duel, une copie de la map pour une partie de Bed Wars). `/p warp` existait
déjà, mais seulement depuis le lobby (« Go back to the lobby (/l) to warp your party. »).

**Fait** (sans nouvelle commande, avec le `/p warp` d'Hypixel) :
- Le chef de party qui regarde un replay tape `/p warp` : chaque membre en ligne y vient, comme
  `/p warp` au lobby (un membre en partie la quitte comme avec `/spawn`, un membre dans un autre
  replay le quitte). Il arrive à côté du chef : « RagoUn warped you into the replay of … ».
- Les membres voient **le même replay au même moment** : les mêmes blocs (le même monde), et les
  corps et projectiles envoyés à chacun au moment du chef. Le chef mène le replay (pause, recul,
  avance, vitesse) ; la barre des membres n'a que les joueurs (aller voir un joueur) et le lit
  (quitter). Le chef qui quitte le replay ramène les membres au lobby (« RagoUn left the
  replay. »).
- Un chef de party qui lance un replay voit le rappel « Your party can watch it with you:
  /p warp ».
- `spectateurs-max` (6 replays en même temps) compte les replays, pas les membres venus dans celui
  du chef : ils n'ajoutent pas de monde ni d'emplacement.

**Test** : un test réel à 2 bots ajouté au test du lobby (`lobby-bots.mjs`, étape 9) : RagoUn, chef
de la party, ouvre `/replay`, regarde un duel de sumo, fait `/p warp` ; RagoDeux doit arriver à
moins de 2 blocs de lui, au même moment du replay dans le scoreboard, avec seulement la boussole et
le lit dans sa barre ; RagoUn quitte, RagoDeux doit être revenu au lobby. **Passé** sur un vrai
serveur PandaSpigot en CI (run 338, Java 8 ; le job Java 21 n'a pas démarré, panne d'installation
de Java chez GitHub) : « You summoned 1 player to your replay. », « RagoUn warped you into the
replay of Sumo duel, 0:11 long. », même moment (0:00 / 0:11) dans les deux scoreboards, puis
« RagoUn left the replay. » et RagoDeux au lobby. Pas encore essayé en jeu par un joueur.

### 4.1b Sons dans les replays — FAISABLE, TESTÉ EN CI (21/11)

**Demande** : « Ajouter les sons des replays si techniquement possible. Analyse la faisabilité. »

**Analyse** :
- Avant, un replay ne gardait aucun son. Seuls les coups (le corps qui clignote rouge, dont le
  client du spectateur fait lui-même le bruit) et les explosions (un son ajouté au replay) en
  avaient.
- En 1.8, les sons d'une partie viennent de deux endroits :
  1. le serveur les envoie aux joueurs proches : blocs posés, arc, coups, TNT allumée, explosion,
     et tous les sons des plugins (compte à rebours, lit détruit, kill…) ;
  2. le client les fait de lui-même : ses propres pas, le bloc qu'il casse lui-même, etc. Les
     autres joueurs reçoivent le bruit et les éclats du bloc cassé par le serveur.
- Bukkit n'a pas d'événement pour les sons. PacketEvents (déjà installé pour le lobby) voit
  chaque paquet de son envoyé. **C'est donc faisable** :
  - enregistrer les sons envoyés aux joueurs de la partie ;
  - enregistrer chaque bloc cassé (événement Bukkit) ;
  - les rejouer au spectateur au même endroit et au même moment.
- **Limites** :
  - Les pas des joueurs, que le serveur n'envoie jamais, ne sont pas enregistrés.
  - Un son que seul un joueur a reçu (un « ding » de kill) est rejoué à l'endroit où il l'a
    entendu.
  - Un replay enregistré avant ce changement reste sans sons. Il se lit toujours : le format ne
    change pas, deux types d'enregistrement s'ajoutent.

**Fait** :
- Pendant une partie enregistrée, chaque son envoyé à l'un de ses joueurs est enregistré, une
  seule fois même si tous l'ont reçu. Pour chaque son : son nom 1.8, l'endroit, le volume et la
  hauteur.
- Chaque bloc cassé par un joueur est enregistré avec son type.
- Au replay, le spectateur entend ces sons au même endroit et au même moment, et voit les éclats
  des blocs cassés. Les membres d'une party venus dans le replay du chef les entendent aussi.
- Pendant un saut (10 secondes en avant ou en arrière), seuls les derniers instants sont joués,
  comme les coups.

**Tests** :
- Test unitaire `ReplayDataTest` : un son et un bloc cassé relus tels qu'écrits.
- Test réel (`lobby-bots.mjs`, étape 9) : dans le replay du duel de sumo gagné 3 - 0, RagoUn doit
  entendre les notes du compte à rebours. RagoDeux, venu par `/p warp`, doit entendre celles d'une
  manche suivante.
- **Résultat** (run 339, commit cf08a76, Java 8 ; Java 21 passé aussi après une relance pour une
  panne d'installation de Java chez GitHub) : le test unitaire passe ; dans le vrai serveur,
  RagoUn entend `note.hat` (le compte à rebours du duel) dans le replay, et RagoDeux, venu par
  `/p warp`, l'entend aussi moins d'une seconde après son arrivée. Le rendu à l'oreille reste à
  juger en jeu.

### 3.7 Organisation Solo / Party / Ranked — PROPOSITION (21/11), à valider

Rien n'est codé. La proposition est dans `sortie-publique/proposition-solo-party-ranked.md` (fichiers
du projet) : le sélecteur s'ouvre sur SOLO (les 4 catégories d'aujourd'hui), PARTY (parties
privées à la taille de la party, file en équipe, Ranked en équipe : tout existe déjà) et RANKED
(Ranked Bedwars, plus une version classée de modes de Solo, chacune avec son ELO). Quatre
questions : quels modes en Ranked (1v1, 2v2), Ranked Bedwars dans RBW Modes ou non, FFA de party
et duels party contre party ou non, boussole ou trois objets.

### 3.8 Nombre de joueurs en file — TESTÉ (test réel en CI, 21/11)

**Ce qui existe déjà.** Chaque mode qui a une file montre combien de joueurs y attendent, et le
nombre est recalculé chaque seconde : dans le sélecteur (`In queue: N` sur chaque mode, le total
sur chaque catégorie, chaque mode d'un groupe, « Popular now », et `Waiting for this map` dans le
menu des maps), sous chaque PNJ de modes (`N in queue · Click to play`, la somme de ses modes) et
dans `/queue`. Les modes joués seuls (entraînements) n'ont pas de file : ils démarrent tout de
suite. Le nombre vient de la file elle-même (`Matchmaker.waiting`) : un joueur compte une fois par
file où il attend, une party compte chacun de ses joueurs.

**Mises à jour vérifiées dans le code.** Un joueur sort des files quand sa partie est trouvée, quand
il lance une partie par un autre chemin (`/duel`, partie privée), quand il quitte le serveur (sa
party avec lui), quand sa party change, et quand il quitte la file ; pendant un entraînement il
garde sa place (voulu). Je n'ai pas trouvé de cas où le nombre resterait faux.

**Ce qui manquait : une vérification réelle.** Aucun test ne lisait ces nombres. Le test sur un vrai
serveur (`tools/lobby-check`, bots en CI) les lit maintenant : le sumo montre `In queue: 0` dans le
sélecteur ; sous le PNJ Duels, RagoDeux lit `0 in queue`, puis `1 in queue` quand RagoUn rejoint
la file, puis `0 in queue` au retour du duel. **Passé** en CI (run 335, build 8daefe6) sur
PandaSpigot 1.8.8, en Java 8 et en Java 21.

### 3.6 ELO des adversaires en Ranked — IMPLÉMENTÉ (21/11)

**Choix.** Parmi Tab, scoreboard, pseudo et lobby d'attente : **le pseudo**, qui en partie est
aussi ce que montre le Tab (les joueurs d'une partie n'ont pas de nom de Tab à eux, le client
montre l'équipe : préfixe, pseudo, suffixe). Une seule modification, lisible pour chaque
adversaire, en 1v1 comme en 4v4, ce que le scoreboard de côté ne peut pas (15 lignes, et Ranked
Bed Wars y met déjà les siennes). Le lobby d'attente ne sert pas : on attend la file au lobby sans
connaître l'adversaire, on le découvre au compte à rebours, où l'ELO s'affiche déjà.

**Ce qui change.** En partie classée **qui compte** (mode classé, venu de la file : pas une partie
privée de party ni un `/duel`, qui ne bougent pas l'ELO), le suffixe du pseudo montre l'ELO du
joueur dans ce mode, avec sa division, **à la place** du niveau Network : `R Sofiene Gold 245`,
au-dessus de la tête et dans le Tab, dès le compte à rebours. Au-delà de 16 caractères (Obsidian à
10 000 et plus) : l'ELO seul, à la couleur de sa division. Ailleurs, le niveau reste. Un joueur
sous `/nick` garde le niveau 1 de son nick : son ELO dirait qui il est.

**Tests.** Test unitaire du texte (`NameStyleTest`, de 0 à 20 000, toujours dans 16 caractères),
passé en CI (run 335). **À vérifier en jeu** : une partie Ranked depuis la file, l'ELO de l'adversaire
au-dessus de sa tête et dans le Tab.

### 3.5 Network Level au-dessus des joueurs par défaut — IMPLÉMENTÉ (21/11)

**Avant.** Deux choses s'affichent au-dessus d'un joueur :

- la **ligne au-dessus du pseudo** (`HeadLines`, un porte-armure invisible), au lobby seulement,
  au choix du joueur (`/display`). Par défaut c'était l'ELO classé, donc « Unranked » au-dessus de
  presque tout le monde (c'est le « UNRANKED » du point 1.3) ;
- le **pseudo** lui-même (équipe de scoreboard, `LobbyScoreboard`) : rang devant, badge et niveau
  `[12✫]` après, partout. Mais une équipe 1.8 n'a que 16 caractères après le pseudo : avec un long
  badge (`BETTER`, `✫100`…), le niveau sautait, au lobby comme en entraînement.

**Ce qui change.**

- La ligne par défaut est le **niveau Network** (`Level [12✫]`) : `HeadLines.Shown.DEFAULT`,
  utilisé par la ligne, le menu `/display` et le profil. Le choix déjà fait par un joueur est gardé
  (qui avait choisi l'ELO le garde) : rien n'est effacé dans les fichiers de stats.
- Au lobby, quand la ligne montre le niveau, le pseudo ne le répète pas : il garde son badge.
- Hors du lobby (entraînements, duels, Bed Wars et les autres modes), il n'y a pas de ligne au-dessus
  du pseudo : le niveau reste après le pseudo, et passe désormais **avant le badge** quand les deux
  ne tiennent pas. En partie d'équipe (Bed Wars, duels), le niveau était déjà seul après le pseudo.

Pourquoi pas la ligne flottante en partie : en partie, la vie (« 20 ❤ ») est sous le pseudo, ce qui
remonte le pseudo d'une ligne (rendu du client 1.8, `RenderPlayer`) ; une ligne flottante à hauteur fixe le
chevaucherait. Le niveau après le pseudo est lu par le client lui-même, sans ce risque.

**Tests.** Tests unitaires : le défaut et le choix gardé (`HeadLineNickTest`), le pseudo sans le
niveau en double et le niveau avant le badge hors du lobby (`NameStyleTest`). Passés en CI (run
335). **À
vérifier en jeu** : au lobby, `Level [x✫]` au-dessus d'un joueur qui n'a rien choisi, et son pseudo
sans `[x✫]` répété ; en entraînement avec un badge `BETTER`, le niveau après le pseudo.

### 3.1 et 5.4d Leaderboards Daily / Weekly / Lifetime et sélecteur — IMPLÉMENTÉ (21/11)

**Avant** (lu dans `StatsDisplays`, `StatsBook`, `ModeStats`, `PlayerStats`, `BoardStat`) : chaque
tableau a sa stat (ou plusieurs, un clic passe à la suivante) et chaque joueur change sa période
d'un clic. Seuls les victoires, parties, meilleure série et kills avaient un compteur du jour et
de la semaine. Le niveau Network, le FKDR et l'ELO n'existaient qu'à vie ; le WLR n'était pas un
classement. Le PNJ `newleaderboardselector` avait un point, mais rien dans `config.yml`.

**Fait** :
- Nouveau type de PNJ `selecteur` : un clic ouvre le menu « Leaderboards » de ses stats (celles du
  plan : Win Streak, Network Level, ELO avec un objet par mode classé, Wins, Kills, FKDR, WLR, puis
  Games played). La stat choisie s'affiche aussitôt, pour celui qui la choisit, sur les murs qui
  suivent le sélecteur (`selecteur:` + `periode:` dans `classements:`). Un clic sur l'un des trois
  murs passe à la stat suivante sur les trois.
- Compteurs du jour et de la semaine ajoutés : XP gagnée, ELO gagné ou perdu par mode classé,
  final kills, final deaths, défaites. Gardés dans les fichiers de stats ; ils partent de zéro avec
  cette version (pas d'historique jour par jour avant).
- Méthode de calcul (proposée, à valider) : Daily et Weekly du niveau = XP gagnée ; de l'ELO = ELO
  gagné dans le mode, négatif s'il est perdu, pour tous ceux qui ont joué ce mode dans la période ;
  du FKDR et du WLR = ratio des compteurs de la période, à partir de 5 parties le jour et 15 la
  semaine (`minimum-parties:`, pour le FKDR les parties des modes Bed Wars). Lifetime = le
  classement à vie actuel, sans minimum.
- WLR (`wlr`) ajouté aux stats des tableaux : victoires / défaites à vie, deux décimales.
- Les murs actuels (`top`, `leaderboards`, `joueurs`, `semaine`) ne changent pas tant que leur
  config ne dit pas `selecteur:`.

**Reste** : ta réponse sur l'ordre des trois murs à côté du sélecteur (`leaderboards`, `joueurs`,
`semaine`), puis la config de ton serveur à l'installation ; la validation de la méthode.

**Tests** :
- `StatsBookTest.theLeaderboardsOfTheSelectorHaveADayAWeekAndAnEver` : ELO gagné et perdu du jour
  et de la semaine, XP du jour et de la semaine, FKDR et WLR avec leur minimum (une partie de sumo
  ne compte pas pour le FKDR), Lifetime sans minimum, tout relu depuis le fichier.
- `StatsDisplaysTest.theThreeWallsOfTheSelectorShowTheSameStatEachOnItsPeriod` : les trois murs
  sur la même stat, chacun sur sa période, le choix au sélecteur, le clic sur un mur, le menu.
- `LobbySettingsTest.aWallOfTheSelectorFollowsASelectorOnItsOwnPeriod` : la config et ses erreurs.
- Passés en CI (run 335, après la correction d'un test de `StatsBookTest` : un kill final daté
  d'un autre jour que son kill). **Pas encore vu en jeu.**

### 3.4 Record du mode au survol dans les menus — IMPLÉMENTÉ (21/11)

**Avant** : survoler un mode, dans la boussole ou le menu d'un PNJ, montrait pour tous les modes
la « Daily Streak » à la Minemen : les victoires d'affilée du jour, puis le top 3 des séries du
jour (`ModeMenu.streakLines`). Le plan le dit : pour un entraînement, elle n'est pas pertinente.

**Fait** :
- Pour chaque entraînement de RagoPractice qui a des records (les 15 de `/records`), la Daily
  Streak est remplacée par son record Lifetime : « Lifetime record: Island clutch », le top 3
  (« 1. Alice - 20 clutches »), puis « Your best: 8 clutches (#5) » ou « Your best: - ».
- Le record montré est celui de la variante sur laquelle l'entraînement commence : la première
  (Block in : 3 couches côté bois ; Block clutch : island clutch), et pour le block edit la
  vitesse du bot par défaut (2).
- RagoPractice le donne au lobby par `LobbyService.setModeRecords`, à chaque ouverture de menu.
  Si RagoPractice s'arrête, les modes reprennent leur Daily Streak.
- Les duels (Sumo, Boxing, Bridge...) gardent leur Daily Streak : là, elle a un sens.

- Fast Shop (RagoBedwars) a un record par liste d'objets à acheter, dix listes à la suite. Il
  montre, par la même règle, le record de sa première liste, « Rush start », celle par laquelle
  chaque partie commence : « Lifetime record: Rush start », le top 3 (« 1. Bob - 2.91 s ») et
  le meilleur temps du joueur avec sa place. À changer si tu préfères une autre liste.

**Tests** :
- `RecordsMenuTest.theRecordOfATrainingForTheMenusOfTheLobby` : le top 3, la place du joueur, la
  vitesse 2 du block edit, rien pour un duel, et chaque entraînement a son record.
- `StatsDisplaysTest.aTrainingShowsItsRecordInThePlaceOfItsDailyStreak` : les lignes du menu, et
  la Daily Streak qui revient sans RagoPractice.
- `BedwarsModesTest.fastShopShowsTheRecordOfItsFirstListInTheMenusOfTheLobby` : le top 3 de Rush
  start, l'ordre à temps égal, la place du joueur, une liste sans temps.
- Lancés par la CI. **Pas encore vu en jeu** : survoler Block clutch, Block in et Fast Shop dans la boussole.

### 3.3 Records Practice : Lifetime et Weekly — IMPLÉMENTÉ (21/11)

**Avant** (lu dans `Records`, `RecordBook`, `RecordsMenu`, `ClutchTraining`, `DailyChallenge`) :
- Les records des entraînements étaient déjà Lifetime : le meilleur score de chaque joueur, gardé
  pour toujours dans `records.yml`. Rien ne les vidait, sauf la remise à zéro du Block in que tu
  as demandée (1.8). `/records`, le mur des records et le scoreboard des entraînements ne
  montraient que ceux-là.
- Pas de records Weekly. Le seul classement de la semaine était celui des plus gros clutchs du
  Block clutch (les récompenses du lobby), gardé deux semaines.
- Le Daily n'existait que par le défi du jour : un entraînement par jour, son top du jour.

**Fait** :
- **Lifetime d'abord, et conservés** : rien ne change pour eux. Ils restent jamais vidés, et
  premiers partout : le top 10 de `/records` s'appelle maintenant « Lifetime top 10 », et le mur
  et le scoreboard des entraînements les montrent comme avant.
- **Weekly** : chaque score sur un record compte aussi pour la semaine, du lundi au dimanche,
  heure de Paris, comme les semaines du Block clutch (`weekly.2026w47.clutch.island` dans
  `records.yml`). Ça concerne tous les records de `/records`, pas les plus gros clutchs, qui sont
  déjà un classement de la semaine.
- **Visibles au moins un mois** : une semaine est gardée et montrée encore 5 semaines après la
  sienne. Le dernier jour d'une semaine reste donc visible 35 jours, plus qu'un mois. Avec 4
  semaines, ce serait 28 jours, moins qu'un mois. Ensuite, la semaine est retirée de
  `records.yml`. Les records Lifetime ne le sont jamais.
- **Où on les voit** :
  - Dans `/records`, sur chaque record, sous le Lifetime top 10 : le top 3 de la semaine
    (« This week », et le joueur sous lui s'il est plus loin), puis le meilleur de chacune des
    5 semaines d'avant qui en a un (« Last week », « 2 weeks ago »…).
  - Sur le mur des records, une ligne sous le top 10 : « This week: Bob - 20 clutches ». Elle
    remplace la ligne vide, donc le mur garde ses 19 lignes.
- **Daily** : rien de nouveau, comme demandé (« lorsque le serveur sera plus actif ») ; le défi du
  jour garde son top du jour.
- **Une remise à zéro demandée** (comme celle du Block in) vide aussi les semaines de ces records.

**Tests** :
- `RecordBookTest` (3 nouveaux, passés en local : 9 sur 9) :
  - le nom des semaines et leur passage le lundi ;
  - la semaine 53 au tournant de l'année ;
  - un record de temps classé pareil dans sa semaine ;
  - seules les semaines trop vieilles sont retirées, jamais un record Lifetime ni les plus gros
    clutchs.
- `RecordsWeeksTest` (nouveau, lancé par la CI) :
  - un score va sur le record et sur sa semaine ;
  - le lundi, une semaine neuve et vide ;
  - les semaines écrites dans le fichier et relues ;
  - encore là 5 semaines après, parties la 6e, du livre et du fichier, les Lifetime gardés ;
  - une semaine qui part pendant que le serveur tourne.
- `RecordsMenuTest` (lancé par la CI) : les lignes de la semaine dans `/records` et sur le mur.
- **Pas encore vu en jeu** : regarder `/records`, puis un record, et le mur des records au lobby.

### Install du 21/11 au soir (vers 20 h, serveur arrêté)

- **Build 339 installé** (commit cf08a76, toute la CI passée, deux jobs Java 21 relancés après une
  panne d'installation de Java chez GitHub) : records de la semaine, record au survol, sélecteur
  de leaderboards, niveau au-dessus des joueurs, ELO en Ranked, plateforme de Bed Break, replay
  en party, sons des replays. 5 jars identiques octet par octet à ceux de la CI ; anciens jars et
  lanceurs dans `sauvegarde-avant-maj-21-11c`.
- **`config.yml` de RagoLobby** : le PNJ `newleaderboardselector` et ses murs Daily, Weekly,
  Lifetime (`appliquer-config.py`), et les deux descriptions de Bed Break (« from the top » au
  lieu de « from your bridge »). Rien d'autre ne change ; l'ancien est dans
  `sauvegarde-avant-maj-21-11c\plugins\RagoLobby\`.
- **Invasion et Archway** : section `bed-break:` et tes 65 blocs enlevés de la map (voir 2.3).
- **Pas encore vu en jeu.**

### Spawns First Rush de Nebuc et Gelato, et install du 21/11 (vers 18 h 25)

- **First Rush (1.7)** : tu as posé le spawn rouge de Nebuc et de Gelato avec `/bw spawn red`. Je
  l'ai recopié sur les 7 autres îles de chaque map, dans le repère de chaque île (son générateur,
  son lit, son shop), avec l'outil déjà vérifié : il redonnait exactement les 8 spawns d'avant à
  partir du rouge d'avant. Les joueurs apparaissent maintenant 4 blocs plus en arrière sur Nebuc
  et 5 sur Gelato, tournés vers leur lit. C'est écrit dans les `bedwars.yml` de ton PC, vérifiés
  après écriture, avec l'ancien à côté (`bedwars.yml.avant-copie-spawns-21-11`). **Pas encore vu
  en jeu.**
- **Build 327 installé** sur ton serveur de test (protection de 1,5 s pour tous les modes,
  Lobby Fight, FKDR) : 5 jars identiques octet par octet à ceux de la CI, anciens jars et
  lanceurs dans `sauvegarde-avant-maj-21-11b`, `config.yml` de RagoLobby inchangé. Toute la CI
  Java était verte ; seul le job du site avait échoué (les données du jeu pas à jour après le
  changement de description du Lobby Fight), corrigé.
- **Bridges de Bed Break (2.3)** : tu les construis toi-même dans `/bw edit Invasion` et
  `/bw edit Archway`. Voir 2.3.

### 3.2 FKDR avec décimales — IMPLÉMENTÉ (21/11)

**Avant** : le FKDR s'écrivait comme un nombre ordinaire, sans zéros inutiles : « 3 » pour 3
final kills par final death, « 2.5 », « 0 », et sans final death le nombre de final kills seul
(« 5 »). Un joueur voyait donc souvent un nombre entier.

**Fait** : un seul format pour le FKDR, toujours deux décimales (`BoardStat.hundredths`,
`StatsMenu.fkdr`) : « 6.42 », « 2.50 », « 3.00 », « 0.00 », « 5.00 » sans final death (la règle
d'Hypixel : les final kills seuls). Partout où il s'affiche : les tableaux de classement et le
podium (`StatsDisplays`), le profil du PNJ de stats, le menu Stats (total et par mode), `/stats`
et `/top fkdr` (`NetworkCommands`). Le WLR et le K/D ne changent pas (le plan ne parle que du
FKDR). Le classement reste calculé au centième près, comme avant.

**Tests** : tests unitaires (`StatsMenuTest.theFkdrAlwaysHasTwoDecimals` : 6.42, 2.50, 3.00, 0.33,
0.67, 0.07, 5.00, 0.00 ; `StatsDisplaysTest` : tableau, podium et profil en deux décimales) ;
le format vérifié en local en compilant `BoardStat` seul. **Pas encore vu en jeu** : regarder le
tableau FKDR du mur `top`, le PNJ de stats, le menu Stats et `/stats`.

### Ton essai en jeu du 21/11 (vers 18 h) et ce qui a suivi

Sur le serveur de test (19) avec le build 325 installé :
- **Bed Break sur Invasion** : forges et spawn « Parfait ! » (1.6 TESTÉ). Les bridges d'Invasion
  et d'Archway « c'est pas du tout ça » : tu envoies une capture de ce que tu veux (2.3 EN COURS).
- **Bedfight** : la protection au respawn est bonne (2.2 TESTÉ en Bed Fight). Tu demandes de la
  mettre « dans tous les autres modes bedwars » : fait, voir 2.2.
- **Block In** : « parfait » (1.8 TESTÉ).
- **Reset des arènes** : « je crois que ça marche » (1.1 reste IMPLÉMENTÉ jusqu'à confirmation).
- **Lobby Fight** : « on a pas de shears, on a plus block infini, on a plus les pearls » : corrigé,
  voir 2.1.
- **Fast Shop** : `/hypixel import` refusé par Hypixel, voir 2.8.
- **First Rush** : tu poses toi-même les spawns de Nebuc et Gelato (1.7).

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

### 1.7 Spawns First Rush — IMPLÉMENTÉ sur Nebuc et Gelato (21/11), à vérifier en jeu

**Fait (21/11)** : tu as posé le spawn rouge des deux maps ; il est recopié sur les 7 autres îles
(voir « Spawns First Rush de Nebuc et Gelato » plus haut). Les 8 autres maps First Rush gardent le
leur tant que tu n'y poses pas de spawn rouge.

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

**Retour en jeu (21/11)** : « on a pas de shears, on a plus block infini, on a plus les pearls ».
Le kit du 1v1 d'avant (celui du FFA) avait une pearl, 2 stacks de laine et des cisailles ; je les
avais retirés en donnant au 1v1 le kit du Lobby Fight FFA. **Corrigé** : le kit commun aux deux
Lobby Fight garde les ajouts du plan (12 end stone, pioche en bois, Haste, 5 pommes) et retrouve la
pearl, les 2 stacks de laine et les cisailles (ajoutés en dernier dans `lobby-fight-ffa:`, pour
qu'un rangement déjà fait garde ses objets à leur place). « Blocs infinis » est lu comme les 2
stacks de laine d'avant, rendus à chaque manche en 1v1 : aucun code ne remplissait la laine à
l'infini avant. Le kit étant commun (ta réponse à C2), le FFA a aussi la pearl, la laine et les
cisailles ; dis-le si le FFA doit rester sans, comme tu l'avais voulu le 14/11.

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

### 2.2 Bedfight : protection au respawn — IMPLÉMENTÉ (21/11)

**Durée** : 1,5 seconde, celle de Minemen, donnée le 21/11 (« 1.5 secondes »).

**Fait** (`SpawnProtection`, `BedwarsGame.SPAWN_PROTECTION_MILLIS`, `BedwarsHost.onDamage`) : à chaque
respawn en Bed Fight (1v1 et 2v2), pendant 1,5 s, un coup d'un ennemi (épée, flèche…) ne fait rien
au joueur : ni dégât ni recul. Dès que le joueur frappe lui-même un ennemi, sa protection s'arrête
tout de suite, que son coup touche ou non. La chute dans le vide tue toujours. Pas de protection au
début de la partie (seulement au respawn, comme demandé). Rien n'est affiché au joueur protégé,
rien n'était demandé.

**Testé en jeu par toi (21/11)** : « Parfait la spawn prot est bonne ».

**Étendue aux autres modes Bed Wars (21/11, à ta demande)** : la durée est maintenant celle de
tous les modes (`BedwarsGame.SPAWN_PROTECTION_MILLIS`, 1,5 s) : chaque fois qu'un joueur revient
pendant la partie (`BedwarsGame.respawn`), il a la même protection qu'en Bed Fight. Ça touche les
parties Bed Wars (Ranked Bedwars, First Rush, Clutch Scenario…), le FFA, le Top Bridge FFA, le
Lobby Fight FFA, les défenseurs de Bed Break et d'Invis, l'Attack/Defense. Les modes en manches
(Top Bridge, Lobby Fight 1v1) ne passent pas par un respawn : tout le monde repart en même temps à
chaque manche, donc rien ne change pour eux. Le Fast Shop n'a pas de PvP. **Pas encore testé en
jeu** dans ces modes.

**Tests** : tests unitaires (`SpawnProtectionTest` : 1,5 s puis plus rien, coupée par un coup,
rendue à la vie suivante, aucune dans les autres modes) ; compilation et tests par la CI. **Pas
testé en jeu** : frapper un joueur qui vient de réapparaître (aucun recul), puis le laisser frapper
en premier pendant sa protection (il redevient touchable aussitôt).

### 2.3 Bed Break Simulation — EN COURS (escaliers relevés, plateforme à valider), forges : TESTÉ (1.6)

**Retour en jeu (21/11)** : les bridges d'Invasion et d'Archway « c'est pas du tout ça ». Tu
envoies une capture de ce que tu veux ; je les refais d'après elle, sans deviner avant.

**Ensuite (21/11, vers 18 h 25)** : plutôt qu'une capture, tu construis le bridge dans la map.
- **Lequel** : dans Bed Break, c'est toujours Vert qui attaque le lit de Rouge, sur les deux
  maps. Rouge est la première équipe complète de la map, et Vert la plus loin d'elle (l'île en
  face sur Archway, le coin opposé sur Invasion).
- **Attention** : ce qui est construit dans `/bw edit` reste dans la map. Or Archway et Invasion
  servent aussi en Ranked, Top Bridge et Invis.
- **Comment** :
  1. J'ai gardé une copie des deux maps juste avant
     (`serveur-de-test/ponts-bed-break-avant/` dans les fichiers du projet). Je la compare à la
     map une fois ton pont fait, pour relever tes blocs exacts.
  2. Le jeu pose alors exactement ce pont à chaque manche, en laine verte, cassable et enlevé
     après la manche.
  3. Tu casses ensuite ton pont dans `/bw edit`.

**Tes escaliers (21/11, 18 h 39)** : « bw edit Archway et Invasion est fini, j'ai fait le pont ; ce
qu'il te reste à faire c'est la plateforme rouge et vert en haut, mais surtout respecter la limite
de hauteur des maps » (Invasion 116, Archway 87, et la liste des autres maps). Relevé en comparant
la map à la copie d'avant, bloc par bloc : sur chaque map **65 laines rouges**, rien d'autre de
changé. Un escalier d'un bloc de large qui monte d'un bloc tous les deux, depuis l'île rouge :
- Invasion : de 33 93 -39 vers l'ouest le long de z -39 jusqu'à x 8 (y 105), puis vers le sud le
  long de x 8 jusqu'à 8 114 -21 ;
- Archway : de -4 69 -65 vers le sud le long de x -4 jusqu'à y 86 (z -25), puis en biais jusqu'à
  -1 86 -22, au-dessus de l'arche (haut de l'arche : y 84).

**La plateforme** : c'est celle de ton Top Bridge d'Antenna (`TopBridge`) : 17 de large, chaque
moitié à la couleur de l'équipe de son côté, une ligne blanche au milieu. Proposée à la hauteur du
haut de ton escalier (y 114 sur Invasion, y 86 sur Archway), dans l'axe nord-sud, de ton escalier
jusqu'au même point côté vert : x -8 à 8, z -21 à 21 sur Invasion, z -22 à 22 sur Archway.
L'attaquant part du milieu, tourné vers le lit rouge. La limite de hauteur reste celle de la map
(plus de +10 au-dessus de la plateforme). Sur Invasion, le dôme d'attente de la map (y 115 à 124,
au-dessus du milieu) serait juste au-dessus de la plateforme, mais il est déjà retiré de chaque
partie (`retirer:` de son `bedwars.yml`, comme sur Hypixel) : il ne gêne pas. Images : `sortie-publique/bed-break/Invasion-plateforme.png` et
`Archway-plateforme.png`. **À valider** avant de coder.

**Plateforme du Top Bridge (21/11, commit bf51927), ton choix du 21/11** : codée avant ton choix
pour ne pas perdre de temps, puis posée sur tes maps une fois choisie (voir « Ajoutée à tes maps »
plus bas).
- Une section `bed-break:` du `bedwars.yml` de la map : `escalier` (tes 65 blocs, du bas vers le
  haut) et `plateforme` (sa hauteur et ses deux coins). `/bw` la réécrit telle quelle.
- À chaque manche : la plateforme est posée comme une partie de la map (ni cassable ni soufflée,
  enlevée après), seulement là où il y a de l'air ; moitié rouge côté lit rouge, moitié verte,
  le bloc du milieu blanc. L'escalier est posé en laine rouge, cassable comme un bloc posé.
- L'attaquant part du milieu de la plateforme, tourné vers la moitié rouge. Sans section
  `bed-break:`, rien ne change (le bridge d'avant).
- La hauteur de placement reste `hauteur-max` de la map (116 sur Invasion, 87 sur Archway) : la
  plateforme est à 114 et 86, sous la limite.
- **Testé** : CI entière passée (run 44935292552 ; deux tests réels relancés après une panne d'installation de Java chez GitHub, puis passés) ; build et tests unitaires : `BedBreakGameTest` (la plateforme
  d'Invasion fait 17 × 43 blocs, un seul blanc, deux moitiés égales ; le départ au milieu, tourné
  vers le lit rouge, sur Invasion, Archway et une plateforme est-ouest) et `BedwarsMapTest` (la
  section relue telle qu'écrite, et les erreurs). **Pas encore testé en jeu.**
- **Pour l'ajouter à tes maps** (après ton choix) : le script
  `serveur-de-test/install-bedbreak-haut/ajouter-bed-break.py` ajoute la section aux deux
  `bedwars.yml` (il refuse une plateforme au-dessus de `hauteur-max`), et les régions d'avant
  (`ponts-bed-break-avant/`) enlèvent tes 65 blocs de la map elle-même, puisque le jeu les pose.
- **Ajoutée à tes maps (21/11, serveur arrêté)** :
  - les deux `bedwars.yml` de `C:\Users\rgabe\Rago\mondes` ont leur section `bed-break:` (tes
    65 blocs et la plateforme à y 114 sur Invasion, y 86 sur Archway, sous `hauteur-max`) ; le
    reste du fichier est inchangé ;
  - tes 65 laines rouges sont enlevées des régions des deux maps. Avant de le faire, j'ai vérifié
    que la seule différence entre la copie d'avant ton escalier et la map actuelle était ces 65
    blocs (air → laine rouge), puis remis les régions d'avant ;
  - tout est relu sur ton PC octet par octet après l'écriture ;
  - la version avec ton escalier est gardée dans `sauvegarde-avant-maj-21-11c\mondes\` du serveur
    et dans `serveur-de-test/install-21-11c/avant/` du projet ; les copies du projet
    (`maps-bedwars/`) ont le nouveau `bedwars.yml`.

- **Bridges** : le top bridge (la plateforme de laine de 17 blocs de large, 16 blocs au-dessus
  des lits, tracée de lit à lit, donc en diagonale sur Invasion) est remplacé par le bridge de
  l'attaquant, comme un joueur le pose en Ranked Bedwars : un bloc de large, en laine de sa
  couleur, de l'avant de son île (là où est son propre lit) jusqu'au lit défendu, à la hauteur
  des îles. Il ne part plus du spawn depuis que celui d'Invasion est au milieu du dôme (1.6) : un
  tracé droit depuis là traverse la vitre du dôme et descendait à y 88 sous l'île du diamant.
  Il suit le même tracé que les
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

### 1.6 Forges de Bed Break Simulation — CORRIGÉ (21/11), à vérifier en jeu

**Symptôme** (donné le 21/11) : sur Invasion, le fer et l'or apparaissent au milieu de la base, là
où le joueur apparaît, au lieu d'être dans la forge.

**Cause** : les points de forge d'Invasion (`generateur` de son `bedwars.yml`) sont faux. L'outil
de maps (`tools/bedwars-maps`) cherche la forge d'Hypixel : une fosse de dalles un demi-bloc sous
le sol. Invasion n'en a pas : c'est la seule des 17 maps de `maps-bedwars` où l'outil s'est rabattu
sur une fosse d'un bloc (« pit »), les 16 autres ont leur fosse de dalles. La fosse trouvée était
le sol en contrebas au milieu du dôme, et le point son centre : le disque de verre éclairé par
dessous (48,5 93 -47,5 pour rouge), à 4 blocs du spawn. Mon analyse du 21/11 matin l'avait prise à
tort pour la forge. Le code de Bed Break et des générateurs n'y est pour rien.

**La vraie forge** : dans chaque base, trois coins du dôme ont un carré de lanternes marines dans
le sol. Deux portent les PNJ (shop et améliorations, sous leurs barrières), le troisième, au fond
de la base dans l'axe lit → spawn → centre, est la forge : 4 lanternes marines (rouge : x 51 à 52,
z -52 à -51, y 92), de l'air au-dessus. Le même carré est à la même place sur les 4 bases (la map
est symétrique).

**Corrigé** : dans `C:\Users\rgabe\Rago\mondes\Invasion\bedwars.yml` (ton serveur) et dans
`maps-bedwars/Invasion`, les 4 forges sont au centre de leur carré de lanternes, au ras du sol :
rouge 52 93 -51, bleu 52 93 52, vert -51 93 52, jaune -51 93 -51.

**Spawn** (ta correction du 21/11 : « le joueur doit spawn au milieu de sa base exactement au point
rouge, before glass in the middle ») : les 4 spawns sont sur la vitre du milieu du dôme, au ras du
sol, tournés vers leur lit comme avant : rouge 48,5 93 -47,5, bleu 48,5 93 48,5, vert -47,5 93
48,5, jaune -47,5 93 -47,5 (avant : 4 blocs plus près du lit, sur le rebord, y 94). Le bridge de
Bed Break part donc maintenant de l'avant de l'île de l'attaquant (voir 2.3).

Les anciens points sont gardés en commentaire au-dessus de chacun ; l'ancien fichier est dans
`sortie-publique/forge-invasion/bedwars-avant.yml`, l'image de la base rouge (avant, après) dans
`sortie-publique/forge-invasion/invasion-forge-rouge.png`. Le serveur lit ce fichier au démarrage :
la correction vaut pour toutes les parties sur Invasion (Bed Break, Ranked…) au prochain démarrage.

**À tester en jeu** : une manche de Bed Break sur Invasion, le fer et l'or doivent tomber sur le
carré de lanternes au fond de la base rouge. **Pas testé en jeu.**

Ce qui avait été vérifié le 21/11 au matin, et reste vrai :
- La map ne garde aucune entité (aucun lingot ni hologramme laissé par la capture).
- En Bed Break, les deux îles jouées sont rouge (défense) et vert (attaque), à l'opposé ; seule
  la forge rouge tourne, à la vitesse moyenne (celle de la file RBW 2v2 à 4v4), remplie au début
  de chaque manche (48 fer, 16 or).
- Les journaux de ton serveur ont deux copies d'Invasion (14/11 et 15/11), sans aucune erreur.

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

### 2.8 Fast Shop et API Hypixel — ÉTUDE FAITE (21/11), proposition à valider

**Ce qui existe déjà** (lu dans le code, rien de changé) :
- `/hypixel import [pseudo]`, ou l'émeraude de l'éditeur de kit, copie depuis l'API Hypixel le
  **quick buy** (21 cases) et le **hotbar manager** (9 cases) d'un joueur, avec la clé du serveur
  (`hypixel.cle-api` de RagoLobby). Une requête `GET /v2/player` par import (plus une à Mojang pour
  un autre pseudo que le sien). Limites : une fois par minute et par joueur, 100 imports par
  5 minutes pour tout le serveur, une pause d'une minute si Hypixel refuse la clé (403) ou demande
  de ralentir (429). Gardé par joueur dans `plugins/RagoLobby/hypixel/<uuid>.yml` ; `/hypixel shop`
  le montre.
- Le Fast Shop et tous les modes Bed Wars ouvrent le shop avec ce quick buy (sinon celui par
  défaut), et rangent les objets achetés selon le hotbar manager.
- Sur ton serveur de test (19), la clé est remplie, mais le dossier `hypixel/` n'existe pas : aucun
  import n'y a encore été fait, donc la clé et la lecture n'ont jamais été vérifiées en vrai.

**Ce que l'API donne vraiment** (sources : developer.hypixel.net/policies et la documentation
api.hypixel.net) :
- `/v2/player` (clé obligatoire, en-tête `API-Key`) renvoie `stats.Bedwars`. Le quick buy y est
  `favourites_2` (21 identifiants séparés par des virgules, `null` pour une case vide) et le hotbar
  manager `favorite_slots` (Melee, Blocks, Tools, Ranged, Potions, Utility, Compass ou `null`).
  **Ces champs ne sont pas dans la documentation officielle** : ils viennent des réponses réelles,
  et la communauté s'en sert (par exemple un gist de 2025 qui liste le quick buy par défaut). Un
  joueur qui n'a jamais réglé son shop peut ne pas les avoir : l'import le dit au joueur.
- Les identifiants : les 14 du quick buy par défaut (wool, stone_sword, chainmail_boots, bow,
  speed_ii_potion_(45_seconds), tnt, oak_wood_planks, iron_sword, iron_boots, shears, arrow,
  invisibility_potion_(30_seconds), water_bucket, null) sont confirmés par cette source et
  reconnus par notre shop. Les autres (hardened_clay, blast-proof_glass, stick_(knockback_i),
  wooden_pickaxe, magic_milk, compact_pop-up_tower…) ne sont confirmés par aucune source publique
  que j'ai pu lire ; notre shop les reconnaît par un mot (clay, glass, stick, pickaxe, milk,
  tower…). Un identifiant inconnu laisse la case vide, sans erreur.

**Ce que l'API ne donne pas** : rien de ce qui se passe pendant une partie. Pas les achats d'une
partie, pas le temps d'un achat, pas de « record » de shop, pas de classement de vitesse ; pas de
mise à jour poussée quand le joueur change son quick buy (il faut redemander). `/v2/recentgames`
ne liste que les parties récentes (mode, map, début, fin), sans achats. Le Fast Shop ne peut donc
pas comparer un temps à Hypixel : ses records restent les nôtres.

**Règles d'Hypixel qui comptent** : la clé ne se partage pas et ne va pas dans le code (elle est
dans `config.yml` du serveur, c'est bon) ; une application « Personal » est pour un projet non
monétisé, un serveur qui vend quelque chose (grades, boutique) doit avoir une application
« Production » ; les clés de développement expirent ; garder les réponses en cache est demandé,
interroger en boucle (polling) ou suivre les sessions des joueurs est interdit. Les règles ne
donnent pas le nombre de requêtes permis ; le code parle de 300 en 5 minutes pour une nouvelle clé,
chiffre que je n'ai pas pu revérifier. Il reste bien en dessous (100 en 5 minutes), et un 429 met
déjà les imports en pause.

**Proposition** : l'intégration actuelle utilise déjà tout ce que l'API donne pour le shop. Je
propose de la garder telle quelle, sans import automatique (interdit par les règles), et de la
vérifier par un vrai import : `/hypixel import` sur ton compte, sur ton serveur de test. Ça
confirme la clé (refusée si l'application n'est pas encore acceptée) et les identifiants de ton
quick buy, que je relirai dans `plugins/RagoLobby/hypixel/`. Un seul petit manque trouvé : un
import fait pendant une partie de Fast Shop ne change le shop qu'à la partie suivante (le message
d'accueil dit d'importer au lobby, donc ce cas est rare). Rien n'est codé sans ton accord.

**Essai réel (21/11, 18 h 10)** : `/hypixel import qAbel` sur ton serveur de test. La console
(`logs/latest.log`) dit : `Hypixel refused the API key (403): {"success":false,"cause":"Invalid
API key"}`. C'est Hypixel qui répond que la clé n'est pas valable : le code a bien envoyé la
requête et lu la réponse. La clé de `hypixel.cle-api` a la bonne forme (36 caractères, celle d'une
clé Hypixel) et c'est exactement celle que tu avais avant la mise à jour (la fusion des configs ne
l'a pas touchée). Les règles d'Hypixel (developer.hypixel.net/policies) : les clés de
développement expirent (« developer keys may expire »), et seules les applications approuvées ont
des clés qui durent plus longtemps. Tu dis aussi avoir été refusé par Hypixel pour les clés de
développement. **Rien à corriger dans le code** : il faut une clé qu'Hypixel accepte. Une nouvelle
clé de développement, créée sur developer.hypixel.net et collée dans `hypixel.cle-api` (puis un
redémarrage), devrait marcher tant qu'elle n'a pas expiré ; pour durer, il faut une application
approuvée. Sans clé valable, le Fast Shop et les modes Bed Wars marchent avec le quick buy par
défaut ; seul l'import manque. Statut : **BLOQUÉ** (clé).

### Étape 7 : les trois premiers modes — Fireball Fight, Snowball Spleef, Red Rover (21/11)

Ta demande : « Parfait commence par ces 3 mods je repond au questions ». Les questions du document
de l'étape 7 n'ont pas encore de réponse : chaque valeur qui en dépend est une valeur de départ,
marquée « à confirmer » dans les `config.yml`.

- **FUN PRACTICE** (6.1) : une cinquième catégorie du lobby, avec le PNJ `FunMod2` sur un bloc de
  quartz (point `funmod2`, à poser avec `/lobby point funmod2`), sans skin à lui pour l'instant.
- **Fireball Fight** (6.1b, `fireball-knockback`, RagoPractice) : le mode à knockback, sans lit.
  Réglages `fireball-knockback:` : rayon 10, recul 1,5 / 1,2, fireballs illimitées (0), souffle
  de 3 s, 1 round gagnant.
- **Snowball Spleef** (6.1a, `snowball-spleef`, RagoPractice) : réglages `snowball-spleef:` :
  rayon 12, 1 étage, écart 6, 1 boule par bloc cassé, 1 round gagnant.
- **Red Rover** (6.5.1) : quatre modes dans un seul item « Red Rover » des Duels, trois joueurs en
  1v1 chacun leur tour, celui qui attend regarde. Réglages communs `red-rover:` du `config.yml` de
  RagoPractice (aux points, le gagnant reste, premier à 3 1v1 gagnés, 90 s par 1v1) :
  - **Red Rover Sumo** : un round de sumo gagne le 1v1 ;
  - **Red Rover Block Edit** : un edit propre gagne le 1v1 ;
  - **Red Rover Block Placement** : un coup +1, le vide +10, premier à 10 (ton choix du 21/11) ;
  - **Red Rover Top Bridge** (RagoBedwars, maps de Top Bridge, kit de Top Bridge 1v1) : des
    rounds d'une vie, le premier à `points-par-1v1-top-bridge` (1, ton choix du 21/11) gagne
    le 1v1. RagoBedwars lit les réglages de Red Rover dans le `config.yml` de RagoPractice : un seul
    endroit pour les quatre. Qui affronte qui est dans `rago-game-model` (`RedRoverRotation`),
    partagé par les deux plugins.

**Testé** (vrais tests, en CI, pas encore en jeu sur ton serveur) : tests unitaires ; les bots
jouent Snowball Spleef et Fireball Fight (`fun-bots`), Red Rover Sumo (trois 1v1, le gagnant
reste, victoire au troisième), Block Placement et Block Edit (un 1v1 gagné, le suivant, ceux qui
quittent) et Top Bridge (un 1v1 gagné en sortant du pont, le gagnant reste contre celui qui
attendait, puis ceux qui quittent : le dernier gagne la partie) (`rover-bots`, CI du commit
0b7b8a0) ; les 9 tests de la rotation passent aussi en local. Certains jobs Java 8 de la CI
échouent encore avant le code, sur GitHub (somme de contrôle du JDK Temurin 8u514) : d'un
lancement à l'autre, ce ne sont pas les mêmes jobs, et les autres passent.

**Installé** sur ton serveur de test le 21/11 (vers 0h20, heure de Paris) : les 5 plugins du commit
e8fd5fa et les `config.yml` du lobby, du practice et du Bed Wars (tes réglages gardés, seuls les
nouveaux modes ajoutés) ; l'ancien état est dans `sauvegarde-avant-maj-fun-1`.

**Installé** à nouveau le 22/11 (vers 1h15, heure de Paris) : les 5 plugins du commit 0271f26
(Clutch Scenario 1v1 à 1v4, catégorie 3 Players, /host) et les `config.yml` du lobby et du Bed
Wars fusionnés avec tes réglages, sans conflit ; le PNJ Fun Practice baissé d'un bloc
(`points.yml` et la région `r.-1.0.mca` du monde du lobby). Empreintes SHA-256 vérifiées sur ton
PC ; l'ancien état est dans `sauvegarde-avant-maj-0271f26`.

**À faire en jeu** : une partie de chaque mode sur ton serveur de test ; poser le PNJ avec
`/lobby point funmod2`.
