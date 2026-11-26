---
type: technique
tags: [serveur-de-test, local-server, build-tools]
maj: 2026-11-26
code: tools/local-server, tools/build-tools
---
# Serveur de test

**Le seul serveur qui existe** : il tourne sur le **PC Windows d'Abel, nommé « FULL PC GAMER »** (profil Windows `rgabe`, réponse d'Abel du 26/11 à q123). C'est là qu'Abel fait les essais en jeu, les réglages de maps et les séances de mesure ; quelques amis s'y connectent ; **pas ouvert au public**. Les installations s'y font seulement avec l'accord de Sofiene ou d'Abel ([[Installer une version sur le serveur de test]]). Mode d'emploi complet : `tools/local-server/README.md` (renommé `LISEZ-MOI.txt` dans le dossier assemblé). Retour à [[Projet]].

## Ce qui est connu (CONFIRMÉ par Abel le 26/11, sinon OBSERVÉ dans le suivi, 21-25/11)
- **Dernière version du serveur** : dans **`C:\Users\rgabe\Downloads\rago-serveur-de-test (19)`** (Abel, 26/11). Mondes dans **`C:\Users\rgabe\Rago\mondes`** (le dossier `Rago` du profil Windows `rgabe`, `RAGO_DONNEES` par défaut).
- **Java sur ce PC : À VÉRIFIER.** Le suivi (21-25/11) dit **Java 17** au démarrage ; la description de la PR #10 (11/11) dit que le serveur de test local est lancé sous **Java 25**. Abel ne sait pas (26/11) et aucun dossier de son PC n'est partagé avec les sessions Claude (Remote Control). Le lanceur l'écrit au démarrage, en tête de la fenêtre de `lancer-windows.bat` (ligne « Java: … », Java choisi par `RAGO_JAVA`, sinon `JAVA_HOME`, sinon `java`). Question **q128** préparée ([[Questions ouvertes]]).
- Données à ne jamais supprimer : `plugins/RagoLobby/stats/`, `kits/`, `replays/`, `semaine.yml`, `skins.yml`, `sanctions.yml`, `panneaux/`, `mondes/lobby/points.yml`, les records de RagoPractice (`records.yml`, `reglages.yml`), les mondes du dossier `mondes`. Le monde `arenes` est refait à chaque démarrage (`rago-arenas.txt`) : rien à y garder.
- Points posés sur le lobby du serveur de test : `newshop`, `newdailychest` (sur un bloc de diamant), `newquest`, `newleaderboardselector`, `qrago`, `kalisodev`, `ragoclient`, `ragooptimizer`, `textremerciement`, `textremerciement2`, etc. (dans `points.yml`).
- Maps : celles que les modes attendent (les 20 maps RBW, SkyRise, les maps First Rush, ArchwayFFA, Bed Fight, BioHazard…, liste dans [[Maps et mondes]]) ; la liste exacte des dossiers présents sur le PC n'est pas dans le dépôt (À VÉRIFIER). Au 26/11, Abel doit encore poser le lobby d'attente de chaque map de Bedwars Normal et les spawns de First Rush ([[État actuel]]). CtfBridge et CtfCastle sont construites par le plugin au démarrage.
- Godemy se connectait par **Tailscale** pour les mesures du 07/11 ([[Mesures Hypixel]]).
- Dernière installation : **commit 2de1bca, le 25/11** (après « Tu peux installer oui » de 14h47) ; avant : ab689ad (24/11), dfae3b7 et 50d79b6 (23/11), plusieurs le 22/11 (aa724b7, 75050b8, e0dadff, 3ae7bb7…), build 346 le 21/11. Voir [[Installer une version sur le serveur de test]].

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
