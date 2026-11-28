---
type: historique
tags: [historique, pr, github]
maj: 2026-11-28
---
# Pull requests

Toutes les PR du dépôt `kaliso-dev/network-server-rago` au 28/11/2026. État actuel des branches : [[Branches et PR]]. Retour à [[Chronologie du projet]].

## Ouvertes
| PR | Titre | Branche | Note |
|---|---|---|---|
| **#47** | Reset des arènes, spectateurs, kit Bed Fight, stats fiables, et checklist de la sortie publique | `claude/project-thread-wi9w0x` | ouverte le 21/11 ; **tout le travail sur le jeu depuis le 21/11** (191 commits au 28/11, dont les PR #54 à #58, #60 et #61) ; sans conflit avec `main` ; la fusion attend Sofiene ou Abel |
| #53 | Vault : réponses d'Abel du 26/11, PR #48 et #10 fermées, accord d'Abel | `claude/vault-abel-26-11` | ouverte le 26/11 ; `docs/vault/` et `CLAUDE.md` seulement ; la fusion attend Abel ou Sofiene |
| #59 | Vault : PR #54 à #57, installation du 28/11, Block Method | `claude/vault-28-11` | ouverte le 28/11, base `claude/vault-abel-26-11` : à fusionner après la #53 ; `docs/vault/` seulement |

## Fusionnées dans la branche de la PR #47 (`claude/project-thread-wi9w0x`)
| PR | Fusion | Titre | Branche | Contenu |
|---|---|---|---|---|
| #54 | 27/11, 21h33 (`1bf902d`) | Lobby (liste d'Abel du 27/11) et 2012 Bedwars | `claude/menus-lobby-2012-bedwars` | boussole de 5 lignes, joueurs en partie sous chaque objet, icônes en couleur, ordre d'Abel, Bridge fight FFA retiré des Arenas, `regard: point`, textes de la salle des remerciements et de RagoBot ; 2012 Bedwars à la place de 2017 Bedwars ; CI 14/14 ; installée le 27/11 au soir |
| #55 | 28/11, vers 10h32 (`fefff6e`) | Block edit: barrier blocks in the place of the glass case | `claude/block-edit-barrier` | la cage du Block edit en blocs barrière (entraînement, Block edit 1v1, Red Rover Block Edit) ; CI 14/14 ; installée le 28/11 |
| #56 | 28/11, vers 10h32 (`843c0d6`) | Hypixel Bow Practice (new training) | `claude/hypixel-bow-practice` | entraînement `bow-practice` sur la map BowPractice ; CI 14/14 ; installée le 28/11 |
| #57 | 28/11, vers 10h32 (`03beb92`) | Block in: the RBW level | `claude/block-in-rbw` | niveau « RBW defense » du Block in sur la map « Block In Practice RBW » ; CI 14/14 ; installée le 28/11 |
| #58 | 28/11, 11h28 (`d050af8`) | Block Pattern Practice: the first ten techniques of Block Method | `claude/block-pattern-practice` | entraînement `block-pattern-practice`, correction de l'ordre d'enregistrement des modes de RagoPractice, test des bots `pattern-bots.mjs` ; CI 14/14 (après le correctif `69c78ac` du script de test) ; installée le 28/11 vers 11h29 |
| #60 | 28/11, 15h30 (`10eaad8`) | READMEs: 2012 Bedwars, Hypixel Bow Practice, Block Pattern Practice | `claude/readme-27-11` | README de rago-practice (Hypixel Bow Practice, Block Pattern Practice, niveau RBW du Block in) et de rago-bedwars (2012 Bedwars) ; texte seulement, rien à installer |
| #61 | 28/11, 16h28 (`3316b1f`) | Block Method 1v1: Bed Fight as a practice against a weaker RagoBot | `claude/block-method-1v1` | mode `block-method-1v1` (Duels, RagoBedwars, `BlockMethodGame`, `BlockMethodMenu`), RagoBot `SOLO` (`ragobot.seul`, pont et full box dans `BotBrain`), 4 maps d'Abel, bot de CI `block-method-bots.mjs` ; 21 fichiers, +1 503 / −43 ; CI 14/14 ; installée le 28/11 vers 16h30 |

## Fermées sans fusion
**#48** « Events: red dye to leave or cancel, clearer announcement every 20 s » (`claude/project-thread-5mjx75`, ouverte le 21/11) : fermée le 26/11, dépassée par #47 qui fait la même chose sur le code récent · **#10** « CI : vérifier aussi le serveur sous Java 25 » (`claude/ci-java-25`, ouverte le 11/11) : fermée le 26/11, pas utile pour l'instant, à refaire si la production tourne sous Java 25 · #3, #5, #6, #7 (leur contenu est arrivé dans `main` par la ligne principale, fusions directes du 11/11 : « Merge /kb sparring », « Merge the KB recorder mod and kb-analyzer », « Merge the measured hypixel-approx-v1 profile ») ; **#8** : ping simulé (abandonné, [[Knockback et combat]]).

