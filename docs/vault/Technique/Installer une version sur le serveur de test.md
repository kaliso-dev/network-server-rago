---
type: technique
tags: [installation, serveur-de-test, procédure]
maj: 2026-11-28
---
# Installer une version sur le serveur de test

La procédure suivie par les Claude du projet pour mettre une nouvelle version sur le serveur de test, qui tourne sur le **PC d'Abel « FULL PC GAMER »** (reconstituée depuis le journal de `docs/suivi-sortie-publique.md`). Retour à [[Serveur de test]].

## Règle
**Jamais sans l'accord explicite de Sofiene ou d'Abel** pour cette installation (l'accord d'Abel suffit depuis le 26/11 ; ex. « Tu peux installer oui », 25/11 14h47 ; « Install tout sur mon [serveur] », 22/11 9h49). Un accord vaut pour l'installation demandée, pas pour les suivantes. Le serveur de test n'est pas la production, mais ses données (stats, mondes, points) sont à garder ([[Règles de travail]]).

## Où
La dernière version en service est dans **`C:\Users\rgabe\Downloads\rago-serveur-de-test (19)`** (Abel, 26/11) ; les mondes sont à part, dans `C:\Users\rgabe\Rago\mondes`.

## Étapes
1. La version est **un commit poussé et vert en CI** ; récupérer l'artefact **`rago-serveur-de-test`** de ce commit (ou les jars construits), vérifier l'empreinte du zip.
2. **Arrêter le serveur** proprement (`stop` dans sa console ; jamais en tuant java), de préférence quand personne n'est connecté.
3. **Sauvegarde** dans le dossier du serveur : `sauvegarde-avant-maj-<sha>` (au moins `plugins\` : les jars et les `config.yml` ; `points.yml` du lobby si on le touche ; le 28/11 aussi `mondes-arenes\`). Jamais de suppression des données listées dans [[Serveur de test]].
4. Remplacer les jars qui changent (RagoCombatEngine, RagoBuildTools, RagoLobby, RagoPractice, RagoBedwars) ; noter lesquels sont identiques.
5. **Fusionner les `config.yml`** (lobby, practice, bedwars) : un nouveau jar ne réécrit pas le `config.yml` en place ; garder chaque réglage déjà fait sur le serveur, **ajouter à la main** les nouvelles sections ou clés du jar ; noter les lignes ajoutées et retirées et tout conflit (ex. 22/11 : conflit dans `classements:`, murs déjà posés gardés ; 28/11 : `bow-practice:` et `block-in.rbw:` dans RagoPractice, le mode `bow-practice` dans RagoLobby). Des scripts de fusion ponctuels ont servi (`serveur-de-test/install-*/appliquer-config.py`, hors dépôt).
6. Ne toucher **ni** à `server.properties`, **ni** à l'icône, **ni** aux mondes déjà en place (on peut lire `online-mode` et `bungeecord` sans rien changer) ; seulement **ajouter** dans `C:\Users\rgabe\Rago\mondes` la map d'un nouveau mode (28/11 : BowPractice et « Block In Practice RBW »).
7. **Vérifier le démarrage** si on le lance : lire le log (pas d'erreur nouvelle, les lignes « Copied for … » des nouvelles maps, le nombre de modes du lobby), puis l'arrêter proprement s'il n'était pas en marche avant.
8. Écrire dans le journal du suivi : date et heure (de Paris), commit, ce qui change, la sauvegarde, et **« rien n'est encore joué en jeu »** quand c'est le cas.
9. Mettre à jour [[État actuel]] et [[Journal]] de ce vault.

## Dernière installation (28/11, vers 16h30, personne connecté)
Commit `3316b1f` (PR #61) : serveur arrêté par Abel à 15h46 ; sauvegarde `sauvegarde-avant-maj-3316b1f\plugins\` ; jars rago-lobby et rago-bedwars de `3316b1f` ; ajoutés par script (`install_configs_28b.py`, à relancer sans risque) : le mode `block-method-1v1` (avant `block-fight-1v1`) et la section `ragobot.seul` au `config.yml` de RagoLobby, `block-method-1v1:` (maps et sorte) au `config.yml` de RagoBedwars ; mondes GodMethod1, GodMethod2, MapV2 et MinemenBedFight copiés dans `C:\Users\rgabe\Rago\mondes`. Démarrage vérifié : 92 modes, « Mode block-method-1v1 hosted by RagoBedwars », les 4 maps prêtes, aucune erreur (seulement les avertissements déjà connus). Laissé allumé pour l'essai d'Abel. Rien n'est encore joué en jeu.

## Installation d'avant (28/11, vers 11h29, personne connecté)
Commit `d050af8` (PR #58) : sauvegarde `sauvegarde-avant-maj-d050af8\plugins\` ; jars rago-practice et rago-lobby de `d050af8` ; mode `block-pattern-practice` ajouté par script au `config.yml` de RagoLobby du serveur. Démarrage vérifié : 91 modes dans le lobby, « Mode block-pattern-practice hosted by RagoPractice », zones d'arène de nouveau dans l'ordre d'avant la #56 (`bow-practice` puis `block-pattern-practice` à la fin) ; puis arrêt propre. Pas encore joué en jeu.

## Installation encore avant (28/11, vers 10h33, personne connecté)
Commit `03beb92` (PR #55 à #57, fusionnées le matin) : sauvegarde `sauvegarde-avant-maj-03beb92\` (`plugins\` et `mondes-arenes\`) ; jars rago-practice et rago-lobby de `03beb92` copiés (les trois autres inchangés) ; sections ajoutées à la main (RagoPractice : `bow-practice:`, `block-in.rbw:` ; RagoLobby : mode `bow-practice`) ; mondes BowPractice et « Block In Practice RBW » copiés dans `C:\Users\rgabe\Rago\mondes`. Démarrage vérifié : pas d'erreur nouvelle, « Copied for Hypixel Bow Practice … 5923 blocks », « Copied for the RBW level … 1374 blocks », « Game world bw_Bedwars2012_1 ready (map Bedwars2012) », lobby à 90 modes ; puis arrêt propre. Avertissements déjà connus : `titres.familles.clutch-scenario` sans modes 2v2, 3v3, 4v4 ; mode `bridge-fight` fermé (voulu) ([[Bugs et problèmes connus]]). Pas encore joué en jeu.

## Accès au PC
Le PC est celui d'**Abel**, « FULL PC GAMER », profil Windows `rgabe` (réponse du 26/11 à q123). Depuis le 27/11 (OBSERVÉ), les installations sont faites par une **session Claude Code qui tourne sur ce PC** : clone de travail `C:\Users\rgabe\rago-serveur-code`, `git push` par Git Credential Manager. Les installations d'avant le 27/11 ont été faites depuis d'autres sessions (accès au dossier ou Remote Control ; moyen exact non noté). Sans accès, préparer le zip et le mode d'emploi pour qu'Abel l'installe sur son PC.

## Revenir en arrière
Arrêter le serveur, remettre les jars et configs de `sauvegarde-avant-maj-<sha>`. Les mondes : sauvegardes automatiques zip du lanceur dans `C:\Users\rgabe\Rago\sauvegardes`.
