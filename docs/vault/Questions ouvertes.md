---
type: pilotage
tags: [pilotage, à-décider, à-vérifier]
maj: 2026-11-26
---
# Questions ouvertes

Ce que le vault ne sait pas encore. **Retirer chaque question dès qu'elle a une réponse** (de Sofiene, ou d'Abel pour ce qui touche le jeu, ses comptes et son PC), et mettre à jour les notes concernées ([[Mettre à jour le vault]]). Voir [[Décisions]] et [[État actuel]].

**Où Sofiene répond** : la page « Questions du vault Rago » (https://claude.ai/artifact/1mqjZuFNVqGT5JhBmG6HPk), partagée avec le vault de Rago Optimizer. Les questions de ce projet ont le thème « Vault du serveur Minecraft (…) ». Règle du 01/11 : **dès qu'un Claude a une question, il l'ajoute lui-même sur cette page**, sans attendre ni demander dans le fil.

Mode d'emploi (outil ArtifactData, collection `questions`) :
- **Poser** : créer un document `qNN` (numéro suivant le plus grand `num` existant) avec `num`, `theme`, `titre`, `question`, `contexte`, `choix` (2 à 4 réponses courtes ; « Je ne sais pas » est ajouté par la page), `precision` (facultatif), `statut: "ouverte"`, `reponse: {choix: null, texte: ""}`.
- **Lire** : au début de chaque tâche, lister les questions `ouverte` qui ont une réponse.
- **Traiter** : mettre le vault à jour selon la réponse, puis passer la question en `statut: "traitée"` avec `traitement` (une phrase).

**Accès (26/11)** : un fil Rago Hub lancé par Abel n'a pas pu lire cette page (« no access » depuis son compte). Les changements à y faire sont donc préparés ci-dessous et restent à appliquer par un compte qui y a accès.

## Sur la page (posée le 25/11, toujours ouverte)
- **q127** : quel domaine pour le serveur (`rago.gg` est affiché en jeu), et est-il acheté ? → **reportée, pas tranchée** : domaine, Discord, site et hébergeur mis de côté jusqu'à ce que le serveur soit fini (Abel, 26/11). Reste « ouverte » ([[Hébergement et production]], [[Site]]).

## Répondues le 26/11 (par Abel, dans Rago Hub), vault mis à jour
À passer en `statut: "traitée"` sur la page (pas encore fait : pas d'accès) :
- **q124** (fermer la PR #48 ?) : **fermée sans fusion le 26/11**, dépassée par #47. Traitement : « PR #48 fermée sans fusion le 26/11 (dépassée par #47) : Branches et PR, Pull requests, État actuel mis à jour. » ([[Branches et PR]])
- **q125** (PR #10, CI sous Java 25) : **fermée sans fusion le 26/11**, à refaire si l'hébergeur de production tourne sous Java 25. Traitement : « PR #10 fermée sans fusion le 26/11 : CI GitHub Actions, Branches et PR, Pull requests mis à jour. » ([[CI GitHub Actions]])
- **q122** (compte qRago) : c'est le compte d'**Abel**. Traitement : « qRago (OWNER) est le compte d'Abel : Personnes et comptes, Outils Owner mis à jour (26/11). »
- **q123** (PC du serveur de test) : le PC d'**Abel**, « FULL PC GAMER », profil Windows `rgabe` ; dernière version dans `C:\Users\rgabe\Downloads\rago-serveur-de-test (19)`. Traitement : « Serveur de test sur le PC d'Abel FULL PC GAMER : Serveur de test, État actuel, Installer une version, Reprendre le développement mis à jour (26/11). »
- **q126** (projet à part ?) : **projet à part**, pas une partie de Rago Client. Traitement : « Rago Practice est un projet à part : Autres projets Rago et Projet mis à jour (26/11). »

## Nouvelles questions préparées le 26/11 (à ajouter sur la page)
- **q128** : le [[Serveur de test]] tourne-t-il sous **Java 17** (ce que dit le suivi des 21-25/11) ou **Java 25** (ce que dit la description de la PR #10 du 11/11) ? **À VÉRIFIER** : Abel ne sait pas (26/11) ; la réponse est en tête de la fenêtre de `lancer-windows.bat` (ligne « Java: … »).
- Répondu sans passer par la page (Abel, 26/11) : l'essai en jeu de la version du 25/11 n'est **pas encore fait** ; Top Bed Fight 2v2 et Attack and Defense sont **finis** de son côté, il les envoie, avec des plugins qui imitent les fireballs d'Hypixel ([[État actuel]]).
## Dans les propositions envoyées à Sofiene (pas sur la page)
Ces questions sont dans des documents de proposition ; leurs réponses arrivent dans les fils de travail. Détail : [[Plan de sortie publique]].
- **Propositions du 23/11** (https://claude.ai/artifact/HbZBGpwTZadVRtzdcsCyPY) : 1. RagoBot qui se déplace sur la map (3 questions) ; 2. RagoBot qui pose des blocs devant l'adversaire puis le combo (3 questions) ; 3. construire le lobby d'après l'image de référence (4 questions) ; 4. fireballs comme sur Minemen, à mesurer (2 questions).
- **Proposition du 25/11, duels entre parties dans tous les modes** (https://claude.ai/artifact/KkzxkuPALJ5WkmDx915nif) : 8 questions.
- **Étape 8 (administration et benchmark)** : document Claude Docs « Étape 8 : administration et benchmark — propositions » du 22/11, 9 questions (rejoindre une partie en cours, anticheat Grim, FPS benchmark).
- **Étape 7** : document « Étape 7 : propositions des nouveaux modes » (21/11), 13 questions ; une partie a été tranchée le 22/11 ([[Décisions]]), le reste garde des « valeurs à confirmer » ([[Fun Practice]], [[Red Rover et 3 Players]]).

## Valeurs « à confirmer » en jeu
Beaucoup de réglages ont été choisis par Claude en attendant l'avis de Sofiene en jeu : rayon et étages du Snowball Spleef, valeurs du Fireball Fight, durée et forges de 2017 Bedwars, captures et durées des CTF, écart de stuff du Bed Fight 1v2, temps de RagoBot (`ragobot.skill`), skin du PNJ Ranked, noms des catégories Fun Practice et 3 Players. Chaque note de mode les liste.

## À vérifier (sans question posée)
- Le skin des PNJ lu chez Mojang (`skin-joueur:`) n'a pas été vu en jeu ([[Lobby]]).
- Le domaine `discord.gg/ragooptimizer` sert à Rago Practice et à Rago Optimizer (choix de Sofiene du 22/11 : ce sont les liens officiels) ; le site utilise par défaut une autre invitation (`discord.gg/FXcvN7mWX`) : laquelle garder au lancement ? **Mis de côté** avec le site jusqu'à ce que le serveur soit fini (Abel, 26/11) ([[Site]]).
- Les listes datées de retours (22/11 au 25/11) sont citées comme celles de Sofiene ; depuis le 26/11, on sait que les essais en jeu sont faits par Abel. Les citations historiques n'ont pas été changées.
- `/speed` est déclaré par RagoPractice (Boxing) et comme alias de `/vitesse` par RagoBuildTools : lequel répond sur le serveur de test ? ([[Commandes et permissions]])
- Restées ouvertes depuis le [[Plan d'architecture v1]] : un compte Hypixel MVP++ pour les parties privées de mesure ? accepter un jour les clients 1.9+ ?
