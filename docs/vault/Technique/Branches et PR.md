---
type: technique
tags: [git, branches, pr]
maj: 2026-11-26
---
# Branches et PR

Retour à [[Reprendre le développement]]. Liste complète : [[Pull requests]].

## État au 26/11/2026 (CONFIRMÉ, git et GitHub)
- **`main`** : `97d674f`, 26/11 12:24 (heure de Paris), fusion de la PR #52. Le **code du jeu** n'a pas bougé depuis le 14/11 (PR #46) ; depuis, `main` a reçu seulement ce vault et `CLAUDE.md` (PR #49, 25/11) et le hook Rago Hub dans `.claude/` (PR #50, #51, #52, 26/11).
- **PR #47 ouverte** : branche `claude/project-thread-wi9w0x`, **172 commits, 311 fichiers, +42 191 / −1 861**, dernier commit `ce8edc5` le 25/11 13:51 UTC, **CI 14/14 verte**, fusionnable sans conflit (7 commits de retard sur `main`, sans fichier en commun). **C'est là qu'est tout le travail sur le jeu du 14 au 25/11** (le plan de sortie publique du 21/11 : Fun Practice, CTF, 2017 Bedwars, RagoBot, Outils Owner, Bedwars Normal, Daily Chest, Ranked, duels entre parties…) et `docs/suivi-sortie-publique.md`. Le serveur de test tourne une version de cette branche (`2de1bca`, 25/11). **Cette branche n'a ni le vault, ni `CLAUDE.md`, ni `.claude/`.**
- **PR #48 ouverte** (`claude/project-thread-5mjx75`) : dépassée par la #47 ; **fermeture sans fusion décidée le 26/11** (demande d'Abel, conseil du Chef), en attente du « GO » de Sofiene.
- **PR #10 ouverte** (`claude/ci-java-25`) : CI sous Java 25, ne change pas le jeu ; **fermeture sans fusion décidée le 26/11**, en attente du « GO » de Sofiene.
- PR #3, #5, #6, #7 fermées sans fusion (leur contenu est entré par la ligne principale) ; **#8 fermée** (ping simulé, abandonné).
- **Où vit le vault** : sur `main` seulement. Où pousser ses mises à jour (une petite PR vers `main`, ou `main` fusionnée dans la branche de la #47) : **À DÉCIDER** par Sofiene (26/11, [[Questions ouvertes]]).

## Rago Hub (sur `main`, PR #50 à #52)
`.claude/settings.json` branche le hook `.claude/hooks/rago-hub-report.py` (après chaque outil et à la fin d'un tour) : il renvoie l'activité d'un fil lancé depuis Rago Hub et ne fait rien sinon. `.claude/hooks/hub.py` donne aux fils les commandes `fils`, `lire`, `envoyer`, `chef`, `memoire`, `retenir`, `fichiers`, `telecharger`, `donner`, `resolu`. L'adresse et le jeton du Hub viennent de l'environnement de la session (`HUB_URL` / `RAGO_HUB_URL`, `HUB_TOKEN` / `RAGO_HUB_TOKEN`), jamais écrits dans le dépôt ([[Clés et secrets]]).

## Règles
- **Rien sur GitHub sans demande explicite de Sofiene** (règle du projet Rago) ; jamais de fusion sans lui. Une PR se fusionne (ou se ferme) quand Sofiene le dit.
- Chaque session Claude travaille sur sa branche `claude/…` ; les commits sont **en anglais**, la doc en français. Chaque commit finit par les lignes d'attribution de la session.
- Le dépôt est **public** : rien de secret dans un commit, une PR ou un commentaire ([[Clés et secrets]]).
- Historique : 423 commits de Claude et 80 de « Kaliso Dev » (comptés sur la #47 au 25/11). Premier commit `b83f944`, 07/11 00:38 UTC.
