---
type: fonctionnalité
tags: [knockback, combat, kb, hitreg]
maj: 2026-11-25
code: combat/rago-combat-model, combat/rago-combat-engine (RagoCombatEngine, /kb)
---
# Knockback et combat

**La promesse du serveur** : un knockback aussi proche que possible de celui d'Hypixel, **mesuré et non deviné**. Détail : `combat/rago-combat-engine/README.md`, `docs/combat/knockback-1.8.8.md` (formule et sources), `docs/combat/mesures-hypixel.md` ([[Mesures Hypixel]]). Code : [[Code de rago-combat-model]], [[Code de rago-combat-engine]]. Retour à [[Projet]].

## Comment ça marche (CONFIRMÉ, code et README)
- Le plugin **RagoCombatEngine** remplace le knockback des **coups de mêlée joueur contre joueur** (coups complets seulement) par celui d'un **profil** de six constantes, calculé avec le modèle exact de `rago-combat-model`. Explosions, flèches, cannes à pêche, mobs et vitesses posées par d'autres plugins ne sont jamais touchés.
- Il lit les entrées à `EntityDamageByEntityEvent` (priorité MONITOR) et pose la vitesse à `PlayerVelocityEvent` (HIGHEST), même tick et même victime ; il l'écrit sur l'événement **et** sur le joueur (résultat identique sur tous les forks 1.8).
- Profils intégrés (non redéfinissables) : **`vanilla`** (Minecraft 1.8.8 au bit près) et **`hypixel-approx-v1`** = vanilla sauf `extra-vertical` **0,07** au lieu de 0,1 (mesure du 07/11). Clés d'un profil : `friction` 2.0, `horizontal` 0.4, `vertical` 0.4, `vertical-limit` 0.4, `extra-horizontal` 0.5, `extra-vertical` 0.1 (vanilla).
- **Délai entre deux coups : 9 ticks** (450 ms, mesuré sur Hypixel ; vanilla 10) : cooldown de dégâts de 18 ticks donné à chaque joueur (`setMaximumNoDamageTicks`), à la connexion, après une réapparition, un changement de monde ; il vaut pour tous les dégâts.
- **Hitreg** `player-tracker-ticks: 1` : la position des joueurs part à chaque tick (vanilla un sur deux, jusqu'à 100 ms de retard), comme sur les serveurs practice.
- Un `config.yml` invalide **désactive** le plugin (un serveur compétitif ne doit pas tourner sur des réglages à moitié lus).

## Configuration livrée (`plugins/RagoCombatEngine/config.yml`)
`mode: override` (ou `observe` : calcule et journalise sans rien changer) · `active-profile: hypixel-approx-v1` · `world-profiles: {}` · `hit-delay-ticks: 9` · **`locked: true`** · `record-hits: false` · `player-tracker-ticks: 1` · `profiles: {}`.

## La commande /kb (`rago.combat.admin`, opérateurs)
`/kb status`, `profiles`, `profile <nom>`, `world <monde> <profil|default>`, `mode`, `hitdelay`, `debug` (chaque coup dans son chat), `sparring [sprint|normal|alterne] [ticks]` (un attaquant invisible frappe celui qui tape la commande), `sparring stop`, `reload`. **Verrouillé** (`locked: true`) : `profile`, `world`, `mode`, `hitdelay` sont refusés même pour un opérateur ; seul le `config.yml` change le KB. Chaque changement est écrit dans la console avec `[audit]`.

## Journal des hits
`record-hits: true` écrit chaque coup dans `plugins/RagoCombatEngine/hits/hits-AAAA-MM-JJ.jsonl` (entrées, vitesse calculée, paquet, appliqué ou non), file de 10 000 lignes sur un thread à part. Format lu par `tools/kb-control` : **on ajoute des champs, on n'en renomme jamais** (un test le vérifie).

## Décisions
- **Ping simulé abandonné** (PR #8 fermée sans fusion, 07/11) : trois versions essayées, aucune ne donnait la sensation d'Hypixel sans faire laguer le jeu. La sensation d'Hypixel vient du **ping réel** du joueur : un Européen à 30 ms aura des combos en l'air plus bas que sur Hypixel à 150 ms, avec les mêmes réglages ([[Mesures Hypixel]]).
- On ne modifie jamais un profil mesuré : une nouvelle mesure qui change une valeur donne `hypixel-approx-v2`.
- Non mesurés : enchantement Knockback, canne à pêche, projectiles, explosions ([[Boules de feu et TNT]]).

## Tests
Tests unitaires (`./gradlew :rago-combat-engine:test`) et **test de contrôle sur un vrai serveur avec des bots** (`tools/kb-control`, job CI `control-test` en Java 8 et 21) : knockback envoyé, puis délai de 10, 9 ticks et 9 ticks après une réapparition. Voir [[Outils de mesure du knockback]], [[Tests et bots]].
