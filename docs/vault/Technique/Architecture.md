---
type: technique
tags: [architecture, modules]
maj: 2026-11-25
---
# Architecture

Retour à [[Projet]]. Plan d'origine (réseau complet, pas encore construit) : [[Plan d'architecture v1]].

## Aujourd'hui : un seul serveur PandaSpigot (CONFIRMÉ)
Un serveur **PandaSpigot 1.8.8 build 226**, joué en **1.8.9**, avec les plugins du dépôt : pas de proxy, pas de base de données. Les données sont des fichiers YAML dans `plugins/<Plugin>/`.

| Plugin (jar) | Module | Dépend de | Rôle |
|---|---|---|---|
| RagoCombatEngine | `combat/rago-combat-engine` (embarque `rago-combat-model`) | — | knockback mesuré, `/kb` |
| RagoLobby | `bukkit/rago-lobby` (embarque `rago-game-model`) | `packetevents` ; softdepend RagoBuildTools | lobby, files, stats, rangs, boutique, replays, party, menus ; publie `LobbyService` |
| RagoPractice | `bukkit/rago-practice` | RagoLobby | duels, entraînements, arènes, events, Fun Practice, Red Rover |
| RagoBedwars | `bukkit/rago-bedwars` | RagoLobby | tous les modes sur des maps de Bed Wars, FFA, CTF |
| RagoBuildTools | `tools/build-tools` | — | mondes de build, protection, sauvegarde (serveur de test seulement) |
| PacketEvents 2.14.0 | externe | — | PNJ, hologrammes, lignes au-dessus des têtes, nicks, replays |
| WorldEdit 6.1 | externe | — | construire (serveur de test) |

## Comment les plugins se parlent
- RagoLobby publie **`LobbyService`** dans le ServicesManager ; chaque plugin de jeu enregistre un **`MatchHost`** par mode (`lobby.registerHost(this, "sumo", host)`).
- Le lobby fait le matchmaking (une fois par seconde) et appelle `MatchHost.start(match)` ; le jeu rend les joueurs avec `lobby.sendToLobby`, compte avec `recordMatch`, `recordKill`, `recordDraw`, `forfeited`, `giveXp`. Kits : `lobby.kit(player, mode)`. Hypixel : `hypixelQuickBuy`, `hypixelHotbar`. Spectateurs : `MatchHost.spectate`. Parties privées : `privateSettings`, `canGather`/`gather`. Replays : `ReplayRecording`.
- Les arènes practice sont des zones du monde `arenes` refait à chaque démarrage (`ArenaGrid`, `Arena` remet chaque bloc). Les parties de Bed Wars se jouent dans une **copie de la map** (`bw_<map>_<n>`, `Instance`), remise à neuf après chaque partie, supprimée à l'arrêt ; `parties-max: 8`.
- Tout est en anglais en jeu ; clés de config en français.

## Taille du code (25/11, branche de la PR #47)
| Module | Classes | Lignes (Java) | Tests |
|---|---|---|---|
| rago-combat-model | 9 | 854 | 7 |
| rago-combat-engine | 12 | 1 464 | 3 |
| rago-game-model | 10 | 1 639 | 7 |
| rago-lobby | 128 | ~42 400 | 58 |
| rago-practice | 80 | ~27 600 | 40 |
| rago-bedwars | 59 | ~21 300 | 34 |
| build-tools | 9 | 1 147 | 6 |
| kb-control / kb-analyzer / kb-recorder | 7 / 13 / 13 | 1 751 / 2 318 / 1 276 | 5 / 3 / 4 |

(« Tests » = nombre de fichiers de tests JUnit.)

## Plus tard (prévu, pas fait)
Le plan v1 prévoit un réseau : proxy Velocity avec matchmaker, lobby-01, games-01, MariaDB 11, Redis 7, `rago-api`, TCPShield, BungeeGuard ; modules `rago-core`, `rago-arena`, `rago-ranked`, `rago-stats`, `rago-cosmetics`, `rago-proxy`, `rago-data`, `rago-api`. **Aucun n'existe**. Le côté `MatchHost` des plugins de jeu ne devrait pas changer quand les files passeront au proxy. Voir [[Hébergement et production]].
