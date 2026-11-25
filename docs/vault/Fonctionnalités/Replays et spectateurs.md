---
type: fonctionnalité
tags: [replays, spectateurs, spectate]
maj: 2026-11-25
code: bukkit/rago-lobby (ReplayService, ReplayRecorder, ReplayViewer, ReplayData, ReplayHook, Spectating), bukkit/rago-practice (Spectators), bukkit/rago-bedwars (Watchers, ReplayWorlds)
---
# Replays et spectateurs

Retour à [[Lobby]]. Détail : `bukkit/rago-lobby/README.md` (« Les replays », « Regarder une partie en direct »).

## Les replays
- **Enregistrés** : les duels de RagoPractice (sumo compris) et les parties de Bed Wars en équipes. **Pas** : arènes ouvertes, entraînements, FFA, Fast Shop, Invis, Bed Break Simulator, Top Bridge d'entraînement d'une party.
- Contenu : mouvements, coups, vie, objets tenus et portés, blocs posés et cassés, flèches, boules de feu, perles, TNT, messages, et les **sons** que le serveur envoie (pas les pas). Un replay plus ancien n'a pas de sons.
- `/replay` (menu des dernières parties), `/replay CODE` (n'importe quelle partie, à partager), **[REPLAY]** après la partie, `/replay leave`. On regarde depuis le lobby en volant ; barre : pause, ±10 s, vitesse ×0,25 à ×4, aller voir un joueur, lit pour sortir.
- **En party**, le chef qui regarde fait venir ses membres avec `/p warp` : ils voient le même moment, le chef mène.
- Duel rejoué dans le monde `rago_replays` (effacé à chaque démarrage), Bed Wars dans une copie libre de sa map. Fichiers dans `plugins/RagoLobby/replays` (dizaines de Ko pour un duel, centaines pour un Bed Wars). Réglages `replays:` : `jours` 3, `taille-max-mo` 2000, `spectateurs-max` 6, `actif`.

## Regarder en direct : /spectate
- `/spectate <joueur>` (`/spec`) depuis le lobby, **ouvert à tous** (`rago.perk.spectate` ne change plus rien) ; la partie d'un joueur sous `/nick` reste cachée sauf pour le staff et ses amis.
- Les joueurs lisent « <nom> is now spectating your game. » en rouge gras avec un ding (22/11), pas pour le staff en vanish.
- Spectateur invisible (mode spectateur), ne touche à rien ; clic gauche = vue à la première personne ; garde sa place dans les files. `/spawn` ou la fin de partie le ramène.
- Tout se regarde : duels, sumo, entraînements, events, Bed Wars. Jusqu'à 10 spectateurs en même temps testés en CI ([[Tests et bots]]).
