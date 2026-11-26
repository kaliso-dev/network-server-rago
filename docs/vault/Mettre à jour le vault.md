---
type: règle
tags: [règle, vault]
maj: 2026-11-26
---
# Mettre à jour le vault

> **Règle n°1 (Sofiene, 31/10/2026, pour tous les projets Rago).** Ce vault doit correspondre **en tout point** à l'état actuel du projet : chaque mode, chaque fonctionnement, chaque décision, chaque chose en cours. Il ne doit **jamais** contenir une info fausse, périmée ou abandonnée. Tout Claude qui travaille sur le projet en est responsable.

Ce vault est la **mémoire du projet** : la mémoire intégrée de Claude est désactivée chez Sofiene, et il change de compte Claude (passage à Claude Team, 25/11) ; ce qui n'est pas écrit ici est perdu à la fin d'un fil. Retour à [[Projet]].

## 1. Avant de commencer
1. Lire [[Projet]], [[État actuel]], [[Règles de travail]], puis les notes du sujet. Pour coder : [[Reprendre le développement]].
2. **Comparer avec la réalité** : le code de la bonne branche (aujourd'hui la PR #47, voir [[Branches et PR]]), les PR ouvertes, `docs/suivi-sortie-publique.md`, ce que dit Sofiene. Toute différence est corrigée dans le vault **dans la même tâche**.
3. Lire les réponses de Sofiene pas encore traitées sur la page « Questions du vault Rago » (mode d'emploi dans [[Questions ouvertes]]) et les appliquer.

## 2. Pendant le travail : détecter tout changement
À chaque info nouvelle, se demander : **est-ce que ça change une note ?**
- un mode ajouté, modifié, renommé, retiré des menus, déplacé de catégorie ;
- une règle de jeu (kit, points pour gagner, durée, forge, prix…) ;
- une version installée sur le serveur de test, une PR ouverte ou fusionnée ;
- une décision de Sofiene (même dite en passant), une réponse à une proposition ;
- un bug trouvé ou corrigé ;
- **une chose arrêtée ou abandonnée**.

## 3. À la fin de chaque tâche (obligatoire, avant de dire « fini »)
1. **Mettre à jour** la note de chaque sujet touché pour qu'elle décrive **l'état réel d'aujourd'hui** (pas l'historique).
2. **Supprimer** ce qui n'est plus vrai : un mode ou une fonction abandonnés perdent leur description ; il reste **une ligne** dans [[Décisions]] > « Abandonné » (date, quoi, raison). Un mode seulement *retiré des menus* (code gardé) reste listé comme tel dans [[Catalogue des modes]].
3. [[État actuel]] : branche, PR ouvertes, dernière installation sur le serveur de test, ce qui attend Abel (en jeu) et Sofiene (décisions et accords).
4. [[Journal]] : une ligne datée par événement marquant.
5. [[Décisions]] : chaque décision de Sofiene, datée, avec ses mots entre « ».
6. [[Bugs et problèmes connus]] ; [[Questions ouvertes]] (retirer les questions répondues).
7. **Notes de code** (dossier `Code/`) : une classe ajoutée, renommée ou supprimée → sa ligne dans la note du module ; un module ajouté → une nouvelle note `Code de <module>` reliée depuis [[Projet]] et [[Architecture]].
8. **Fiches techniques** : une commande, une section de `config.yml`, un job de CI, un outil qui change → [[Commandes et permissions]], [[Configuration]], [[CI GitHub Actions]], [[Tests et bots]].
9. Le `docs/suivi-sortie-publique.md` reste le journal détaillé du plan ; le vault doit dire la même chose que lui en résumé.
10. Mettre `maj:` à la date du jour dans l'en-tête des notes touchées.
11. Vérifier qu'aucun lien `[[…]]` n'est cassé et que chaque nouvelle note est reliée depuis [[Projet]] ou une note de carte.

## 4. En cas de doute
- **Ne jamais deviner.** Si on ne sait pas si une chose est faite, prévue ou abandonnée : ajouter soi-même la question sur la page « Questions du vault Rago » (règle de Sofiene du 01/11, mode d'emploi dans [[Questions ouvertes]]), l'écrire « À VÉRIFIER » dans la note et dans [[Questions ouvertes]], et prévenir Sofiene en une ligne.
- Le code, git et la parole la plus récente de Sofiene font foi, dans cet ordre pour les faits, et Sofiene pour les décisions.

## 5. Règles d'écriture
- Français simple, phrases complètes. Une note par sujet ; nom de fichier = titre, unique dans tout le vault (les liens se font par nom). Les noms des modes et des commandes restent ceux du jeu (anglais).
- Précis : ids exacts des modes (`bed-fight-1v1`), valeurs exactes, sections de `config.yml`, classes Java.
- Statut et source pour ce qui n'est pas évident (CONFIRMÉ, DÉCIDÉ, OBSERVÉ, IMPLÉMENTÉ, TESTÉ EN CI, À VÉRIFIER).
- **Relier** : chaque note pointe vers au moins deux autres notes.
- **Jamais de secret** : seulement les noms de champs ou de variables ([[Clés et secrets]]).
- Les maps, mondes, captures et vidéos restent hors du dépôt : le vault ne garde que leur chemin ([[Sources]]).
