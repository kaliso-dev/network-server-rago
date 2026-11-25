---
type: fonctionnalité
tags: [stats, classements, leaderboards]
maj: 2026-11-25
code: bukkit/rago-lobby (StatsService, StatsBook, StatsFiles, PlayerStats, ModeStats, StatsMenu, StatsDisplays, BoardStat, PodiumPlace, WeeklyRewards, TeamStreak)
---
# Statistiques et classements

Retour à [[Lobby]]. Détail : `bukkit/rago-lobby/README.md` (« Les stats », « Catégories, PNJ et classements »).

## Ce qui est compté
- Les plugins de jeu donnent chaque résultat au lobby (`recordMatch`, `recordKill`, `recordDraw`, `forfeited`) : victoires, défaites, égalités, série en cours et meilleure série, par mode, **à vie, du jour et de la semaine** (heure de Paris : jour à minuit, semaine le lundi à minuit). Games played = victoires + défaites + égalités. Un abandon = une défaite.
- Kills et morts ; au sumo chaque point est un kill. En Bed Wars un final kill compte seulement en final kills (et final deaths pour la victime), comme Hypixel. FKDR et WLR à deux décimales.
- Séries d'**équipe** (les mêmes joueurs ensemble) : `plugins/RagoLobby/stats/equipes/`, un fichier par mode ; affichées en « Daily Streak » dans les menus.
- Stockage sur le serveur de test : `plugins/RagoLobby/stats/` (un fichier par joueur), écrit hors du thread principal. Pas de base de données (prévue avec `rago-data`, voir [[Architecture]]).
- **Remise à zéro du 23/11/2026** (`stats-2026-11`, demandée par Sofiene, « les stats seulement ») : copie dans `stats-sauvegarde-stats-2026-11/`, puis XP, niveaux, modes, ELO, séries, meilleurs scores et records des entraînements et du Fast Shop remis à zéro. Gardés : Rago Points, rangs, cosmétiques, amis, réglages, quêtes, récompenses déjà données, saisons. **Aucune autre remise à zéro jusqu'à la sortie publique.**

## Où les voir
- `/stats [joueur]`, `/top elo|level|wins|kills|fkdr|streak|games|points [mode]`.
- **PNJ de stats** (`type: stats`, skin « l'analyste ») : au-dessus, le profil de celui qui regarde ; un clic ouvre « Your stats » par catégorie puis par mode.
- **Murs de classements** (`classements:`) : 1 à 5 tableaux côte à côte, top 10 + place du joueur ; clic = période (jour, semaine, à vie) ou mode classé ; tableaux groupés (mur `top` : niveau, ELO, FKDR, kills, parties). **Podiums** (`podium:`, marches `top1`…`top3`) avec le skin des premiers.
- **Sélecteur de leaderboards** (plan 3.1, PNJ `newleaderboardselector`) : menu des stats (meilleure série, niveau, ELO par mode classé, victoires, kills, FKDR, WLR, parties), affichées sur les trois murs Daily, Weekly et Lifetime ; FKDR/WLR du jour et de la semaine avec un minimum de parties (5 le jour, 15 la semaine).
- Stats au-dessus du pseudo : `/display` ([[Progression et récompenses]]).

## Classements de la semaine
Le lundi à minuit, le top 3 de chaque classement de la semaine (victoires, meilleure série, kills, parties, plus gros clutchs du practice) gagne 2 000, 1 000 et 500 Rago Points et la **couronne ♛** jusqu'aux résultats suivants (`semaine:`, `semaine.yml`). Mur `semaine` avec son podium.

Voir aussi [[Records des entraînements]], [[Files, Ranked et ELO]].
