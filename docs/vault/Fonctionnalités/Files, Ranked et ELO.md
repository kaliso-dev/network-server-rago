---
type: fonctionnalité
tags: [files, matchmaking, ranked, elo, saisons]
maj: 2026-11-25
code: game/rago-game-model (Matchmaker, ModeQueue, RatingWindow), bukkit/rago-lobby (QueueController, EloLadder, StatsBook, Season, RankedBans)
---
# Files, Ranked et ELO

Retour à [[Catalogue des modes]]. Code : [[Code de rago-game-model]], [[Code de rago-lobby]].

## Les files (matchmaking)
- Le matchmaking de `rago-game-model` (Java pur, sans Bukkit) tourne **une fois par seconde** dans le lobby (`QueueController`).
- **Non classé** : premier arrivé, premier servi ; équipes distribuées à tour de rôle. Priorité par rang (`priorite-file` : PREMIUM+ 2, VIP 1, autres 0) **jamais en classé** ; après une minute d'attente tout le monde passe devant ([[Rangs, boutique et cosmétiques]]).
- Un joueur seul attend dans **3 files au plus** (`MAX_QUEUES`) ; la première partie trouvée le sort des autres ; « Leave the queue » (colorant rouge) ou `/queue leave` les quitte toutes. Les entraînements ne comptent pas dans les trois : on garde sa place en s'entraînant, et la partie trouvée sort de l'entraînement directement. Une party n'attend que dans une file.
- Choix de map : un mode à plusieurs maps prêtes ouvre « Random map » ou une map précise (`/queue <mode> <map>`) ; on ne joue qu'avec ceux qui ont choisi la même map ou n'importe laquelle.
- Partie impossible (aucune arène ou copie libre) : les joueurs gardent leur place.

## Le Ranked
- **Catégorie Ranked** (`classe`, `/ranked`, PNJ Ranked sur le point `prive` depuis le 23/11) : Ranked Bedwars 1v1 à 4v4 et six duels classés en 1v1 (`ranked-bed-fight-1v1`, `ranked-top-bridge-1v1`, `ranked-bridge-fight-1v1`, `ranked-block-fight-1v1`, `ranked-sumo`, `ranked-boxing`), chacun le même jeu que son mode non classé (même map, `kit-de:`), avec sa file et son ELO.
- Appariement : le joueur qui attend depuis le plus longtemps contre les ELO les plus proches ; écart accepté **100 points**, +50 toutes les 10 s, jusqu'à 500 (`file.ecart-elo-*`) ; équipes équilibrées par ELO (1-2-2-1). Une party file avec la moyenne de ses ELO.
- **Seule une partie trouvée par la file compte** : un `/duel`, une partie privée, un duel entre parties ou une partie avec RagoBot ne bougent ni l'ELO ni les séries.
- **Abandon** (quitter une partie classée en cours : `/spawn`, Play again, déconnexion) : files classées fermées **5 minutes** (`file.abandon-classe-minutes`), aussi pour sa party ; un redémarrage les rouvre (`RankedBans`).
- En partie classée, l'ELO du mode remplace le niveau à côté du nom (`R Sofiene Gold 245`), sauf sous `/nick` ; en Bed Wars, la barre d'XP montre l'ELO.

## L'ELO (`elo:` du config du lobby, valeurs « à nous », les chiffres de RBW ne sont pas publics)
Commence à 0 par mode. Onze divisions ; une victoire rapporte plus et une défaite coûte moins en bas ; le MVP (le plus de kills) gagne `bonus-mvp` 5 de plus (`EloLadder`). Gardé dans les fichiers de stats du lobby (`StatsBook`).

| Division | ELO | Victoire | Défaite |
|---|---|---|---|
| Coal | 0 | +35 | −5 |
| Iron | 100 | +30 | −10 |
| Gold | 200 | +30 | −15 |
| Diamond | 300 | +25 | −15 |
| Emerald | 400 | +25 | −20 |
| Sapphire | 500 | +20 | −20 |
| Ruby | 600 | +20 | −25 |
| Crystal | 700 | +15 | −25 |
| Opal | 800 | +15 | −30 |
| Amethyst | 900 | +10 | −30 |
| Obsidian | 1000 | +10 | −35 |

## Les saisons (`/season`, `saison:`)
Saison en cours : **numéro 2, « Season 2 »** (celle des divisions), `fin: ""` (pas de date). Nouvelle saison = numéro suivant + redémarrage : l'ELO repart de 0, le meilleur ELO de la saison finie reste au profil (« Season 2: Diamond 310 ») et rapporte des Rago Points une fois (Iron 250, Gold 500, Diamond 1000, Emerald 1500, Sapphire 2000, Ruby 3000, Crystal 4000, Opal 5000, Amethyst 7500, Obsidian 10 000) et un badge de saison (`S2 Diamond`).

## À savoir
- Le README de `rago-game-model` dit encore que « tous les joueurs ont le même ELO » tant que `rago-data` n'existe pas : **c'est dépassé**, l'ELO est gardé par joueur dans les stats du lobby ([[Bugs et problèmes connus]]).
- Sur le futur réseau, les files passeront dans le proxy (`rago-proxy`, pas écrit) : [[Architecture]].
