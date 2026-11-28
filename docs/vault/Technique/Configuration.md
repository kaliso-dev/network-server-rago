---
type: technique
tags: [configuration, config.yml]
maj: 2026-11-28
---
# Configuration

Chaque plugin a son `config.yml` (créé au premier démarrage dans `plugins/<Plugin>/`, commenté en anglais, **clés en français**, lu en **UTF-8**). Les fichiers fournis sont dans `src/main/resources/config.yml` de chaque module. Un fichier invalide : RagoCombatEngine se désactive ; RagoLobby se désactive en disant quoi corriger ; RagoPractice joue alors avec les valeurs de départ et le signale. **Un nouveau jar ne réécrit pas le `config.yml` déjà en place** : sur le [[Serveur de test]], chaque nouvelle section se recopie à la main à l'installation (28/11 : `bow-practice:` et `block-in.rbw:` dans celui de RagoPractice, les modes `bow-practice` puis `block-pattern-practice` dans celui de RagoLobby). Retour à [[Reprendre le développement]].

## RagoLobby (`bukkit/rago-lobby/src/main/resources/config.yml`)
`monde` (lobby) · `hauteur-vide` · `slime` (`avant` 3.5, `haut` 1.3) · `titre` (`&c&lRAGO PRACTICE`) · `adresse` (`&erago.gg`) · `file` (`elo-par-defaut` 0, `ecart-elo-initial` 100, `ecart-elo-pas` 50, `ecart-elo-secondes` 10, `ecart-elo-max` 500, `abandon-classe-minutes` 5) · `fin-de-partie-secondes` 3 · `replays` · `boussole` (`haut: [classe, party, red-rover, trois-joueurs]`) · `categories` (entrainement, ranked « Bedwars Modes », duels, arenes, fun, trois-joueurs, party, classe) · `accueil` · `premiere-venue` · `groupes` · `pnj` (depuis le 27/11, `regard: point` : le PNJ regarde dans le sens de son point, sinon vers le spawn ; le Shop et les Leaderboards l'ont) · `textes` (dont `textremerciement`, nouveau texte du 27/11, et `ragobot`, le texte de RagoBot, 27/11) · `point-coffre-du-jour` · `point-pnj-quetes` · `cle-mineskin` (`""`) · `niveau` · `quetes` · `rangs` · `styles` · `elo` · `saison` · `rago-points` · `coffre-du-jour` · `boutique` · `pack-depart` · `chat` · `tab` · `motd` · `annonces` · `liens` (discord.gg/ragooptimizer, rago-optimizer.com) · `panneaux` · `classements` · `semaine` · `titres` · `hypixel` (`cle-api` `""`) · `parties-privees` · `parties-privees-equipes` · `ragobot` · `outils-owner` (`compte` qRago, `uuid` `""`) · **`modes`** (93 modes dans le fichier fourni, 10 cachés ; 91 dans celui du serveur de test au 28/11 : `nom`, `icone`, `equipes`, `joueurs-par-equipe`, `classe`, `categorie`, `groupe`, `kit`, `kit-de`, `cache`, `commencer-ici`, `description`…). Depuis le 27/11, `icone:` (des modes, des groupes et des catégories) accepte une couleur après deux-points, de 0 à 15 : `"WOOL:14"` (laine rouge), `"WOOL:4"` (laine jaune). Modes ajoutés : `bedwars-2012`, `bedwars-2012-classique` (27/11, groupe `bedwars-2012`), `bow-practice` et `block-pattern-practice` (28/11, icône `"WOOL:3"`) ; retiré : `bridge-fight` (27/11) ; cachés : `fast-bedwars-1v1`, `fast-bedwars-2v2` (27/11). Titres : la famille `fast-bedwars` devient `bedwars-2012`.
Dans le dossier du monde du lobby : `spawn.yml` et `points.yml` (posés par `/lobby setspawn` et `/lobby point`). Données : `stats/`, `kits/<uuid>.yml`, `hypixel/<uuid>.yml`, `replays/`, `semaine.yml`, `skins.yml`, `skins-joueurs.yml`, `sanctions.yml`, `panneaux/`.
Ce fichier est la source du [[Catalogue des modes]] (`docs/vault`) et des données du [[Site]] (`npm run data`).

## RagoPractice
`monde-arenes` (arenes) · `espacement` 200 · `arenes-max` 16 · `sumo` · `boxing` · `points-pour-gagner` · `block-fight` · `block-fight-arena` · `bridge-fight` · `fireball` · `xp-entrainement` · `evenements` · `defi-du-jour` · `mur-des-records` (records) · `aim-trainer` · `pearl` · `block-in` (avec `block-in.rbw` depuis le 28/11 : `monde` « Block In Practice RBW », `lit`, `tete`, `zone` ; le niveau RBW defense) · `kb-displace` · `block-extend` · `clutch` · `bow-training` · `bow-practice` (28/11 : `monde` BowPractice, `centre`, `zone`, `niveaux` avec pour chacun `joueur` [x, y, z, yaw, pitch] et `pont` [x, y, z du premier bloc, x, z du dernier]) · `fireball-knockback` · `snowball-spleef` · `parkour-duel` · `red-rover` (lu aussi par RagoBedwars). Données : `records.yml`, `reglages.yml`.

## RagoBedwars
`modes` (maps de chaque mode, ou `toutes` ; `bedwars-2012` et `bedwars-2012-classique` : `[Bedwars2012]`, 27/11) · `jeux` (le jeu de chaque mode : ffa, lobby-fight, invis, fast-shop, bed-break, arene, `bedwars-2012`, `bedwars-2012-classique`…) · `attaque-defense` · `bed-break` · `stuff-ameliore` · `ctf` · `fast-bedwars` · `equipes-exactes` · `reglages-prives` · `ffa-privee-minutes` 30 · `salle-attente-privee` ArchwayFFA · `parties-max` 8 · `carte-demo` true · `reapparition-secondes` 5 · `top-bridge` · `iles-voisines` · `forges-partout` · `shops-partout` · `forge` (dont `forge.modes`) · `generateurs` (diamant 30/23/12 s max 4 ; émeraude 65/50/35 s max 2) · `evenements` (6/12/18/24/30/45 min) · `boule-de-feu`. Chaque map a son `bedwars.yml` dans son dossier ([[Maps et mondes]]). Données : `fastshop.yml`.

## RagoCombatEngine
`mode: override` · `active-profile: hypixel-approx-v1` · `world-profiles: {}` · `hit-delay-ticks: 9` · `locked: true` · `record-hits: false` · `player-tracker-ticks: 1` · `profiles: {}` ([[Knockback et combat]]).

## RagoBuildTools
`sauvegarde-auto-minutes: 2` ([[Serveur de test]]).

## Valeurs vides à remplir sur un vrai serveur (jamais dans le dépôt)
`hypixel.cle-api`, `cle-mineskin`, `outils-owner.uuid` (en mode offline) : voir [[Clés et secrets]].
