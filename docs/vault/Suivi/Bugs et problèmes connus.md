---
type: suivi
tags: [bugs, problèmes]
maj: 2026-11-26
---
# Bugs et problèmes connus

Retour à [[État actuel]]. Les corrections passées et leur détail sont dans le journal de `docs/suivi-sortie-publique.md`.

## Ouverts ou à vérifier
| Problème | État |
|---|---|
| Clé d'API Hypixel refusée (« Invalid API key ») : pas d'import du Quick Buy | BLOQUÉ, nouvelle demande de Sofiene (22/11) ([[Import Hypixel]]) |
| Boules de feu et TNT jamais mesurées (valeurs de BedWars1058) | BLOQUÉ, séance de mesure à faire par Abel ([[Boules de feu et TNT]]) |
| Spawns First Rush trop en avant sur les maps autres que Nebuc et Gelato | À FAIRE en jeu par Abel (spawns pas encore posés au 26/11 ; le point Hypixel d'une île par map) |
| Bridges du Bed Break Simulator sur Invasion et Archway « pas du tout ça » (essai du 21/11) | remplacés par la plateforme du Top Bridge ; à tester en jeu |
| Encodage : badges et € cassés sur Windows (Java 17, Windows-1252) | corrigé le 23/11 (lecture UTF-8) ; **à vérifier en jeu** |
| Reset des arènes (Block Placement, Block Edit) | IMPLÉMENTÉ, « je crois que ça marche » (21/11) : à confirmer |
| Lobby d'attente de Bedwars Normal : seule ArchwayFFA a une `salle-attente:` | à poser par Abel sur chaque map (`/bw edit <map>` puis `/bw lobbyfight`) ([[Parties privées]]) |
| `/speed` déclaré par RagoPractice et RagoBuildTools (alias) | À VÉRIFIER en jeu ([[Commandes et permissions]]) |
| Discord du site (`discord.gg/FXcvN7mWX`) différent de celui du jeu (`discord.gg/ragooptimizer`) | mis de côté avec le site jusqu'à ce que le serveur soit fini (Abel, 26/11) ([[Site]]) |
| Java du serveur de test : 17 (suivi) ou 25 (PR #10) ? | À VÉRIFIER : Abel ne sait pas ; la fenêtre de `lancer-windows.bat` l'affiche au démarrage ([[Serveur de test]]) |
| Beaucoup de fonctions **TESTÉ EN CI** mais jamais jouées en jeu depuis le 23/11 | essai en jeu d'Abel de la version du 25/11 : pas encore fait (26/11) ([[État actuel]]) |

## Documentation dépassée dans le dépôt
- `README.md` racine : dit « Jalon en cours : M1 Labo KB », que RagoPractice ne joue que le sumo, et que stats, Ranked, cosmétiques sont « à venir » : **dépassé** (tout cela existe depuis les PR #9 à #46, 11-14/11). À réécrire (petite PR doc, sur demande).
- `game/rago-game-model/README.md` : « tous les joueurs ont le même ELO » tant que `rago-data` n'existe pas : **faux**, l'ELO est gardé par joueur dans les stats du lobby (`StatsBook`).
- `tools/local-server/README.md` : parle encore du « duel sumo » comme seul mode et des colorants vert/gris pour cacher les joueurs ; à rafraîchir.
- L'audit du suivi (1.1) donne des tailles du 21/11 (lobby ~35 000 lignes, 110 classes) : aujourd'hui 128 classes, ~42 400 lignes ([[Architecture]]).

## Corrigés récemment (pour mémoire)
Remise en Survival des spectateurs par RagoBuildTools (21/11) · joueurs invisibles au spawn (21/11) · kit Bed Fight remis à la mort dans le vide (21/11) · double comptage des stats (audit 1.4, 21/11) · forges du Bed Break sur Invasion (21/11, TESTÉ EN JEU) · Block In réaliste (TESTÉ EN JEU, 21/11) · Lobby Fight sans cisailles, laine ni perle (21/11) · full box au Block placement (23/11) · Snowball qui ratait des coins (23/11).
