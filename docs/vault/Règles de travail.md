---
type: règle
tags: [règle]
maj: 2026-11-25
---
# Règles de travail

Règles en vigueur au 25/11/2026 pour ce dépôt. Elles viennent du `README.md` du dépôt (« Règles du projet »), du suivi de la sortie publique (`docs/suivi-sortie-publique.md`) et des règles générales de Sofiene pour tous les projets Rago (vault de Rago Optimizer). Détail daté dans [[Décisions]]. Retour à [[Projet]].

## Règles de Sofiene (tous les projets Rago)
1. **Rien sur GitHub sans demande explicite de Sofiene** : préparer est permis (branche, PR), fusionner non. « fusionne » / « push » de Sofiene = accord pour fusionner la PR concernée.
2. **Rien d'installé sur le [[Serveur de test]] sans son accord** : chaque installation du suivi attend son « oui » / « Tu peux installer » (ex. 24/11 à 14h46, 25/11 à 14h47). Voir [[Installer une version sur le serveur de test]].
3. **Aucun secret** dans le dépôt, le vault, un commit, une PR ou le chat ([[Clés et secrets]]).
4. **Ne jamais affirmer sans preuve** : un statut TESTÉ n'est donné que si c'est vérifié (tests, bots en CI, ou essai en jeu signalé comme tel). Ce qui n'a pas été joué en jeu est dit « pas encore joué en jeu ».
5. **Ne jamais deviner** : une question à Sofiene plutôt qu'une supposition ; une valeur choisie par Claude est écrite « à confirmer ». Les gros changements passent d'abord par une **proposition** (Claude Docs ou Artifact) avant tout code (règle de l'étape 7 du plan, appliquée depuis à chaque demande floue).
6. **Répondre en français**, simplement ; le jeu lui-même est en **anglais** (messages, menus, PNJ, commandes). Les anciennes commandes françaises (`/file`, `/monde creer`…) restent comme alias.
7. **Le vault correspond en tout point à l'état du projet** : le lire avant, le mettre à jour à la fin de chaque tâche ([[Mettre à jour le vault]]).

## Règles du projet (README du dépôt)
- Des **modules séparés**, pas un plugin géant ; pas de plugin abandonné comme base.
- Tout ce qui tourne dans un serveur 1.8.8 reste **compatible Java 8** (`--release 8`), et **le thread principal ne touche jamais au disque** ni à la base de données (lectures et écritures de fichiers sur un autre thread).
- Chaque dépendance est vérifiée et listée dans `docs/compatibility.md` ([[Dépendances et compatibilité]]).
- Chaque mécanique importante est testée ; une ébauche n'est jamais présentée comme terminée.
- **Les paramètres de knockback d'Hypixel ne sont jamais inventés** : ils viennent de mesures. Une nouvelle mesure donne un nouveau profil (`hypixel-approx-v2`), on ne modifie pas un profil déjà mesuré ([[Knockback et combat]]).
- Les maps (mondes) ne sont pas dans le dépôt ([[Maps et mondes]]).

## Règles du plan de sortie publique (21/11)
- Ordre imposé : audit, bugs critiques, puis les étapes 3 à 9 dans l'ordre ; les nouveaux modes ne commencent qu'une fois les systèmes communs stabilisés, chaque concept étant d'abord proposé ([[Plan de sortie publique]]).
- Aucune tâche n'est retirée du suivi parce qu'elle a commencé ; un mode retiré à la demande de Sofiene garde son code et ses données (records), il est seulement débranché des menus.
- **Avant chaque installation** sur le serveur de test : copie complète du dossier `plugins/`.
- **Données à ne jamais supprimer** : stats, kits arrangés, records, replays, rangs et sanctions, `points.yml` du lobby, skins en cache, mondes du dossier `mondes`. Le monde `arenes` est refait à chaque démarrage, rien n'y est à garder.
- **Plus aucune remise à zéro** des stats, records ou classements jusqu'à la sortie publique (Sofiene, 23/11).

## PR et commits
- Commits : en **anglais**, une phrase qui décrit l'effet (« Party: a member who disconnects has 60 seconds to rejoin ») ; une demande de Sofiene est souvent citée « (the owner, 23/11) ».
- Titres et descriptions de PR : en **français** (« Pourquoi », « Changements », « Tests », avec ce qui n'est pas testé en jeu).
- Branches `claude/…`, PR vers `main` ; pas de branche de staging dans ce dépôt ([[Branches et PR]]).
- Le document de suivi `docs/suivi-sortie-publique.md` est mis à jour à chaque demande (statut, commit, test, installation) : c'est le journal détaillé ; ce vault en donne l'état.
