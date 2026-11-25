---
type: fonctionnalité
tags: [party, amis, messages, duel]
maj: 2026-11-25
code: bukkit/rago-lobby (Parties, PartyBook, PartyDuels, DuelRequests, Social)
---
# Party, amis et messages

Le côté social du lobby, comme sur Hypixel. Détail : `bukkit/rago-lobby/README.md`. Retour à [[Lobby]].

## La party
- `/p <joueur>` ou `/party invite` : invitation d'une minute avec « Click here to join! » ; la party naît à la première invitation. **16 joueurs au plus** depuis le 22/11 (limite choisie pour Bedwars Normal ; 8 avant, comme Hypixel sans grade).
- `/party accept`, `leave`, `list`, `kick`, `transfer`, `warp` (les membres du lobby viennent au chef ; aussi dans un replay), `disband` ; chat de party `/pc <message>` ou `/chat party` (`/chat all` pour revenir). Messages entre deux lignes bleues.
- **Déconnexion** (demande du 25/11) : un joueur qui quitte le serveur reste dans sa party **60 secondes** (point rouge dans `/party list`, le chef peut le kick) ; revenu à temps, il est toujours dedans ; sinon il sort (si c'était le chef, le suivant présent mène). RagoBot sort tout de suite.
- **Les files en party** : seul le chef lance une file. Une party qui remplit toute la partie joue une partie privée entre ses joueurs (sans stats) ; une party qui tient dans une équipe file comme une équipe (en classé, avec la moyenne de ses ELO) ; plus grande qu'une équipe sans remplir la partie : refusée. Un joueur qui quitte la file, la party ou le serveur fait sortir toute la party de la file.
- Voir [[Parties privées]] et [[RagoBot]].

## Les défis : /duel
- `/duel <joueur> [mode]` : défi d'un joueur du lobby, [Accept] / [Deny], une minute (`DuelRequests`). Un duel **n'est jamais classé** (« A duel never changes the ranked ELO. »).
- **Duels entre parties** (`PartyDuels`, demande du 25/11) : le chef d'une party de 2+ tape `/duel` sans nom, ou clique **Party Duels** (dernière case de la catégorie Party) : les autres parties (tête du chef, joueurs ; pas celles dont le chef est déconnecté). Un clic montre les modes des Duels à la taille des deux parties (deux parties de deux : les 2v2 ; tailles différentes ou 3+ : aucun tant qu'il n'y a pas de 3v3/4v4 dans Duels). Le chef d'en face a une minute (`/duel accept <chef>`, `/duel deny <chef>`). Ni ELO ni série ; victoires et XP comptent. Tous au lobby, aucun gelé, sans RagoBot. Proposition : artifact « Proposition 25/11 duels entre parties » (https://claude.ai/artifact/KkzxkuPALJ5WkmDx915nif).
- Après un 1v1 non classé : **[REMATCH]** au retour au lobby.

## Amis et messages privés
`/friend add|accept|deny|remove|list|requests|toggle` (ou `/f`) : 100 amis au plus, demandes cliquables qui expirent après 5 minutes, « Friend > X joined. ». `/msg` (`/tell`, `/w`) et `/r`. Chaque type (messages, invitations de party, demandes d'ami, défis) se coupe dans `/settings`. Le clic droit sur un joueur ouvre son menu (duel, stats, party, ami, offrir un rang, Staff pour le staff) : [[Boussole et menus]].
