---
type: technique
tags: [ci, github-actions]
maj: 2026-11-26
---
# CI GitHub Actions

Un seul workflow : `.github/workflows/ci.yml` (668 lignes). Retour à [[Construire et tester]].

- **Déclencheurs** : push sur `main`, chaque pull request, lancement à la main (`workflow_dispatch`). `concurrency` par ref, un nouveau push annule le précédent. Permissions `contents: read`.
- **Jobs** (14 avec les matrices) :

| Job | Nom affiché | Détail |
|---|---|---|
| `build` | Build and unit tests | `./gradlew build`, rapports de tests, artefact `rago-combat-engine`, vérifie que les deux lanceurs fixent les mêmes téléchargements et la somme de PacketEvents contre sa release GitHub, assemble l'artefact **`rago-serveur-de-test`** |
| `kb-recorder` | KB recorder mod (Forge 1.8.9) | artefact `rago-kb-recorder` |
| `kb-recorder-e2e` | KB recorder end-to-end test | client 1.8.9 réel sous affichage virtuel |
| `control-test` | KB control test (Java 8, 21) | [[Tests et bots]] |
| `lobby-test` | Lobby and sumo test (Java 8, 21) | |
| `arena-test` | Arena reset and lobby visibility test (Java 21) | ajouté par la PR #47 |
| `billboard-test` | Billboard test | |
| `local-server` | Local test server (ubuntu, windows × Java 8, 21) | `needs: build` |
| `site` | Website | `npm ci`, `data:check`, lint, vitest, `build:e2e`, typecheck, Playwright, Lighthouse (≥ 90 perf, ≥ 95 accessibilité sur téléphone) |

- **Le dépôt est public depuis le 10/11** (DÉCIDÉ) : les minutes gratuites de GitHub Actions d'un dépôt privé étaient épuisées ; un dépôt public a des minutes illimitées. Conséquence : **rien de secret ne doit jamais être commité** ([[Clés et secrets]]).
- La CI n'a **aucun secret** et ne déploie rien.
- PR #10 (`claude/ci-java-25`, CI sous Java 25) : **fermée sans fusion le 26/11** (elle ne change pas le jeu ; à refaire si l'hébergeur de production tourne sous Java 25) ([[Décisions]]). La CI vérifie donc toujours Java 8 et 21 seulement. Sa description disait que le serveur de test est lancé sous Java 25, le suivi dit Java 17 : **À VÉRIFIER** ([[Serveur de test]]).
- Le récit des runs (numéros, verts ou rouges) est dans `docs/suivi-sortie-publique.md` ; l'état de la PR #47 se lit sur GitHub.
