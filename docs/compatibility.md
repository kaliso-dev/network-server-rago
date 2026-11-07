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
| Java des serveurs | Java 8 pour le test de contrôle | le choix pour la production (8 ou plus récent, PandaSpigot accepte les deux) se fera au jalon infrastructure |

## Build

| Outil | Version | Remarques |
| --- | --- | --- |
| JDK de build | 17 ou plus (la CI utilise 21) | `--release 8` garantit que ni le bytecode ni l'API Java utilisée ne dépassent Java 8 |
| Gradle | 9.8.2, par le wrapper (`./gradlew`) | somme SHA-256 de la distribution vérifiée par le wrapper ; le jar du wrapper est vérifié en CI (`wrapper-validation`) |
| Node.js | 20 ou plus (la CI utilise 22) | seulement pour les bots du test de contrôle |

## Dépendances

| Dépendance | Version | Utilisée par | Pourquoi, et ce qui a été vérifié |
| --- | --- | --- | --- |
| `org.spigotmc:spigot-api` | 1.8.8-R0.1-SNAPSHOT | plugins (`compileOnly`, fournie par le serveur) | l'API que PandaSpigot implémente ; publiée seulement sur le dépôt Nexus de SpigotMC, limité dans le build aux groupes `org.spigotmc`, `org.bukkit` et `net.md-5` |
| JUnit Jupiter | 5.14.4 | tests | JUnit 5 est la dernière ligne qui tourne encore sous Java 8 (JUnit 6 exige Java 17) |
| Gson | 2.15.0 | `tools/kb-control` uniquement | lecture des fichiers JSON du test ; bytecode Java 8 vérifié ; jamais embarqué dans un plugin (les plugins écrivent leur JSON eux-mêmes, et le serveur 1.8.8 fournit déjà sa propre vieille Gson) |
| mineflayer | 4.39.0 | bots du test de contrôle (Node.js) | maintenu ; la version 1.8.8 fait partie des versions testées par le projet ; figée par `package-lock.json` |
| PandaSpigot | dernier build 1.8.8 | serveur du test de contrôle | téléchargé depuis downloads.hpfxd.com ; sa somme SHA-256 est affichée à chaque test, et vérifiée si `PANDASPIGOT_SHA256` est donnée |

Les plugins n'embarquent pour l'instant que `rago-combat-model`, un module du projet sans
dépendance. Quand un plugin aura besoin d'une bibliothèque tierce, elle devra être relocalisée
(plugin Shadow) pour ne pas entrer en conflit avec celles du serveur ou d'autres plugins.

## Dépôts Maven

Le build lit Maven Central par le miroir de Google
(`maven-central.storage-download.googleapis.com`) puis Maven Central lui-même : Maven Central
limite le débit des machines de CI, le miroir sert les mêmes fichiers. Le dépôt de SpigotMC ne
sert que les groupes cités plus haut.
