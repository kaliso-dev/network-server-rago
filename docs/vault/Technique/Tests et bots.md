---
type: technique
tags: [tests, bots, mineflayer, ci]
maj: 2026-11-26
---
# Tests et bots

Les tests qui font tourner un **vrai serveur PandaSpigot 1.8.8 (build 226)** et des **bots mineflayer** (Node 22) qui jouent comme des joueurs. Tous lancés par la CI ([[CI GitHub Actions]]). Retour à [[Construire et tester]].

| Test | Script | Ce qu'il vérifie | Job CI |
|---|---|---|---|
| Contrôle du KB | `tools/kb-control/run-control-test.sh` (bots dans `tools/kb-control/bots/`) | le knockback envoyé par RagoCombatEngine (**108 coups sur 108 exacts**), le délai de 10 ticks, 9 ticks, et 9 ticks après une réapparition, un seul paquet par coup du sparring | `control-test` (Java 8 et 21) |
| Lobby, sumo, Bed Wars, event | `tools/lobby-check/run-lobby-test.sh` (bots `tools/kb-control/bots/lobby-bots.mjs`) | 2 puis 3 bots : premier menu, boussole, éditeur de kit, import Hypixel sans clé, files, duel sumo (3-0, ding), stats et classements, abandon, `/duel`, Ranked Bedwars 1v1 sur `bw-demo` (shop, laine, lit, boule de feu, mort et retour, abandon = file classée fermée), First Rush, party et partie privée, replays, events, RagoBot, outils Owner… (README de 325 lignes) | `lobby-test` (Java 8 et 21) |
| Arènes et visibilité | `tools/lobby-check/run-arena-test.sh` | remise à zéro des arènes practice, visibilité au lobby (ajouté par la PR #47) | `arena-test` (Java 21) |
| Panneaux | `tools/lobby-check/run-billboard-test.sh`, `billboard-shot.py`, `minecraft-client.py` | un vrai client Minecraft 1.8.9 (affichage virtuel) photographie les panneaux du lobby | `billboard-test` |
| Mod kb-recorder | build du mod + test bout en bout dans un client 1.8.9 | le mod enregistre les coups | `kb-recorder`, `kb-recorder-e2e` |
| Serveur de test | job `local-server` | le dossier assemblé démarre deux fois (Linux et Windows, Java 8 et 21), plugins actifs, monde retrouvé et sauvegardé | `local-server` (4 combinaisons) |

- Spectateurs : jusqu'à 10 en même temps testés ([[Replays et spectateurs]]).
- Les bots n'ont pas de vrai ping ni de vrais réflexes : **un test vert ne remplace pas un essai en jeu** (les essais en jeu sont faits par Abel). Beaucoup de fonctions sont « TESTÉ EN CI, pas encore joué » ([[État actuel]]).
- `tools/local-server` sert aussi aux bots : le serveur est lancé avec `RAGO_ACCEPTE_CLUF=oui`.
