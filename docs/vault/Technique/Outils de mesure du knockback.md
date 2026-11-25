---
type: technique
tags: [kb-recorder, kb-analyzer, kb-control, mesures]
maj: 2026-11-25
code: tools/kb-recorder, tools/kb-analyzer, tools/kb-control
---
# Outils de mesure du knockback

Retour à [[Knockback et combat]]. Résultats : [[Mesures Hypixel]]. Code : [[Code des outils]].

## kb-recorder (mod client Forge 1.8.9)
- Enregistre ce que le client reçoit et envoie pendant un combat, **sans rien modifier** (passif). Version **0.2.0** : enregistre aussi boules de feu, TNT et explosions (le 0.1.0 a servi aux mesures du 07/11).
- Installation : Forge 1.8.9 (11.15.1.2318), jar `rago-kb-recorder-<version>.jar` (artefact CI `rago-kb-recorder`) dans `mods`.
- Commandes : `/kbrec start [nom]`, `/kbrec mark [texte]`, `/kbrec stop`, `/kbrec status`. Fichiers `.jsonl` dans `%appdata%\.minecraft\rago-kb-recorder`.
- Lignes : `session`, `tick` (position, mouvement, sol, regard, sprint, touches, vie, ping du tab, joueurs proches), `packet` (octets bruts base64), `mark`, `blocks` (explosions), `context`, `error`, `end`.
- **Hypixel n'autorise pas officiellement ce mod** : l'utiliser sur un deuxième compte.
- Build à part (Gradle 8.8, Loom d'Essential 0.10.0.5).

## kb-analyzer (ligne de commande, machine de dev)
`./gradlew :kb-analyzer:installDist` puis `kb-analyzer --fight hypixel a.jsonl b.jsonl --fight rago c.jsonl d.jsonl --report rapport.md` ; `--explosions` pour les boules de feu et TNT. Un combat = **les deux fichiers** (victime et attaquant). Le rapport ne garde que le domaine du serveur.

## kb-control (test de contrôle)
Test sur un vrai PandaSpigot 1.8.8 avec deux bots mineflayer, lancé par la CI (`run-control-test.sh`, `test-server.sh`) : compare le knockback envoyé au calcul exact, puis le délai entre deux coups. Lit le journal des hits du moteur. Voir [[Tests et bots]].

## Doc de référence
`docs/combat/knockback-1.8.8.md` (la formule vérifiée et ses sources), `docs/combat/mesures-hypixel.md`, `docs/combat/protocole-explosions.md` ([[Boules de feu et TNT]]).
