---
type: technique
tags: [dépendances, versions, java]
maj: 2026-11-28
---
# Dépendances et compatibilité

Source : `docs/compatibility.md` (à tenir à jour : toute nouvelle dépendance y est listée avec ce qui a été vérifié). Retour à [[Construire et tester]].

## Cibles
| Élément | Version |
|---|---|
| Client | Minecraft **1.8.9** (protocole 47), Rago Client ou vanilla (le serveur n'exige jamais Rago Client) |
| Serveur | **PandaSpigot 1.8.8, build 226** (`git-PandaSpigot-226`), SHA-256 `ac73146b…6359b` vérifié à chaque test, téléchargé sur downloads.hpfxd.com |
| Bytecode | **Java 8** (`--release 8`) |
| Java des serveurs | Java 8 et Java 21 (les deux testés en CI) ; sur le serveur de test (PC d'Abel) : **JDK 17.0.21** (OBSERVÉ le 28/11, par `JAVA_HOME` ; `java` seul y est Java 8) ([[Serveur de test]]) |

## Build
JDK 17+ (CI 21) · Gradle 9.8.2 (wrapper) · Node.js 22 (bots, site) · kb-recorder : Forge 1.8.9-11.15.1.2318, Loom d'Essential 0.10.0.5 (figé), architectury-pack200 0.1.3, MCP `mcp_stable` 22-1.8.9, Gradle 8.8, foojay 0.8.0.

## Dépendances
| Dépendance | Version | Où |
|---|---|---|
| spigot-api | 1.8.8-R0.1-SNAPSHOT | plugins (`compileOnly`) ; `bungeecord-chat` exclu (version introuvable) |
| PacketEvents | **2.14.0** | RagoLobby (`compileOnly`, plugin `packetevents` installé à côté) |
| WorldEdit | 6.1 (2015) | serveur de test seulement |
| JUnit Jupiter | 5.14.4 | tests (JUnit 6 exige Java 17) |
| Gson | 2.15.0 | kb-control et kb-analyzer seulement |
| mineflayer | 4.39.0 | bots (Node) |
| Site | Next.js 16.4.6, React 19.3.1, TypeScript 6.0.3, Playwright 1.63.0, vitest 5.1.0, Lighthouse CI 0.16.1 | `site/web` |

Écartés : Multiverse-Core (pas de version stable 1.8), CleanroomGenerator, VoidGenerator. Les plugins n'embarquent que des modules du projet ; une bibliothèque tierce devra être relocalisée (Shadow).
