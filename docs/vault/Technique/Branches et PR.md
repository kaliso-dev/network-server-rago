---
type: technique
tags: [git, branches, pr]
maj: 2026-11-28
---
# Branches et PR

Retour à [[Reprendre le développement]]. Liste complète : [[Pull requests]].

## État au 28/11/2026 (CONFIRMÉ, git et GitHub)
- **`main`** : `97d674f`, 26/11 12:24 (heure de Paris), fusion de la PR #52. Le **code du jeu** n'a pas bougé depuis le 14/11 (PR #46) ; depuis, `main` a reçu seulement ce vault et `CLAUDE.md` (PR #49, 25/11) et le hook Rago Hub dans `.claude/` (PR #50, #51, #52, 26/11).
- **PR #47 ouverte** : branche `claude/project-thread-wi9w0x`, **187 commits, 328 fichiers, +45 211 / −2 137**, dernier commit `d050af8` le 28/11 à 11h28 (fusion de la PR #58), sans conflit avec `main` (7 commits de retard, sans fichier en commun). **CI 14/14 verte** sur `03beb92` et sur la tête de chacune des PR #54 à #58 ; sur `d050af8`, encore en cours au moment de cette mise à jour. **C'est là qu'est tout le travail sur le jeu depuis le 14/11** (le plan de sortie publique du 21/11 : Fun Practice, CTF, RagoBot, Outils Owner, Bedwars Normal, Daily Chest, Ranked, duels entre parties… ; le 27-28/11 : liste d'Abel du 27/11, 2012 Bedwars, Block Edit en barrière, Hypixel Bow Practice, niveau RBW du Block in, Block Pattern Practice) et `docs/suivi-sortie-publique.md`. Le serveur de test tourne cette branche (`d050af8`, 28/11). **Cette branche n'a ni le vault, ni `CLAUDE.md`, ni `.claude/`.**
- **Petites PR fusionnées dans la branche de la #47** (base `claude/project-thread-wi9w0x`, pas `main`) : **#54** (`claude/menus-lobby-2012-bedwars`, liste d'Abel du 27/11 et 2012 Bedwars), fusionnée le 27/11 à 21h33 (`1bf902d`) ; **#55** (`claude/block-edit-barrier`), **#56** (`claude/hypixel-bow-practice`), **#57** (`claude/block-in-rbw`), fusionnées le 28/11 vers 10h32 dans cet ordre (`fefff6e`, `843c0d6`, `03beb92`). CI 14/14 sur chacune ([[Pull requests]]).
- **PR #58** (`claude/block-pattern-practice`) : Block Pattern Practice (les 10 premières techniques de Block Method), la correction de l'ordre d'enregistrement des modes de RagoPractice et le test des bots `pattern-bots.mjs` ; fusionnée le 28/11 à 11h28 (`d050af8`, CI 14/14 sur sa tête `69c78ac`) et installée ([[Entraînements]], [[Bugs et problèmes connus]]).
- **PR #53 ouverte** (`claude/vault-abel-26-11`, partie de `main`) : mise à jour du vault avec les réponses d'Abel du 26/11, la fermeture de #48 et #10 et la règle de l'accord d'Abel (`docs/vault/` et `CLAUDE.md` seulement). **Sa fusion attend une demande d'Abel ou de Sofiene.**
- **PR #59 ouverte** (vault du 28/11, `claude/vault-28-11`, partie de la branche de la #53, base `claude/vault-abel-26-11`) : PR #54 à #57, installation du 28/11, Block Method ; `docs/vault/` seulement. **À fusionner après la #53** (GitHub la reciblera sur `main`).
- **PR #48 fermée sans fusion le 26/11** (`claude/project-thread-5mjx75`, branche gardée) : « Superseded by #47, which implements the same events changes on the newer code. »
- **PR #10 fermée sans fusion le 26/11** (`claude/ci-java-25`, branche gardée) : « Closing: not needed for now; can be redone if the production host runs Java 25. »
- PR #3, #5, #6, #7 fermées sans fusion (leur contenu est entré par la ligne principale) ; **#8 fermée** (ping simulé, abandonné).
- **Où vit le vault** : sur `main` (et sur les branches des PR du vault encore ouvertes). Chaque mise à jour du vault part d'une branche de `main` et passe par une **petite PR vers `main`** (option A, choix d'Abel du 26/11) ; tant qu'une PR du vault est ouverte, la suivante part de sa branche et la prend pour base, pour éviter les conflits (ex. 28/11 : partie de la #53).

## Rago Hub (sur `main`, PR #50 à #52)
`.claude/settings.json` branche le hook `.claude/hooks/rago-hub-report.py` (après chaque outil et à la fin d'un tour) : il renvoie l'activité d'un fil lancé depuis Rago Hub et ne fait rien sinon. `.claude/hooks/hub.py` donne aux fils les commandes `fils`, `lire`, `envoyer`, `chef`, `memoire`, `retenir`, `fichiers`, `telecharger`, `donner`, `resolu`. L'adresse et le jeton du Hub viennent de l'environnement de la session (`HUB_URL` / `RAGO_HUB_URL`, `HUB_TOKEN` / `RAGO_HUB_TOKEN`), jamais écrits dans le dépôt ([[Clés et secrets]]).

## Règles
- **Rien sur GitHub sans demande explicite de Sofiene ou d'Abel** (règle du projet Rago ; l'accord d'Abel suffit depuis le 26/11) ; jamais de fusion sans l'un d'eux. Une PR se fusionne (ou se ferme) quand Sofiene ou Abel le dit.
- Chaque session Claude travaille sur sa branche `claude/…` ; les commits sont **en anglais**, la doc en français. Chaque commit finit par les lignes d'attribution de la session.
- Le dépôt est **public** : rien de secret dans un commit, une PR ou un commentaire ([[Clés et secrets]]).
- Historique : 423 commits de Claude et 80 de « Kaliso Dev » (comptés sur la #47 au 25/11). Premier commit `b83f944`, 07/11 00:38 UTC.