## Fusionnées dans `main` (titres des commits de fusion)
| PR | Fusion | Titre | Branche |
|---|---|---|---|
| #2 | 07/11 | Serveur de test à lancer sur son PC | `claude/local-test-server` |
| #1 | 11/11 | M1 « Labo KB » : moteur de knockback et test de contrôle | `claude/m1-labo-kb` |
| #4 | 11/11 | Serveur de build : WorldEdit, mondes de build et sauvegardes | `claude/build-server` |
| #9 | 11/11 | Lobby principal, files d'attente, modes practice et Bed Wars | `claude/project-thread-nukjdk` |
| #11 | 12/11 | Fin de partie, réglages retenus, Bed Wars façon Hypixel | `claude/project-thread-nukjdk` |
| #12 | 12/11 | Replays : /replay pour revoir les duels et les parties de Bed Wars | `claude/project-thread-nukjdk` |
| #13 | 12/11 | Entraînements : menu Settings à la place des colorants, objets Trainings et Leave | `claude/project-thread-nukjdk` |
| #14 | 12/11 | Files : jusqu'à trois à la fois, et la place gardée pendant un entraînement | `claude/project-thread-nukjdk` |
| #15 | 12/11 | Menus : catégorie Arenas, « Popular now » avec le défi du jour, « Start here » | `claude/project-thread-nukjdk` |
| #16 | 12/11 | Premier menu « What do you want to do? » et quêtes « Getting started » | `claude/project-thread-nukjdk` |
| #17 | 12/11 | Quêtes de practice et 3 quêtes du jour tirées dans une liste | `claude/project-thread-nukjdk` |
| #18 | 12/11 | /records, mur des records practice et récompenses des classements de la semaine | `claude/project-thread-nukjdk` |
| #19 | 12/11 | Titres des modes, gagnés avec les victoires | `claude/project-thread-nukjdk` |
| #20 | 12/11 | Lire config.yml tel qu'il est écrit (une section absente = celle du plugin) | `claude/project-thread-nukjdk` |
| #21 | 12/11 | Prestiges après le niveau 100 et badges des saisons classées | `claude/project-thread-nukjdk` |
| #22 | 12/11 | /spectate, et le récap des duels avec [View] de l'inventaire | `claude/project-thread-nukjdk` |
| #23 | 12/11 | 5 minutes sans file classée après une partie classée abandonnée | `claude/project-thread-nukjdk` |
| #24 | 12/11 | Le bridge chronométré revient : 16, 32 ou 64 blocs, droit ou diagonale, à plat ou en montée | `claude/project-thread-nukjdk` |
| #25 | 12/11 | Les couleurs du serveur : le jaune pour Rago, le slogan, la nouvelle liste des serveurs | `claude/project-thread-nukjdk` |
| #26 | 12/11 | KB d'Hypixel entre joueurs, verrouillé, et podium de la semaine | `claude/project-thread-nukjdk` |
| #27 | 12/11 | DA rouge, blanc et gris, et le logo rouge | `claude/project-thread-nukjdk` |
| #28 | 12/11 | Trainings : pas de coups dans les menus, poudre verte, hotbar gardée, Block In honnête | `claude/project-thread-nukjdk` |
| #29 | 12/11 | Trainings : poudre rouge, plus de lit, restart seulement où il sert ; Play again sans changer de slot ; clutch plus lent après une chute | `claude/project-thread-nukjdk` |
| #31 | 13/11 | Lobby : un PNJ Private game et des noms clairs pour les points des PNJ | `claude/project-thread-nukjdk` |
| #30 | 13/11 | Ranked sans priorité de file pour les rangs payants ; le site officiel, refait autour du practice | `claude/project-thread-8ycg0x` |
| #32 | 13/11 | Site : la vraie police de Minecraft à la place des polices pixel | `claude/project-thread-8ycg0x` |
| #33 | 14/11 | Lobby : effacer à la connexion un titre resté d'un autre serveur | `claude/project-thread-nukjdk` |
| #34 | 14/11 | Lobby Fight 1v1 et FFA, /center, armure de combat, vie du Block Placement | `claude/project-thread-nukjdk` |
| #35 | 14/11 | Site : le block placement d'abord, les parties privées et la liste de tous les modes | `claude/project-thread-8ycg0x` |
| #36 | 14/11 | Bed Break Simulator : casser le lit depuis le top bridge avec un des 3 kits | `claude/project-thread-nukjdk` |
| #37 | 14/11 | Pearl Practice : boucle sur les maps de Bed Wars, avec le pearl block clutch contre un mur | `claude/project-thread-nukjdk` |
| #38 | 14/11 | Fireball practice : une boucle de vraies situations Bed Wars sur les maps | `claude/project-thread-nukjdk` |
| #39 | 14/11 | KB displace : le recul part toujours dans l'axe du pont, plus de frappe sur le côté | `claude/project-thread-nukjdk` |
| #40 | 14/11 | Clutch Scenario : Sky Rise et Archway, ponts déjà posés, défense du lit perdu cassée | `claude/project-thread-nukjdk` |
| #41 | 14/11 | Lobby : panneaux publicitaires, tes images sur des murs de cartes comme sur Hypixel | `claude/project-thread-nukjdk` |
| #42 | 14/11 | Bed Wars : le split de la forge, comme sur Hypixel, de tous les côtés de chaque forge d'île | `claude/project-thread-nukjdk` |
| #43 | 14/11 | Lobby Fight FFA : une épée et 3 fireballs, rien d'autre | `claude/project-thread-nukjdk` |
| #44 | 14/11 | Lobby Fight FFA : 5 pommes en or, remises à 5 à chaque kill | `claude/project-thread-nukjdk` |
| #45 | 14/11 | Panneaux publicitaires : test avec un vrai client Minecraft 1.8.9 | `claude/project-thread-nukjdk` |
| #46 | 14/11 | Invis practice et Bed Break Simulator en 1v1 à 1v4 | `claude/project-thread-nukjdk` |
| #49 | 25/11 | Vault Obsidian du projet (docs/vault) et CLAUDE.md | `claude/project-thread-c5qy7f` |
| #50 | 26/11 | Rago Hub : hook de retour des fils | `rago-hub-hook` |
| #51 | 26/11 | Rago Hub : hook v2 (activité en direct, images, tokens) | `rago-hub-hook` |
| #52 | 26/11 | Rago Hub : hook v3 (tâches en direct, texte en cours, outils hub.py) | `rago-hub-hook` |
