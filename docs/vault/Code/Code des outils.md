---
type: code
tags: [code, outils]
maj: 2026-11-28
code: tools/
---
# Code des outils

Les outils du dossier `tools/` (sauf RagoBuildTools : [[Code de rago-build-tools]]). Fonctions : [[Outils de mesure du knockback]], [[Tests et bots]], [[Maps et mondes]]. Retour à [[Architecture]].

## kb-control (`tools/kb-control`, module Gradle `:kb-control`, Java)
Le test de contrôle sur un vrai PandaSpigot : `run-control-test.sh` (lance tout), `test-server.sh` (prépare le serveur). Lit le journal des hits de RagoCombatEngine et compare au calcul de `rago-combat-model`.
| Classe | Lignes | Ce qu'elle fait (javadoc, en anglais) |
|---|---|---|
| `BotAttack` | 29 | An attack the test bots sent (type "attack" in received.jsonl). |
| `ControlCheck` | 518 | Checks a control run: the hits the engine logged on a real 1.8.8 server against the velocity packets the victim bot actually received, and both against rago-combat-model. |
| `ExplosionCheck` | 189 | The explosions part of the kb-recorder end-to-end test. |
| `HitDelayCheck` | 264 | Checks the engine's hit-delay-ticks setting on a real server, with the bursts of bots/hit-delay-bots.mjs: during a burst the attacker hits the victim twice per tick, so every hit must count exactly the hit delay after th |
| `ReceivedVelocity` | 27 | A velocity packet a test bot received for its own entity (type "velocity" in received.jsonl). |
| `RecordedHit` | 105 | One line of the engine's hit log (hits-YYYY-MM-DD.jsonl, written by HitRecord in rago-combat-engine). |
| `RecorderCheck` | 619 | End-to-end check of kb-recorder (tools/kb-recorder): compares a recording made by the mod in a real Minecraft 1.8.9 client with the hit log of the combat engine on the server that hit it. |

### Les bots (`tools/kb-control/bots/`, Node 22, mineflayer 4.39.0)
Partagés par tous les tests sur vrai serveur (un `package.json` et un `package-lock.json` communs) :
`control-bots.mjs` (knockback), `hit-delay-bots.mjs` (délai entre deux coups), `lobby-bots.mjs` (1 528 lignes : le test du lobby ; depuis le 27/11 il lit la boussole de 5 lignes, la laine des Arenas, la laisse du sumo et le nouveau texte de la salle des remerciements), `arena-bots.mjs`, `visibility-bots.mjs`, `blockin-bots.mjs`, `fast-bots.mjs` (2017 Bedwars, caché des menus depuis le 27/11 mais gardé pour ce test ; Fast Shop), `fun-bots.mjs` (Fun Practice), `parkour-runner.mjs`, `ctf-bots.mjs`, `rover-bots.mjs` (Red Rover), `private-bots.mjs` (parties privées), `party-duel-bots.mjs` (duels entre parties), `ragobot-bots.mjs` (RagoBot), `owner-bots.mjs` (outils Owner), `spectate-bots.mjs`, `recorder-attacker.mjs` (attaquant du test bout en bout de kb-recorder).

