---
type: pilotage
tags: [journal]
maj: 2026-11-28
---
# Journal

Une ligne datée par événement marquant, la plus récente en haut. L'histoire racontée : [[Chronologie du projet]]. Les PR : [[Pull requests]]. Retour à [[Projet]].

| Date | Événement | Note |
|---|---|---|
| 28/11 | `3316b1f` installé vers 16h30 (serveur arrêté par Abel à 15h46, personne connecté) : jars rago-lobby et rago-bedwars, mode `block-method-1v1`, `ragobot.seul` et maps ajoutés aux `config.yml` (script), mondes GodMethod1, GodMethod2, MapV2 et MinemenBedFight copiés, sauvegarde `sauvegarde-avant-maj-3316b1f\plugins\` ; démarrage vérifié (92 modes, 4 maps prêtes, aucune erreur) ; essai local avec un bot sur GodMethod1 et GodMethod2 | [[Installer une version sur le serveur de test]] |
| 28/11 | PR #61 fusionnée à 16h28 (`3316b1f`, CI 14/14) : **Block Method 1v1** (Bed Fight en entraînement contre un RagoBot moins fort, mort = la manche recommence, bâton de blaze, carte pour changer de map), bot de CI `block-method-bots.mjs` | [[Duels]], [[RagoBot]] |
| 28/11 | Réponses d'Abel sur Block Method 1v1 : contre RagoBot « en beaucoup moins fort », « On gagne pas, c'est du practice », kit de Bed Fight, ses 4 maps, RagoBot sort de la full box, les « Pattern » oui | [[Décisions]] |
| 28/11 | PR #60 fusionnée à 15h30 (`10eaad8`) : README de rago-practice (Hypixel Bow Practice, Block Pattern Practice, niveau RBW du Block in) et de rago-bedwars (2012 Bedwars) ; liste de vérification envoyée à Abel ; questions sur Block Method 1v1 | [[Entraînements]], [[Bed Wars]] |
| 28/11 | `d050af8` installé vers 11h29 (personne connecté) : jars rago-practice et rago-lobby, mode `block-pattern-practice` ajouté au `config.yml` du lobby (script), sauvegarde `sauvegarde-avant-maj-d050af8\plugins\` ; démarrage vérifié (91 modes, zones d'arène de nouveau dans l'ordre d'avant la #56), arrêt propre | [[Installer une version sur le serveur de test]] |
| 28/11 | PR #58 fusionnée à 11h28 (`d050af8`, CI 14/14 après le correctif `69c78ac` du script de test) : Block Pattern Practice (10 premières techniques de Block Method), correction de l'ordre d'enregistrement des modes de RagoPractice, test des bots `pattern-bots.mjs` | [[Entraînements]], [[Bugs et problèmes connus]] |
| 28/11 | Avis sur les deux plugins de fireball proposés par Abel (BedWars1058 DirectionalFireballs, Fireball Launcher) : ne pas installer, la mesure kb-recorder reste la solution | [[Boules de feu et TNT]] |
| 28/11 | Java du serveur de test lu : JDK 17.0.21 (`JAVA_HOME`) | [[Serveur de test]] |
| 28/11 | `03beb92` installé vers 10h33 (personne connecté) : jars rago-practice et rago-lobby, sections `bow-practice:`, `block-in.rbw:` et mode `bow-practice` ajoutés à la main, mondes BowPractice et « Block In Practice RBW » copiés ; démarrage vérifié (2012 Bedwars chargé, 90 modes), arrêt propre ; rien joué en jeu | [[Installer une version sur le serveur de test]] |
| 28/11 | PR #55, #56, #57 fusionnées dans la branche de la #47 (CI 14/14) : Block edit en barrière, Hypixel Bow Practice, niveau RBW du Block in | [[Pull requests]], [[Entraînements]] |
| 27/11 | Réponses d'Abel : Bow Practice « 2 touche », « Teinture oui comme les autres niveaux », Block in RBW « Niveau », barrière partout « Oui », Block Method « Oui je suis d'accord » (10 techniques d'abord) | [[Décisions]] |
| 27/11 | Vidéo « Block Method » d'Abel étudiée : 34 techniques contre un adversaire, puis 4 « Pattern » solo ; plan Block Pattern Practice et Block Method 1v1 | [[Entraînements]], [[RagoBot]] |
| 27/11 | PR #54 fusionnée dans la branche de la #47 (`1bf902d`) et installée le soir : liste d'Abel du 27/11 pour le lobby et les menus, 2012 Bedwars à la place de 2017 Bedwars (map Bedwars2012) | [[Boussole et menus]], [[Bed Wars]] |
| 26/11 | Réponses d'Abel dans Rago Hub : qRago et le PC du serveur de test (« FULL PC GAMER ») sont à lui, dernière version dans `Downloads\rago-serveur-de-test (19)` ; c'est lui qui fait essais en jeu, réglages de maps et mesures ; Rago Practice est un projet à part ; domaine, Discord, site et hébergeur mis de côté ; maps et modes finis, restent le lobby d'attente de Bedwars Normal, les spawns de First Rush et la mesure des boules de feu | [[État actuel]], [[Personnes et comptes]] |
| 26/11 | Abel : essai en jeu de la version du 25/11 pas encore fait ; Top Bed Fight 2v2 et Attack and Defense finis, à envoyer ; plugins qui imitent les fireballs d'Hypixel trouvés (piste provisoire) ; Java du serveur de test inconnu | [[État actuel]], [[Boules de feu et TNT]] |
| 26/11 | Sofiene : « l'accord d'abel suffira désormais » (GitHub et serveur de test) | [[Règles de travail]] |
| 26/11 | PR #48 et #10 **fermées sans fusion** (demande d'Abel, conseil du Chef) ; PR #53 ouverte vers `main` pour la mise à jour du vault (option A) | [[Branches et PR]], [[Décisions]] |
| 26/11 | PR #50, #51, #52 fusionnées dans `main` : hook Rago Hub (`.claude/`), les fils de travail sont lancés depuis Rago Hub | [[Branches et PR]] |
| 25/11 | PR #49 fusionnée dans `main` : ce vault et `CLAUDE.md` | [[Pull requests]] |
| 25/11 | Vault créé (`docs/vault/`) pour reprendre le projet sur un nouveau compte Claude ; 6 questions posées (q122 à q127) | [[Mettre à jour le vault]] |
| 25/11 | `2de1bca` installé sur le serveur de test (lobby et bedwars changent), serveur arrêté, pas joué | [[Installer une version sur le serveur de test]] |
| 25/11 | Liste de 9h19 : Private game refaite, party 60 s après déconnexion, duels entre parties 2v2 ; CI 14/14 | [[Parties privées]], [[Party, amis et messages]] |
| 24/11 | `ab689ad` installé (lobby d'attente des parties privées, remise à zéro des stats au démarrage) | [[Installer une version sur le serveur de test]] |
| 23/11 | Liste de 20h54 après le test en jeu : 20 points (déconnexion Bed Wars, anti full box, UTF-8 des config, 2017 Bedwars, Bedwars Modes, /ranked, reset des stats…) ; 4 propositions envoyées | [[Plan de sortie publique]] |
| 23/11 | FFA Archway et One Hit One Kill en partie privée ; RagoBot étape 3 (Block Edit) ; `dfae3b7` puis `50d79b6` installés | [[Parties privées]], [[RagoBot]] |
| 22/11 | Grosse journée : Solo / Party / Ranked, six duels classés, CTF, Fast Bedwars, Parkour Duel, Snowball Spleef 2 étages, Block Aim, Bed Fight 1v2, RagoBot (étapes 1 et 2), outils Owner, salle des remerciements, Daily Chest animé, moins de rouge, nouveaux skins de PNJ ; 5 installations | [[Plan de sortie publique]] |
| 21/11 | Plan de sortie publique de Sofiene ; audit ; bugs critiques corrigés (reset des arènes, spectateurs, joueurs invisibles, stats, kit Bed Fight, Block In) ; PR #47 ouverte | [[Plan de sortie publique]] |
| 21/11 | PR #48 (events) ouverte, dépassée le 22/11 par #47 | [[Branches et PR]] |
| 14/11 | PR #36 à #46 fusionnées : Bed Break Simulator, Pearl et Fireball practice, KB displace dans l'axe, Clutch Scenario sur Sky Rise et Archway, panneaux publicitaires, split de la forge, Lobby Fight FFA, test des panneaux avec un vrai client 1.8.9, Invis et Bed Break en 1v1 à 1v4 | [[Pull requests]] |
| 13/11 | Site officiel (Next.js) : système de design, première version, refonte « Master your skills », police de Minecraft (PR #30, #32, #35) ; Ranked sans priorité des rangs payants | [[Site]] |
| 12/11 | PR #12 à #29 : replays, menus Settings des entraînements, 3 files à la fois, catégories Arenas, premier menu, quêtes, records, titres, prestiges, /spectate, bans classés, bridge chronométré, couleurs du serveur, KB verrouillé | [[Pull requests]] |
| 11/11 | PR #1, #4, #9, #11 fusionnées dans `main` : labo du knockback, serveur de build, lobby, practice et Bed Wars | [[Pull requests]] |
| 10/11 | Les minutes GitHub Actions du compte sont épuisées par ce dépôt (privé) ; Sofiene décide de le rendre public | [[CI GitHub Actions]] |
| 08/11 | Lobby, files, duels, practice et RagoBedwars (Ranked Bedwars 1v1 à 4v4) | [[Chronologie du projet]] |
| 07/11 | Mesure du knockback d'Hypixel (Housing et Bed Wars) ; profil `hypixel-approx-v1` ; ping simulé essayé puis abandonné | [[Mesures Hypixel]] |
| 07/11 | Premier commit du dépôt (00:38 UTC) : build Gradle, `rago-combat-model`, RagoCombatEngine, test de contrôle | [[Chronologie du projet]] |
| 06/11 | Document « Rago Practice — Architecture technique v1 » | [[Plan d'architecture v1]] |
