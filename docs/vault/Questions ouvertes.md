---
type: pilotage
tags: [pilotage, à-décider, à-vérifier]
maj: 2026-11-28
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

## Questions pour Abel (28/11, Block Method)
Pas sur la page (les fils lancés par Abel n'y ont pas accès) : à poser à Abel directement.
- Les **4 « Pattern » solo** de la fin de la vidéo « Block Method » (après le carton « All the techniques shown can be combined… », sans adversaire), et la séquence finale sans titre : à ajouter à Block Pattern Practice ou non ? ([[Entraînements]], [[RagoBot]])
- **Block Method 1v1** (pas commencé) : quelle **condition de victoire** ? quel **kit** ? quelle **map** ? Et le « full box » demandé pour RagoBot contredit l'**anti full box** du Block placement (`FullBox`, choix du 22/11 « Interdire de s'enfermer ») : l'autoriser dans ce duel seulement ? ([[Duels]])
- Block Pattern Practice (PR #58) : les formes des 10 techniques et le trajet du bot sont lus dans la vidéo, **à régler avec Abel en jeu** une fois installé.

## Réglée sans passer par la page
- **q128** (préparée le 26/11, jamais posée) : le [[Serveur de test]] tourne sous **JDK 17.0.21** (OBSERVÉ le 28/11 : le lanceur prend `JAVA_HOME` ; `java` seul = Java 8). Rien à ajouter sur la page.
- Abel, 26/11 : l'essai en jeu de la version du 25/11 n'était **pas encore fait** (toujours pas au 28/11, avec celles du 27/11 et du 28/11) ; Top Bed Fight 2v2 et Attack and Defense sont **finis** de son côté, il les envoie ([[État actuel]]). Les plugins de fireball qu'il a proposés : avis du 28/11, ne pas les installer ([[Boules de feu et TNT]]).
## Dans les propositions envoyées à Sofiene (pas sur la page)
Ces questions sont dans des documents de proposition ; leurs réponses arrivent dans les fils de travail. Détail : [[Plan de sortie publique]].
- **Propositions du 23/11** (https://claude.ai/artifact/HbZBGpwTZadVRtzdcsCyPY) : 1. RagoBot qui se déplace sur la map (3 questions) ; 2. RagoBot qui pose des blocs devant l'adversaire puis le combo (3 questions) ; 3. construire le lobby d'après l'image de référence (4 questions) ; 4. fireballs comme sur Minemen, à mesurer (2 questions).
- **Proposition du 25/11, duels entre parties dans tous les modes** (https://claude.ai/artifact/KkzxkuPALJ5WkmDx915nif) : 8 questions.
- **Étape 8 (administration et benchmark)** : document Claude Docs « Étape 8 : administration et benchmark — propositions » du 22/11, 9 questions (rejoindre une partie en cours, anticheat Grim, FPS benchmark).
- **Étape 7** : document « Étape 7 : propositions des nouveaux modes » (21/11), 13 questions ; une partie a été tranchée le 22/11 ([[Décisions]]), le reste garde des « valeurs à confirmer » ([[Fun Practice]], [[Red Rover et 3 Players]]).

## Valeurs « à confirmer » en jeu
Beaucoup de réglages ont été choisis par Claude en attendant l'avis de Sofiene en jeu : rayon et étages du Snowball Spleef, valeurs du Fireball Fight, captures et durées des CTF, écart de stuff du Bed Fight 1v2, temps de RagoBot (`ragobot.skill`), skin du PNJ Ranked, noms des catégories Fun Practice et 3 Players. Chaque note de mode les liste.

## À vérifier (sans question posée)
- Le skin des PNJ lu chez Mojang (`skin-joueur:`) n'a pas été vu en jeu ([[Lobby]]).
- Le domaine `discord.gg/ragooptimizer` sert à Rago Practice et à Rago Optimizer (choix de Sofiene du 22/11 : ce sont les liens officiels) ; le site utilise par défaut une autre invitation (`discord.gg/FXcvN7mWX`) : laquelle garder au lancement ? **Mis de côté** avec le site jusqu'à ce que le serveur soit fini (Abel, 26/11) ([[Site]]).
- Les listes datées de retours (22/11 au 25/11) sont citées comme celles de Sofiene ; depuis le 26/11, on sait que les essais en jeu sont faits par Abel. Les citations historiques n'ont pas été changées.
- `/speed` est déclaré par RagoPractice (Boxing) et comme alias de `/vitesse` par RagoBuildTools : lequel répond sur le serveur de test ? ([[Commandes et permissions]])
- Restées ouvertes depuis le [[Plan d'architecture v1]] : un compte Hypixel MVP++ pour les parties privées de mesure ? accepter un jour les clients 1.9+ ?
