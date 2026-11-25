---
type: technique
tags: [build, gradle, tests]
maj: 2026-11-25
---
# Construire et tester

Retour à [[Reprendre le développement]]. Versions : [[Dépendances et compatibilité]].

## Prérequis
- **JDK 17 ou plus** pour lancer Gradle (la CI utilise 21). Tout est compilé avec `--release 8` (bytecode et API Java 8, la version des serveurs 1.8.8).
- **Gradle 9.8.2** par le wrapper (`./gradlew`), somme SHA-256 vérifiée ; le jar du wrapper est vérifié en CI.
- **Node.js 22** pour les bots des tests (mineflayer) et pour le site.
- Le mod `tools/kb-recorder` a son **propre build** (Gradle 8.8, Loom d'Essential 0.10.0.5, JDK 17 pour lancer, JDK 8 pour compiler, téléchargé par foojay) : il ne fait pas partie du `./gradlew build` racine.

## Le build Gradle
- `settings.gradle.kts` : projet `network-server-rago`, modules déclarés par `module(nom, dossier)` ; `build-logic/` contient les conventions (`rago.java-conventions`, `rago.bukkit-plugin-conventions` : Java 8, JUnit 5, `plugin.yml`).
- Dépôts : miroir Google de Maven Central d'abord (Maven Central limite les machines de CI), puis Maven Central, SpigotMC (seulement `org.spigotmc`, `org.bukkit`), CodeMC (seulement `com.github.retrooper` pour PacketEvents). `FAIL_ON_PROJECT_REPOS`.
- Version du projet : `rago.version` = `0.1.0-SNAPSHOT` (`gradle.properties`).

## Commandes
```bash
./gradlew build                          # tout, avec les tests unitaires
./gradlew :rago-combat-model:build       # ou :rago-combat-engine, :rago-game-model, :rago-lobby,
./gradlew :rago-practice:build           #    :rago-bedwars, :rago-build-tools, :kb-control, :kb-analyzer
./gradlew :rago-lobby:test --tests 'gg.rago.lobby.TeamRoomTest'
```
Jars : `<module>/build/libs/`. Le serveur de test complet (les 5 jars + lanceurs) est assemblé par la CI : artefact **`rago-serveur-de-test`** ([[Serveur de test]]).

## Tests
- **Unitaires** (JUnit 5.14.4, Java pur, sans serveur) dans chaque module : 7 fichiers pour combat-model (identique au code vanilla sur 200 000 coups), 3 combat-engine, 7 game-model, 58 lobby, 40 practice, 34 bedwars, 6 build-tools.
- **Sur un vrai serveur avec des bots** : [[Tests et bots]].
- **Site** : lint, typecheck, vitest, Playwright, Lighthouse ([[Site]]).
- Règle : chaque mécanique importante est testée ; ce qui n'est qu'une ébauche n'est jamais présenté comme terminé ; dire ce qui est seulement testé en CI et pas en jeu.
