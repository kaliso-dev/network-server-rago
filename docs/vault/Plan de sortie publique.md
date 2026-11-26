---
type: pilotage
tags: [pilotage, plan, sortie]
maj: 2026-11-26
---
# Plan de sortie publique

Le plan complet de Sofiene du **21/11/2026** pour amener Rago Practice jusqu'à son ouverture au public. Il est suivi tâche par tâche dans **`docs/suivi-sortie-publique.md`** (branche de la PR #47, 2 559 lignes au 25/11 : audit, checklist, puis un journal de chaque correction, de chaque liste de Sofiene et de chaque installation). Cette note en donne l'état ; le suivi fait foi pour le détail. Voir [[État actuel]], [[Prochaines étapes]], [[Décisions]].

**Statuts du suivi** : À FAIRE, EN COURS, IMPLÉMENTÉ (code écrit, compilé, testé automatiquement, essai en jeu à faire), TESTÉ (CI ou en jeu, dit lequel), BLOQUÉ. **Priorités** : P0 bug critique, P1 avant la sortie, P2 souhaité, P3 après la sortie ou étude. Ordre imposé : audit, bugs critiques, puis étapes 3 à 9.

## Étape 1 : audit (fait le 21/11)
Architecture relevée (voir [[Architecture]]), systèmes partagés à corriger une fois (reset des arènes, spectateurs, stats, leaderboards, records, kits, visibilité, files, PNJ, fireballs, replays, events), données à ne pas supprimer ([[Règles de travail]]), 8 conflits du plan tranchés (C1 à C8), informations manquantes listées.

## Étape 2 : bugs critiques (P0)
| # | Tâche | Statut au 25/11 |
|---|---|---|
| 1.1 | Reset des arènes (blocs de la partie d'avant retrouvés après Play again) | IMPLÉMENTÉ, test réel en CI (`run-arena-test.sh`) ; à voir sur les maps du serveur |
| 1.2 | Spectateur remis en Survival puis tué (RagoBuildTools) | IMPLÉMENTÉ ; /spectate ouvert à tous |
| 1.3 | Joueurs invisibles à l'arrivée | IMPLÉMENTÉ, test réel en CI (`visibility-bots.mjs`) |
| 1.4 | Audit des stats (double comptage des final kills, morts sans tueur, abandons) | IMPLÉMENTÉ |
| 1.5 | Kit Bed Fight remis au rangement du lobby à la mort | IMPLÉMENTÉ |
| 1.6 | Forges de Bed Break sur Invasion | TESTÉ EN JEU (21/11) |
| 1.7 | Spawns First Rush trop en avant | IMPLÉMENTÉ sur Nebuc et Gelato ; autres maps : spawns pas encore posés, à faire en jeu par Abel (26/11) |
| 1.8 | Block In réaliste + remise à zéro de ses records | TESTÉ EN JEU (21/11) |

## Étape 3 : modes existants (P1)
2.1 Lobby Fight (kit commun, Haste, blocs qui partent après 10 s, premier à 3) : corrigé après l'essai, à retester · 2.2 Bed Fight protection au respawn 1,5 s : TESTÉ EN JEU, étendue aux autres modes Bed Wars · 2.3 Bed Break Simulator (bridges droits, défense RBW, plateforme du Top Bridge) : IMPLÉMENTÉ, plateforme à tester · 2.4 Clutch Scenario (bridges réalistes) : IMPLÉMENTÉ · 2.5 10 spectateurs (50 places) : test réel en CI · 2.6 Fireballs fidèles à Hypixel : mesure prête, BLOQUÉ (séance de mesure d'Abel) · 2.7 TNT : À FAIRE après 2.6 · 2.8 Fast Shop et API Hypixel : BLOQUÉ (clé refusée).

## Étape 4 : stats et progression
3.1 Leaderboards Daily / Weekly / Lifetime : IMPLÉMENTÉ · 3.2 FKDR à deux décimales : IMPLÉMENTÉ · 3.3 Records Lifetime et Weekly (gardés 5 semaines) : IMPLÉMENTÉ · 3.4 Top 3 Lifetime au survol des menus : IMPLÉMENTÉ · 3.5 Network Level au-dessus des têtes par défaut : IMPLÉMENTÉ · 3.6 ELO des adversaires en Ranked : IMPLÉMENTÉ · 3.7 Solo / Party / Ranked : IMPLÉMENTÉ (22/11) · 3.8 nombre de joueurs en file : TESTÉ EN CI · 5.4d sélecteur de leaderboard : IMPLÉMENTÉ. Détail : [[Statistiques et classements]], [[Files, Ranked et ELO]].

