---
type: fonctionnalité
tags: [lobby, menus, boussole]
maj: 2026-11-28
code: bukkit/rago-lobby (ModeMenu, ModeCategory, ModeDisplay, FirstJoinMenu, ClickMenu)
---
# Boussole et menus

La boussole (1re case de la barre, ou `/play`) ouvre le **sélecteur des modes**, un menu coffre (`ModeMenu`, 1 071 lignes). Les PNJ du lobby ouvrent la même boussole sur leur catégorie. Voir [[Lobby]], [[Catalogue des modes]]. Retour à [[Projet]].

## Disposition actuelle (choix de Sofiene du 22/11, 17h54 ; 5 lignes depuis le 27/11)
- **5 lignes** (45 cases) depuis le 27/11 (liste d'Abel, PR #54 ; 6 lignes avant), **tout centré**, une ligne vide entre chaque rangée d'objets : sur la ligne du haut, une case entre chacune : **Ranked**, **Party**, **Red Rover**, **3 Players** (`boussole.haut: [classe, party, red-rover, trois-joueurs]`) ; sur la ligne du milieu, les cinq autres catégories : Practice, Bedwars Modes, **Duels au centre**, Arenas, Fun Practice ; sur la ligne du bas, « Popular now ».
- Un clic sur une catégorie ouvre ses modes, sept par ligne, sous une ligne vide ; un **groupe** (`groupes:`) montre les tailles d'un jeu (1v1, 2v2…) dans un sous-menu ; « Go back » revient à la boussole, ou à la catégorie d'où l'on vient.
- Historique : le 22/11 une boussole « comme sur Minemen » (catégories en haut, modes dessous dans le même menu) a été faite puis **retirée** à 16h22 (« remettre la boussole comme avant avec les différentes catégories »).

## Ce que montre un mode
- **Le nombre sous l'objet** (27/11, « comme sur Minemen ») : le nombre de joueurs **en partie** dans ce mode, de 1 (pas de chiffre) à 64, parties privées comprises (`QueueController.playingAll`). Sous une catégorie : le total de ses modes ; sous la catégorie Party : les joueurs en partie privée ; sous un groupe : le total de ses tailles.
- **Icônes** (`icone:`) : un objet ne change pas d'un mode à l'autre d'un même jeu. Depuis le 27/11, `icone:` accepte une couleur après deux-points, de 0 à 15 (`"WOOL:14"` laine rouge, `"WOOL:4"` laine jaune) : **Arenas** en laine rouge ; **Block Placement** en laine jaune dans tous ses modes (1v1, 2v2, classé, Red Rover, arena) ; **Sumo** une laisse (`LEASH`, aussi Ranked Sumo, Red Rover Sumo et l'event sumo) ; **Top BedFight** un bloc de fer ; **Block clutch** et son top fight du grès (`SANDSTONE`, aussi « Train a skill » du premier menu).
- **Ordre** : Practice et Duels dans l'ordre d'Abel (27/11), Block clutch en premier ([[Catalogue des modes]]).
- Son nom dans la couleur de sa catégorie, son format, sa description, le **nombre de joueurs en file** (« In queue: 0 », aussi sous le PNJ : « 1 in queue », plan 3.8), le **Daily Streak** ou, pour un entraînement, le **top 3 Lifetime** de son record (plan 3.4 ; Fast Shop : sa première liste). Un mode avec plusieurs maps prêtes propose de **choisir sa map** ou « Random map » ([[Maps et mondes]]).
- **« Popular now »** : la ligne du bas montre les modes les plus joués, avec le défi du jour ([[Progression et récompenses]]).
- **« Start here »** : le mode marqué `commencer-ici: true` (le Block clutch) brille.

## Les catégories spéciales
- **Ranked** (`classe`) : Ranked Bedwars 1v1 à 4v4 et six duels classés en 1v1 (Bed Fight, Top Bridge, Bridge Fight, Block Placement, Sumo, Boxing), chacun avec son ELO ; `/ranked` l'ouvre directement (23/11). [[Files, Ranked et ELO]].
- **Party** (`party: true`) : ce que la party joue contre elle-même à sa taille, l'objet **Private games** ([[Parties privées]]) et, dans la dernière case, **Party Duels** (épée en fer) ([[Party, amis et messages]]).
- **3 Players** (`trois-joueurs`, sans PNJ) : Red Rover, Bed Break Simulator 1v2, Invis practice 1v2, Clutch scenario 1v2, Bed fight 1v2 (par `aussi:` : ces modes restent dans leur catégorie, leurs stats aussi) ([[Red Rover et 3 Players]]).

## Les autres menus
- **Premier menu** « What do you want to do? » (`FirstJoinMenu`, `premiere-venue:`).
- **Profil** (tête, `/profile`) : niveau, rang, titre, points, divisions, stats, amis, réglages, titres, boutique ([[Progression et récompenses]]).
- **Réglages** (`/settings`) : messages, invitations, heure du ciel, Play again, vitesse du lobby.
- **Menu d'un joueur** (clic droit sur lui) : sa tête, son rang, son niveau, son ELO, puis duel, stats, invitation en party, demande d'ami, et Staff pour le staff (`PlayerMenu`).
- **Menu des stats** (PNJ de stats) : les catégories avec leurs totaux, puis leurs modes ([[Statistiques et classements]]).
- **Menus des entraînements** : un menu Settings (variantes, valeurs, Bot KB…) à la place des colorants, gardé d'une partie à l'autre ([[Entraînements]]).
- Les menus de jeu (shop Bed Wars, améliorations, kits) : [[Bed Wars]].
