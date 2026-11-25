---
type: fonctionnalité
tags: [niveau, xp, quêtes, daily, récompenses]
maj: 2026-11-25
code: bukkit/rago-lobby (NetworkLevel, LevelRewards, LevelMenu, Quests, QuestsNpc, GettingStarted, DailyChest, DailyRewards, Titles, TitlesMenu, WeeklyRewards, XpBoosters, HeadLines), bukkit/rago-practice (PracticeXp)
---
# Progression et récompenses

Retour à [[Lobby]]. Détail : `bukkit/rago-lobby/README.md`.

## Le niveau Network (`niveau:`)
- XP : **50 par victoire, 15 par défaite, 5 par kill** ; entraînements : un peu par minute, par essai, plus par réussite, plafonné (`xp-entrainement` de RagoPractice : 1, 1, 3, 8 au plus par minute).
- Niveau 1 à 1000 ; le niveau 2 demande 100 XP, chaque niveau 50 de plus ; environ 250 000 XP pour le 100, puis 5 050 XP par niveau.
- Affiché comme Hypixel `[12✫]` (tête, Tab, chat), une couleur par palier de 10 (gris, blanc, or, aqua, vert, cyan, rouge, rose, bleu, violet, arc-en-ciel au 100).
- **Prestiges** tous les 100 niveaux (200 à 1000), chacun avec son look et des Rago Points (20 000 au 200, +5 000 à chacun, 100 000 au 1000), annoncés à tout le serveur (`niveau.prestiges`).
- **Récompenses de niveau** (`/level`, `niveau.recompenses`) : tous les 5 niveaux jusqu'au 100, puis à chaque prestige : Rago Points, cosmétiques, couleurs de pseudo Aqua (25), Gold (50), Legend (100), badge `✫100` ; rattrapées à la connexion.

## Les quêtes (`/quests`, `quetes:`)
- **Du jour** : 3 tirées parmi 8, les mêmes pour tous (jouer 5 parties, en gagner 3, 15 kills, gagner une classée, 25 clutchs, battre un record, jouer le défi du jour, s'entraîner 15 minutes ; 500 à 750 XP).
- **De la semaine** : 5 (40 parties, 25 victoires, 100 kills, 10 classées, 2 h d'entraînement ; 3 000 à 5 000 XP).
- **Getting started** (nouveau joueur, `quetes.debut`) : jouer un entraînement, battre un record, jouer un duel, importer son quick buy Hypixel, trouver le Discord ; 100 à 150 points chacune (600 en tout), puis la traînée Emerald sparks.
- PNJ des quêtes (skin « l'explorateur », point `point-pnj-quetes`, `newquest` sur le serveur de test).

## Le Daily Chest (`/daily`, demande du 22/11 « quelque chose d'impressionnant »)
Coffre au spawn (point `point-coffre-du-jour`, `newdailychest` sur le serveur de test). Une bande de 64 récompenses défile en 4,5 s (23/11), s'arrête sur celle du joueur ; effets selon la rareté. Récompenses (`coffre-du-jour.recompenses`, choix de Claude modifiables) :

| Rareté | Récompense | Chance |
|---|---|---|
| Common | 100 à 200 Rago Points | 30 % |
| Common | 100 à 500 XP | 25 % |
| Rare | 200 à 350 points | 15 % |
| Rare | 500 à 1 000 XP | 13 % |
| Epic | 350 à 500 points | 6 % |
| Epic | 1 000 à 2 000 XP | 6 % |
| Legendary | un cosmétique que le joueur n'a pas | 5 % |

Série de jours : +25 points par jour d'affilée après le premier, jusqu'à 7 (`rago-points.quotidien-serie`).

## Les titres des modes (`/titles`, `titres:`)
Gagnés avec les victoires : paliers Bronze, Silver, Gold, Platinum, Diamond, Master, Legend, Mythic, en cinq niveaux chacun (Bronze I à 10 victoires, Silver I à 75, Gold I à 200, Mythic I à 10 000). Portés dans le chat (`[12✫] [Sumo Gold III] [PREMIUM+] Pseudo`). Pas de titre pour les modes classés (leurs divisions jouent ce rôle).

## Les Rago Points (`rago-points:`)
Victoire 20, défaite 5, kill 2, nouveau niveau = niveau × 10, 1 point pour 3 XP des entraînements et events, Daily Chest. Dépensés dans la boutique : [[Rangs, boutique et cosmétiques]].

## La ligne au-dessus du pseudo (`/display`)
Niveau (défaut, plan 3.5), ELO, victoires, kills, meilleure série, titre, message à soi (PREMIUM) ou rien ; un porte-armure invisible connu des seuls clients.

Voir aussi [[Statistiques et classements]] (classements de la semaine), [[Files, Ranked et ELO]] (saisons).
