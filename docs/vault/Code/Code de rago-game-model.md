---
type: code
tags: [code, rago-game-model]
maj: 2026-11-25
code: game/rago-game-model
---
# Code de rago-game-model

Java pur, sans Bukkit : les modes, les files, le matchmaking (ELO en classé), la grille d'arènes, la défense de lit, la rotation du Red Rover. Embarqué dans RagoLobby ; RagoPractice et RagoBedwars prennent ses classes par RagoLobby. Prévu pour être repris par le futur proxy. Fonction : [[Files, Ranked et ELO]]. README : `game/rago-game-model/README.md` (passage sur l'ELO dépassé, voir [[Bugs et problèmes connus]]).

Packages : `gg.rago.game`. **10 classes, 1639 lignes** (branche de la PR #47, `ce8edc5`, 25/11). Retour à [[Architecture]].

## Les classes
Description = la première phrase de la javadoc de la classe, **en anglais comme le code** (le code et ses commentaires sont en anglais ; les README sont en français).

| Classe | Lignes | Ce qu'elle fait |
|---|---|---|
| `ArenaGrid` | 75 | The arena slots of a game server: zones on a line in an empty world, usually far enough apart that players of two games never see each other (the 1.8 tracking range of a player is 48 blocks); closer for a mode whose play |
| `BedDefense` | 120 | The bed defenses the owner built, as templates that the games and the trainings build around a bed. |
| `GameMode` | 105 | One game mode players can queue for: its id (used in configs, commands and the database), its name shown to players, how many teams of how many players a match has, and whether it is ranked. |
| `Matchmaker` | 384 | Every queue of the network. |
| `MatchProposal` | 100 | A match the matchmaker found: the mode, and the players of each team, as queue entries so that a match that cannot start (no free arena) goes back to the queue with its original waiting times. |
| `ModeCatalog` | 36 | The game modes of the network, in the order they are shown to players. |
| `ModeQueue` | 439 | The queue of one game mode. |
| `QueueEntry` | 89 | A player waiting in a queue: who, with which rating, since when (milliseconds), the map they chose (null: any map), and the party they queue with (null: alone). |
| `RatingWindow` | 35 | How far apart two ratings may be for a ranked match, as a function of how long a player has waited: narrow at first for fair games, wider with time so that a quiet queue still finds a game. |
| `RedRoverRotation` | 256 | Who fights whom in a Red Rover (plan 6.5, solution 1): the players in 1v1, in turn, the others waiting. |

## Les tests (7 fichiers)
`ArenaGridTest`, `BedDefenseTest`, `GameModeTest`, `MatchProposalTest`, `MatchmakerTest`, `ModeQueueTest`, `RedRoverRotationTest`

Lancer : `./gradlew :rago-game-model:test` ([[Construire et tester]]).
