---
type: fonctionnalité
tags: [records, entraînements]
maj: 2026-11-28
code: bukkit/rago-practice (Records, RecordBook, RecordCatalog, RecordsMenu, DailyChallenge, Challenges), bukkit/rago-bedwars (FastShopRecords)
---
# Records des entraînements

Retour à [[Entraînements]]. Détail : `bukkit/rago-practice/README.md` (« Les entraînements »).

- Chaque **variante** d'entraînement a son record : le meilleur score du joueur et celui du serveur, dans `plugins/RagoPractice/records.yml` (Fast Shop : `plugins/RagoBedwars/fastshop.yml`).
- **Lifetime** (jamais vidés sauf remise à zéro demandée : Block in le 21/11, toutes les stats le 23/11) et **Weekly** (du lundi au dimanche, heure de Paris, clé `weekly.2026w47.clutch.island`, gardés cinq semaines). Pas de records Daily (le défi du jour a son top du jour).
- **`/records [joueur]`** : tous les entraînements, meilleur score et place du joueur sur chaque record, résumé (records, premières places, top 10). Un entraînement ouvre ses records : top 10 Lifetime, place du joueur et parmi ses amis, top 3 de la semaine, meilleur des cinq semaines d'avant. Un clic sur un record le joue.
- **Mur des records** au lobby (point `records`, `mur-des-records`) : top 10 Lifetime d'un record après l'autre toutes les 8 s, meilleur de la semaine, place du joueur, défi du jour ; clic gauche = suivant, clic droit = `/records`.
- Scoreboard d'un entraînement : top 3 du serveur, meilleur score du joueur et son rang, dernier score.
- Dans la boussole, un entraînement montre le top 3 Lifetime de sa première variante ([[Boussole et menus]]).
- **Défi du jour** (`/challenge`, `defi-du-jour:`) : un entraînement pour tout le serveur chaque jour, à tour de rôle (Block-In côté bois, island clutch, aim trainer, fast defense, ladder, extend…) ; le top 3 gagne de l'XP.
- **Nouveaux records du 28/11** (`RecordCatalog`) : **Hypixel Bow Practice** (`bow-practice`, PR #56), un record par niveau (pont droit, en diagonale, en hauteur), le score est la série de victoires d'affilée (« wins in a row ») ; **Block in** a un 4e record, « RBW defense » (PR #57), comme ses trois autres niveaux ([[Entraînements]]). Installés le 28/11, aucun score encore fait en jeu.
- **En attente (PR #58, pas installée)** : **Block Pattern Practice** (`block-pattern-practice`), un record par technique (le temps), 10 techniques ; avec lui, 18 entraînements ont des records (`RecordCatalog`).
- Block clutch : tableau des plus grands clutchs sur chaque île et distances de clutch de la semaine (classement de la semaine récompensé, [[Statistiques et classements]]). Aim trainer : `/aimtop`.
- Les entraînements retirés des menus (KB displace, Pearl practice, Fireball practice, Combo bot, Find Your Sensitivity) gardent leurs records dans les données, hors de `/records` et du mur.
- Une partie avec un outil Owner actif ne compte pour aucun record ([[Outils Owner]]).