## kb-analyzer (`tools/kb-analyzer`, module `:kb-analyzer`, Java + Gson)
| Classe | Lignes | Ce qu'elle fait (javadoc, en anglais) |
|---|---|---|
| `BlockSnapshot` | 80 | A blocks line: every block that is not air in a cube around an explosion, as the client saw them just before it applied the explosion. |
| `ExplosionReport` | 184 | The Markdown section of the explosions of one recording. |
| `Explosions` | 388 | The explosions of one recording (kb-recorder 0.2 and later), each with what caused it, the blocks it broke and the ones it left, and how it pushed the player. |
| `Fight` | 164 | A fight recorded by both players. |
| `FightStats` | 201 | The numbers of a fight that the report shows, for both players together. |
| `Json` | 26 | Reads optional fields: a recording from an older mod version may lack some of them. |
| `KbAnalyzer` | 232 | Compares the knockback and the hit timing of fights recorded with kb-recorder, both players recording at once. |
| `Packet` | 373 | A packet line: the bytes of one packet as on the wire after decompression, without the packet id. |
| `PlayerView` | 186 | What one recording says about its player's fight: the velocity packets the player received, the hurt animations it saw, and the attacks, sprint changes and looks it sent. |
| `Recording` | 170 | A kb-recorder recording (tools/kb-recorder): one JSON line per event, written by the mod in .minecraft/rago-kb-recorder. |
| `Registration` | 118 | Which attacks of a player counted, seen from the attacker's recording: an attack counts when the target's hurt animation comes back about one attack-to-animation delay later. |
| `Tick` | 143 | A tick line: what the client knew at the end of one of its ticks. |
| `Times` | 53 | Helpers over sorted lists of times in milliseconds. |

## kb-recorder (`tools/kb-recorder`, build Gradle à part, mod Forge 1.8.9)
| Classe | Lignes | Ce qu'elle fait (javadoc, en anglais) |
|---|---|---|
| `ClientSampler` | 124 | Reads, on the game thread, what the client knows at the end of a tick. |
| `Clock` | 23 | Time source of the recorder, replaced in tests. |
| `ExplosionBlocks` | 92 | Writes the blocks around an explosion as the client sees them just before the explosion: which blocks an explosion breaks, and which ones it leaves, is then known from the recording alone. |
| `JsonLine` | 129 | Builds one JSON object on a single line. |
| `KbRecCommand` | 92 | The client command /kbrec: start, stop, mark and status. |
| `KbRecorderMod` | 39 | (pas de javadoc) |
| `PacketBytes` | 125 | Reads the few fields of a serialized packet that decide whether it is kept: entity ids, the type of a spawned object, the centre of an explosion. |
| `PacketCatalog` | 113 | The packets kept in a recording, with their protocol 47 id and name. |
| `PacketTap` | 86 | Sits in the connection's Netty pipeline, just before Minecraft's own packet handler, and copies the packets of the PacketCatalog into the recording. |
| `Recorder` | 213 | One recording session at a time, written as JSON lines. |
| `RecorderControl` | 47 | Starts and stops recordings for the command, the auto-start and the game's shutdown. |
| `RecorderEvents` | 90 | Puts the packet tap on each new connection and records one line per client tick. |
| `RecordingWriter` | 103 | Writes the lines of one recording on its own thread: the game thread and the network threads only put lines in a queue and never wait for the disk. |

## lobby-check (`tools/lobby-check`, scripts)
`run-lobby-test.sh` (263 lignes), `run-arena-test.sh` (381), `run-billboard-test.sh` (239), `minecraft-client.py` (un vrai client 1.8.9 lancé sous affichage virtuel), `billboard-shot.py` (capture et contrôle de l'image des panneaux). README détaillé de 325 lignes.

## local-server (`tools/local-server`)
`lancer-windows.bat` + `lancer.ps1`, `lancer-mac-linux.sh` (téléchargements avec SHA-256 fixés, sauvegardes des mondes, variables `RAGO_*`), `server.properties`, `server-icon.png`. Voir [[Serveur de test]].

## bedwars-maps (`tools/bedwars-maps`, Python : numpy, scipy, pillow)
`bwsetup.py` (946 lignes : trouve équipes, couleurs, générateurs, spawns, shops, diamants, émeraudes d'une map d'Hypixel et écrit `bedwars.yml`), `runall.py` (plusieurs maps, 4 à la fois), `anvil.py` et `world.py` (lecture du format de monde 1.8), `render.py` (vue de dessus).

## npc-skins (`tools/npc-skins`, Python)
`npc_skins.py` et `skinlib.py` : dessinent les skins 64×64 des PNJ du lobby (coach, joueur de Bed Wars, duelliste, gladiateur, marchand, analyste, explorateur…), mis dans le jar de RagoLobby.
