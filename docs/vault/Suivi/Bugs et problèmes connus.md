---
type: suivi
tags: [bugs, problèmes]
maj: 2026-11-28
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
| Avertissement au démarrage : `titres.familles.clutch-scenario: no mode clutch-scenario-2v2 (3v3, 4v4) in modes:, its wins never count` | connu, sans effet sur le jeu : Clutch Scenario se joue en 1v1 à 1v4 depuis le 22/11, la famille de titres cite encore les anciennes tailles (log du 28/11) ([[Progression et récompenses]]) |
| Avertissement au démarrage : « The lobby has no mode "bridge-fight" in its config.yml: it stays closed. » | voulu : Bridge fight FFA retiré des Arenas par Abel le 27/11 ([[Arènes ouvertes]]) |
| `/speed` déclaré par RagoPractice et RagoBuildTools (alias) | À VÉRIFIER en jeu ([[Commandes et permissions]]) |
| Discord du site (`discord.gg/FXcvN7mWX`) différent de celui du jeu (`discord.gg/ragooptimizer`) | mis de côté avec le site jusqu'à ce que le serveur soit fini (Abel, 26/11) ([[Site]]) |
| Beaucoup de fonctions **TESTÉ EN CI** mais jamais jouées en jeu depuis le 23/11 | versions installées le 25/11, le 27/11 et le 28/11 (`03beb92` puis `d050af8`) : Abel n'a encore rien testé en jeu (28/11) ([[État actuel]]) |

## Documentation dépassée dans le dépôt
- `README.md` racine : dit « Jalon en cours : M1 Labo KB », que RagoPractice ne joue que le sumo, et que stats, Ranked, cosmétiques sont « à venir » : **dépassé** (tout cela existe depuis les PR #9 à #46, 11-14/11). À réécrire (petite PR doc, sur demande).
- `game/rago-game-model/README.md` : « tous les joueurs ont le même ELO » tant que `rago-data` n'existe pas : **faux**, l'ELO est gardé par joueur dans les stats du lobby (`StatsBook`).
- `tools/local-server/README.md` : parle encore du « duel sumo » comme seul mode et des colorants vert/gris pour cacher les joueurs ; à rafraîchir.
- L'audit du suivi (1.1) donne des tailles du 21/11 (lobby ~35 000 lignes, 110 classes) : aujourd'hui 128 classes, ~42 600 lignes ([[Architecture]]).
- `bukkit/rago-lobby/README.md` ne suit pas la liste d'Abel du 27/11 : il dit que la boussole a « six lignes » (cinq depuis le 27/11) et ne parle ni de `icone: "WOOL:14"` ni de `regard: point`. (Les README de RagoPractice et de RagoBedwars sont mis à jour par une PR à part : Bow Practice, niveau RBW, Block Pattern Practice, 2012 Bedwars.) `docs/suivi-sortie-publique.md` s'arrête à l'installation du 25/11.

## Corrigés récemment (pour mémoire)
Ordre d'enregistrement des modes de RagoPractice (28/11) : la PR #56 avait enregistré `bow-practice` au milieu, ce qui décalait les zones d'arène des 13 modes d'après (les events, `bridge-fight-2v2`, `bridge-chrono`, `fireball-knockback`, `snowball-spleef`, Red Rover, `block-aim`, `parkour-duel`) ; corrigé par la PR #58 (les nouveaux modes à la fin, règle écrite dans `RagoPractice.registerModes`), installé le 28/11 vers 11h29, zones de nouveau dans l'ordre d'avant la #56 ; aucune partie jouée entre-temps ([[Code de rago-practice]]) · Remise en Survival des spectateurs par RagoBuildTools (21/11) · joueurs invisibles au spawn (21/11) · kit Bed Fight remis à la mort dans le vide (21/11) · double comptage des stats (audit 1.4, 21/11) · forges du Bed Break sur Invasion (21/11, TESTÉ EN JEU) · Block In réaliste (TESTÉ EN JEU, 21/11) · Lobby Fight sans cisailles, laine ni perle (21/11) · full box au Block placement (23/11) · Snowball qui ratait des coins (23/11).
