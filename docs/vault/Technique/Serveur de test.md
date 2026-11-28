---
type: technique
tags: [serveur-de-test, local-server, build-tools]
maj: 2026-11-28
code: tools/local-server, tools/build-tools
---
# Serveur de test

**Le seul serveur qui existe** : il tourne sur le **PC Windows d'Abel, nommé « FULL PC GAMER »** (profil Windows `rgabe`, réponse d'Abel du 26/11 à q123). C'est là qu'Abel fait les essais en jeu, les réglages de maps et les séances de mesure ; quelques amis s'y connectent ; **pas ouvert au public**. Les installations s'y font seulement avec l'accord de Sofiene ou d'Abel ([[Installer une version sur le serveur de test]]). Mode d'emploi complet : `tools/local-server/README.md` (renommé `LISEZ-MOI.txt` dans le dossier assemblé). Retour à [[Projet]].

## Ce qui est connu (CONFIRMÉ par Abel le 26/11, OBSERVÉ sur le PC le 28/11, sinon OBSERVÉ dans le suivi, 21-25/11)
- **Dernière version du serveur** : dans **`C:\Users\rgabe\Downloads\rago-serveur-de-test (19)`** (Abel, 26/11). Mondes dans **`C:\Users\rgabe\Rago\mondes`** (le dossier `Rago` du profil Windows `rgabe`, `RAGO_DONNEES` par défaut).
- **Java : JDK 17.0.21** (OBSERVÉ le 28/11). Le lanceur prend `JAVA_HOME`, qui pointe sur ce JDK 17 ; la commande `java` seule donne Java 8 sur ce PC (ordre du lanceur : `RAGO_JAVA`, sinon `JAVA_HOME`, sinon `java`). La question q128 (17 ou 25 ?) est réglée : 17.
- **Accès** : depuis le 27/11, les sessions Claude Code tournent sur ce PC même (profil `rgabe`), avec le clone de travail `C:\Users\rgabe\rago-serveur-code` ; elles construisent, installent et poussent sur GitHub (Git Credential Manager) ([[Installer une version sur le serveur de test]]).
- Données à ne jamais supprimer : `plugins/RagoLobby/stats/`, `kits/`, `replays/`, `semaine.yml`, `skins.yml`, `sanctions.yml`, `panneaux/`, `mondes/lobby/points.yml`, les records de RagoPractice (`records.yml`, `reglages.yml`), les mondes du dossier `mondes`. Le monde `arenes` est refait à chaque démarrage (`rago-arenas.txt`) : rien à y garder.
- Points posés sur le lobby du serveur de test : `newshop`, `newdailychest` (sur un bloc de diamant), `newquest`, `newleaderboardselector`, `qrago`, `kalisodev`, `ragoclient`, `ragooptimizer`, `textremerciement`, `textremerciement2`, `ragobot` (-3 65 10, d'après le `config.yml`), etc. (dans `points.yml`).
- **Dossiers de `C:\Users\rgabe\Rago\mondes` le 28/11** (OBSERVÉ) : Acropolis, Ambush, Antenna, Aqil, Aquarium, Archway, ArchwayFFA, Arena, Artemis, BedFight, **Bedwars2012**, BioHazard, **Block In Practice RBW**, BlockClutchExtend, BlockClutchNew, BlockPlacement map 1, 2 et 4, Boletum, **BowPractice**, Bridge Fight Plateform, Carapace, Cascade, Catalyst, Chained, Crogorm, CtfBridge, CtfCastle, Eastwood, Extinction, Fireball Fight, Fort Royale, Frosted, Gelato, Invasion, Katsu, KbDisplace, Lectus, Lighthouse, Lotus, Minemen (Cave, Cobble, Fishtank, Goku, Lollipop, Pokemon, Redstone, Shy Guy), Nebuc, Obelisk, Paladin, Planet98, Ponsen, Rise, SkyRise, Space Mine, Stonekeep, Sumo, Swashbuckle, TopBedFight, Treenan, Ultimate Block-In Practice, White Crystal, et les mondes `arenes`, `bw-demo`, `lobby`, `world`, `world_the_end`. Ce que les modes attendent : [[Maps et mondes]]. Au 28/11, Abel doit encore poser le lobby d'attente de chaque map de Bedwars Normal et les spawns de First Rush ([[État actuel]]). CtfBridge et CtfCastle sont construites par le plugin au démarrage.
- Godemy se connectait par **Tailscale** pour les mesures du 07/11 ([[Mesures Hypixel]]).
- Dernière installation : **commit `03beb92`, le 28/11 vers 10h33** (PR #55 à #57 : Block Edit en barrière, Hypixel Bow Practice, niveau RBW du Block in) : jars rago-practice et rago-lobby remplacés (rago-bedwars est celui du 27/11, inchangé depuis), sauvegarde `sauvegarde-avant-maj-03beb92\` (`plugins\` et `mondes-arenes\`), sections ajoutées à la main dans les `config.yml`, mondes BowPractice et « Block In Practice RBW » copiés ; démarrage vérifié puis arrêt propre. Avant : la version de la **PR #54** le 27/11 au soir (liste d'Abel du 27/11 et 2012 Bedwars, map Bedwars2012 prête à 21h20), `2de1bca` (25/11), ab689ad (24/11), dfae3b7 et 50d79b6 (23/11), plusieurs le 22/11 (aa724b7, 75050b8, e0dadff, 3ae7bb7…), build 346 le 21/11. **Abel n'a encore rien joué en jeu des installations du 25/11 au 28/11** (28/11). Voir [[Installer une version sur le serveur de test]].

## Le dossier assemblé (artefact CI `rago-serveur-de-test`)
- `lancer-windows.bat` (+ `lancer.ps1`), `lancer-mac-linux.sh`, `server.properties`, `server-icon.png` (un R blanc sur un hexagone rouge), `plugins/` avec les 5 jars du dépôt.
- Au premier lancement, les scripts téléchargent **PandaSpigot 226, WorldEdit 6.1, PacketEvents 2.14.0** et vérifient leur SHA-256, puis le serveur 1.8.8 de Mojang. Demande d'accepter le CLUF.
- Mondes dans `RAGO_DONNEES/mondes` (`--world-container`), schematics, et **sauvegardes** (une copie zip des mondes à chaque lancement, 10 gardées). Mondes écrits toutes les 2 minutes, `/monde save` tout de suite.
- Réglages : `RAGO_JAVA`, `RAGO_ACCEPTE_CLUF=oui`, `RAGO_MEMOIRE` (2G), `RAGO_DONNEES` (`%USERPROFILE%\Rago`), `RAGO_SAUVEGARDES` (10).
- Mode en ligne (comptes vérifiés). Mettre à jour = décompresser la nouvelle version, elle retrouve les mondes ; redevenir op.

## RagoBuildTools (`tools/build-tools`, serveur de test seulement)
`/monde create|tp|import|spawn|save` (`rago.build.admin`), `/vitesse` (`/speed`, 1 à 10), `/nv` ; mondes de build : pas de pluie, toujours jour, pas de feu ni de monstres, seuls les opérateurs construisent (`rago.build.construire`). Une map doit venir de Minecraft 1.8 à 1.12 (1.13+ refusée). Bug corrigé : RagoBuildTools remettait en Survival ([[Bugs et problèmes connus]]). Code : [[Code de rago-build-tools]].

## Limites
Joué depuis le PC qui l'héberge, le ping est nul : la sensation des combats n'est pas celle d'un vrai serveur. 2 Go de mémoire.
