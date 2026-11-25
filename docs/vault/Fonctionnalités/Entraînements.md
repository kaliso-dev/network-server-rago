---
type: fonctionnalité
tags: [practice, entraînements, ragopractice]
maj: 2026-11-25
code: bukkit/rago-practice (Training et ses sous-classes)
---
# Entraînements

Les entraînements solo de la catégorie **Practice** (`entrainement`), joués par **RagoPractice**. Chaque entraînement est une suite d'essais sur une arène reconstruite à chaque essai (`Training`, 1 244 lignes), avec un record personnel et un record du serveur par variante ([[Records des entraînements]]), un menu **Settings** dans la barre (variante, réglages ; gardé par joueur dans `plugins/RagoPractice/reglages.yml`, `TrainingPrefs`), des objets **Trainings** (changer d'entraînement) et **Leave**. Une **poudre verte** passe au niveau suivant, une **poudre rouge** au niveau d'avant ; un **bâton de blaze** relance là où une chute ne relance pas toute seule ; aucun coup n'est donné pendant qu'un menu est ouvert (PR #28, #29). Le Block clutch est le mode « Start here ». Détail complet : `bukkit/rago-practice/README.md`, « Les entraînements ». Retour à [[Catalogue des modes]].

| Mode (id) | Ce qu'on travaille | Points notables |
|---|---|---|
| Block clutch (`clutch`) | au bord d'une petite île de Bed Wars (île de la map BlockClutchNew du propriétaire), un coup jette dans le vide : poser sa laine pour se sauver | knockback, moment du coup et distance réglables dans la barre ; le premier coup après une chute un peu plus tard (PR #29) ; top 3 commun et tableau des plus grands clutchs sur chaque île ; distances de clutch de la semaine |
| Block clutch : top fight (`clutch-top-fight`) | le même clutch sur une plateforme de top fight à la limite de hauteur | aucun bloc au-dessus |
| Block in (`block-in`) | tomber sur la défense d'un lit, s'enfermer, creuser jusqu'au lit, chrono par couche | depuis le 21/11 un **ennemi** sur un côté casse pour entrer et ne frappe que par le trou (`BlockInEnemy`) ; records remis à zéro le 21/11 (après sauvegarde) ; TESTÉ EN JEU le 21/11 |
| Fast defense (`fast-defense`) | couvrir son lit le plus vite possible | défenses du propriétaire : Ranked (end stone, verre) ou duo (end stone, laine) |
| Block edit (`block-edit`) | casser un bloc du mur de laine, frapper le bot derrière, remettre le bloc avant qu'il frappe | comme la map Block Edit Practice du propriétaire (`EditStation`) |
| Extend practice (`extend-practice`) | doubles et triples extends (5+1, 6+0, 6+1, 7+0, 7-1, 7-2) ; « Chain » : 10 pads d'affilée | sauts du Clutch Training de Flarefin |
| Block extend (`block-extend`) | prendre un coup sur le pont, se sauver à la laine, comme dans un vrai bed fight | map BlockClutchExtend ; Gold, Diamond, Emerald : d'où vient le coup |
| Timed bridge (`bridge-chrono`) | bridger 16, 32 ou 64 blocs au chrono | droit ou diagonale, à plat ou en montée, et depuis le 22/11 **avec un virage** ; un record par combinaison |
| Ladder practice (`ladder-practice`) | block-ladder clutch : sauter de 4 à 24 blocs, laine et échelle avant le sol | map de lavy ; une échelle posée juste avant l'atterrissage compte |
| Fast Shop (`shop-practice`, joué par RagoBedwars) | acheter des listes d'objets au shop le plus vite possible, avec son propre quick buy Hypixel | records par liste (`FastShopRecords`, `fastshop.yml`) ; [[Import Hypixel]] |
| Bow training (`bow-training`) | sur Archway, des bots bridgent du milieu vers sa base : les faire tomber à l'arc | touches, précision, plus long tir |
| Aim trainer (`aim-trainer`) | une cible de la taille d'un joueur qui strafe, saute ou tourne | XP par seconde sur la cible ; `/aimtop` |
| Block aim (`block-aim`, 22/11) | une face s'allume (sous les pieds au bout du pont, sur le côté du pont, sur le lit) : y poser un bloc | 5 niveaux de plus en plus rapides (3 s à 1 s, à confirmer), un record par niveau ; pas encore joué en jeu |

**Retirés des menus** (code et records gardés, hors de `/records` et du mur) : KB displace et KB displace contre un bot, Pearl practice, Fireball practice (23/11) ; Combo bot (22/11, « le combo bot on le supprime ») ; Find Your Sensitivity (22/11, 19h51).

## Commun à tous les entraînements
- XP Network des entraînements (`xp-entrainement:` de RagoPractice, `PracticeXp`) : un peu par minute, par essai, plus par réussite, plafonné.
- **Défi du jour** (`/challenge`, `defi-du-jour:`) : un entraînement pour tout le monde chaque jour (heure de Paris), le top 3 gagne de l'XP ([[Progression et récompenses]]).
- Les arènes sont des zones du monde `arenes`, refait à chaque démarrage (`rago-arenas.txt`) ; chaque bloc changé est remis à la fin d'un essai ou d'une partie (`Arena`, corrigé le 21/11 : bug 1.1).
- Spectateurs possibles (`/spectate`), jusqu'à 10 en même temps testés en CI ([[Replays et spectateurs]]).
- Rien ne s'use (`Unbreakable`).

Voir aussi [[Duels]], [[Arènes ouvertes]], [[Code de rago-practice]].
