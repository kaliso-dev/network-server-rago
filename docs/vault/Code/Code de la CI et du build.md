---
type: code
tags: [code, ci, gradle]
maj: 2026-11-25
code: .github/workflows/ci.yml, build-logic/, settings.gradle.kts, gradle/, gradle.properties, netlify.toml
---
# Code de la CI et du build

Retour à [[Architecture]]. Fonctionnement : [[CI GitHub Actions]], [[Construire et tester]].

| Fichier | Rôle |
|---|---|
| `settings.gradle.kts` | projet `network-server-rago` ; dépôts (miroir Google de Maven Central, Maven Central, SpigotMC limité à `org.spigotmc`/`org.bukkit`, CodeMC limité à `com.github.retrooper`) ; modules `rago-combat-model`, `rago-combat-engine`, `rago-game-model`, `rago-lobby`, `rago-practice`, `rago-bedwars`, `kb-control`, `rago-build-tools`, `kb-analyzer` |
| `build-logic/` (build inclus) | `rago.java-conventions.gradle.kts` (Java 8 `--release 8`, JUnit 5) et `rago.bukkit-plugin-conventions.gradle.kts` (plugins Bukkit : `plugin.yml`, jar) |
| `gradle.properties` | `rago.version=0.1.0-SNAPSHOT` et réglages Gradle |
| `gradle/wrapper/` | Gradle 9.8.2, somme SHA-256 de la distribution |
| `<module>/build.gradle.kts` | dépendances de chaque module (`compileOnly` spigot-api, PacketEvents, `rago-lobby` pour les jeux) |
| `<module>/src/main/resources/plugin.yml` | nom, `depend`, commandes, permissions de chaque plugin ([[Commandes et permissions]]) |
| `tools/kb-recorder/` | build Gradle 8.8 séparé (Loom) |
| `.github/workflows/ci.yml` | le seul workflow, 9 jobs (14 avec les matrices) |
| `netlify.toml` | copie temporaire du site ([[Site]]) |
| `.editorconfig`, `.gitattributes`, `.gitignore` | format des fichiers ; `.gitattributes` garde les `.bat` en CRLF pour Windows |
