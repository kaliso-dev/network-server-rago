---
type: technique
tags: [git, branches, pr]
maj: 2026-11-25
---
# Branches et PR

Retour à [[Reprendre le développement]]. Liste complète : [[Pull requests]].

## État au 25/11/2026 (CONFIRMÉ, git et GitHub)
- **`main`** : `e09d9dc`, 14/11 14:06 (fusion de la PR #46). Rien n'a été fusionné depuis.
- **PR #47 ouverte** : branche `claude/project-thread-wi9w0x`, **172 commits, 311 fichiers, +42 191 / −1 861**, dernier commit `ce8edc5` le 25/11 13:51 UTC, fusionnable sans conflit. **C'est là qu'est tout le travail du 14 au 25/11** (le plan de sortie publique du 21/11 : Fun Practice, CTF, 2017 Bedwars, RagoBot, Outils Owner, Bedwars Normal, Daily Chest, Ranked, duels entre parties…) et `docs/suivi-sortie-publique.md`. Le serveur de test tourne une version de cette branche (2de1bca, 25/11).
- **PR #48 ouverte** (`claude/project-thread-5mjx75`) : dépassée par la #47 ; question **q124** (la fermer ?).
- **PR #10 ouverte** (`claude/ci-java-25`) : CI sous Java 25 ; question **q125**.
- PR #3, #5, #6, #7 fermées sans fusion (leur contenu est entré par la ligne principale) ; **#8 fermée** (ping simulé, abandonné).
- Cette PR du vault : branche `claude/project-thread-c5qy7f`, partie de `main` ; elle n'ajoute que `docs/vault/` et `CLAUDE.md`, sans conflit avec la #47.

## Règles
- **Rien sur GitHub sans demande explicite de Sofiene** (règle du projet Rago) ; jamais de fusion sans lui. Une PR se fusionne quand Sofiene le dit.
- Chaque session Claude travaille sur sa branche `claude/…` ; les commits sont **en anglais**, la doc en français. Chaque commit finit par les lignes d'attribution de la session.
- Le dépôt est **public** : rien de secret dans un commit, une PR ou un commentaire ([[Clés et secrets]]).
- Historique : 423 commits de Claude et 80 de « Kaliso Dev » (comptés sur la #47 au 25/11). Premier commit `b83f944`, 07/11 00:38 UTC.
