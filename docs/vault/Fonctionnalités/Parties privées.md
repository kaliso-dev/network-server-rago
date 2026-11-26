---
type: fonctionnalité
tags: [parties-privees, private-games, bedwars-normal, party]
maj: 2026-11-26
code: bukkit/rago-lobby (TeamRoom, TeamRooms, PrivateSetting, PartyDuels), bukkit/rago-bedwars (PrivateWaitingLobby, PrivateRules, FfaGame)
---
# Parties privées

L'objet **Private games** (dans Bedwars Modes et dans la catégorie Party de la boussole) permet au **chef d'une party** de lancer une partie pour sa party seule. Une partie privée **ne compte dans aucune stat** (ni ELO, ni XP, ni victoire). Il remplace depuis le 22/11 l'ancienne « Private game » (RBW 1v1 à 4v4) et le « Private top bridge ». Détail : `bukkit/rago-lobby/README.md` et `bukkit/rago-bedwars/README.md` (« Bedwars Normal »). Retour à [[Catalogue des modes]].

## Bedwars Normal (`parties-privees-equipes:`, réponses de Sofiene du 22/11)
1. Le chef clique Bedwars Normal et choisit la map **au lobby** : une ligne pour les **4s** (maps à 4 équipes, `bedwars-normal-4s`), une pour les **2s** (maps à 8 équipes, `bedwars-normal-2s`). Toutes les maps du serveur (`toutes`) avec exactement ce nombre d'équipes (`equipes-exactes:`).
2. **Dès que la map est choisie** (demande du 25/11), toute la party est téléportée dans le **lobby d'attente au-dessus de cette map**, dans une copie à elle (`MatchHost.canGather` / `gather`, `PrivateWaitingLobby`), en aventure. Pas de dégâts, pas de faim, rien à construire ; chute = retour au milieu ; chat = chat de party. Si la copie se fait encore, la party attend quelques secondes au lobby.
3. Chacun choisit son équipe (laine, case 5 ; `/pg team`) pendant **30 s** (`compte-a-rebours`, 23/11), ramenées à **15 s** dès que tout le monde a choisi. Une équipe pleine refuse ; une équipe ne peut pas contenir toute la party. Qui n'a pas choisi va dans l'équipe la moins remplie (au moins deux équipes).
4. Le chef règle la partie (comparateur **Game settings**, case 2 ; `/pg settings` ; clic = choix suivant, clic droit = précédent) ; la party lit chaque changement. Les réglages restent ceux du chef jusqu'à sa déconnexion.
5. La partie part sur **la même copie**, sous leurs pieds. De 2 à 16 joueurs. Une party qui change, ou un joueur qui quitte, annule ; `/spawn` annule pour toute la party.

Une map sans `salle-attente:` : la party attend dans celle de `salle-attente-privee` (**Archway FFA**, la seule réglée sur le serveur de test, confirmé par Abel le 26/11), puis joue sur une copie de la map choisie. Aucune salle : les équipes se choisissent au lobby.

**À faire par Abel en jeu** (26/11) : poser le lobby d'attente de **chaque map de Bedwars Normal** (`/bw edit <map>`, debout sur le sol voulu, puis `/bw lobbyfight`, qui écrit `salle-attente:` dans le `bedwars.yml` de la map) ([[Maps et mondes]], [[État actuel]]).

**Les réglages** (`reglages-prives:`, `PrivateRules`, testés par `PrivateRulesTest`) : les quatre de Sofiene d'abord, **Speed** (aucun, I, II, III), **Max Diamond** (Sharpened Swords + Reinforced Armor IV au départ ; Maniac Miner, Forge et Heal Pool restent à acheter, à confirmer), **Max Emerald** (émeraudes au Tier III dès le départ), **Break the Map** (tout bloc se casse sauf près des spawns et générateurs) ; puis ceux d'Hypixel : **Health Buff** (10/20/30 cœurs), **Low Gravity** (Jump Boost III), **Respawn Time** (1 ou 10 s), **Event Times** (0.5x, 2x, 4x), **Bed Instabreak**, **One Hit One Kill** (choix du 23/11 : le chef coche les armes qui tuent d'un coup parmi Wooden/Stone/Iron/Diamond Sword, Knockback Stick, Bow, Fireball, TNT ; le poing n'y est pas ; la liste exacte d'Hypixel n'est écrite nulle part).

## FFA Archway (`parties-privees:`, choix du 23/11 : « 30 minutes, puis classement des kills »)
Le chef clique FFA Archway (`ffa-archway`) : toute la party (2 à 16) entre dans sa propre copie d'Archway FFA, chacun pour soi, avec le kit du FFA (`kit-de: ffa`). Le jeu caché `ffa-archway-partie` (16 équipes d'un joueur) ne se lance que de là. Fin au bout de `ffa-privee-minutes` (30, de 1 à 120) avec le **classement des kills** de la partie (`FfaGame.leaderboard`), « One minute left. » avant. Voir [[Arènes ouvertes]].

## Ce qu'une party joue contre elle-même
Une party qui remplit exactement une partie (2 joueurs dans un 1v1, 4 dans un 2v2…) joue tout de suite une partie privée entre ses membres, sans stats. La catégorie **Party** de la boussole montre ces modes à la taille de la party. Les **duels entre parties** sont dans [[Party, amis et messages]].

Voir aussi [[Bed Wars]], [[Boussole et menus]].
