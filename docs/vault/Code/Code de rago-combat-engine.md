---
type: code
tags: [code, rago-combat-engine]
maj: 2026-11-25
code: combat/rago-combat-engine
---
# Code de rago-combat-engine

Le plugin **RagoCombatEngine** : applique un profil de knockback aux coups de mêlée entre joueurs, le délai de 9 ticks, le tracker à chaque tick, `/kb`, le sparring et le journal des hits. Point d'entrée : `RagoCombatEngine`. Fonction : [[Knockback et combat]]. README : `combat/rago-combat-engine/README.md`.

Packages : `gg.rago.combat.engine`. **12 classes, 1464 lignes** (branche de la PR #47, `ce8edc5`, 25/11). Retour à [[Architecture]].

## Les classes
Description = la première phrase de la javadoc de la classe, **en anglais comme le code** (le code et ses commentaires sont en anglais ; les README sont en français).

| Classe | Lignes | Ce qu'elle fait |
|---|---|---|
| `EngineMode` | 32 | What the engine does with the velocity it computes for a melee hit. |
| `EngineSettings` | 184 | An immutable snapshot of the engine's settings. |
| `HitDelayListener` | 51 | Gives every player the damage cooldown of the hit-delay-ticks setting. |
| `HitRecord` | 116 | Everything the engine knows about one melee hit, for the hit log and the debug messages. |
| `HitRecorder` | 107 | Appends hit records to hits/hits-YYYY-MM-DD.jsonl on a background thread, so that the main thread never waits for the disk. |
| `KbCommand` | 261 | /kb: staff controls for the combat engine. |
| `KnockbackListener` | 120 | Replaces the velocity of player-against-player melee hits. |
| `PendingHit` | 34 | A melee hit seen in the damage event, waiting for the velocity event that the same attack fires a moment later on the same thread. |
| `RagoCombatEngine` | 164 | RagoCombatEngine: applies a measurable, configurable knockback profile to melee hits between players on a 1.8.8 server. |
| `Sparring` | 100 | The hits of /kb sparring: an attacker nobody sees, standing in front of the player and facing them, hits them at a fixed interval. |
| `SparringSessions` | 165 | Runs /kb sparring: every few ticks, a hit from an invisible attacker standing in front of the player (see Sparring), computed with the world's profile like a real hit, with the hurt animation and sound. |
| `TrackerTuner` | 130 | How often the server sends each player's position to the others (config.yml, player-tracker-ticks). |

## Les tests (3 fichiers)
`EngineSettingsTest`, `HitRecordTest`, `SparringTest`

Lancer : `./gradlew :rago-combat-engine:test` ([[Construire et tester]]).
