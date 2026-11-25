---
type: historique
tags: [historique, chronologie]
maj: 2026-11-25
---
# Chronologie du projet

Pour comprendre d'où vient une chose, pas pour décider (la décision la plus récente est dans [[Décisions]]). Une ligne par jour : [[Journal]]. Retour à [[Projet]].

**Activité git** (commits par jour, branche de la PR #47, 503 commits : 423 de Claude, 80 de « Kaliso Dev ») : 07/11 : 36 · 08/11 : 59 · 09/11 : 54 · 10/11 : 40 · 11/11 : 60 · 12/11 : 40 · 13/11 : 14 · 14/11 : 28 · **pause du 15 au 20/11** · 21/11 : 63 · 22/11 : 75 · 23/11 : 27 · 25/11 : 7.

## 06/11 : le plan
Document « Rago Practice — Architecture technique v1 » : un réseau 1.8.8 en Europe pour 30 €/mois, et d'abord le jalon **M1 « Labo KB »** : prouver qu'on peut reproduire le knockback d'Hypixel en le mesurant ([[Plan d'architecture v1]]).

## 07/11 : le labo du knockback
Premier commit à 00:38 UTC : build Gradle, `rago-combat-model` (KB exact de 1.8.8), RagoCombatEngine, test de contrôle avec bots sur PandaSpigot 226 (CI). Le serveur de test à lancer sur son PC (PR #2, fusionnée à 12:43). Le même jour : le mod **kb-recorder**, la mesure sur Hypixel en Housing puis en Bed Wars avec qRago et godemy, **`hypixel-approx-v1`** et les 9 ticks ; essai du ping simulé (PR #8), abandonné ([[Mesures Hypixel]]).

## 08-10/11 : du labo au jeu
Le lobby (RagoLobby) avec ses files, le duel sumo (RagoPractice), puis RagoBedwars (Ranked Bedwars 1v1 à 4v4 à la façon d'Hypixel, shop avec le Quick Buy importé d'Hypixel, maps réglées avec `/bw`, `tools/bedwars-maps`), le serveur de build (WorldEdit, RagoBuildTools). Tout le jeu passe en anglais (08/11). Le 10/11, les minutes de GitHub Actions sont épuisées : **le dépôt devient public**.

## 11-14/11 : la ligne principale fusionnée, puis 40 PR
11/11 : les branches du labo, du serveur de build et du lobby sont fusionnées dans `main` (PR #1, #4, #9, #11). 12/11 : replays, entraînements avec menu Settings, trois files à la fois, Arenas et « Popular now », premier menu et quêtes, records, titres, prestiges, saisons, `/spectate`, bans classés, bridge chrono, couleurs puis DA rouge, KB verrouillé (PR #12 à #29). 13/11 : le **site officiel** (Next.js, design system, refonte « Master your skills », police de Minecraft) et Ranked sans priorité payante (PR #30 à #35). 14/11 : Lobby Fight, Bed Break Simulator, Pearl et Fireball practice, KB displace, Clutch Scenario, panneaux publicitaires, split de la forge, Invis et Bed Break en 1v1 à 1v4 (PR #36 à #46). **`main` s'arrête là** (`e09d9dc`).

## 21/11 : le plan de sortie publique
Sofiene écrit le **plan complet** de la sortie (phases 0 à 9). Audit, bugs critiques (reset des arènes, spectateurs, joueurs invisibles, stats, kit Bed Fight, Block In), premier essai en jeu de Sofiene vers 18 h, installations du soir. Création de `docs/suivi-sortie-publique.md` et de la **PR #47** (PR #48 aussi, dépassée).

## 22/11 : la plus grosse journée
Solo / Party / Ranked et six duels classés ; CTF (The Bridge, château), Fast Bedwars, Snowball Spleef, Parkour Duel, Fireball Fight à trois, Red Rover, Block Aim, Find Your Sensitivity, Bed Fight 1v2 ; RagoBot (étapes 1 et 2), outils Owner ; salle des remerciements, PNJ Rago Client et Rago Optimizer ; boussole refaite trois fois ; moins de rouge, nouveaux skins, Daily Chest. Plusieurs installations (aa724b7, 75050b8, e0dadff, 3ae7bb7).

## 23/11 : le test de Sofiene et ses 20 retours
Liste de 20h54 : déconnexion en Bed Wars, anti full box, UTF-8, 2017 Bedwars, Bedwars Modes, `/ranked`, PNJ Ranked, remise à zéro des stats (« les stats seulement »), retrait de KB displace, Pearl et Fireball practice ; FFA Archway et One Hit One Kill en partie privée ; RagoBot étape 3 ; quatre propositions envoyées. Installations `50d79b6` et `dfae3b7`.

## 24-25/11
24/11 : `ab689ad` installé (lobby d'attente des parties privées, remise à zéro des stats au démarrage). 25/11 (liste de 9h19) : Private game refaite, 60 s pour revenir dans sa party, duels entre parties ; `2de1bca` installé ; Sofiene passe sur un abonnement Claude Team et demande **ce vault** pour que le projet survive au changement de compte.
