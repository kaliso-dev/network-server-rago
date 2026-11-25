---
type: code
tags: [code, rago-build-tools]
maj: 2026-11-25
code: tools/build-tools
---
# Code de rago-build-tools

Le plugin **RagoBuildTools**, pour le serveur de test seulement : mondes de build vides, créatif, protection, sauvegarde toutes les 2 minutes, `/monde`, `/vitesse`, `/nv`. Fonction : [[Serveur de test]]. README : `tools/build-tools/README.md`.

Packages : `gg.rago.buildtools`. **9 classes, 1147 lignes** (branche de la PR #47, `ce8edc5`, 25/11). Retour à [[Architecture]].

## Les classes
Description = la première phrase de la javadoc de la classe, **en anglais comme le code** (le code et ses commentaires sont en anglais ; les README sont en français).

| Classe | Lignes | Ce qu'elle fait |
|---|---|---|
| `BuilderCommands` | 79 | /vitesse (flying speed) and /nv (night vision), the two comforts of building. |
| `BuildListener` | 213 | What keeps a build world a building site: the game mode of the world for whoever enters, only builders may change a protected world, and nature (rain, fire, leaves, melting ice, explosions, mobs) leaves builds alone. |
| `BuildWorlds` | 208 | The build worlds: every folder of the server's world container that holds a level.dat and a WorldSettings#FILE_NAME. |
| `LevelVersion` | 170 | Reads which Minecraft version last saved a world, from its level.dat, before the 1.8 server opens it. |
| `MondeCommand` | 210 | /monde: list, create, import and visit the build worlds, set their spawn and save them. |
| `RagoBuildTools` | 44 | RagoBuildTools: what a builder needs on the local test server, next to WorldEdit. |
| `VoidGenerator` | 41 | Generates nothing: every chunk is air. |
| `WorldName` | 45 | Names of the worlds created with /monde. |
| `WorldSettings` | 137 | The settings of a build world, kept in #FILE_NAME inside the world's own folder. |

## Les tests (6 fichiers)
`BuildListenerTest`, `BuilderCommandsTest`, `LevelVersionTest`, `VoidGeneratorTest`, `WorldNameTest`, `WorldSettingsTest`

Lancer : `./gradlew :rago-build-tools:test` ([[Construire et tester]]).
