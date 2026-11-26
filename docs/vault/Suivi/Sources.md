---
type: suivi
tags: [sources]
maj: 2026-11-26
---
# Sources

D'où vient chaque information de ce vault, de la plus sûre à la moins sûre. Retour à [[Projet]].

## Dans le dépôt `kaliso-dev/network-server-rago`
- **Le code** (branche de la PR #47, `ce8edc5`) et les `config.yml` / `plugin.yml` : source primaire (CONFIRMÉ).
- **Les README des modules** : `bukkit/rago-lobby/README.md` (1 055 l.), `bukkit/rago-bedwars/README.md` (808), `bukkit/rago-practice/README.md` (630), `tools/lobby-check/README.md` (325), `tools/local-server/README.md` (278), `combat/rago-combat-engine/README.md` (179), `site/design/README.md` (176), `tools/kb-recorder`, `kb-analyzer`, `kb-control`, `build-tools`, `site/web`, `bedwars-maps`, `combat-model`, `game-model`.
- **`docs/suivi-sortie-publique.md`** (2 559 l., sur la #47) : le plan de Sofiene du 21/11 tâche par tâche, ses listes datées, ses réponses, chaque installation (OBSERVÉ : écrit par les Claude au fil de l'eau).
- `docs/combat/knockback-1.8.8.md`, `docs/combat/mesures-hypixel.md`, `docs/combat/protocole-explosions.md`, `docs/compatibility.md`.
- L'historique git et les PR (titres, dates, auteurs).

## Hors du dépôt
- **Artifacts claude.ai** (privés, sur le compte de Sofiene ; à rouvrir ou exporter avant de changer de compte) :
  - Architecture technique v1 (06/11) : https://claude.ai/artifact/JHXQTtaRJU7REysX4xrvxo ([[Plan d'architecture v1]])
  - Plan du lobby et des modes : https://claude.ai/artifact/TCFxyP3KsZsRagZhBMNPeo
  - Propositions du 23/11 (RagoBot sur la map, RagoBot qui pose des blocs, build du lobby, fireballs de Minemen) : https://claude.ai/artifact/HbZBGpwTZadVRtzdcsCyPY
  - Proposition du 25/11 (duels entre parties dans tous les modes) : https://claude.ai/artifact/KkzxkuPALJ5WkmDx915nif
  - Page « Questions du vault Rago » (commune aux projets Rago) : https://claude.ai/artifact/1mqjZuFNVqGT5JhBmG6HPk
  - Claude Docs cités par le suivi : « Proposition : Private Game, Bedwars Normal et FFA Archway » (22/11), « Étape 7 : étude du bot IA » (22/11), le doc de direction artistique du site.
- **Fichiers du projet Claude** (pas dans le dépôt) : `site-officiel/` (analyse et stratégie du site), `sortie-publique/references/lobby-reference-23-11.png` (image de référence du lobby), `serveur-de-test/install-*/` (scripts de fusion des configs). **À récupérer avant le changement de compte** : ils ne suivent pas le dépôt.
- Le PC d'Abel « FULL PC GAMER » (serveur de test, maps, données des joueurs) : [[Serveur de test]].
- **Rago Hub** (depuis le 26/11) : les fils de travail lancés par Abel ou le chef, leur mémoire commune et leurs comptes rendus (outil `.claude/hooks/hub.py` sur `main`). Les réponses d'Abel du 26/11 y ont été données ([[Personnes et comptes]]).
