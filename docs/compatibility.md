# Compatibilité et dépendances

Chaque dépendance du projet est listée ici avec sa raison d'être et ce qui a été vérifié. Avant
d'en ajouter une : vérifier qu'elle est maintenue, compatible Java 8 si elle tourne dans un serveur
1.8.8, et l'ajouter à ce tableau.

## Cibles

| Élément | Version | Remarques |
| --- | --- | --- |
| Client des joueurs | Minecraft 1.8.9 (protocole 47) | Rago Client (Forge 1.8.9) ou client vanilla : le serveur n'exige jamais Rago Client |
| Serveurs de jeu | PandaSpigot 1.8.8 | fork maintenu de Paper 1.8.8 ; implémente l'API Spigot 1.8.8 |
| Bytecode des modules | Java 8 | tout est compilé avec `--release 8`, même sur un JDK récent |
| Java des serveurs | Java 8 et Java 21 | le test de contrôle et le serveur de test local (`tools/local-server`) passent sous les deux ; le choix pour la production se fera au jalon infrastructure |

## Build

| Outil | Version | Remarques |
| --- | --- | --- |
| JDK de build | 17 ou plus (la CI utilise 21) | `--release 8` garantit que ni le bytecode ni l'API Java utilisée ne dépassent Java 8 |
| Gradle | 9.8.2, par le wrapper (`./gradlew`) | somme SHA-256 de la distribution vérifiée par le wrapper ; le jar du wrapper est vérifié en CI (`wrapper-validation`) |
| Node.js | 20 ou plus (la CI utilise 22) | seulement pour les bots du test de contrôle |

## Mod client `tools/kb-recorder`

Un build Gradle à part, parce que l'outillage Forge 1.8.9 ne tourne pas sous Gradle 9.

| Élément | Version | Remarques |
| --- | --- | --- |
| Minecraft Forge | 1.8.9-11.15.1.2318 | la dernière version de Forge pour 1.8.9, celle des clients PvP |
| Loom d'Essential (`gg.essential.loom`) | 0.10.0.5, figée | fork d'Architectury Loom maintenu par Essential, le seul outil de build actuel qui sait encore faire un mod Forge 1.8.9 ; servi par repo.essential.gg |
| `dev.architectury.architectury-pack200` | 0.1.3 | Pack200, retiré du JDK 14, dont l'installateur de Forge 1.8.9 a besoin |
| Mappings MCP | `mcp_stable` 22-1.8.9 | les noms de Forge 1.8.9 |
| Gradle | 8.8, par le wrapper de `tools/kb-recorder` | somme SHA-256 de la distribution vérifiée par le wrapper ; Loom 0.10 ne marche pas sous Gradle 9 |
| `org.gradle.toolchains.foojay-resolver-convention` | 0.8.0 | télécharge un JDK 8 pour compiler s'il n'y en a pas sur la machine |
| JDK | 17 pour lancer Gradle, 8 pour compiler | le mod tourne dans le client 1.8.9, sous Java 8 |

## Dépendances

| Dépendance | Version | Utilisée par | Pourquoi, et ce qui a été vérifié |
| --- | --- | --- | --- |
| `org.spigotmc:spigot-api` | 1.8.8-R0.1-SNAPSHOT | plugins (`compileOnly`, fournie par le serveur) | l'API que PandaSpigot implémente ; publiée seulement sur le dépôt Nexus de SpigotMC, limité dans le build aux groupes `org.spigotmc` et `org.bukkit`. Sa dépendance `net.md-5:bungeecord-chat:1.8-SNAPSHOT` (l'API des messages de chat cliquables) n'est plus servie ni par SpigotMC ni par Maven Central : elle est exclue (voir plus bas) |
| JUnit Jupiter | 5.14.4 | tests | JUnit 5 est la dernière ligne qui tourne encore sous Java 8 (JUnit 6 exige Java 17) |
| Gson | 2.15.0 | `tools/kb-control` et `tools/kb-analyzer` uniquement | lecture des fichiers JSON du test ; bytecode Java 8 vérifié ; jamais embarqué dans un plugin (les plugins écrivent leur JSON eux-mêmes, et le serveur 1.8.8 fournit déjà sa propre vieille Gson) |
| mineflayer | 4.39.0 | bots du test de contrôle (Node.js) | maintenu ; la version 1.8.8 fait partie des versions testées par le projet ; figée par `package-lock.json` |
| PandaSpigot | build 226 (`git-PandaSpigot-226`, MC 1.8.8) | serveur du test de contrôle | téléchargé depuis downloads.hpfxd.com ; somme SHA-256 `ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b`, vérifiée à chaque test ; le test de contrôle y passe sous Java 8 (108 coups sur 108 exacts) |

Les plugins n'embarquent pour l'instant que `rago-combat-model`, un module du projet sans
dépendance. Quand un plugin aura besoin d'une bibliothèque tierce, elle devra être relocalisée
(plugin Shadow) pour ne pas entrer en conflit avec celles du serveur ou d'autres plugins.

`bungeecord-chat` est exclu de `spigot-api` parce que la version 1.8-SNAPSHOT qu'elle demande
n'est plus téléchargeable. En 1.8.8, cette API n'apparaît que dans `Player.Spigot` et
`Server.Spigot`, si bien qu'un plugin qui n'envoie que du texte (`sendMessage(String)`) compile
sans elle, et le serveur fournit sa propre copie à l'exécution. Le premier plugin qui voudra des
messages cliquables (`player.spigot().sendMessage(...)`) devra ajouter en `compileOnly` une
version publiée de `bungeecord-chat`, après avoir vérifié qu'elle correspond à celle que contient
PandaSpigot.

## Dépôts Maven

Le build lit Maven Central par le miroir de Google
(`maven-central.storage-download.googleapis.com`) puis Maven Central lui-même : Maven Central
limite le débit des machines de CI, le miroir sert les mêmes fichiers. Le dépôt de SpigotMC ne
sert que les groupes cités plus haut.
