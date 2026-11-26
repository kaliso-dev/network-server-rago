---
type: technique
tags: [installation, serveur-de-test, procédure]
maj: 2026-11-26
---
# Installer une version sur le serveur de test

La procédure suivie par les Claude du projet pour mettre une nouvelle version sur le serveur de test, qui tourne sur le **PC d'Abel « FULL PC GAMER »** (reconstituée depuis le journal de `docs/suivi-sortie-publique.md`). Retour à [[Serveur de test]].

## Règle
**Jamais sans l'accord explicite de Sofiene** pour cette installation (ex. « Tu peux installer oui », 25/11 14h47 ; « Install tout sur mon [serveur] », 22/11 9h49). Un accord vaut pour l'installation demandée, pas pour les suivantes. Le serveur de test n'est pas la production, mais ses données (stats, mondes, points) sont à garder ([[Règles de travail]]).

## Où
La dernière version en service est dans **`C:\Users\rgabe\Downloads\rago-serveur-de-test (19)`** (Abel, 26/11) ; les mondes sont à part, dans `C:\Users\rgabe\Rago\mondes`.

## Étapes
1. La version est **un commit poussé et vert en CI** ; récupérer l'artefact **`rago-serveur-de-test`** de ce commit (ou les jars construits), vérifier l'empreinte du zip.
2. **Arrêter le serveur** (`stop` dans sa fenêtre). Ne pas le relancer sauf demande.
3. **Sauvegarde** dans le dossier du serveur : `sauvegarde-avant-maj-<sha>` (les 5 jars, les `config.yml` modifiés, `points.yml` du lobby si on le touche). Jamais de suppression des données listées dans [[Serveur de test]].
4. Remplacer les jars qui changent (RagoCombatEngine, RagoBuildTools, RagoLobby, RagoPractice, RagoBedwars) ; noter lesquels sont identiques.
5. **Fusionner les `config.yml`** (lobby, practice, bedwars) : garder chaque réglage déjà fait sur le serveur, ajouter les nouvelles clés du jar ; noter les lignes ajoutées et retirées et tout conflit (ex. 22/11 : conflit dans `classements:`, murs déjà posés gardés). Des scripts de fusion ponctuels ont servi (`serveur-de-test/install-*/appliquer-config.py`, hors dépôt).
6. Ne toucher **ni** à `server.properties`, **ni** à l'icône, **ni** aux mondes (on peut lire `online-mode` et `bungeecord` sans rien changer).
7. Écrire dans le journal du suivi : date et heure (de Paris), commit, ce qui change, la sauvegarde, et **« rien n'est encore joué en jeu »** quand c'est le cas.
8. Mettre à jour [[État actuel]] et [[Journal]] de ce vault.

## Accès au PC
Le PC est celui d'**Abel**, « FULL PC GAMER », profil Windows `rgabe` (réponse du 26/11 à q123). Les installations passées ont été faites à distance depuis des sessions Claude (accès au dossier ou Remote Control) : le moyen exact reste **À VÉRIFIER**. Sans accès, préparer le zip et le mode d'emploi pour qu'Abel l'installe sur son PC, après l'accord de Sofiene.

## Revenir en arrière
Arrêter le serveur, remettre les jars et configs de `sauvegarde-avant-maj-<sha>`. Les mondes : sauvegardes automatiques zip du lanceur dans `C:\Users\rgabe\Rago\sauvegardes`.
