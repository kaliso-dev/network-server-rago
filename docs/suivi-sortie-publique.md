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
| C6 | 4.5 `/discord` et `/store` existent déjà, avec `discord.gg/ragooptimizer` et `rago-optimizer.com`. | Réglé le 22/11 : ce sont les liens officiels (« rago-optimizer.com et discord.gg/ragooptimizer »). |
| C7 | 5.6 « jncl : nom à afficher : directemeent ». | Réglé le 22/11 : « jncl : directement nativ : woryes », jncl affiché « directement », nativ affiché « woryes ». |
| C8 | 7.1 Reach et Scaffold sont des triches. | Réservés au compte Owner (UUID), désactivés par défaut, jamais en partie classée ni comptée. |

### 1.5 Ce qui manque (à fournir, rien n'est inventé)

- ~~Lien officiel du Discord de Rago Practice, et du Store (4.5, 5.7, 8.2).~~ Reçu le 22/11 : `discord.gg/ragooptimizer` et `rago-optimizer.com`.
- ~~Liens officiels de Rago Client et de Rago Optimizer, et les skins voulus pour leurs PNJ (5.6 A).~~ Reçu le 22/11 : les mêmes liens, le skin de qRago.
- ~~Pseudo exact du compte « Woryes » pour le skin de `nativ` (5.6 C).~~ Le 22/11 : le compte `Woryes`, comme dans le plan ; s'il n'existe pas chez Mojang, le log du lobby le dit au démarrage.
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
- Probabilités et cosmétiques du Daily Chest (5.5) : choisis par moi le 22/11 (tu me l'as laissé), modifiables dans `coffre-du-jour:`.
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
| 2.8 | Fast Shop et API Hypixel : étude des données réellement disponibles | Modes | P2 | BLOQUÉ : Hypixel refuse la clé du serveur (« Invalid API key ») ; tu refais une demande de clé (22/11) | `FastShopGame`, `HypixelProfiles`, `HypixelProfile` | — | un vrai `/hypixel import` sur ton serveur |

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
| 3.7 | Organisation Solo / Party / Ranked (ELO par mode) : proposition avant tout code | Files | P2 | IMPLÉMENTÉ le 22/11 (ta réponse « Build » et ton choix « les grands duels 1v1 ») : deux onglets de plus dans la boussole, Party et Ranked ; Ranked tient Ranked Bedwars 1v1 à 4v4 (ses ELO gardés) et six duels classés en 1v1, chacun avec son ELO (Bed Fight, Top Bridge, Bridge Fight, Block Placement, Sumo, Boxing) ; Party montre ce que la party joue contre elle-même à sa taille. Tests du dépôt en CI : le run 385 (5e5ba79) a trouvé 1 test sur 300 à mettre à jour (le menu des stats gagne le bouton Ranked), corrigé avec 5.6A ; tous les tests passés en CI le 22/11 (commit 1e78770, test des arènes compris) ; pas encore joué en jeu ni installé (voir le journal) | `ModeCategory`, `ModeMenu`, `QueueController`, `LobbySettings`, `DuelRequests`, `StatsMenu`, `RagoPractice`, `SumoHost`, config.yml du lobby et des Bed Wars | 3.8 | — |

### Étape 5 : expérience joueur (Phase 4)

| # | Tâche | Cat. | Prio | Statut | Fichiers | Manque |
| --- | --- | --- | --- | --- | --- | --- |
| 4.1 | Party Replay : les membres de la party rejoignent le replay | Replays | P2 | IMPLÉMENTÉ : `/p warp` du chef dans un replay ; test réel à 2 bots passé en CI (Java 8) ; à voir en jeu | `ReplayService`, `ReplayViewer`, `Parties` | — |
| 4.1b | Sons dans les replays : étude de faisabilité | Replays | P3 | TESTÉ en CI : faisable ; sons envoyés par le serveur et blocs cassés enregistrés et rejoués ; test unitaire et test réel à 2 bots passés (run 339) ; à écouter en jeu | `ReplayRecorder`, `ReplayData` | — |
| 4.2 | `/host` : annonce cliquable toutes les 10 s (20 s depuis ta demande du 21/11) ; inscription gardée sans téléporter ; départ quand assez de joueurs ; poudre rouge pour quitter ou annuler | Events | P1 | Ta demande du 21/11 (poudre rouge « Leave the event » / « Cancel the event » au dernier slot, annonce centrée entre deux lignes avec [CLICK TO JOIN], toutes les 20 s) : IMPLÉMENTÉ le 22/11 dans cette PR (la PR #48 partait de l'ancienne version des events) ; TESTÉ par les bots en CI le 22/11 (Java 8 : l'annonce, le [CLICK TO JOIN], la poudre Leave puis Cancel ; le premier essai échouait côté test, le clic « en l'air » d'un bot qui vient de poser un bloc étant ignoré par le serveur : le bot clique maintenant le sol, comme un joueur) ; pas encore en jeu. Avant : ton choix « Les sortir de leur partie » : au lancement, les inscrits encore en partie la quittent comme avec /spawn et vont à l'event ; test réel à 3 bots passé ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `Events`, `EventGame`, `PracticeHost`, `HostWatcher` | — |
| 4.3 | Kit Editor plus clair et trié | Kits | P2 | TESTÉ en CI (21/11, run 342, Java 8 et 21) : kits rangés par catégorie et par nom, « Also for », Save et Cancel ramènent à la liste ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `KitEditor`, `KitLayouts` | — |
| 4.4 | Menu Stats : présentation, catégories, navigation | Stats | P2 | TESTÉ en CI (21/11, run 343, Java 8 et 21) : une page des catégories du sélecteur avec les totaux de chacune, un clic montre ses modes, Go back ramène aux catégories ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `StatsMenu` | — |
| 4.5 | `/discord` et `/store` avec les liens officiels | Cmd | P1 | IMPLÉMENTÉ : tes liens officiels du 22/11 sont ceux déjà en place, `discord.gg/ragooptimizer` et `rago-optimizer.com` (C6) | `config.yml` (`liens`), `ServerSettings` | — |

### Étape 6 : lobby (Phase 5)

| # | Tâche | Cat. | Prio | Statut | Fichiers / points | Manque |
| --- | --- | --- | --- | --- | --- | --- |
| 5.1 | Direction artistique : moins de rouge, identité Rago gardée | DA | P2 | TESTÉ en CI le 22/11 (ta liste de 18h22, à mon choix ; 3ae7bb7 : tests unitaires, bot du lobby en Java 8) : le rouge pour le nom et le préfixe RAGO, le jaune pour ce qu'on peut faire, l'or pour les titres, le bleu pour la party et les amis ; installé sur ton serveur le 22/11 (3ae7bb7) ; journal « Ta liste du 22/11 (18h22) » ; pas encore vu en jeu | `Brand`, `config.yml`, menus, scoreboard | — |
| 5.2 | Build monumental « RAGO PRACTICE » (rocheux, angélique, stone, void), sans détruire le spawn : plan précis d'abord | Build | P2 | BLOQUÉ : ta réponse du 22/11, tu m'envoies l'image, à faire après les modes et les fonctionnalités | monde `lobby` | image de référence |
| 5.3 | Blocs sous PNJ : Jouer quartz, Stats fer, Shop émeraude | Lobby | P1 | TESTÉ en CI (21/11, runs 345 et 346, Java 8 et 21) : `bloc:` sous chaque PNJ dans config.yml ; quartz sous les 5 PNJ pour jouer, fer sous les PNJ de stats, émeraude sous le Shop ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `Pedestal`, `LobbyNpcs`, `LobbySettings`, `config.yml` (`pnj:`) | — |
| 5.4a | `NewQuest` relié aux quêtes | Lobby | P1 | TESTÉ en CI (21/11, runs 345 et 346 : compilation, tests unitaires, test du lobby) : le PNJ des quêtes (clic = `/quests`) va sur le point choisi par `point-pnj-quetes:` (`newquest` pour ton serveur) ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `QuestsNpc`, `LobbySettings`, point `newquest` | — |
| 5.4b | `NewShop` sur émeraude | Lobby | P1 | TESTÉ en CI (21/11, runs 345 et 346 : compilation, tests unitaires, test du lobby) : le PNJ Shop passe du point `shop` au point `newshop` (config de ton serveur), sur ton bloc d'émeraude ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `LobbyNpcs`, `Pedestal`, point `newshop` | — |
| 5.4c | `NewDailyChest` sur diamant | Lobby | P1 | TESTÉ en CI (21/11, runs 345 et 346 : compilation, tests unitaires, test du lobby) : le coffre du jour va sur le point choisi par `point-coffre-du-jour:` (`newdailychest` pour ton serveur), sur ton bloc de diamant ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `DailyChest`, `LobbySettings`, point `newdailychest` | — |
| 5.5 | Daily Chest : animation à suspense, Rago Points 100–500, XP 100–2000, cosmétiques, probabilités configurables, jamais de doublon | Lobby | P2 | TESTÉ en CI le 22/11 (ea3bbe9 : `DailyRewardsTest` ; le bot du lobby a ouvert `/daily`, la bande a défilé et s'est arrêtée sur une Rare, un second `/daily` refusé), pas encore joué en jeu ; pas encore installé (ton serveur de test tournait) ; ta liste de 18h22 : probabilités à mon choix, dans `coffre-du-jour:` ; journal « Ta liste du 22/11 (18h22) » | `DailyChest`, `DailyRewards`, `Profiles`, `config.yml` | — |
| 5.6A | `RagoClient` et `RagoOptimizer` : nom, skin, diamant, liens au clic, texte « OUR OTHER PRODUCTS », visuels publicitaires existants derrière | Lobby | P2 | IMPLÉMENTÉ le 22/11 (tes réponses : les liens `rago-optimizer.com` et `discord.gg/ragooptimizer`, le skin de qRago pour les deux) : PNJ `type: produit` sur tes points `ragoclient` et `ragooptimizer`, « Rago Client » et « Rago Optimizer » sous « OUR OTHER PRODUCTS », sur un bloc de diamant, un clic écrit le site et le Discord dans le chat ; tes deux panneaux `rago-client` et `rago-optimizer` (tes visuels) à replacer 3 blocs derrière, contre le mur, en 6 x 6 (la hauteur du mur), à l'install (voir le journal). Testé en CI par les tests du dépôt et le bot du lobby (il clique Rago Client et lit ses deux liens) une fois le run passé ; pas encore installé ni vu en jeu | `LobbyNpc`, `LobbyNpcs`, `LobbySettings`, `config.yml`, `panneaux:` de ton config | — |
| 5.6B | `qRago` (Owner) et `Kalisodev` (kaliso_dev, Dev) sur obsidienne, surélevés d'un bloc | Lobby | P2 | TESTÉ en CI (21/11, runs 345 et 346, Java 8 et 21), sauf le skin lu chez Mojang : PNJ `type: personne` sur tes points `qrago` et `kalisodev`, « [OWNER] qRago » et « [DEV] kaliso_dev » (les préfixes de `rangs:`), skin de leur compte Minecraft lu chez Mojang (`skin-joueur:`), sur obsidienne un bloc plus haut ; installé sur ton serveur le 21/11 (build 346, sauvegarde `sauvegarde-avant-maj-21-11d`) ; à voir en jeu | `LobbyNpc`, `LobbyNpcs`, `AccountSkins`, `LobbySettings`, `config.yml` | — |
| 5.6C | `jncl` et `nativ` (affiché woryes, skin Woryes), rôle Mod en jaune clair | Lobby | P2 | RETIRÉS à ta demande du 22/11 à 17h56 (« supprime woryes et directement »), IMPLÉMENTÉ, journal « La salle des remerciements (22/11, 17h56) » ; le rang Mod en jaune clair reste. Avant : IMPLÉMENTÉ le 22/11 (ta réponse : jncl affiché « directement », nativ affiché « woryes », C7) : PNJ `type: personne` sur tes points `jncl` et `nativ`, « [MOD] directement » dans le skin du compte jncl et « [MOD] woryes » dans le skin du compte Woryes ; le rang Mod passe en jaune clair (`&e`, avant vert foncé `&2`), partout où le rang se voit (chat, tab, au-dessus de la tête) ; pas de bloc demandé dans le plan pour eux : le bloc de tous les PNJ (andésite polie). Testé en CI par les tests du dépôt une fois le run passé ; pas encore installé ni vu en jeu | `LobbySettings`, `Ranks`, `config.yml` (`pnj:`, `rangs:`) | 5.6B |
| 5.7 | Texte de remerciement en anglais, fidèle à ton histoire, avec le Discord, à valider avant intégration | Lobby | P2 | TESTÉ en CI le 22/11 (5216997 : le bot du lobby lit ses 18 lignes dans l'ordre) ; validé le 22/11 : « Le texte est parfait » ; à installer | point `textremerciement`, `textes:` du `config.yml`, `LobbyText`, `FakeHologram` | — (Discord : `discord.gg/ragooptimizer`, 22/11) |
| 5.8 | Revoir les skins des PNJ du lobby | Lobby | P2 | TESTÉ en CI le 22/11 (ta liste de 18h22, à mon choix ; 331b14c et 3ae7bb7 : tests unitaires, bot du lobby en Java 8, mains vides) : nouveaux skins dans la couleur de chaque catégorie, Fun Practice compris, aucun objet en main ; installé sur ton serveur le 22/11 (3ae7bb7) ; journal « Ta liste du 22/11 (18h22) » ; pas encore vu en jeu | `NpcSkins`, `skins.yml`, `skins/*.png`, `FakePlayer` | — |
| 5.9 | Boussole comme sur Minemen (ta demande du 22/11) : les catégories en haut, un espace, puis les modes de la catégorie choisie | Lobby | P1 | RETIRÉ à ta demande du 22/11 à 16h22 (« remettre la boussole comme avant avec les différentes catégories ») : la boussole d'avant est remise, IMPLÉMENTÉ, journal « Ta liste du 22/11 (16h22) » ; le 22/11 à 17h54, Red Rover, Party, 3 Players et Ranked en haut, IMPLÉMENTÉ, journal « Ta liste du 22/11 (17h54) ». Avant : TESTÉ par les bots en CI le 22/11 (commit af042fc, Java 8 et 21 : le PNJ Duels ouvre la boussole sur Duels ; un clic sur Duels en haut remplace les jeux de Practice dans le même menu, puis la sumo rejoint sa file) ; installé sur ton serveur le 22/11 vers 1h40 (seul `rago-lobby` change, sauvegarde `sauvegarde-avant-maj-af042fc`) ; pas encore vu en jeu : les 6 catégories sur la ligne du haut (slots 1 à 6), une ligne vide, les jeux de la catégorie choisie à partir du slot 19 ; un clic sur une autre catégorie change les jeux dans le même menu, celle choisie brille (« Its modes are below ») ; s'ouvre sur Practice, ou sur la catégorie du PNJ, de `/play` ou du groupe dont on revient ; « Popular now » passe sur la ligne du bas ; le menu garde la taille de la plus grande catégorie (Practice, 15 jeux : 6 lignes) | `ModeMenu`, `lobby-bots.mjs` | depuis 3.7 (22/11, pas encore installé) : 8 onglets, Party et Ranked en plus, sur les slots 0 à 7 |

### Étape 7 : nouveaux modes (Phase 6) — proposition avant tout code

Les propositions sont dans le document « Étape 7 : propositions des nouveaux modes » (21/11), avec
les 13 questions à trancher. Ta demande du 21/11 (« commence par ces 3 mods ») : Fireball Fight,
Snowball Spleef et Red Rover sont codés avec des valeurs de départ à confirmer (détail plus bas,
« Étape 7 : les trois premiers modes ») ; les autres attendent tes réponses.

| # | Tâche | Prio | Statut | Manque |
| --- | --- | --- | --- | --- |
| 6.1 | Catégorie FUN PRACTICE, PNJ `FunMod2` sur quartz | P2 | IMPLÉMENTÉ (21/11 ; tests unitaires et test du lobby en CI passés) ; le 22/11 à 16h22, ta demande : le PNJ Fun Practice va à la place du PNJ Private game (point `prive`), IMPLÉMENTÉ, journal « Ta liste du 22/11 (16h22) » | le skin du PNJ : le sien depuis le 22/11 (18h22, `fun`) ; le nom de la catégorie à confirmer |
| 6.1a | Snowball Spleef (style Minemen) | P2 | TESTÉ en CI le 22/11 (ta demande : « agrandi la map fait 2 étape, et fait en sorte que les joueurs ne puisse pas se frapper entre eux ») : 2 étages de neige, 6 blocs l'un sous l'autre, rayon 18 au lieu de 12 ; plus aucune poussée entre joueurs (ni main, ni boule de neige). Vérifié par le bot sur un serveur 1.8.8 en CI (commit 1e78770) : le second étage, une boule qui touche l'autre sans le pousser ni casser de neige, la chute sur le second étage sans perdre ; pas encore installé | valeurs à confirmer : rayon 18 (« agrandi » sans chiffre), 2 étages, écart 6, 1 boule par bloc, 1 round |
| 6.1b | Fireball Fight (voir C1) | P2 | TESTÉ par les bots en CI (21/11), pas encore en jeu | valeurs de départ à confirmer : rayon 10, recul 1,5 / 1,2, fireballs illimitées, souffle 3 s, 1 round ; le nom (« Fireball Fight », FUN PRACTICE) |
| 6.1c | Fast Bedwars (Bedwars 2018 simplifié) | P2 | TESTÉ par les bots en CI le 22/11 (757ba0b), installé sur ton serveur de test le 22/11 (aa724b7), pas encore joué en jeu : codé selon la proposition du 21/11 (ta demande du 22/11 : finir les modes demandés), `fast-bedwars-1v1` et `fast-bedwars-2v2` dans Fun Practice, sur les 20 maps RBW ; forges deux fois plus rapides, événements deux fois plus tôt, shop des 20 objets du Quick Buy par défaut, pas d'améliorations ni de diamants (détail plus bas, « Étape 7 : Fast Bedwars ») | ce que « 2018 » veut dire (shop, améliorations, équipes, durée) ; valeurs de départ à confirmer : 240 fers et 24 ors par minute, lits à 15 min, fin à 22 min 30, la liste du shop, pas d'améliorations, 1v1 et 2v2, les 20 maps RBW |
| 6.1d | Autres modes FUN connus : Parkour Duel | P3 | TESTÉ en CI le 22/11 (ton choix : Parkour Duel « dans une map stylée ») : une course sur un parcours du plugin, une piste par joueur, 5 sections à thème, 4 checkpoints, une chute ramène au dernier ; chaque saut essayé avec la physique 1.8. Le bot l'a fait en entier sur un serveur 1.8.8 en CI (commit 1e78770) : 30 sauts, aucun raté, les 4 checkpoints, deux chutes remises au bon endroit, l'arrivée en 48,6 s et la victoire ; pas encore installé ni joué par toi. (Le Snowball Spleef agrandi : ligne 6.1a) | à confirmer : le parcours lui-même (à toi de le jouer), 1 manche gagnante |
| 6.2 | Capture the Flag, version Bedwars et seconde version : études | P3 | TESTÉ par les bots en CI le 22/11 (757ba0b), installé sur ton serveur de test le 22/11 (aa724b7), pas encore joué en jeu : tes deux types du 22/11, The Bridge (`ctf-bridge-1v1`, `ctf-bridge-2v2`, map CtfBridge) et château contre château avec un mid (`ctf-castle-2v2`, `ctf-castle-4v4`, map CtfCastle), dans Fun Practice ; les deux maps construites par le plugin (détail plus bas, « Étape 7 : Capture the Flag ») | valeurs de départ à confirmer : 3 captures, 15 et 20 minutes, 30 s de retour du drapeau, les tailles, le dessin des deux maps, les icônes |
| 6.3 | Find Your Sensitivity : faisabilité et protocole | P3 | IMPLÉMENTÉ le 22/11 (ton choix sur la carte : « Find Your Sensitivity » d'abord), selon le protocole de la proposition ; build et tests unitaires passés en CI (0bcfd61), installé sur ton serveur de test ; pas encore joué, ni par les bots ni en jeu (détail plus bas, « Étape 7 : Find Your Sensitivity ») | question 9, valeurs de départ à confirmer : séries d'environ une minute (12 flicks, 4 demi-tours, 20 s de suivi), 3 sensibilités (la tienne, 20 % moins vite, 20 % plus vite), sans historique ; l'icône (œil de l'Ender) |
| 6.4 | Block Aim Practice (patterns de la communauté) | P3 | Parcours : IMPLÉMENTÉ le 22/11 (ton choix sur la carte : Block Aim ensuite) ; le Bridge Chrono avait déjà les ponts droits, diagonaux et en montée, il a maintenant aussi le pont avec un virage (16, 32 et 64 blocs, un record chacun) ; build et tests unitaires passés en CI (d216c86) ; installé le 22/11 (avec 28a4bee), pas encore joué. Placement sur cible : IMPLÉMENTÉ le 22/11, nouveau mode « Block aim » (`block-aim`) : une face s'allume sous les pieds au bout du pont, sur le côté du pont ou sur le lit, 5 niveaux de plus en plus rapides comme ceux de Ladder Practice, un record par niveau ; build et tests unitaires passés en CI (28a4bee) ; installé sur ton serveur de test le 22/11 avec le pont avec virage, pas encore joué (détail plus bas, « Étape 7 : Block Aim Practice ») | à confirmer : le virage à droite, à mi-chemin, à plat ; pas de virage en montée (la place manque à moins de 64 blocs du centre) ; placement sur cible : la scène générée (question 8 sans réponse), les 15 faces, les temps des 5 niveaux (3 s à 1 s), un bloc posé ailleurs compté sans finir la série |
| 6.5.1 | Red Rover (Sumo, Block Placement, Block Edit, Top Bridge…) | P2 | Sumo, Block Edit, Block Placement, Top Bridge : TESTÉ par les bots en CI (21/11) ; rien encore en jeu | question 10 (qui reste, points ou élimination : codé « points, le gagnant reste, premier à 3 », à confirmer) ; 90 s par 1v1 à confirmer  |
| 6.5.2 | Modes 2v1 asymétriques (Clutch Scenario, stuff amélioré) | P3 | Clutch Scenario : IMPLÉMENTÉ le 22/11, build et tests unitaires passés en CI (le partage du joueur seul, la party gardée ensemble) ; pas encore joué, ni par les bots ni en jeu ; en 1v1, 1v2, 1v3 et 1v4 comme Invis Practice et le Bed Break Simulator (ta demande du 22/11, à la place des 2v2, 3v3 et 4v4 ; les victoires de ceux-ci comptent toujours pour le titre Clutch Scenario) ; à partir du 1v2, le joueur seul, tiré au sort, part sans lit avec une vie, les autres gardent le leur (ton choix du 21/11) ; une party reste ensemble, le joueur seul est tiré parmi les autres (ton choix du 22/11). Stuff amélioré : IMPLÉMENTÉ le 22/11 (ton choix sur la carte : Bed Fight 1v2), CI verte (3cdfed2 : build, tests unitaires, tests sur serveur) ; installé sur ton serveur de test le 22/11 (aa724b7) ; pas encore joué | l'écart de stuff (valeurs de départ : jambières et bottes en fer, épée en pierre, sans Protection, dans `stuff-ameliore:` de la config de rago-bedwars) |
| 6.5.3 | Bot IA : étude technique | P3 | TESTÉ EN CI le 22/11 (47fdcd4 : Sumo, Top Bridge 1v1 et 2v2, invitation, refus, départ), essai en jeu à faire ; installé sur ton serveur de test le 22/11 à 14h50 sans la correction du Top Bridge, puis vers 17h30 avec elle (e0dadff, journal « RagoBot ») ; tes réponses du 22/11 aux 6 questions (détail plus bas, « Tes réponses du 22/11 ») : un vrai joueur, en Top Bridge 1v1 et 2v2 et en Sumo 1v1 pour commencer. Ta demande du 22/11 à 17h54 (dans tous les modes, poser, casser, faire un pont) : l'ordre en 6 étapes validé le 22/11 ; étape 1 (Boxing, Lobby Fight 1v1) TESTÉ en CI (6776a1a) ; étape 2 (Block Placement 1v1/2v2 et son Red Rover) IMPLÉMENTÉ, le 1v1 passe en CI (3fe54af), le Red Rover en cours ; journal « Ta liste du 22/11 (17h54) ». Le Combo Bot : TESTÉ en CI le 22/11 (243a20d), retiré des menus (journal « Combo Bot retiré des menus ») | — |
| 6.6 | Catégorie 3 joueurs (Red Rover, Clutch Scenario, Bed Break Scenario, Invis Practice) | P2 | IMPLÉMENTÉ le 22/11, CI verte (tests unitaires ; les bots du lobby ouvrent la boussole à 6 catégories et y prennent Duels au slot 12 ; tests du site) : « 3 Players » dans la boussole seulement, sans PNJ (ton choix du 21/11), après Fun Practice ; elle montre Red Rover (un objet), Bed Break Simulator 1v2, Invis Practice 1v2 et Clutch Scenario 1v2 (`aussi:` de `categories:`), avec les mêmes files : ces modes restent dans leur catégorie, leurs stats aussi ; le retour d'un groupe ouvert depuis elle y ramène ; aussi dans l'accueil, `/play 3 players` et le site (tests du site passés en local) | son nom, son slogan, sa description et son icône (une tête) sont à confirmer ; la boussole passe à 6 catégories, côte à côte des slots 10 à 15 (sur la ligne du haut, slots 1 à 6, depuis 5.9) |

### Étape 8 : administration et benchmark (Phase 7)

Proposition écrite le 22/11, rien n'est codé : document « Étape 8 : administration et benchmark —
propositions » (Claude Docs), avec 9 questions à trancher.

| # | Tâche | Prio | Statut | Fichiers | Manque |
| --- | --- | --- | --- | --- | --- |
| 7.1 | Outils Owner (Reach, Scaffold…) réservés au compte Owner | P3 | TESTÉ EN CI le 22/11 (75050b8, les bots Owner : journal « Outils Owner »), installé sur ton serveur de test le 22/11, essai en jeu à faire : ta réponse du 22/11, « je t'écoute », puis « Enchaine » sur ma recommandation (qRago seul, Reach et Scaffold, coupés par défaut, jamais en Ranked, partie non comptée) | `OwnerTools`, `OwnerRules` (nouveaux), `LobbyState`, `QueueController`, `RagoLobby`, `LobbyService`, `Training` (practice), `plugin.yml`, `config.yml` | C8 ; questions 1, 2 et 5 |
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

### 3.7 Organisation Solo / Party / Ranked — PROPOSITION (21/11), validée le 22/11 (voir « 3.7 Solo / Party / Ranked, le code (22/11) »)

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
- **Parkour Duel** (6.1d, `parkour-duel`, RagoPractice) : le parcours du plugin (`ParkourCourse`),
  réglage `parkour-duel:` : 1 manche gagnante.
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

**Installé** encore le 22/11 (vers 1h40) : les 5 plugins du commit af042fc, la boussole façon
Minemen (5.9) ; seul `rago-lobby` change, les 4 autres jars sont identiques octet pour octet ;
aucune config ni aucun monde touché ; l'ancien état est dans `sauvegarde-avant-maj-af042fc`.

**À faire en jeu** : une partie de chaque mode sur ton serveur de test ; poser le PNJ avec
`/lobby point funmod2`.

### Étape 7 : Find Your Sensitivity (22/11)

Ta demande du 22/11 : « En attendant que je test commence l'étape suivante des mods », puis ton
choix sur la carte : Find Your Sensitivity d'abord. Codé selon le protocole du document de
l'étape 7 ; la question 9 n'a pas de réponse, donc chaque nombre est une valeur de départ, à
confirmer.

- **Le mode** (`find-your-sensitivity`, RagoPractice, catégorie Practice de la boussole) : le
  joueur donne la sensibilité de ses options (Options > Controls > Mouse Settings) avec le
  comparateur (boutons -10, -1, +1, +10 ; 100 % par défaut, comme dans le jeu), puis tape la
  cible verte. Trois séries identiques : à sa sensibilité, puis à une qui tourne 20 % moins vite
  et à une 20 % plus vite (sur la courbe du client 1.8.9 : 100 % donne 88 % et 110 %). Entre
  deux séries, le jeu lui dit quelle valeur mettre dans ses options ; s'il n'y arrive pas
  exactement, il entre la sienne au comparateur.
- **Une série** : 12 flicks (35 à 120° à gauche et à droite du viseur) et 4 demi-tours (une cible
  derrière), dans un ordre tiré au sort mais le même pour les trois séries ; chaque cible finie
  par un clic dessus, 3 secondes au plus ; puis 20 secondes à suivre une cible qui strafe comme
  celle de l'aim trainer. Le joueur reste sur son bloc (des barrières invisibles autour) : chaque
  cible est à 6 blocs.
- **Les mesures** : le temps jusqu'au clic sur la cible ; un dépassement (le viseur passe le bord
  de la cible) ou un arrêt avant la cible, selon ce qui vient en premier ; les corrections (le
  viseur qui repart dans l'autre sens) ; le suivi (le pourcentage des ticks sur la cible).
- **Le résultat** : chaque série, la plus précise (le meilleur total des rangs sur ces quatre
  mesures, à confirmer), le sens à prendre (plus de dépassements : plus bas ; plus d'arrêts
  avant : plus haut), et que cela ne vaut que pour cette souris à son DPI, le ping pouvant le
  décaler. Rien n'est gardé : ni record, ni historique. L'XP des entraînements (par minute)
  reste celle de tous les entraînements.
- **Le menu des réglages** des entraînements sait maintenant faire des pas de 10 pour un réglage
  qui en a besoin ; seuls les réglages de Find Your Sensitivity s'en servent, les autres ne
  changent pas.

**Testé** : les calculs (les sensibilités, les dépassements et arrêts avant, les demi-tours, le
classement des séries) sur des flicks simulés, en local, puis les tests unitaires en CI (commit
0bcfd61, tous les jobs verts, Java 8 et 21). Rien encore en jeu : aucun bot ne joue ce mode (il
faudrait viser à la souris), il reste à l'essayer sur ton serveur.

**Installé** sur ton serveur de test le 22/11 (vers 2h20, heure de Paris, serveur arrêté) : les
5 plugins du commit 0bcfd61 (empreinte du zip vérifiée) ; `rago-lobby` et `rago-practice`
changent, les 3 autres jars sont identiques. Le `config.yml` du lobby est fusionné avec tes
réglages, sans conflit : seules les 12 lignes du mode sont ajoutées. Rien d'autre n'est touché
(ni `server.properties`, ni les mondes) ; l'ancien état est dans `sauvegarde-avant-maj-0bcfd61`.

### Étape 7 : Block Aim Practice (22/11)

Ta demande : « passer à l'étape suivante le temps que je teste », puis ton choix sur la carte :
Block Aim Practice. Ma carte se trompait : elle parlait d'ajouter des ponts diagonaux et en
montée, mais le Bridge Chrono les avait déjà (12 parcours). De la proposition, il restait donc le
pont avec un virage et le placement sur cible.

- **Le pont avec un virage** (le Bridge Chrono, `bridge-chrono`) : un réglage « Turn » de plus. Le
  pont part vers l'ouest, tourne à droite (vers le nord) à mi-chemin et finit sur le pad vert,
  à plat : 8 + 8, 16 + 16 ou 32 + 32 blocs. Allumer le virage éteint la diagonale et la montée,
  et l'inverse. Les 3 parcours sont placés dans la place que laissent les 12 autres, à moins de
  64 blocs du centre comme eux, avec le même écart de 2 blocs. Un record chacun
  (`bridge-chrono.16.turn`…) ; `/records` montre maintenant le Bridge Chrono en trois rangées (à
  plat, en montée, avec un virage). Le défi du jour reste le pont droit et à plat.
- **À confirmer** : le virage à droite, sa place à mi-chemin, à plat seulement. Un virage en
  montée demanderait 3 parcours de plus : il n'y a pas la place pour celui de 64 blocs à moins de
  64 blocs du centre.
- **Ensuite** : le placement sur cible (une face s'allume, tu y poses un bloc le plus vite
  possible, des niveaux de plus en plus rapides), avec des valeurs de départ à confirmer.

**Testé** : la place des 15 parcours vérifiée en local (aucun ne touche un autre, tous à moins de
64 blocs du centre), puis les tests unitaires en CI (commit d216c86, tous les jobs verts, Java 8
et 21). Rien encore en jeu, ni installé.

**Le placement sur cible** (22/11, pendant que tu testes) : un nouveau mode d'entraînement,
« Block aim » (`block-aim`, dans la boussole avec les autres entraînements, icône poudre de
glowstone).

- **La scène**, générée (ta question 8 n'a pas encore de réponse : ni map à toi, ni patterns
  choisis) : une île de 4 sur 3 en bois avec un lit au milieu, et un pont de laine bleue de 3 blocs
  qui en sort. Le joueur apparaît au début du pont, face au lit.
- **Une face s'allume** (de la poussière verte dessus), parmi 15 : sous les pieds (le bout du pont :
  il faut s'avancer au bord, shift tenu, comme en bridge), sur le côté du pont (6 faces), ou sur le
  lit à couvrir (8 faces : dessus, côtés, bouts ; shift tenu pour poser contre le lit, comme sur
  Hypixel). On y pose un bloc le plus vite possible ; il part aussitôt et la face suivante
  s'allume, jamais la même, à 4,5 blocs au plus de la précédente (pour que le temps reste jouable).
- **Les niveaux**, comme les hauteurs de Ladder Practice (les colorants vert et rouge de la hotbar) : 3 s, 2,5 s,
  2 s, 1,5 s et 1 s par cible, un record chacun (`block-aim.level1` à `level5`, aussi dans
  `/records`). Chaque cible posée à temps ajoute un à la série ; une cible pas posée à temps ou une
  chute la finit. La première cible d'un essai attend : elle lance la série quand tu es prêt.
- **On compte** le temps moyen par cible et les blocs posés ailleurs (au scoreboard et à la fin de
  la série) ; un bloc posé ailleurs part aussi, il ne finit pas la série.
- **À confirmer** : la scène, les faces, les 5 temps, la distance de 4,5 blocs entre deux cibles,
  et qu'un bloc posé ailleurs ne finisse pas la série. Pas de défi du jour pour ce mode.

**Testé** : en local, les 15 faces : chacune est visible et à portée (4,5 blocs) d'une place où
le joueur peut se tenir sur la scène, debout ou au bord shift tenu, sans être dans la case du bloc
à poser ; le tirage de la cible suivante (3 000 tirages : jamais la même, toujours à 4,5 blocs au
plus, les trois places tirées). Ces tests sont dans `BlockAimTest`. Puis en CI (commit 28a4bee) :
le build et les tests unitaires passent, et les tests sur serveur (Java 8 et 21) aussi. Les 3 jobs
du client 1.8.9 (KB recorder, Billboard) ont échoué avant de lancer le moindre code : le
téléchargement de Java 8 (Temurin 8u514) n'a pas la bonne empreinte, comme déjà signalé sur la PR.
Le test de bout en bout du Block In (serveur, Java 21) a échoué une fois sur 27b40e7, un commit qui
ne changeait que ce document, et passe sur 28a4bee avec le même code : test instable, à surveiller.
Rien encore en jeu, ni installé.

**Installé** sur ton serveur de test le 22/11 (vers 3h05, heure de Paris, serveur arrêté) : les
5 plugins du commit 28a4bee (empreinte du zip vérifiée), donc le pont avec virage et Block aim.
`rago-lobby` et `rago-practice` changent, les 3 autres jars sont identiques. Le `config.yml` du
lobby est fusionné avec tes réglages, sans conflit : les 12 lignes de Block aim sont ajoutées et la
description du Bridge Chrono passe de 2 à 3 lignes (« with a turn »), comme dans le jar. Rien
d'autre n'est touché (ni `server.properties`, ni les mondes) ; l'ancien état est dans
`sauvegarde-avant-maj-28a4bee`.

### Étape 7 : Stuff amélioré, le Bed Fight 1v2 (22/11)

Ton choix sur la carte : « Bed Fight 1v2 : le joueur seul, tiré au sort, a un meilleur stuff que
les deux autres ; l'écart de départ est à régler en jeu avec toi. »

- **Le mode** `bed-fight-1v2` (« Bed fight 1v2 »), dans l'objet Bed fight des Duels à côté du 1v1
  et du 2v2, et dans la catégorie 3 Players. Même map (BedFight), mêmes règles, même kit du lobby
  (son propre kit dans l'éditeur, comme le 1v1 et le 2v2) ; non classé. Ses victoires comptent pour
  le titre Bed Fight.
- **Le joueur seul** : les trois joueurs arrivent en une équipe de 3, partagée par la partie comme
  le Clutch Scenario. Il est tiré au sort, mais pas parmi les joueurs d'une party : des amis jouent
  ensemble (comme ton choix du 22/11 pour le Clutch Scenario).
- **Son meilleur stuff**, à chaque vie : des jambières et des bottes en fer (le casque et le
  plastron restent en cuir de sa couleur, comme en Bed Wars), une épée en pierre à la place de celle
  en bois, dans la case où il l'a rangée. Au début, le chat dit à chacun ce stuff.
- **Réglable sans recompiler**, pour caler l'écart en jeu : `stuff-ameliore:` dans le `config.yml`
  de rago-bedwars, `armure` (0 cuir, 1 maille, 2 fer, 3 diamant), `epee` (`STONE_SWORD`,
  `IRON_SWORD`…), `protection` (0 à 4). Une valeur fausse est refusée au démarrage avec son nom.
- **À confirmer** : les trois valeurs de départ (fer, épée en pierre, sans Protection).

**Testé** : en CI (commit 3cdfed2), le build et les tests unitaires passent : la lecture de
`stuff-ameliore:` et ses erreurs, l'épée remplacée dans sa case, le texte du stuff, le mode dans
la config du lobby, la catégorie 3 Players (5 entrées) et le site. Les tests sur serveur (lobby et
sumo, Java 8 et 21 ; reset des arènes) et le site passent aussi ; seul le job du serveur de test
Linux en Java 8 échoue, avant tout code, sur l'empreinte de Temurin 8u514 (déjà signalé sur la
PR). Aucun bot ne joue encore de partie de Bed Fight 1v2, rien en jeu, rien d'installé (ton PC
est éteint cette nuit).

### Étape 7 : Capture the Flag, The Bridge et château contre château (22/11)

Ta demande du 22/11 (vers 3 h 10) : « Pour le CTF j'imagine 2 type : 1. The Bridge 2. type normal
avec une map assez grande type Chateau vs Chateau et 1 mid. » Les deux sont dans Fun Practice, non
classés, dans le titre « Capture the Flag ».

- **Les règles communes** : un drapeau par équipe, une bannière de sa couleur sur un bloc d'or au
  fond de sa base. On le prend en marchant dessus (la bannière passe sur la tête du porteur) et on
  marque en l'apportant sur son propre drapeau, s'il est chez lui. Le porteur qui meurt le lâche :
  un ennemi le reprend, un joueur de son équipe le ramène en le touchant, sinon il rentre seul
  après 30 s ; tombé dans le vide, il rentre tout de suite. Pas de lit : on revient toujours. On ne
  pose pas de bloc sur un drapeau. La première équipe à 3 captures gagne, sinon celle qui en a le
  plus à la fin du temps (égalité possible). Le scoreboard montre le score et l'état de chaque
  drapeau (« Safe », « Taken », « Dropped 24s »).
- **The Bridge** (`ctf-bridge-1v1`, `ctf-bridge-2v2`) : sur CtfBridge, une map que le plugin
  construit lui-même. Deux bases en quartz reliées par un pont d'argile d'un bloc de large et de 41
  de long, qu'on casse et sur lequel on construit (4 blocs dessous à 4 au-dessus). Le kit de The
  Bridge d'Hypixel : armure en cuir sans casque, épée en fer, arc, pioche en diamant Efficiency II,
  deux stacks d'argile de l'équipe, 8 pommes d'or (toute la vie d'un coup et deux cœurs
  d'absorption), une flèche qui revient 3,5 s après le tir. Ni forge ni shop ; retour immédiat
  après une mort, avec le kit rangé comme dans l'éditeur du lobby. 15 minutes au plus.
- **Château contre château** (`ctf-castle-2v2`, `ctf-castle-4v4`) : sur CtfCastle, l'autre map
  construite par le plugin, 151 blocs de long. Deux châteaux de pierre taillée de 31 × 31 (murs à
  créneaux, quatre tours, une porte vers le mid, une cour éclairée, le donjon au fond avec le
  drapeau) et un mid rond avec l'émeraude et deux diamants, à 34 blocs de vide de chaque château.
  C'est une partie de Bed Wars : forge, shop, améliorations et pièges dans la cour, générateurs du
  mid qui montent de niveau ; on revient après les 5 s de réapparition. 20 minutes au plus.
- **Réglable sans recompiler** : `ctf:` dans le `config.yml` de rago-bedwars (`captures`,
  `minutes-bridge`, `minutes-chateau`, `retour-drapeau-secondes`).
- **À confirmer** : 3 captures, 15 et 20 minutes, 30 s de retour du drapeau, les tailles (The
  Bridge 1v1 et 2v2, château 2v2 et 4v4), le dessin des deux maps (modifiables en jeu : le plugin
  ne les reconstruit pas tant que leur dossier existe), les icônes (arc, épée en fer).

**Testé** : en CI (commit 757ba0b), le build et les tests unitaires passent (les deux maps, leur
symétrie, le chemin de la porte au drapeau, les lumières, les réglages, les drapeaux), et les bots
jouent les deux types sur le vrai serveur. The Bridge 1v1 : le kit, le pont d'un bloc, la pioche
qui casse le bout du pont et l'argile remise, la pomme d'or (absorption, sans régénération), la
flèche revenue après 3,9 s, le drapeau pris puis rentré tout seul depuis le vide, trois captures
(« Red 3 - Blue 0 ») et la victoire, le retour au lobby. Château 2v2 : les quatre dans leur cour
avec le départ de Bed Wars, la porte ouverte, les murs, les drapeaux sur l'or dans les donjons, les
forges, les shops, l'émeraude et les diamants du mid ; un bot ramasse 6 fers à sa forge et va
jusqu'à son drapeau sans rien déclencher ; les bleus partent, les rouges gagnent. Un premier essai
du test bots s'était arrêté à la première capture : il attendait le score sous une autre forme
(« Red 1 - 0 Blue ») que celle du jeu ; le test est corrigé (49f1b4a), le jeu n'a pas changé. Pas
encore joué en jeu par de vrais joueurs. Rien d'installé (ton PC est éteint cette nuit) : à
l'install, les nouvelles lignes `modes:`, `jeux:` et `ctf:` du `config.yml` de
rago-bedwars et les 4 modes et 2 groupes du `config.yml` du lobby seront à ajouter à tes réglages.

### Étape 7 : Fast Bedwars (22/11)

Ta demande du 22/11 : « finir tous les mods que j'avais demandés, un par un », en restant fidèle au
plan (6.1 : « un Bedwars simplifié, inspiré du Bedwars de 2018 : rapide, simple et amusant ») et à
la proposition du 21/11 : le construire sur le plugin Bed Wars, rapide par les forges et les
événements, simple par un shop réduit et peu ou pas d'améliorations, sur les maps du Bed Wars.

- **Les modes** `fast-bedwars-1v1` et `fast-bedwars-2v2` (« Fast Bedwars 1v1 / 2v2 »), dans Fun
  Practice (un objet « Fast Bedwars »), non classés, sur les 20 maps de la file RBW, avec leur
  titre. Une vraie partie de Bed Wars : lits, îles, forge, shop, émeraudes, réapparition tant que
  le lit tient.
- **Rapide** : la forge deux fois celle d'une map Hypixel de quatre équipes ou moins (240 fers et
  24 ors par minute, dans `forge.modes`) ; les événements deux fois plus tôt (Emerald II à 6 min,
  Emerald III à 12, la fin des lits à 15, la fin de la partie à 22 min 30). Les événements étaient
  communs à tous les modes : Fast Bedwars a maintenant les siens, `fast-bedwars.evenements`.
- **Simple** : un shop réduit à une liste (`fast-bedwars.shop`) : pour commencer, les 20 objets du
  Quick Buy par défaut (laine, planches, end stone, verre, épées en pierre et en fer, armures en
  mailles et en fer, cisailles, pioche, hache, arc, flèches, potions de vitesse et de saut, TNT,
  seau d'eau, pomme en or, fireball, ender pearl). Le Quick Buy du joueur (celui d'Hypixel s'il l'a
  importé) garde ses places, sans les objets non vendus. Pas d'améliorations d'équipe
  (`fast-bedwars.ameliorations: false`) : ni PNJ, ni pièges, et donc pas de générateur de diamants
  (ils n'achètent que les améliorations), ni Diamond II ni III. `true` les remet.
- **L'accueil** le dit en une ligne : « Faster forges, a smaller shop, no team upgrades. The beds go
  at 15:00, the game ends at 22:30. »
- **À confirmer** : ce que « le Bedwars de 2018 » veut dire pour toi (toujours sans réponse :
  shop, améliorations, équipes, durée) ; la liste du shop ; les valeurs des forges et des
  événements ; pas d'améliorations ; les tailles (1v1 et 2v2) ; les maps (les 20 de la file RBW) ;
  l'icône (un lit).

**Testé** : en CI (commit 757ba0b), le build et les tests unitaires passent (les réglages
`fast-bedwars:` et leurs erreurs, les forges du mode, le shop réduit et son Quick Buy, les
événements sans diamants, la ligne d'accueil, les modes du lobby), et deux bots jouent Fast
Bedwars 1v1 sur la carte démo : les deux lignes d'accueil, Emerald II annoncé à 6 minutes sans
Diamond II, le générateur d'émeraudes sans générateur de diamants, l'item shop sans PNJ des
améliorations, 9 ors et 92 fers ramassés en 12 secondes sur le spawn, le Quick Buy par défaut
sans le panneau « Empty slot! », les Blocks réduits à laine, verre, end stone et planches, la
laine achetée ; le bleu part, le rouge gagne. Pas encore joué en jeu ; pas testé sur une des 20
maps RBW (absentes du serveur de test). Rien d'installé : à l'install, les lignes
`modes:`, `jeux:`, `forge.modes` et `fast-bedwars:` du `config.yml` de rago-bedwars et les 2 modes
et le groupe du `config.yml` du lobby seront à ajouter à tes réglages.

### Tes réponses du 22/11 (10h37)

Tu as répondu à toutes les questions ouvertes « pour que tu puisses build ». Tu feras ensuite une
grande liste des bugs de la nouvelle version, une fois les modes et fonctionnalités finis.

- **Bot IA (6.5.3)**, tes réponses aux 6 questions de l'étude :
  1. un vrai joueur pour le serveur (le faux joueur côté serveur), qui s'appelle RagoBot. On joue
     avec ou contre lui seulement en party ou en duel 1v1, jamais en Ranked. `/p invite RagoBot` :
     il rejoint tout seul ; il peut être dans plusieurs parties à la fois s'il y a beaucoup de monde ;
  2. à terme dans presque tous les modes, en équipe ou en 1v1 ; pour commencer, Top Bridge 1v1 et
     2v2, et Sumo 1v1 ;
  3. jamais pour compléter une file : seulement si un joueur l'invite ; sinon il ne fait rien ;
  4. le skin de qRago, le nom RagoBot ;
  5. ce sont des parties de party : on gagne de l'XP, jamais d'ELO ; il compte comme un vrai joueur ;
  6. pas de niveaux : « c'est un vrai joueur avec un skill prédéfini » ; et le Combo Bot est
     supprimé (« le combo bot on le supprime »). Je le lis ainsi : l'entraînement Combo Bot sort des
     menus, ses records restent dans les données ; le code de son combat (`ComboAi`) sert au bot.
- **Autres modes FUN (6.1d)** : Parkour Duel, « dans une map stylée ». Et « le mod spleef » : la
  map agrandie, en deux étages, les joueurs ne peuvent plus se frapper. Je le lis comme le Snowball
  Spleef actuel.
- **Outils Owner (7.1)** : « je t'écoute ». Ma recommandation : ton compte qRago seul (UUID lu
  dans le `usercache.json` du serveur), Reach et Scaffold seulement, coupés par défaut, jamais en
  Ranked, une partie avec un outil actif ne compte pour personne.
- **Solo / Party / Ranked (3.7)** : « Build ». Ton choix sur la carte pour la question 1 : « les
  grands duels 1v1 », Bed Fight, Top Bridge, Bridge Fight, Block Placement, Sumo et Boxing en 1v1
  classé, chacun avec son ELO, plus Ranked Bedwars comme aujourd'hui. Pour les autres, je suis la proposition : Ranked Bedwars passe dans RANKED, la
  Party garde ce qui existe, la boussole donne accès à Solo, Party et Ranked.
- **`/discord`, `/store` et le texte de remerciement (4.5, 5.7)** : `rago-optimizer.com` et
  `discord.gg/ragooptimizer`, déjà ceux des commandes.
- **RagoClient et RagoOptimizer (5.6A)** : les mêmes liens pour les deux, et le skin de qRago.
- **jncl et nativ (5.6C)** : jncl affiché « directement », nativ affiché « woryes ».
- **Build monumental (5.2)** : tu m'envoies l'image ; à faire après les modes et fonctionnalités.
- **Fast Shop et API Hypixel (2.8)** : tu refais une demande de clé.
- **Fast Bedwars (6.1c)** : « Bedwars 2018 », ce sont les maps de l'époque, les premières. Il me
  faut leurs mondes (comme pour les maps RBW), ou au moins leurs noms.

### 3.7 Solo / Party / Ranked, le code (22/11)

Ta réponse du 22/11 : « Solo / Party / Ranked : Build », et ton choix sur la carte : « les grands
duels 1v1 ». La proposition est suivie, dans la boussole comme sur Minemen que tu as demandée
ensuite (5.9) : les six onglets d'aujourd'hui sont le **Solo**, et deux onglets s'ajoutent sur la
même ligne du haut, **Party** puis **Ranked** (dans l'ordre de la proposition : Solo, Party,
Ranked). Huit onglets au lieu de six : Practice passe du slot 1 au slot 0, Duels du slot 3 au slot 2.

- **Ranked** (la catégorie `classe` du `config.yml` du lobby) : tous les modes classés, chacun avec
  son ELO, sa division, sa file par ELO, son classement (le sélecteur de leaderboards, `/top elo`),
  les saisons et la file fermée 5 minutes après un abandon, comme Ranked Bedwars aujourd'hui.
  - Ranked Bedwars 1v1 à 4v4 y passe, avec ses ELO (rien n'est perdu). Il sort de RBW Modes, comme
    le dit la proposition (« Seul Ranked Bedwars en sort, pour aller dans RANKED ») : la question 2
    (le garder aussi dans RBW Modes) n'avait pas de réponse ; si tu le veux aussi là, c'est une
    ligne (`aussi: [ranked]` dans la catégorie RBW Modes). Le PNJ RBW Modes ouvre la boussole sur
    RBW Modes, avec l'onglet Ranked à côté.
  - Six duels classés en 1v1 : `ranked-bed-fight-1v1`, `ranked-top-bridge-1v1`,
    `ranked-bridge-fight-1v1`, `ranked-block-fight-1v1` (Block Placement), `ranked-sumo` et
    `ranked-boxing`. Chacun est le même jeu que son mode de Solo : même plugin, mêmes maps (les 20
    maps RBW pour Top Bridge, BedFight pour Bed Fight, les zones et maps de Practice pour les
    quatre autres), même kit (`kit-de:` : le kit de Solo, arrangé une fois dans l'éditeur ; le
    sumo n'en a pas), mais sa propre file par ELO et son propre ELO. Les modes de Solo ne changent
    pas et restent dans Duels.
  - Les stats d'une partie classée (victoires, kills, replay) vont au mode classé joué, comme pour
    Ranked Bedwars. Dans les lignes « Elo » (profil, menu des stats, `/season`), un duel classé
    s'appelle par son jeu (« Elo Sumo », « Elo Bed Fight 1v1 ») et Ranked Bedwars garde « Elo 1v1 ».
  - `/duel` ne propose pas les duels classés : un `/duel` n'est jamais classé, ce serait le même
    duel que son mode de Solo, déjà dans la liste. Ranked Bedwars 1v1 y reste.
- **Party** (la catégorie `party`, avec `party: true`) : pas de modes à elle. Elle montre au
  joueur ce que sa party joue contre elle-même à sa taille, en partie privée (sans stats ni ELO,
  comme aujourd'hui) : à deux les 1v1, à trois les 1v2 et Red Rover, à quatre les 2v2, etc., et la
  partie privée de Ranked Bedwars ; les tailles d'un même jeu font un seul objet quand il y en a
  plusieurs. Hors party, un objet dit comment en créer une (`/party invite <name>`). Seul le chef
  lance, comme aujourd'hui. Une party qui tient dans une équipe (deux joueurs en 2v2) file toujours
  contre d'autres depuis les autres onglets, Ranked compris, avec la moyenne de ses ELO. Rien de
  nouveau n'est ajouté (pas de FFA de party ni de party contre party : la question 3 est restée
  sans réponse, je suis la proposition, « ce qui existe »).
- **Accès** : la boussole (question 4 sans réponse, je suis la proposition). `/play` et `/queue`
  ne changent pas : `/play ranked` ouvre toujours RBW Modes (c'est l'id de cette catégorie),
  `/play classe` ouvre Ranked, `/play party` ouvre Party.
- **Le site** montre la catégorie Ranked et ses modes ; Party n'y est pas (elle n'a pas de modes à
  elle). La section Ranked de l'accueil parle toujours de Ranked Bedwars en 1v1, 2v2, 3v3 et 4v4.

Fichiers : lobby (`ModeCategory`, `LobbySettings`, `ModeMenu`, `QueueController`, `DuelRequests`,
`StatsMenu`, `config.yml`), Bed Wars (`config.yml` : `modes:`, `jeux:`, `top-bridge:`), Practice
(`RagoPractice`, `SumoHost`, `SumoMatch`, `PracticeHost`), le site (données et accueil), les bots
du lobby (`lobby-bots.mjs`, slots des onglets), les README. Tests : `LobbySettingsTest` (92 modes,
8 catégories, le contenu de Ranked, la catégorie Party et ses erreurs de config),
`LobbyWelcomeTest`, `LayoutTest`, `BedwarsSettingsTest`, les tests du site. Vérifié ici : la
syntaxe Java (javac, sans les classes de Bukkit) et les tests du site (34 passés) ; les tests Java
tournent en CI. Pas encore joué en jeu, rien d'installé : à l'install, les 6 modes, les 2
catégories et les nouvelles descriptions du `config.yml` du lobby s'ajoutent à tes réglages par
la fusion, et le `config.yml` des Bed Wars gagne ses 2 modes classés.

### 5.6A Rago Client et Rago Optimizer, OUR OTHER PRODUCTS (22/11)

Le plan : « PNJ 1 — RagoClient », nom affiché « Rago Client », bloc de diamant, au clic les
liens officiels ; pareil pour « PNJ 2 — RagoOptimizer » ; « OUR OTHER PRODUCTS » au-dessus des
deux PNJ ; les visuels publicitaires déjà utilisés dans le lobby replacés derrière eux, contre le
mur. Tes réponses du 22/11 : `rago-optimizer.com` et `discord.gg/ragooptimizer` pour les deux, le
skin de qRago pour les deux.

- Un nouveau type de PNJ, `type: produit` : un joueur dans le skin de qRago (`skin-joueur: qRago`),
  les mains vides, sur un bloc de diamant (un bloc plus haut que son point, comme les autres PNJ).
  Au-dessus : « OUR OTHER PRODUCTS » (`titre:`), puis son nom. Un clic écrit ses liens dans le
  chat, comme `/discord` et `/store` : `RAGO » Rago Client: rago-optimizer.com` puis `RAGO » Join
  our Discord: discord.gg/ragooptimizer` (`liens:`, de 1 à 8 lignes, modifiables).
- « OUR OTHER PRODUCTS » au-dessus de chacun des deux PNJ : tes points `ragoclient` et
  `ragooptimizer` sont à 22 blocs l'un de l'autre, un seul texte au milieu serait loin des deux.
  Si tu préfères un seul texte entre eux, dis-le.
- Les couleurs sont les miennes (le titre en or, les noms en aqua), à changer dans le config si tu
  veux. Pas de ligne sous le nom (le plan n'en demande pas ; `sous-titre:` en ajoute une).
- **Les visuels** : ce sont tes deux panneaux `rago-client` et `rago-optimizer` (tes images
  `rago-client.png` et `rago-optimizer.png`), qui ne sont que dans ton `config.yml`, pas dans
  celui du dépôt. J'ai lu ton monde `lobby` : le mur est 4 blocs derrière tes PNJ (z = -40), plein
  de y = 69 à 74 sur 9 blocs de large derrière chacun, avec l'air juste devant (z = -39) et le
  plafond à y = 75. Un panneau de 8 x 8 n'y tient pas en hauteur : 6 x 6, la hauteur du mur. À
  l'install, je change seulement ces deux panneaux dans ton config (largeur 6, hauteur 6,
  `lieu: "-11 69 -39 sud"` et `"11 69 -39 sud"`, tournés vers les PNJ), sans afficher le reste du
  fichier ; ou tu le fais toi-même, ou tu te mets à l'endroit et tapes `/lobby point
  panneau-rago-client` (le point passe avant `lieu:`).
- **À voir** : sous tes points `ragoclient` et `ragooptimizer` (y = 70), il y a un bloc d'air (y =
  69) puis la dalle du sol (y = 68) : le bloc de diamant sera en l'air, d'un bloc. Pareil sous
  `jncl` et `nativ`. Sous `qrago` et `kalisodev`, tu as posé un bloc de quartz. Pour le poser au
  sol : refaire le point debout sur le sol, ou poser un bloc dessous.

Fichiers : `LobbyNpc`, `LobbyNpcs`, `LobbySettings`, `config.yml` du lobby, le README du lobby.
Tests : `ProductNpcTest` (les deux PNJ du config, les erreurs de config), `LobbySettingsTest`
(18 PNJ), et le bot du lobby en CI (`lobby-bots.mjs`) : il trouve Rago Client sur du diamant sous
« OUR OTHER PRODUCTS », clique dessus et lit ses deux liens dans le chat. Vérifié ici : la
syntaxe Java (javac, sans les classes de Bukkit) et celle du bot (node --check) ; le reste tourne
en CI. Avec ce commit, `StatsMenuTest` est mis à jour pour 3.7 (le run 385 l'avait trouvé : le
menu des stats a maintenant un bouton Ranked, avec les modes classés, et pas de bouton Party).

### 5.6C Les modérateurs jncl et nativ (22/11)

Le plan : « PNJ 5 — jncl », nom à afficher « directemeent », skin du joueur jncl, rôle Mod ;
« PNJ 6 — nativ », nom à afficher « woryes », skin du joueur Woryes, rôle Mod ; « Couleur du rôle
Mod : utiliser un jaune clair ». Ta réponse du 22/11 : « jncl : directement nativ : woryes ».

- Deux PNJ `type: personne`, comme qRago et kaliso_dev : sur le point `jncl`, « [MOD] directement »
  dans le skin du compte `jncl` ; sur le point `nativ`, « [MOD] woryes » dans le skin du compte
  `Woryes`. Les mains vides, un clic ne fait rien. Le nom interne (le point), le compte du skin et
  le nom affiché restent séparés, comme le plan le demande.
- **Le rang Mod en jaune clair** : `&e[MOD]` au lieu de `&2[MOD]` (vert foncé), dans `rangs:` du
  config et dans les rangs par défaut du plugin. C'est le rang lui-même qui change : un vrai
  modérateur le verra aussi en jaune clair dans le chat, le tab et au-dessus de sa tête. Si ton
  `config.yml` a déjà son propre `mod:`, la fusion à l'install le garde (dis-moi si tu veux le
  jaune quand même).
- **Le bloc** : le plan n'en donne pas pour eux (contrairement à l'obsidienne de qRago et de
  kaliso_dev), donc c'est le bloc de tous les PNJ, l'andésite polie, un bloc plus haut que leur
  point. Une ligne `bloc:` le change. Comme pour Rago Client, il y a un bloc d'air sous tes points
  `jncl` et `nativ` (y = 69, ton bloc de fer est à y = 68) : le bloc sera en l'air, d'un bloc.

Fichiers : `config.yml` du lobby (`pnj:`, `rangs:`), `Ranks` (le rang Mod par défaut), le README.
Tests : `PersonNpcTest` (les deux modérateurs, leur nom après « [MOD] » en jaune clair, leurs
comptes, leur bloc), `LobbySettingsTest` (20 PNJ). Vérifié ici : la syntaxe Java ; le reste
tourne en CI.

### 6.1a Snowball Spleef : deux étages, plus grand, sans coups (22/11)

Ta demande du 22/11 : « pour le mod spleef agrandi la map fait 2 étape, et fait en sorte que les
joueurs ne puisse pas se frapper entre eux ».

- **Deux étages** : deux sols de neige, le second 6 blocs sous le premier (`etages: 2`,
  `ecart-etages: 6`). Tomber du premier sur le second ne fait rien perdre (et ne blesse pas) ; on
  perd en tombant sous le second.
- **Plus grand** : rayon 18 au lieu de 12 (le sol fait 37 blocs de large au lieu de 25, un peu plus
  du double de neige). Tu n'as pas donné de chiffre : 18 est à confirmer, `rayon:` dans le
  `config.yml` de RagoPractice (de 5 à 40). Chaque joueur part à 9 blocs du milieu.
- **Sans coups** : la main ne poussait déjà pas. Ce qui poussait, c'était la boule de neige qui
  touche un joueur. J'ai lu « ne puissent pas se frapper » comme « personne ne pousse personne » :
  une boule qui touche un joueur s'arrête maintenant sur lui, sans le pousser ni casser de neige.
  On ne gagne plus qu'en cassant la neige sous l'autre. Si tu voulais garder la poussée des boules
  et enlever seulement les coups à la main, dis-le : c'est une ligne.
- Le texte du mode dans la boussole ne dit plus « pushes » : « Break the snow under them: each
  block gives a snowball that breaks the snow too. Two floors, no hits: don't fall! ».

Fichiers : `SnowballSpleef`, `RagoPractice` (les valeurs par défaut), le `config.yml` de RagoPractice
et celui du lobby (la description), les README, le site (sa description). Tests :
`SnowballSpleefTest` (les réglages fournis : 2 étages, rayon 18) et le bot de FUN PRACTICE en CI
(`fun-bots.mjs`) : il vérifie le second étage, lance des boules de neige sur l'autre bot jusqu'à
en toucher un (aucune poussée, aucun bloc cassé), puis fait tomber l'autre bot sur le second
étage (toujours en jeu) et sous lui (victoire). Vérifié ici : la syntaxe Java, celle du bot et les
tests du site (34 passés). À l'install : ton `config.yml` de RagoPractice garde ses valeurs
(`rayon: 12`, `etages: 1`) tant qu'on ne les change pas ; je les passerai à 18 et 2.

### 6.1d Parkour Duel (22/11)

Ton choix du 22/11 : « Parkour Duel dans une map stylée ». Comme le Parkour Duel connu (celui de
Hypixel) : une course, le premier à l'arrivée gagne, sans coups.

- **Le parcours** (`ParkourCourse`, construit par le plugin comme la plateforme du Snowball
  Spleef) : une piste par joueur, la même pour les deux, rouge et bleue côte à côte (10 blocs de
  vide entre elles : on ne gêne pas l'autre et on ne passe pas chez lui). Environ 100 blocs de long,
  cinq sections à thème : des piliers de quartz, un escalier de verre bleu clair qui monte, un
  zigzag de prismarine avec une course d'élan en prismarine sombre et un saut de trois blocs au
  bout, des lanternes aquatiques qui descendent puis remontent, une ligne d'or jusqu'à l'arrivée.
  Le départ en quartz sous une arche de quartz, l'arrivée en or avec un bloc de diamant au milieu,
  sous une arche d'or et de glowstone, chaque plateforme bordée de la couleur de la piste.
- **Les checkpoints** : quatre, entre les sections (une lanterne aquatique sous une plaque d'or). Le
  joueur qui en atteint un le voit dans le chat (« Checkpoint 2/4! ») et sur son tableau (celui des
  deux équipes). Une chute le ramène sur le dernier atteint, sans point pour l'autre.
- **La fin** : le premier sur l'arrivée gagne la manche ; le chat dit son temps (« ... finished the
  course in 41.250s. »). 1 manche gagnante (`parkour-duel.points-pour-gagner`, de 1 à 10). Aucun
  kit, aucun coup, aucun dégât de chute, pas de limite de temps (comme les autres duels).
- **Chaque saut est faisable** : je les ai tous essayés avec la physique de Minecraft 1.8 (celle
  de mineflayer, prismarine-physics) : trois blocs de vide au plus, deux en montant d'un bloc,
  chacun réussi de nombreuses façons (de 215 à 1764 façons sur 3510 essayées par saut). Ce que je
  ne peux pas juger d'ici : si le parcours te plaît en jeu (« stylé ») et s'il est assez dur.
- Dans la boussole : FUN PRACTICE, « Parkour Duel » (une plume), et la catégorie le cite. Un titre
  « Parkour Duel » comme les autres modes, et le site le montre.

Fichiers : `ParkourCourse`, `ParkourDuel`, `RagoPractice` (le mode, en dernier pour que les autres
gardent leurs zones), les `config.yml` de RagoPractice et du lobby, `Titles`, les README, le site
(le mode, la plume). Tests : `ParkourCourseTest` (chaque bloc existe en 1.8 et a sa place, chaque
saut dans les limites, les checkpoints dans l'ordre, les pistes séparées, le réglage fourni),
`LobbySettingsTest` (93 modes, le Parkour Duel dans FUN, sans kit), `StatsMenuTest`, et le bot de
FUN PRACTICE en CI (`fun-bots.mjs`, `parkour-runner.mjs`) : il vérifie les deux pistes (leurs
couleurs, leurs blocs), fait tomber le bot bleu de son départ (remis au départ, aucun point), puis
le bot rouge court jusqu'au premier checkpoint en calculant chaque saut, en tombe (remis dessus),
et va au bout par les trois autres checkpoints : il gagne. Vérifié ici : la syntaxe Java ; les
tests de `ParkourCourseTest` sur le parcours, lancés avec des remplaçants de JUnit et de Bukkit
(6 sur 7 passés, le 7e lit le `config.yml` et ne tourne qu'en CI) ; le bot sur le parcours simulé
avec la même physique (30 sauts, 0 raté, les 4 checkpoints puis l'arrivée) ; les tests du site
(34 passés). En CI (commit 1e78770) : tous les tests passés. Le bot sur le vrai serveur : les deux
pistes construites comme prévu, la chute du bleu au départ et celle du rouge au premier checkpoint
remises au bon endroit sans point, puis 30 sauts sans un raté, « Checkpoint 1/4! » à « 4/4! »,
« RagoFunUn finished the course in 48.634s. » et la victoire. Deux corrections du bot en route :
il marchait vers le vide hors du parcours, où mineflayer (en 1.8) prend les chunks vides pour des
chunks déchargés et se fige ; il tombe maintenant entre les deux pistes. Et le bot du Block in,
qui échouait par moments avant mes changements (« no push with the hit », aussi sur un commit qui
ne changeait que ce document) : il cherchait la poussée à l'instant même du coup, alors qu'elle
peut arriver juste après ; il l'attend maintenant 300 ms au plus. Passé.

### Combo Bot retiré des menus (22/11)

Ta réponse du 22/11 : « le combo bot on le supprime deja ». Je l'ai lu ainsi : l'entraînement sort
des menus, ses records restent dans les données, et son combat (`ComboAi`) servira à RagoBot.

- Il n'est plus dans la boussole (Practice) ni sur le site : son entrée `combo-bot` quitte le
  `config.yml` du lobby. RagoPractice ne l'ouvre plus ; sa rangée de zones reste réservée, pour
  que les autres modes gardent les leurs (comme l'ancien Top Fight).
- Ses records restent dans les données (rien n'est effacé) ; `/records` et le mur des records ne
  montrent que les modes ouverts, donc plus les siens.
- Le défi du jour ne le propose plus (`combo-bot.hard` sort de `defi-du-jour.epreuves`). Un
  `config.yml` qui le cite encore se lit toujours : le défi le laisse de côté, avec un avertissement
  au démarrage (déjà le cas pour tout mode fermé). Le défi du jour peut donc changer le jour de
  l'install (la liste a un entraînement de moins).
- Le code de l'entraînement reste dans le dépôt, débranché ; le remettre, c'est une ligne et son
  entrée dans le lobby.

Fichiers : `config.yml` du lobby et de RagoPractice, `RagoPractice`, `DailyChallenge`, le README de
RagoPractice, le site. Tests : `LobbySettingsTest` (92 modes, plus de Combo bot), `DailyChallengeTest`
(plus dans le défi, un ancien config se lit), `RecordsMenuTest` (ses records gardés, hors du menu).
Vérifié ici : la syntaxe Java et les tests du site (34 passés). En CI (commit 243a20d) : tous les
tests passés, le test des arènes compris.

### Texte de remerciement (5.7, 22/11)

Ta réponse du 22/11 : « Le texte est parfait ». Le texte est posé tel quel, mot pour mot, sur ton
point `textremerciement` (x 0, y 69, z -35 dans ton lobby).

- Une nouvelle section `textes:` du `config.yml` du lobby : un texte par point, ses lignes de haut
  en bas. Le config.yml fourni y met le texte de remerciement : « THANK YOU » en or et en gras, le
  texte en blanc, la dernière ligne avec le Discord en rouge, comme sur les PNJ Rago Client et Rago
  Optimizer. Les deux lignes vides de ton texte sont deux blancs.
- Il flotte au-dessus du point : la dernière ligne à 0,45 bloc du sol, la première à 5,58 blocs.
  J'ai relu ta salle dans le monde que tu m'as envoyé : sol à y 69, plafond de quartz à y 75, donc
  6 blocs d'air ; le texte va du sol au plafond sans le toucher. Chacun lit le même texte ; un clic
  ne fait rien.
- Une ligne de plus de 64 caractères (la limite des noms en 1.8, couleurs comprises) est refusée au
  démarrage avec un message, au lieu d'être coupée ; un texte sur le point d'un PNJ aussi.
  `/lobby points` montre le point du texte.

Fichiers : `LobbyText` (nouveau), `LobbySettings`, `RagoLobby`, `LobbyCommands`, le `config.yml` et
le README du lobby. Tests : `LobbyTextTest` (le texte mot pour mot, sa hauteur dans la salle, les
erreurs refusées) ; le bot du lobby en CI lit les 18 lignes écrites, dans l'ordre, sur un point du
serveur de test, et vérifie leurs hauteurs. Vérifié ici : la syntaxe Java ; le reste tourne en CI : la CI du 22/11 (5216997) est passée, ce bot compris.
À l'install : la section `textes:` arrive avec la fusion de ton `config.yml`.

### RagoBot (6.5.3, 22/11)

Tes réponses du 22/11 (« Tes réponses du 22/11 », plus haut), sans rien de plus :

- `/p invite RagoBot` (ou `/p RagoBot`) : RagoBot entre tout de suite dans la party, à côté du
  chef. C'est un joueur du serveur (un faux joueur sans client derrière, que le serveur connecte
  comme un joueur), nommé RagoBot, avec le skin du compte qRago. Un par party qui l'invite, 10 à la
  fois au plus (`ragobot.max`, à confirmer).
- Il joue les parties de la party dans ses modes : Top Bridge 1v1, Top Bridge 2v2 et le Sumo duel.
  Une party de deux (toi et lui) joue le 1v1 contre lui ; une party de deux avec lui attend dans la
  file du 2v2 comme une équipe, contre deux autres joueurs ; une party de quatre avec lui joue le
  2v2 entre elle. Il n'est jamais chef : le chef qui part laisse la party au joueur suivant, et une
  party où il reste sans joueur est dissoute.
- Refusés, avec un message : le classé (« RagoBot never plays ranked: /p kick RagoBot first. »), un
  mode qu'il ne joue pas, une deuxième invitation, `/duel RagoBot` (qui renvoie vers la party).
  Jamais dans une file au hasard.
- Les stats : une partie avec lui compte dans les stats et l'XP des joueurs (victoire, défaite,
  kills, morts), jamais dans l'ELO. Lui n'a pas de stats (mon choix, à confirmer : il n'entre pas
  dans les classements).
- Un seul niveau, sans niveaux (`ragobot.skill` du `config.yml`) : 11 CPS, 88 % de précision,
  portée 3, strafe, w-tap, jump reset, 3 ticks de réaction, 30° de rotation par tick, une pomme
  d'or sous 10 de vie si son kit en a. Ce sont les valeurs du niveau « hard » de l'ancien Combo
  Bot, à confirmer en jeu. Sur le sol, il ne marche jamais de lui-même dans le vide ni hors de la
  plateforme du sumo.
- Comment il joue : à chaque tick, il appuie sur les touches d'un client (avancer, strafe, saut,
  sprint, clics, slot, manger) et le serveur le déplace avec la physique 1.8 d'un joueur ; ses
  coups passent par l'attaque d'un joueur, donc par le knockback de RagoCombatEngine.
- Il quitte le serveur une fois revenu au lobby sans party (`/p kick RagoBot`, la party dissoute,
  ses joueurs partis). En partie, il la joue jusqu'au bout.

À confirmer : les valeurs du skill, le maximum de 10, l'absence de stats pour lui. Deux limites :
plusieurs RagoBot en même temps portent le même nom, donc une commande qui cherche un joueur par
son nom (`/msg RagoBot`) n'en trouve qu'un ; les plugins de ton serveur qui ne sont pas dans le
dépôt n'ont pas été essayés avec lui.

Fichiers : `BotSettings`, `BotBrain`, `BotBody`, `RagoBots` (nouveaux), `Parties`, `PartyBook`,
`QueueController`, `StatsBook`, `RagoLobby`, `LobbySettings`, `DuelRequests`, `LobbyListener`,
`FakeEntities`, `HeadLines`, `Books`, `ReplayViewer`, le `config.yml` (section `ragobot:`) et le
README du lobby. Tests : `BotBrainTest` (visée, rotation, réaction, bords, pomme d'or, portée et
clics), `BotSettingsTest`, `PartyBookTest` (il n'est jamais chef), `StatsBookTest` (il ne compte
que pour les autres) ; en CI, `ragobot-bots.mjs` : un bot l'invite, RagoBot refuse le classé et les
autres modes, gagne un Sumo duel et une manche de Top Bridge 1v1 contre le bot immobile, joue le
Top Bridge 2v2 dans l'équipe de la party, et quitte le serveur après `/p kick` et après une party
dissoute. Vérifié ici : la syntaxe Java, et `BotBrainTest` (10 tests) et `PartyBookTest` (6, sans
celui du bouton qui demande Bukkit) lancés ici, tous passés ; le reste tourne en CI.
À l'install : la section `ragobot:` arrive avec la fusion de ton `config.yml`.

**En CI le 22/11 (75050b8).** Passés : l'invitation et les refus, le Sumo duel (RagoBot pousse le
bot immobile hors de la plateforme : 2 coups, vu par le client du bot avec 82 déplacements, 24
coups d'épée et 2 knockbacks). Échoué : le Top Bridge 1v1. RagoBot frappe une fois 3 secondes
après « Fight! », puis plus rien pendant 60 secondes, sans que personne tombe. Lors du passage
précédent (d0bc040), il avait gagné la manche en poussant le bot jusqu'en bas de l'escalier.

**La cause** (trouvée ici, pas encore vue en CI). L'escalier du Top Bridge fait un bloc de large.
RagoBot ne fait un pas que si le sol continue sous le centre de ses pieds, à 0,6 et à 1,2 bloc
devant lui. Quand il se tient près du bord de l'escalier et que l'autre est un peu de ce côté,
tous ses chemins vers lui sortent de l'escalier : tout droit, avec ou sans strafe. Il reste donc
sur place, et contre un joueur immobile, pour toujours.

**Ce que j'ai simulé.** Une simulation grossière de la physique 1.8, avec la vraie carte du Top
Bridge de bw-demo (calculée par `TopBridge`) et le vrai `BotBrain`, face à un joueur immobile :
- sans la correction, il reste bloqué dans 73 parties sur 200 ;
- avec la correction, il gagne les 200.

C'est une simulation, pas le serveur.

**La correction** (`BotBrain`). Quand aucun de ses chemins ne garde le sol, il essaie les mêmes
touches tournées de 20, 40 puis 60 degrés, d'un côté puis de l'autre. Le premier chemin qui garde
le sol le ramène vers le milieu de l'escalier, et aux ticks suivants il va droit vers l'autre.
Jamais au-delà de 60 degrés : si l'autre est derrière un bord sans chemin vers lui, il reste sur
place, comme avant.

Tests :
- nouveau `BotBrainTest.onAPathOneBlockWideItGoesOnToTheOtherALittleToItsSide`, qui échoue avec
  l'ancien code ;
- les 11 tests de `BotBrainTest` lancés ici, tous passés ;
- en CI, le message d'échec du Top Bridge 1v1 donne maintenant où sont RagoLeader et RagoBot.

**En CI le 22/11 (47fdcd4), avec la correction : tout le test de RagoBot est passé.**
- L'invitation et les refus passent.
- Sumo duel gagné en 2 coups.
- Top Bridge 1v1 : RagoBot pousse le bot immobile dans l'escalier puis le fait tomber, et gagne la
  manche. Vu par le client du bot : 129 déplacements sur 21 blocs, 35 coups d'épée, 3 knockbacks.
- Top Bridge 2v2 : il joue dans l'équipe de la party et frappe un adversaire.
- Il quitte le serveur après `/p kick` et après une party dissoute.

Un seul passage : il montre que RagoBot n'est plus bloqué cette fois, pas qu'il ne le sera jamais.
Reste l'essai en jeu. La correction n'est pas dans la version installée le 22/11 à 14h50 ; elle
est dans celle installée vers 17h30 (e0dadff).

### Outils Owner (7.1, 22/11)

Ma recommandation du 22/11, sans contre-ordre de ta part (« Enchaine ») :

- `/owner reach <3-6|off>` : tes coups portent jusqu'à cette distance (3 pour un joueur normal, 6
  au plus : au-delà, un serveur 1.8 refuse un coup de toute façon). Quand tu cliques dans le vide,
  le premier joueur dans ta visée, au-delà de 3 blocs et dans ta portée, sans bloc plein entre
  vous, prend le coup comme le serveur fait un coup de joueur (règles de la partie, knockback de
  RagoCombatEngine). En dessous de 3, ton client frappe lui-même, rien ne change.
- `/owner scaffold [on|off]` : en sortant d'un bord (au sol l'instant d'avant, donc pas en sautant
  ni en tombant), un bloc de celui que tu tiens se pose sous tes pieds, contre le bloc voisin, comme
  un joueur pose un bloc : la partie peut le refuser (zone, règles), et hors créatif il est pris
  dans ta main. Seulement en survie ou en créatif (jamais au lobby).
- `/owner off` coupe tout ; `/owner` seul dit ce qui est actif.
- Pour le compte `outils-owner.compte` (qRago) seulement, qui doit aussi avoir `rago.admin` (ton
  rang owner, ou op) : la permission cache `/owner` de la complétion des autres. Pour tous les
  autres, opérateurs compris, la réponse est celle de Spigot à une commande inconnue (« Unknown
  command. Type "/help" for help. »).
- Coupés par défaut, et de nouveau quand tu quittes le serveur. Jamais en classé : ils n'y font
  rien (un message te le dit si tu les actives en partie classée).
- Une partie où un outil est actif ne compte pour personne dedans : ni victoire ni défaite, ni
  kills, ni XP, ni points, ni quêtes, ni ELO, ni record d'entraînement (« not counted: an Owner tool
  is on »). Activé en pleine partie, un message te le dit ; le log du serveur note « Game with an
  Owner tool in (mode): not counted in the stats ».

Une différence avec ma recommandation, à confirmer : je proposais l'UUID lu dans le
`usercache.json`. Sur un serveur en mode online (ou derrière BungeeCord), Mojang vérifie le nom :
le nom qRago suffit, sans UUID à copier (`uuid: ""`). En mode offline, n'importe qui peut prendre
un nom : il faut alors l'id de ton compte dans `outils-owner.uuid`, sinon les outils restent coupés
(le log le dit au démarrage). À l'install, je regarderai seulement la ligne `online-mode` de ton
`server.properties` (sans rien y changer) pour savoir lequel des deux.

Fichiers : `OwnerTools`, `OwnerRules` (nouveaux), `LobbyState` (les joueurs de chaque partie, et
celles marquées), `QueueController`, `RagoLobby` (rien n'est compté pour une partie marquée),
`LobbyService` et `Training` de RagoPractice (pas de record), `plugin.yml` (`/owner`), `config.yml`
(section `outils-owner:`), README du lobby. Tests : `OwnerRulesTest` (qui est l'Owner, la portée,
où un coup touche, où le scaffold pose), `OwnerSettingsTest` ; en CI, `owner-bots.mjs` : `/owner`
inconnu pour un joueur et pour un autre opérateur ; Reach 6 en Sumo duel (le coup porte à 6 blocs,
la partie ne compte pas, ni XP ni points) ; en Ranked Sumo, aucun coup et la partie compte ; Scaffold
en Block placement 1v1 (la laine se pose sous qRago qui recule hors de sa plateforme, la partie ne
compte pas). Vérifié ici : la syntaxe Java, et `OwnerRulesTest` (6 tests) lancé ici, passé ; le
reste tourne en CI. À l'install : la section `outils-owner:` arrive avec la fusion de ton
`config.yml`.

**En CI le 22/11 (75050b8) : tous les tests des bots Owner passés.**
- `/owner` est inconnu pour RagoCible et pour RagoOp (opérateur), et qRago voit l'état de ses
  outils.
- `/owner reach 7` est refusé.
- En Sumo duel, Reach 6 activé dans la partie : le coup donné à 6 blocs pousse RagoCible. La
  victoire est là, le récapitulatif compte le coup, et la partie ne donne ni XP ni points.
- En Ranked Sumo : le message « Never in a ranked game », 3 coups dans le vide sans effet, et la
  partie compte.
- En Block placement 1v1 avec Scaffold : qRago recule de 4 blocs hors de sa plateforme sans
  tomber, la laine se pose sous lui (128 laines, puis 125) et la partie ne compte pas.
- Le log du serveur note « Game with an Owner tool in sumo » et « in block-fight-1v1: not counted
  in the stats ».

Au passage suivant (47fdcd4), la vérification automatique du log du serveur est passée aussi :
les deux lignes y sont, et aucune partie classée n'y est notée.

**À l'install du 22/11** : ton serveur est en `online-mode=true` (sans BungeeCord), donc
`uuid: ""` reste tel quel. Il suffit d'être qRago et op (ou rang owner).

### Ta liste du 22/11 (19h51) et la Fun Practice (19h55)

Tes deux messages du 22/11 à 19h51 et 19h55. Je finis d'abord l'étape 2 de RagoBot, puis je les
fais un par un, dans ton ordre.

| Point | Ce que j'ai fait | Statut |
| --- | --- | --- |
| « remettre les pub rago optimizer et client au lobby » | Ton journal du 22/11 à 19h36 montre que les deux images se chargent (6 x 6 blocs), la section `panneaux:` de ton `config.yml` est intacte, le code des panneaux n'a pas changé depuis e0dadff et leur test en CI passe. Pas encore de cause trouvée : je t'ai demandé ce que tu vois à leur place (rien, ou des cadres vides) | EN COURS : j'attends ta réponse |
| « La boussole ca va pas du tout le menu doit etre beaucoup plus grand en haut RANKED PARTY RED ROVER 1v3 puis au centre les 5 PNJ » | La boussole passe de 3 à 6 lignes (`ModeMenu.COMPASS_SIZE`). En haut, dans ton ordre (`boussole: haut:`) : Ranked, Party, Red Rover (le groupe) et 3 Players ; j'ai lu « 1v3 » comme la catégorie 3 Players (à confirmer). Au centre, sur la troisième ligne (`ModeMenu.CENTRE_ROW`), les 5 catégories des PNJ du lobby : Practice, RBW Modes, Duels, Arenas, Fun Practice. « Popular now » reste sur la ligne du bas. La ligne exacte des PNJ et « Popular now » en bas sont mon choix, à confirmer en jeu. Tests : `LobbySettingsTest` (l'ordre du haut), `lobby-bots.mjs` (6 lignes, chaque case, Red Rover et Duels) | IMPLÉMENTÉ, CI en cours |
| « le texte de remercicment séparé en 2 le text, mettre une partit a gauche la suite a droite » | Le texte est coupé entre deux phrases : à gauche (`textremerciement`, 10 lignes) le titre THANK YOU, le premier paragraphe et le début du deuxième, jusqu'à « … high ping on Hypixel is unbearable. » ; à droite (`textremerciement2`, 11 lignes) la suite, depuis « So I made this server… », jusqu'au Discord. Les mots sont ceux que tu as validés, dans le même ordre : seuls les retours à la ligne de la partie de droite changent. Les deux côtés n'ont plus les mêmes lignes (`*merci` retiré). Chaque côté monte maintenant à environ 3 blocs au lieu de 5,6. Tests : `LobbyTextTest` (chaque côté, les mots du texte validé, sous le plafond), `lobby-bots.mjs` (les lignes de chaque côté et leur hauteur) | IMPLÉMENTÉ, CI en cours |
| « Find your sensivity supprime » | — | À FAIRE |
| « Block Aim on va le refaire avec des God method etc je vais t'expliquer en details » | — | BLOQUÉ : j'attends tes détails |
| « Kit editor trié comme le menu de la boussole pour que ce soit plus clair et trier par categorie » | — | À FAIRE |
| « selectionner leaderboard pnj : trier et faire en sorte que ce soit plus clair » | — | À FAIRE |
| « Private Game 2 Mod, Bedwars Normal et FFA Archway » ; Bedwars Normal : le choix de la carte (4s ou 2s), le lancement selon la carte et le nombre de joueurs, le choix des équipes, les réglages de l'admin (vitesse, diamants et émeraudes max, carte destructible…) « comme sur hypixel » | — | À FAIRE : une proposition d'abord pour Bedwars Normal |
| Fun Practice, « Fireballfight : faire en sorte qu'on a deja le double jump de base, rajoute des block infinit mais qui despawn tres vite, ensuite agrandi beaucoup la plateform et fait en sorte que ca ce joue a 3, fait en sorte qu'on ne peux pas frapper l'adversaire » | — | À FAIRE (la durée avant que les blocs disparaissent : à confirmer) |
| Fun Practice, « Spleef : on ne peux pas frapper ladversaire fait en sorte que l'étape du bas soit plus bas pour que le joueur ne puisse pas casser l'étape du haut, fait en sorte qu'on puisse jeter les boul de neige sur l'adversaire et que ca les Hit » | — | À FAIRE |
| Fun Practice, « Fast bedwars : je t'envoie la map » | — | BLOQUÉ : j'attends ta carte |

### Ta liste du 22/11 (18h22)

Ta demande du 22/11 à 18h22, avec ces choix laissés à moi (« fait ce que tu penses etre le
meilleur »). Je les fais un par un, dans cet ordre : les icônes, les skins des PNJ, la direction
artistique, le Daily Chest. Ton feu vert de 18h29 pour installer (« des que tu as finis tu peux push
installer sur mon pc ») : **installé sur ton PC le 22/11 vers 19h35** (commit 3ae7bb7 : les icônes,
les skins, la direction artistique, la salle des remerciements et ta liste de 17h54 ; pas le Daily
Chest, fini après). Serveur arrêté pendant l'installation et pas relancé. Sauvegarde dans
`sauvegarde-avant-maj-3ae7bb7` (les 5 jars, le `config.yml` du lobby, ton `points.yml`). La fusion
du `config.yml` du lobby a eu un conflit, dans `classements:` : tes murs de classement sont gardés
tels quels, seuls leurs titres passent du rouge à l'or (3 lignes `nom:`), comme les autres titres.
Ton `points.yml` : `ragoclient`, `ragooptimizer`, `textremerciement` déplacés, `textremerciement2`
ajouté (les places de la salle ci-dessous).

| Point | Ce que j'ai fait | Statut |
| --- | --- | --- |
| « assure toi que chaque mod qui se joue dans plusieurs mods (ffa duel practice) ont le meme item/icon dans tout les pnj et menu » | Toutes les icônes des modes viennent de `icone:` dans le `config.yml` du lobby (boussole, menus des PNJ, éditeur de kit, statistiques, titres, Popular now) ; le menu des records du practice a les siennes (`RecordCatalog`). J'ai relevé chaque jeu joué dans plusieurs modes. Déjà le même objet partout : Bed Fight (lit), Top Bridge (échelle), Bridge Fight (laine), Block Placement (grès), Sumo (slime), Boxing (épée en diamant), Block Edit (cisailles), KB displace (bâton), Fireball (boule de feu), avec leurs versions Ranked, Red Rover, FFA et event. Changés : **Ranked Bedwars 2v2, 3v3, 4v4** prennent le lit, comme le 1v1 et le groupe (avant : épée, plastron, bloc de diamant) ; **Lobby Fight FFA** prend la perle, comme le Lobby Fight 1v1 (avant : la boule de feu, déjà celle des modes Fireball) ; **Bow duel** prend l'arc, comme Bow training et Aim trainer (avant : la flèche) ; dans le menu des records, **Bow training** prend l'arc et **Block extend** la laine, comme dans les menus du lobby ; dans le menu de la première venue, « Fight now » prend l'épée en pierre du FFA qu'il ouvre (avant : l'épée en diamant). Gardés différents, car ce sont des jeux différents dans un même groupe : Sky Rise et Sky Rise Resources, First rush et First rush full map, Block clutch et sa version top fight, Top BedFight. Test `LobbySettingsTest.oneGameKeepsOneItemInAllItsModes` (12 jeux), `GettingStartedTest` ; le bot du lobby lit l'épée en pierre dans le menu de la première venue ; le site reprend les icônes (`game.json`). Remarque, non changé : dans la boussole, Arenas et Ranked ont tous deux l'épée en diamant | TESTÉ en CI (6776a1a : tests unitaires, bot du lobby en Java 8 ; en Java 21, Java ne s'est pas téléchargé) |
| « La revue des skins des PNJ : a toi de selectionner de meilleur skin et d'enlever les items des mains des joueurs » | **Les mains** : plus aucun PNJ ne tient d'objet (avant : l'icône de la catégorie pour Practice, RBW Modes, Duels, Arenas, Fun Practice, l'émeraude du Shop, le livre des stats, le panneau des leaderboards, le livre et la plume des quêtes) ; le serveur n'envoie plus d'objet en main (`FakePlayer`, `LobbyNpcs`, `StatsDisplays`, `QuestsNpc`). **Les skins** : ceux d'avant étaient plats, d'une seule couche. Mon choix : de nouveaux skins originaux, dessinés pour Rago, avec la deuxième couche (casquette, capuche, casque, chapeau) et une lumière venant du haut ; chacun porte la couleur de sa catégorie, celle de son nom au-dessus de lui, pour que le joueur relie le PNJ à son menu : Practice, le coach turquoise (casquette, survêtement, sifflet) ; RBW Modes, le joueur de Bed Wars doré (cuir doré, jambières de diamant, le lit sur la poitrine) ; Duels, le duelliste vert (tunique, mailles, épées croisées) ; Arenas, le gladiateur (casque de bronze, petite crête) ; Fun Practice, qui était en Steve, un skin à lui en violet (capuche, boule de neige) ; Shop, le marchand vert (tablier, émeraude) ; stats et leaderboards, l'analyste en bleu nuit et or (cravate, lunettes) ; quêtes, l'explorateur (chapeau, veste de cuir, boussole dorée). Mêmes noms de fichiers dans le jar (plus `fun`) : ton `config.yml` n'a rien à changer, sauf `skin: fun` sous `prive` (la fusion de l'install l'ajoute). Au démarrage, le lobby fait signer chaque PNG changé par MineSkin, une fois (en attendant, Steve). `tools/npc-skins/npc_skins.py` les redessine ; le site montre les mêmes. Tests `MineSkinTest` (chaque PNJ a son PNG de 64 x 64 dans le jar), `LobbySettingsTest` ; le bot du lobby vérifie que le PNJ Duels et le PNJ de stats ont les mains vides. Vus en image ici, pas encore en jeu | TESTÉ en CI (331b14c et 3ae7bb7 : tests unitaires, bot du lobby en Java 8) ; installé (3ae7bb7) |
| « La direction artistique (moins de rouge) : Prend la meillleur palette qui reste dans la DA mais sans en abuser » | Ce que j'ai lu : le 12/11, le serveur est passé du jaune (logo jaune compris) au rouge du logo, et depuis tout ce qui se clique, se tape ou s'ouvre était rouge, comme les erreurs (« Click to buy » et « Not enough Rago Points » de la même couleur). Mon choix : **le rouge reste la signature** (le nom RAGO PRACTICE, le « RAGO » du préfixe, le logo, la catégorie Arenas, les erreurs, l'alerte du spectateur que tu as demandée en rouge) ; **ce qu'on peut faire passe en jaune** (« Click to … » dans tous les menus, du lobby, du practice et des Bed Wars, la commande à taper, le lien, l'adresse rago.gg du sidebar et du Tab) : c'est le jaune des clics de Minecraft, il ne se confond plus avec une erreur ; **les titres en or** (murs de classement, règlement, « NEW » de la liste des serveurs, le Starter Pack du PNJ Shop) ; **la party et les amis en bleu** (leurs traits, « Party » du chat de party), le bleu de la catégorie Party. Le logo ne change pas. `Brand` (`ACCENT`, `ADDRESS`, `SOCIAL`), `Parties`, `Social`, `LobbySettings`, le `config.yml` fourni (les astuces, `liens:`, le Tab, la liste des serveurs, les murs, les produits, le texte de remerciement), `Training`, `RecordsMenu`, `Events`, `BridgeArena`, `KitMenu`, `FfaSpawnMenu` ; README du lobby, « Les couleurs du serveur ». Tests `NetworkFeaturesTest` (une seule fois du rouge dans chaque astuce, le RAGO, et la commande ou le lien en jaune), `StatsDisplaysTest`, `ProductNpcTest`, `LobbyTextTest` ; le bot du lobby lit le Daily Streak en jaune. Pour revenir en arrière : `ACCENT` dans `Brand` et les `&e` du config. Pas encore vu en jeu. Deux tests à corriger après coup (3ae7bb7) : `GameSidebarTest` attendait encore le temps de file en rouge ; le test d'accessibilité du site mesurait /modes pendant qu'il remontait en défilement doux (une fois sur quatre sur téléphone), `scrollThrough` défile maintenant d'un coup | TESTÉ en CI (3ae7bb7 : tests unitaires, bot du lobby en Java 8, site) ; installé (3ae7bb7) |
| « Le Daily Chest : l'animation à suspense et les probabilités […] je veux quelque chose d'impressionnant » | Avant : un clic sur le coffre, `/daily` ou le profil donnait 100 Rago Points, plus 25 par jour d'affilée, avec quelques particules. **L'animation** : un menu « Daily Chest » s'ouvre ; au milieu, une bande de récompenses défile entre des vitres qui changent de couleur à chaque pas, sur une note qui monte, de plus en plus lentement (32 pas, environ 6 secondes), et s'arrête sous l'entonnoir (qui donne les chances de chaque rareté) ; un court silence, puis la récompense s'allume dans la couleur de sa rareté, le reste de la bande s'éteint, les vitres prennent sa couleur (elles clignotent pour Epic et Legendary), avec le son de sa rareté (jusqu'au cri du dragon pour Legendary) ; le menu se ferme sur un grand titre (« EPIC! » et la récompense). Au coffre du spawn : le couvercle s'ouvre pendant le tirage, des glyphes d'enchantement tournent autour, puis étincelles et feu d'artifice bleu (Rare), violet (Epic) ou trois dorés (Legendary) ; un Legendary est annoncé à tout le lobby. Fermer le menu avant la fin donne la récompense tout de suite (de même si le joueur part ou si le serveur s'arrête) ; le jour est noté dès l'ouverture. **Les probabilités** (mon choix, `coffre-du-jour.recompenses:` du `config.yml`, modifiables) : Common 55 % (100–200 Rago Points 30 %, 100–500 XP 25 %), Rare 28 % (200–350 points 15 %, 500–1 000 XP 13 %), Epic 12 % (350–500 points 6 %, 1 000–2 000 XP 6 %), Legendary 5 % (un cosmétique de la boutique, environ un tous les 20 jours). **Jamais de doublon** : le cosmétique est tiré parmi ceux que le joueur n'a pas (ni achetés, ni par son rang), jamais un de PREMIUM ; s'il les a tous, 500 Rago Points à la place. L'XP est donnée sans les boosters. La série de jours reste : +25 Rago Points par jour d'affilée après le premier, jusqu'à 7 jours (`quotidien-serie`) ; `rago-points.quotidien` ne sert plus. `/daily`, le profil et la boutique ouvrent le même tirage. `DailyRewards`, `DailyChest`, `Profiles`, `StatsService` ; README du lobby ; le site dit « 100 to 500 in the Daily Chest each day ». Tests `DailyRewardsTest` (le config fourni = celui du plugin, les chances de chaque rareté, 200 000 tirages qui suivent les chances et restent dans leurs montants, jamais un cosmétique possédé, la durée du tirage, les erreurs du config), `NetworkFeaturesTest` (la série) ; le bot du lobby tape `/daily` : le menu, les chances sur l'entonnoir, la bande qui bouge, les notes, la récompense au centre et dans le chat, le titre, puis un second `/daily` refusé | TESTÉ en CI (ea3bbe9 : tests unitaires ; le bot du lobby : `/daily`, la bande a défilé, 64 notes, arrêtée sur une Rare, un second `/daily` refusé ; 4 jobs n'ont pas pu télécharger Java) ; pas encore installé : ton serveur de test tournait, j'installe quand tu l'as arrêté |

### La salle des remerciements (22/11, 17h56)

Ta demande du 22/11 à 17h56. J'ai lu la salle dans ton monde lobby (`mondes/lobby`, fichiers de
région et `points.yml`, sans rien changer) : les murs de laine blanche en x = -16 et x = 16, le sol
de dalles de sapin en y = 68, le plafond de quartz en y = 75, l'allée de tapis rouge en travers
(z = -33 à -31). Les PNJ des côtés flottent : leur point est en y = 70, leur bloc y est posé, et ils
se tiennent dessus en y = 71, deux blocs et demi au-dessus du sol. Rien n'est encore installé : tout
part à la prochaine installation, quand tu le diras.

| Point | Ce que j'ai fait | Statut |
| --- | --- | --- |
| « supprime woryes et directement » | Les PNJ `jncl` (directement) et `nativ` (woryes) sortent du `config.yml` fourni ; ils y restent en commentaire, et leurs points dans ton `points.yml`. Le rang Mod ne change pas. Tests `PersonNpcTest` et `LobbySettingsTest` (17 PNJ) | TESTÉ en CI (tests unitaires, b6a837f) |
| « le pnj rago client et rago optimizer recule les de 1 block derriere eux, et descend les de 2 blocks en bas » | Ils regardent le spawn, donc derrière eux, c'est vers le fond de la salle (z plus petit). Un bloc derrière et deux plus bas, il y a déjà un bloc de diamant dans le sol, de chaque côté, en (-11, 68, -37) et (11, 68, -37). À l'installation, je change seulement leurs deux points dans ton `points.yml` : `ragoclient` en (-10.5, 69, -36.5) et `ragooptimizer` en (11.5, 69, -36.5). Le plugin voit le diamant sous leurs pieds et les pose dessus, sans ajouter de bloc. Le code ne change pas | IMPLÉMENTÉ : points changés dans ton `points.yml` le 22/11 (installation 3ae7bb7, l'ancien fichier dans `sauvegarde-avant-maj-3ae7bb7`) ; à voir en jeu |
| « le message de remerciment change le de place met la sur les coté de la salle » | Le même texte des deux côtés de la salle : à gauche sur `textremerciement`, à droite sur le nouveau point `textremerciement2`, avec les mêmes lignes (un texte changé à gauche change à droite). À l'installation : `textremerciement` en (-13.5, 69, -31.5) et `textremerciement2` en (14.5, 69, -31.5), au bout de l'allée de tapis rouge, à trois blocs des murs, symétriques. Ces deux places sont mon choix, à confirmer en jeu (`/lobby point textremerciement` les change). Tests `LobbyTextTest` ; le bot du lobby lit les deux textes | TESTÉ en CI pour le texte (bot du lobby, b6a837f) ; points changés dans ton `points.yml` le 22/11 (installation 3ae7bb7) ; à voir en jeu |

**Quatre blocs à casser à la main** : le plugin ne retire pas un bloc qu'il a posé sous un PNJ. Il
restera, flottant en y = 70, l'andésite polie de directement et woryes, en (11, 70, -28) et
(-11, 70, -28), et le diamant de l'ancienne place de Rago Client et Rago Optimizer, en
(-11, 70, -36) et (11, 70, -36). Je ne touche pas aux mondes.

### Ta liste du 22/11 (17h54)

Ta liste du 22/11 à 17h54, point par point. « TESTÉ » veut dire testé en CI, par les tests et les
bots nommés dans la ligne : rien n'est encore joué en jeu par un joueur. Installé sur ton serveur
le 22/11 (3ae7bb7).

| Point | Ce que j'ai fait | Statut |
| --- | --- | --- |
| « /ping joueur pour voir le ping du joueur comme sur minemen » | `/ping` donne ton ping, `/ping <joueur>` celui d'un joueur en ligne que tu vois (« qRago's ping: 42 ms ») ; en vert sous 80 ms, en jaune sous 150, en rouge au-delà, comme dans le scoreboard des parties ; « ? » si le serveur ne le connaît pas encore. Un joueur nické se trouve par son nick ; par son vrai nom, seulement pour le staff ou lui-même. `NetworkCommands.ping`, `plugin.yml`, test `PingCommandTest` | TESTÉ en CI (987fd40, tests unitaires) |
| « a chaque win chaque round gagné petit bruit de ding » | Le ding est le son de l'orbe d'XP (`ORB_PICKUP`), pour les gagnants seulement : à chaque point ou manche gagnés et à la victoire (qui garde aussi son son de victoire), dans tous les modes du practice et des Bed Wars : Sumo, duels, Bridge, Block Placement, Red Rover, events, Top Bridge, Lobby Fight, Top Bed Fight, Bed Wars, Bed Break, Invis, Capture the Flag (à chaque drapeau ramené). Le bot du lobby compte les dings du Sumo : un par point, deux au dernier (le point et la victoire) | TESTÉ en CI (987fd40, test du lobby en Java 8 ; en Java 21, Java ne s'est pas téléchargé, il repasse au commit suivant) |
| « quand un joueur spec la game faire un bruit de ding et mettre le message en rouge en evidence » | « RagoVue01 is now spectating your game. » est en rouge et en gras, avec le ding, pour chaque joueur de la partie, au practice et aux Bed Wars. `Spectators`, `Watchers`, tests `SpectatorsTest` et `WatchersTest` ; le bot des spectateurs vérifie la couleur et entend les dings | TESTÉ en CI (987fd40, tests unitaires et bot des spectateurs) |
| « A la fin des partie red rover faire le top 3 des joueurs » | À la fin d'une partie Red Rover (Sumo, Block Edit, Block Placement, Top Bridge), tout le monde lit « Top 3 », puis « 1st - <joueur> - 3 1v1 won », 2nd et 3rd. Le classement : le champion d'abord, puis le plus de 1v1 gagnés ; à égalité, celui qui a tenu le plus longtemps, puis l'ordre de départ. `RedRoverRotation.top`, `RedRoverTurns`, `RedRoverTopBridgeGame`, tests `RedRoverRotationTest`, `RedRoverTest`, `RedRoverTopBridgeTest` ; le bot du Red Rover Sumo lit les trois lignes | TESTÉ en CI (987fd40, tests unitaires et bot du Red Rover Sumo) |
| « menu de la boussole a refaire comme avant : trier plus clair center, mettre redrover party a 3 ranked en haut » | Ton choix sur la carte (22/11) : « Les 4 en haut, Popular now en bas ». Trois lignes, centrées, une case entre chaque objet : en haut Red Rover, Party, 3 Players, Ranked ; au milieu Practice, RBW Modes, Duels (au centre), Arenas, Fun Practice ; en bas « Popular now ». Red Rover ouvre ses quatre modes, et son bouton retour revient à la boussole. Réglable : `boussole: haut:` dans le `config.yml` du lobby ; sans lui, la boussole d'avant. `ModeMenu`, `LobbySettings`, tests `LayoutTest` et `LobbySettingsTest` ; le bot du lobby vérifie les deux lignes, Red Rover et son retour, puis Duels au slot 13 | TESTÉ en CI (b6a837f : tests unitaires, bot du lobby en Java 8 et 21) |
| « rago bot a améliorer et a intégrer dans tout les mods et donc rajouter un skill pour qu'il puisse et sache poser des block, block placement les joueur, casser des block, block edit et bridge » | Proposition envoyée : trois savoir-faire (poser un bloc, casser un bloc, faire un pont), comme le ferait un joueur, dans les règles du jeu ; les modes un par un, chacun vérifié par un bot en CI avant l'install : 1. Boxing et Lobby Fight 1v1, 2. Block Placement et son Red Rover, 3. Block Edit et son Red Rover, 4. Bridge Fight, 5. Bed Fight et Top Bed Fight, 6. les autres, une proposition par mode. Jamais en Ranked ni en file. « Améliorer » son combat : à toi de dire ce qui ne va pas quand tu l'auras essayé. Ton choix sur la carte (22/11) : « Oui, dans cet ordre ». **Étape 1** : Boxing et Lobby Fight 1v1 ajoutés à `ragobot.modes` (et aux modes par défaut, `BotSettings`), sans nouveau savoir-faire : il s'y bat comme en Sumo et en Top Bridge. Test `BotSettingsTest` ; en CI, `ragobot-bots.mjs` : RagoBot gagne un Boxing 10 - 0 (premier à 10 coups pour le test) et une manche de Lobby Fight 1v1 contre un bot immobile, sur l'île du milieu de la carte démo, dont un opérateur fait la salle d'attente (`/bw lobbyfight`), comme sur Archway FFA. Sur ton serveur, le Lobby Fight 1v1 se joue sur Archway FFA, qui a sa salle d'attente. En CI (6776a1a), RagoBot gagne le Boxing 10 - 0 et le Lobby Fight 1v1 3 - 0 (le test lisait mal la ligne d'accueil du Lobby Fight, « you are Red. », corrigé). **Étape 2** (ma proposition validée : « poser pour se protéger et gêner l'autre, casser ses blocs ») : Block placement 1v1, 2v2 et Red Rover Block Placement ajoutés à `ragobot.modes`. J'ai lu tes 3 cartes de Block Placement (sol continu entre les départs, le vide autour, des collines à marches d'un bloc). Dans ces trois modes seulement (`BotSettings.BLOCK_MODES`), RagoBot pose et casse par les mêmes paquets qu'un client : le serveur les accepte ou les refuse comme pour un joueur (portée, blocs posés dans la manche, anti full box). **Le clutch** : au-dessus du vide (rien sur 10 blocs sous ses pieds), il prend un bloc de son kit (la laine, sinon l'end stone), tourne la tête vers le côté d'un bloc voisin, à 4,5 blocs au plus de ses yeux, et le pose sous lui. **Casser ses blocs** : l'autre debout sur un seul bloc au-dessus du vide, de laine (aux cisailles) ou d'end stone (à la pioche), hors de ses coups mais à portée : il s'arrête, le regarde et le casse ; un bloc que la partie refuse (de la carte) n'est plus essayé. **Les marches** : il saute une marche d'un bloc sur son chemin (les collines de tes cartes). En Red Rover, il se bat contre celui de son 1v1 et regarde quand il attend. Sa vitesse de pose : `ragobot.skill.blocs-par-seconde` (6, à confirmer en jeu). Pas de pont : c'est l'étape 4. Si le serveur refuse ses paquets de blocs (une autre version de PandaSpigot), il ne touche plus aux blocs, se bat quand même, et une erreur est notée dans le log. Tests `BotBrainTest` (17 : le clutch, le bloc sous l'autre, rien sur un bloc de la carte, les marches, le regard comme celui d'un client), `BotSettingsTest` ; en CI, `ragobot-bots.mjs`, sur les deux plateformes du mode sans carte (16 blocs de vide entre elles), RagoOp (opérateur) déplace les joueurs avec `/tp` : le clutch au-dessus du vide, la laine sous le bot cassée sans un coup (+10 pour Blue, le bot perd), puis un 1v1 de Red Rover Block Placement gagné. Le Block placement 2v2 n'a pas de test à lui en CI (le même code que le 1v1) | Étape 1 : TESTÉ en CI (6776a1a, `ragobot-bots.mjs`) ; étape 2 : IMPLÉMENTÉ ; en CI (3fe54af), le Block placement 1v1 passe (le clutch au-dessus du vide, puis la laine sous RagoLeader cassée sans un coup : +10 pour Blue, RagoBot gagne) ; le Red Rover Block Placement a démarré, mais mon test attendait un mauvais message de départ (une party de trois rejoint la file du mode et la remplit aussitôt, ce n'est pas une partie privée) : test corrigé, CI en cours ; étapes 3 à 6 : À FAIRE, une à la fois |

### Installation du 22/11 en fin d'après-midi (e0dadff)

Ta demande du 22/11 à 16h53 : « Install et push sur le server ».

**Installé** sur ton serveur de test le 22/11, vers 17h30 heure de Paris, serveur arrêté :
- les 5 plugins du commit e0dadff, empreinte du zip vérifiée ;
- `rago-lobby`, `rago-practice` et `rago-bedwars` changent, `rago-combat-engine` et
  `rago-build-tools` sont identiques.

Ce que contient cette version, depuis 75050b8 :
- la correction de RagoBot en Top Bridge (47fdcd4) ;
- ta liste du 22/11 à 16h22 (ci-dessous) : la boussole avec ses catégories, les menus des PNJ sans
  rien en haut, le kit FFA rendu après un kill, l'anti full box et les blocs récupérés en Block
  Placement, le PNJ Fun Practice sur le point `prive`.

Seul le `config.yml` du lobby change. Il est fusionné avec tes réglages, sans conflit : 15 lignes
ajoutées, 12 retirées, comme dans le jar. Il n'y a plus de PNJ sur `funmod2`, `prive` est le PNJ
Fun Practice et `partieprivee` est en commentaire. Les `config.yml` du practice et des Bed Wars
ne changent pas.

J'ai seulement lu les `points.yml` du lobby, sans les changer. Ils ont encore les points `prive` et
`funmod2`, et pas de point `panneau-rago-client` ni `panneau-rago-optimizer` : les deux panneaux
restent au `lieu` de ton `config.yml`. Rien d'autre n'est touché : ni `server.properties`, ni
l'icône, ni les mondes. L'ancien état est dans `sauvegarde-avant-maj-e0dadff`.

Rien n'est encore joué en jeu.

### Ta liste du 22/11 (16h22)

Ta liste du 22/11 à 16h22, point par point. Tout est codé, avec ses tests, passé en CI au commit
e0dadff et installé sur ton serveur (voir « Installation du 22/11 en fin d'après-midi »). « TESTÉ »
veut dire testé en CI, par les tests et les bots nommés dans la ligne : rien n'est encore joué en jeu
par un joueur.

| Point | Ce que j'ai fait | Statut |
| --- | --- | --- |
| « FFA : redonner le kit complet après un kill » | Dans le FFA (`ffa`, Archway FFA), un kill soigne et remet tout le kit : chaque objet au nombre du kit (boules de feu, perle, laine, pommes), sur ses piles ou à sa place dans le kit, celui de l'éditeur de kit ou à défaut le kit de combat. Lobby Fight FFA (les pommes seules) et Top Bridge FFA ne changent pas. `FfaGame.refillAll`, test `aKillInTheFfaBringsTheWholeKitBack` (test seul, aucun bot ne joue le FFA) | TESTÉ en CI (e0dadff) |
| « Block Placement 1v1 : Anti Full boxe comme en bedfight » | Je n'ai trouvé aucune règle anti full box dans notre Bed Fight ; ton choix sur la carte : « Interdire de s'enfermer ». Un bloc qui enfermerait complètement un joueur, celui qui le pose ou un autre (ses quatre côtés aux pieds et à la tête, et au-dessus de sa tête ; le sol ne compte pas), est refusé : « You can't box yourself in! » ou « You can't box a player in! ». Pour tout le Block Placement : 1v1, 2v2, Ranked et Red Rover (le même jeu) ; pas l'arène de block placement. `FullBox`, `BlockFight.canPlace`, test `FullBoxTest` et le bot de l'arène : il s'enferme sur sa plateforme, les quatre côtés aux pieds et à la tête, et le bloc au-dessus de sa tête est refusé avec « You can't box yourself in! » | TESTÉ en CI (e0dadff) |
| « Récupérer les blocks quand on les casse » | En Block Placement, un bloc cassé tombe par terre, comme au Fireball Fight : la laine de sa couleur, l'end stone, l'échelle, à ramasser par n'importe qui. `BlockFight.dropOf`, test et bot de l'arène : la laine cassée tombe par terre, une laine | TESTÉ en CI (e0dadff) |
| « remettre la boussole comme avant avec les différentes catégories » | La boussole d'avant 5.9 : « Popular now » en haut, les 8 catégories côte à côte sur la ligne du milieu (Party et Ranked comprises, slots 9 à 16) ; un clic ouvre les jeux de la catégorie, avec « Back to the categories » en bas. `ModeMenu`, `LayoutTest`, bot du lobby : la boussole à 3 lignes, Duels au slot 11, son menu à lui | TESTÉ en CI (15446cb) |
| « enlever les items en haut dans les menus des PNJ, remettre comme avant » | Le menu d'une catégorie, depuis son PNJ, la boussole ou `/play`, n'a plus rien sur sa ligne du haut (ni les catégories, ni « Popular now ») : seulement ses jeux. Le bot du lobby le vérifie pour le PNJ Duels et pour Duels depuis la boussole | TESTÉ en CI (15446cb) |
| « enlever le PNJ Fun practice et le mettre à la place de private game » | Dans le `config.yml` fourni, le PNJ Fun Practice est sur le point `prive`, celui du PNJ Private game, sur le quartz déjà là ; il n'y a plus de PNJ Private game (en commentaire, `partieprivee`, pour un autre point) ni de PNJ sur `funmod2`. Ton `points.yml` n'est pas touché. Le bloc de quartz que le plugin a pu poser sous l'ancien PNJ, au point `funmod2`, reste : à casser à la main si tu veux. À l'installation, ton `points.yml` du lobby a bien encore les points `prive` et `funmod2`. Tests `LobbySettingsTest`, `MineSkinTest`, `PedestalTest` | TESTÉ en CI (e0dadff) |
| « private game le mettre dans la catégorie rbw mod comme mod » | Déjà le cas : RBW Modes a l'objet « Private games », qui ouvre Private game et Private top bridge. Je n'ai rien changé, le test le vérifie | TESTÉ en CI (e0dadff, déjà là) |

### Installation du 22/11 après-midi (75050b8)

Ta demande du 22/11 à 14h34 : « Release Owner Tool et install la nouvelle version sur mon pc ».

**Installé** sur ton serveur de test le 22/11, vers 14h50 heure de Paris, serveur arrêté :
- les 5 plugins du commit 75050b8, empreinte du zip vérifiée ;
- `rago-lobby`, `rago-practice` et `rago-bedwars` changent, `rago-combat-engine` et
  `rago-build-tools` sont identiques.

Ce que contient cette version, depuis aa724b7 :
- les menus Solo / Party / Ranked (3.7) ;
- les PNJ Rago Client et Rago Optimizer et leurs panneaux (5.6A), jncl et nativ (5.6C) ;
- le texte de remerciement ;
- le Snowball Spleef à deux étages (rayon 18) et le Parkour Duel ;
- le Combo Bot retiré des menus ;
- RagoBot et les outils Owner.

Les trois `config.yml` sont fusionnés avec tes réglages :
- lobby : 269 lignes ajoutées, 39 retirées. Le seul conflit portait sur un commentaire, celui des
  PNJ de la salle des remerciements (3 lignes de ton fichier contre les 5 du nouveau) : j'ai pris
  le nouveau, aucun réglage n'est touché ;
- practice : 18 ajoutées, 11 retirées, dont `rayon: 18`, `etages: 2` et `parkour-duel:` ;
- bedwars : 7 ajoutées, 1 retirée.

Dans ton `config.yml` du lobby, j'ai aussi déplacé les panneaux `rago-client` et `rago-optimizer`
contre le mur. Ils font 6 x 6, avec `lieu: "-11 69 -39 sud"` et `"11 69 -39 sud"` : seules 6 lignes
changent, et `points.yml` n'a pas de point qui les remplace.

J'ai lu seulement la ligne `online-mode` de `server.properties` et la ligne `bungeecord` de
`spigot.yml`, sans les changer. Rien d'autre n'est touché : ni `server.properties`, ni l'icône, ni
les mondes. L'ancien état est dans `sauvegarde-avant-maj-75050b8`.

Rien n'est encore joué en jeu. La correction de RagoBot en Top Bridge (journal « RagoBot ») n'est
pas dans cette version.

### Installation du 22/11 au matin (aa724b7)

Ta demande du 22/11 à 9h49 : « Install tout sur mon [serveur] ».

**Installé** sur ton serveur de test le 22/11 (vers 9h55, heure de Paris, serveur arrêté) : les
5 plugins du commit aa724b7 (empreinte du zip vérifiée), donc le Bed Fight 1v2 avec son stuff
amélioré, CTF The Bridge, CTF château contre château et Fast Bedwars. `rago-lobby` et
`rago-bedwars` changent, les 3 autres jars sont identiques. Le `config.yml` du lobby est fusionné
avec tes réglages, sans conflit : 133 lignes ajoutées, 6 retirées (des descriptions et des listes
qui gagnent les nouveaux modes), comme dans le jar ; les 10 nouvelles entrées sont les groupes
`ctf-bridge`, `ctf-castle`, `fast-bedwars` et les modes `bed-fight-1v2`, `ctf-bridge-1v1`,
`ctf-bridge-2v2`, `ctf-castle-2v2`, `ctf-castle-4v4`, `fast-bedwars-1v1`, `fast-bedwars-2v2`. Le
`config.yml` des Bed Wars était celui installé le 22/11 à 1h10, sans changement de ta part : il
devient celui du jar (les modes, `forge.modes`, `stuff-ameliore:`, `ctf:` et `fast-bedwars:`).
Rien d'autre n'est touché (ni `server.properties`, ni l'icône, ni les mondes) ; l'ancien état est
dans `sauvegarde-avant-maj-aa724b7`. Les maps CtfBridge et CtfCastle n'existent pas encore : le
plugin les construit au prochain démarrage. Rien n'est encore joué en jeu.

### Étape 7 : Bot IA, l'étude technique (22/11)

Question 13 du document des propositions : l'étude seule, sans code. Document « Étape 7 : étude du
bot IA » (Claude Docs). En bref : un bot qui joue comme un joueur est faisable s'il est un vrai
joueur pour le serveur, un faux joueur créé par le plugin sans client derrière, qui entre dans les
files et les équipes sans réécrire les modes ; le combat existe déjà (`ComboAi`, le Combo Bot et
ses 4 niveaux), restent le déplacement, les blocs et les décisions. Premier objectif proposé : la
quatrième place d'un Top Bridge 2v2, en cinq étapes testées chacune par les bots en CI, puis une
mesure du temps par tick avec 1, 4 et 10 bots. Le coût est estimé, pas mesuré. **6 questions à
trancher** (la voie, l'objectif, quand un bot joue, son nom et son skin, les stats, les niveaux) :
rien n'est codé avant tes réponses.
