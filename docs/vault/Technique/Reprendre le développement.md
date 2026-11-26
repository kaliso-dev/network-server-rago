---
type: technique
tags: [onboarding, développement]
maj: 2026-11-26
---
# Reprendre le développement

La fiche à lire **avant de coder ou de dépanner**. Retour à [[Projet]].

## Dans l'ordre
1. [[État actuel]] (où on en est), [[Règles de travail]] (ce qu'on ne fait jamais), [[Questions ouvertes]].
2. La note de la fonctionnalité visée (dossier `Fonctionnalités/`) puis la note du module (dossier `Code/`), qui décrit chaque classe.
3. Le README du module dans le dépôt : c'est la doc la plus détaillée, écrite au fil des demandes de Sofiene (lobby 1 055 lignes, bedwars 808, practice 630).
4. Le suivi de la sortie publique : `docs/suivi-sortie-publique.md` (2 559 lignes, **sur la branche de la PR #47**, pas encore sur `main`) : chaque demande du plan du 21/11, son statut et son journal ([[Plan de sortie publique]]).

## Quelle branche ?
**Le travail récent sur le jeu est sur la PR #47** (`claude/project-thread-wi9w0x`, ouverte, 172 commits, non fusionnée au 26/11) ; le code du jeu de `main` est resté au 14/11 (PR #46). Pour toute nouvelle tâche sur le jeu, partir de la branche de la PR #47 tant qu'elle n'est pas fusionnée, ou demander à Sofiene. **Le vault, `CLAUDE.md` et le dossier `.claude/` (hook Rago Hub) sont sur `main`** (PR #49 à #52), **pas sur la branche de la #47**. Voir [[Branches et PR]].

## Quelle fiche pour quel travail
| Je veux… | Lire | Fichiers principaux |
|---|---|---|
| changer un mode ou en ajouter un | [[Catalogue des modes]], [[Configuration]] | `bukkit/rago-lobby/src/main/resources/config.yml` (`modes:`), le plugin du jeu |
| toucher au lobby, aux menus, aux files | [[Lobby]], [[Boussole et menus]], [[Code de rago-lobby]] | `RagoLobby`, `ModeMenu`, `QueueController`, `LobbySettings` |
| un entraînement, un duel | [[Entraînements]], [[Duels]], [[Code de rago-practice]] | `Training`, `PracticeGame`, `PracticeGames` |
| Bed Wars | [[Bed Wars]], [[Code de rago-bedwars]] | `BedwarsGame`, `BedwarsHost`, `BedwarsSettings` |
| le knockback | [[Knockback et combat]], [[Mesures Hypixel]] | `combat/` ; **ne jamais inventer de valeur** |
| une map | [[Maps et mondes]] | `/bw`, `tools/bedwars-maps` |
| installer sur le serveur de test (PC d'Abel « FULL PC GAMER ») | [[Installer une version sur le serveur de test]] | artefact CI `rago-serveur-de-test` |
| le site | [[Site]], [[Code du site]] | `site/web`, `site/design` |

## Commandes
```bash
./gradlew build                      # tout : compilation Java 8 et tests unitaires (JDK 17+)
./gradlew :rago-lobby:build          # un module : rago-lobby, rago-practice, rago-bedwars, rago-combat-engine…
tools/kb-control/run-control-test.sh # test de contrôle du KB sur un vrai PandaSpigot (Node 22 pour les bots)
tools/lobby-check/run-lobby-test.sh  # le lobby, le sumo, Bed Wars et un event joués par des bots
cd site/web && npm ci && npm run dev # le site
```
Détail : [[Construire et tester]], [[Tests et bots]].

## Pièges connus
- Tout ce qui tourne dans le serveur doit rester **Java 8** (`--release 8`) ; le thread principal ne touche jamais au disque.
- Les clés des `config.yml` sont **en français** (`monde`, `modes`, `nom`…), les messages en jeu **en anglais**. Les trois plugins lisent leur config **en UTF-8** (bug du 23/11 sur Windows).
- Les plugins de jeu prennent les classes de RagoLobby (`depend: [RagoLobby]`, `compileOnly`), sans copier `rago-game-model`.
- PacketEvents est un plugin à part, installé à côté de RagoLobby.
- Le README racine et celui de `rago-game-model` ont des passages dépassés ([[Bugs et problèmes connus]]).
- Signaler ce qui n'est pas testé en jeu : beaucoup de choses sont **TESTÉ EN CI** mais pas encore jouées en jeu (les essais en jeu sont faits par Abel).
- Un fil lancé par Rago Hub démarre sur `main` : passer sur la branche de la #47 fait disparaître `.claude/hooks/` (absent de cette branche) et le hook Rago Hub échoue à chaque outil (vu le 26/11). Le vault n'existe pas non plus sur cette branche : où pousser une mise à jour du vault (PR vers `main`, ou `main` fusionnée dans la #47) est **À DÉCIDER** par Sofiene (26/11, [[Questions ouvertes]]).
