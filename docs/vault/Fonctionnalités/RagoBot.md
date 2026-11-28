---
type: fonctionnalité
tags: [ragobot, bot, party]
maj: 2026-11-28
code: bukkit/rago-lobby (RagoBots, BotBrain, BotBody, BotSettings)
---
# RagoBot

Un **adversaire bot** qu'on invite dans sa party (`ragobot:` du `config.yml` du lobby, choix du propriétaire du 22/11). Détail : `bukkit/rago-lobby/README.md` (« RagoBot ») et `docs/suivi-sortie-publique.md`. Retour à [[Catalogue des modes]].

## Ce qu'il fait
- `/p invite RagoBot` (ou `/p RagoBot`) le fait entrer tout de suite dans la party, à côté du chef : un **vrai joueur du serveur** (faux client connecté), avec le skin du compte `skin:` (**qRago**) et le nom RagoBot ; un par party, `max:` à la fois (10, de 1 à 50).
- Il joue les parties de la party dans ses `modes:` comme un membre : party de 2 = 1v1 contre lui ; party avec lui = équipe dans la file du 2v2 ; party de 3 = Red Rover avec lui ([[Red Rover et 3 Players]]).
- **Ordre des modes validé le 22/11** : Top Bridge 1v1 et 2v2 et Sumo 1v1 d'abord ; étape 1 Boxing et Lobby Fight 1v1 ; étape 2 Block placement 1v1, 2v2 et Red Rover Block Placement ; étape 3 Block edit 1v1 et Red Rover Block Edit.
- En Block placement et Block edit, il **pose et casse des blocs par les paquets d'un client**, dans les règles : clutch au-dessus du vide, casse le bloc sous un adversaire hors de portée (laine aux cisailles, end stone à la pioche), saute les marches d'un bloc. Au mur du Block edit (proposition validée le 23/11) : casse le bloc à hauteur de tête, frappe par le trou après son temps de réaction, rebouche. Il ne frappe jamais à travers un bloc.
- **Ping simulé de 40 ms** (`skill.ping`, demande du 23/11 : sans ping il avait le premier coup à chaque fois) : ses coups visent l'endroit où l'autre était 40 ms plus tôt.
- Un seul niveau (`skill:`) : CPS 11, précision 0,88, portée 3,0, strafe 0,8, w-tap 0,75, jump reset 0,45, réaction 3 ticks, rotation 30, pomme d'or sous 10 de vie (`soin-a`), 6 blocs par seconde, ping 40. **Valeurs à confirmer en jeu.**

## Au lobby
Depuis le 27/11, un texte flottant présente RagoBot (« /p invite RagoBot · No friends online? RagoBot plays with you, any time. · Every mode, like a real player, 40 ms ping. · Warm up before Ranked, or learn with no pressure. », `textes.ragobot`, point `ragobot`) ([[Lobby]]).

## Block Method (EN COURS)
Plan validé par Abel le 27/11 (« Oui je suis d'accord »), d'après sa vidéo « Block Method » (34 techniques numérotées contre un adversaire, [[Sources]]) :
- **Block Pattern Practice** (Practice) : un niveau par technique, du plus simple au plus dur, les techniques avec lit à la fin ; le bot rejoue le trajet de l'adversaire de la vidéo ; réussi = niveau suivant, raté = le même ; un réglage coupe le passage automatique. **Les 10 premières techniques sont dans la PR #58** (28/11, ni fusionnée ni installée) ; là, « RagoBot » est un **bot d'entraînement** (zombie avec la tête de qRago, comme le Combo bot, mû par `PatternBot`), **pas ce vrai client RagoBot** ([[Entraînements]]).
- **Block Method 1v1** (Duels) : RagoBot (le vrai client) imite un vrai joueur (preshot, full box, pose de blocs). **Pas commencé** ; questions à Abel : condition de victoire, kit, map, et la règle anti full box du Block Placement (`FullBox`), qui contredit le « full box » demandé ([[Duels]], [[Questions ouvertes]]).
- Question ouverte : les 4 « Pattern » solo de la fin de la vidéo ([[Questions ouvertes]]).

## Limites
Jamais en classé, jamais pour remplir une file, jamais chef de party (le chef qui part passe la main au joueur suivant ; sans autre joueur, la party est dissoute). Une partie avec lui compte dans les stats et l'XP des joueurs, **jamais dans l'ELO** ; lui n'a pas de stats. Il quitte le serveur une fois revenu au lobby sans party (`/p kick RagoBot`). `/duel RagoBot` renvoie vers la party. Il quitte une party tout de suite (pas les 60 s de grâce des joueurs).

## Tests
Le combat du bot est testé avec des bots mineflayer et des tests unitaires (`BotBrain`) ; voir [[Tests et bots]]. Il n'a pas été réglé contre des joueurs réels (À VÉRIFIER avec Sofiene).