## Étape 5 : expérience joueur
4.1 Party Replay (`/p warp` dans un replay) : test réel passé · 4.1b sons dans les replays : TESTÉ EN CI · 4.2 `/host` (annonce toutes les 20 s, poudre rouge) : TESTÉ EN CI · 4.3 Kit Editor trié : TESTÉ EN CI · 4.4 menu Stats par catégories : TESTÉ EN CI · 4.5 `/discord`, `/store` : liens officiels en place.

## Étape 6 : lobby
5.1 moins de rouge : TESTÉ EN CI · 5.2 build monumental « RAGO PRACTICE » : BLOQUÉ (proposition du 23/11) · 5.3 blocs sous les PNJ (quartz, fer, émeraude) : TESTÉ EN CI · 5.4 NewQuest, NewShop, NewDailyChest : TESTÉ EN CI · 5.5 Daily Chest animé : TESTÉ EN CI · 5.6A PNJ Rago Client et Rago Optimizer : IMPLÉMENTÉ · 5.6B PNJ qRago et kaliso_dev : TESTÉ EN CI · 5.6C modérateurs : RETIRÉS · 5.7 texte de remerciement : validé, installé · 5.8 skins des PNJ : TESTÉ EN CI · 5.9 boussole Minemen : RETIRÉE (boussole d'avant + 4 catégories en haut). Détail : [[Lobby]], [[Boussole et menus]].

## Étape 7 : nouveaux modes (proposés avant le code)
6.1 Fun Practice : Snowball Spleef, Fireball Fight à trois, 2017 Bedwars, Parkour Duel, CTF (deux types) : TESTÉS EN CI par les bots · 6.3 Find Your Sensitivity : fait puis retiré des menus · 6.4 Block Aim et bridge avec virage : IMPLÉMENTÉ · 6.5.1 Red Rover (Sumo, Block Edit, Block Placement, Top Bridge) : TESTÉ EN CI · 6.5.2 Clutch Scenario 1v1 à 1v4 et Bed Fight 1v2 : IMPLÉMENTÉ · 6.5.3 RagoBot : étapes 1 à 3 TESTÉES EN CI, la suite BLOQUÉE · 6.6 catégorie 3 Players : IMPLÉMENTÉ. Détail : [[Fun Practice]], [[Red Rover et 3 Players]], [[RagoBot]].

## Étape 8 : administration et benchmark
7.1 Outils Owner (Reach, Scaffold) : TESTÉ EN CI ([[Outils Owner]]) · 7.2 rejoindre une partie en cours (admin) : BLOQUÉ · 7.3 anticheat (Grim proposé) : BLOQUÉ · 7.4 FPS benchmark : BLOQUÉ.

## Étape 9 : release
8.1 hébergeur européen ≤ 30 €/mois et 8.2 site, liens, Netlify, domaine : **mis de côté jusqu'à ce que le serveur soit fini** (Abel, 26/11) · 9 sécurité et tests finaux : À FAIRE. Voir [[Hébergement et production]].

## Les listes de retours après les essais en jeu
Depuis le 22/11, la version installée est essayée en jeu (les essais en jeu sont faits par Abel, précisé le 26/11) et une liste datée de retours arrive (22/11 à 16h22, 17h54, 18h22, 19h51 ; 23/11 à 20h54 ; 25/11 à 9h19). Abel a annoncé le 26/11 une nouvelle liste de maps, modes et features à ajouter. Chaque point est codé un par un, les simples d'abord, les gros passent par une proposition. Les points de la liste du 23/11 encore ouverts : RagoBot qui joue sur la map, RagoBot qui pose des blocs, Top Bed Fight 2v2 (sa map) et Attack and Defense (sa vision), tous deux finis par Abel et à recevoir (26/11), build du lobby, fireballs de Minemen (Abel propose en attendant des plugins qui imitent les fireballs d'Hypixel, [[Boules de feu et TNT]]). L'essai en jeu de la version installée le 25/11 n'est pas encore fait (Abel, 26/11).
