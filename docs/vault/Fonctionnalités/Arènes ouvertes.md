---
type: fonctionnalité
tags: [arènes, ffa]
maj: 2026-11-28
code: bukkit/rago-bedwars (FfaGame, LobbyFight, BlockDecay, KillBoard), bukkit/rago-practice (BlockArena, BridgeArena)
---
# Arènes ouvertes

La catégorie **Arenas** (`arenes`) : des FFA ouverts où l'on entre et sort quand on veut, sans file ni fin de partie ; les kills comptent dans les stats du mode ([[Statistiques et classements]]). Retour à [[Catalogue des modes]].

| Mode (id) | Plugin | Règles |
|---|---|---|
| FFA Ranked Bedwars (`ffa`) | RagoBedwars | sur **Archway FFA** (l'Archway du propriétaire, ponts de laine, défenses end stone et verre, escaliers jusqu'au top bridge à y 85, construction jusqu'à y 95) ; kit épée en pierre, pommes d'or, 3 boules de feu, perle, 2 stacks de laine, cisailles ; armure Protection IV (jambières et bottes en diamant, casque et plastron en cuir rouge) ; un kill soigne et **redonne tout le kit** ; blocs posés partis au bout de **10 s** ; blocs seulement sur les îles (`iles:` du `bedwars.yml`), jamais sur les ponts ni près d'un point d'apparition ; à chaque vie, menu **Top bridge / Low / Mid / Random** (`/ffaspawn`) ; murs « TOP KILLS » (10 meilleurs à vie) près du top bridge et au low ; top 3 de la partie dans la sidebar |
| Top Bridge FFA (`top-bridge-ffa`) | RagoBedwars | le top bridge d'Invasion en FFA, armure en diamant, retour en haut à chaque mort ; mur des kills à côté |
| Lobby Fight FFA (`lobby-fight-ffa`) | RagoBedwars | dans le lobby d'attente (plancher de verre) d'Archway FFA ; **même kit que le Lobby Fight 1v1** (21/11) : épée en pierre, 5 pommes d'or (remises à 5 à chaque kill), 3 boules de feu, 12 end stone, pioche en bois, perle, 2 stacks de laine, cisailles, Haste ; blocs partis au bout de 10 s (pas ceux du lobby) ; 50 places |
| Arena Free (`free-ffa`) | RagoPractice | l'arène verte du propriétaire (monde `Arena`) ; une épée en fer, sans armure, Résistance V : personne ne meurt des coups, seul le vide renvoie au départ ; `/center` ramène au centre (pas dans les 5 s après un coup) ; le slime du cube noir fait sortir du trou |
| Block placement arena (`block-fight-arena`) | RagoPractice | la même arène verte ; épée en bois, laine sans fin, cisailles, Résistance V : faire tomber les autres ; les blocs se fissurent puis cassent au bout de **15 s** |

**Retiré des Arenas le 27/11** (liste d'Abel, PR #54) : le **Bridge fight FFA** (`bridge-fight`, l'arène de plusieurs plateformes de la map « Bridge Fight Plateform »). Il n'est plus dans le `config.yml` du lobby ; RagoPractice garde son code et ses records et le laisse fermé (« The lobby has no mode "bridge-fight" in its config.yml: it stays closed. », log du 28/11, voulu). Les **Bridge fight 1v1 et 2v2** restent dans [[Duels]], sur une plateforme de cette map. Depuis le 27/11, l'objet de la catégorie Arenas est une laine rouge ([[Boussole et menus]]).

Le Lobby Fight 1v1 est un duel ([[Duels]]). Le FFA Archway en partie privée est une copie du FFA pour une party seule ([[Parties privées]]). Voir aussi [[Bed Wars]], [[Maps et mondes]].
