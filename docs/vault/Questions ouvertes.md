---
type: pilotage
tags: [pilotage, à-décider, à-vérifier]
maj: 2026-11-25
---
# Questions ouvertes

Ce que le vault ne sait pas encore. **Retirer chaque question dès que Sofiene y répond**, et mettre à jour les notes concernées ([[Mettre à jour le vault]]). Voir [[Décisions]] et [[État actuel]].

**Où Sofiene répond** : la page « Questions du vault Rago » (https://claude.ai/artifact/1mqjZuFNVqGT5JhBmG6HPk), partagée avec le vault de Rago Optimizer. Les questions de ce projet ont le thème « Vault du serveur Minecraft (…) ». Règle du 01/11 : **dès qu'un Claude a une question, il l'ajoute lui-même sur cette page**, sans attendre ni demander dans le fil.

Mode d'emploi (outil ArtifactData, collection `questions`) :
- **Poser** : créer un document `qNN` (numéro suivant le plus grand `num` existant) avec `num`, `theme`, `titre`, `question`, `contexte`, `choix` (2 à 4 réponses courtes ; « Je ne sais pas » est ajouté par la page), `precision` (facultatif), `statut: "ouverte"`, `reponse: {choix: null, texte: ""}`.
- **Lire** : au début de chaque tâche, lister les questions `ouverte` qui ont une réponse.
- **Traiter** : mettre le vault à jour selon la réponse, puis passer la question en `statut: "traitée"` avec `traitement` (une phrase).

## Sur la page (posées le 25/11, en attente)
- **q122** : à qui est le compte Minecraft **qRago** (Owner) ? ([[Personnes et comptes]], [[Outils Owner]])
- **q123** : sur quel PC tourne le [[Serveur de test]] (`C:\Users\rgabe\Rago`) ?
- **q124** : fermer la PR #48, dépassée par #47 ? ([[Branches et PR]])
- **q125** : la PR #10 (CI sous Java 25) : fermer, refaire ou laisser ? ([[CI GitHub Actions]])
- **q126** : Rago Practice est-il un projet à part ou une partie de Rago Client ? ([[Autres projets Rago]])
- **q127** : quel domaine pour le serveur (`rago.gg` est affiché en jeu), et est-il acheté ? ([[Hébergement et production]], [[Site]])

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
- Le domaine `discord.gg/ragooptimizer` sert à Rago Practice et à Rago Optimizer (choix de Sofiene du 22/11 : ce sont les liens officiels) ; le site utilise par défaut une autre invitation (`discord.gg/FXcvN7mWX`) : laquelle garder au lancement ? ([[Site]])
- `/speed` est déclaré par RagoPractice (Boxing) et comme alias de `/vitesse` par RagoBuildTools : lequel répond sur le serveur de test ? ([[Commandes et permissions]])
- Restées ouvertes depuis le [[Plan d'architecture v1]] : un compte Hypixel MVP++ pour les parties privées de mesure ? accepter un jour les clients 1.9+ ?
