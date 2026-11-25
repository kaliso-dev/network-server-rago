---
type: fonctionnalité
tags: [events, host, premium]
maj: 2026-11-25
code: bukkit/rago-practice (Events, EventGame)
---
# Events host

Comme sur Minemen : un joueur **PREMIUM** (`rago.perk.host`) lance un event pour tout le serveur avec `/host` (menu) ou `/host sumo|brackets|lms`. Détail : `bukkit/rago-practice/README.md`, « Les events (/host) ». Retour à [[Catalogue des modes]].

## Les trois events
- **Sumo** : un 1v1 à la fois sur les maps du sumo pendant que les autres regardent, par manches (un joueur sans adversaire passe).
- **Brackets** : un tournoi en 1v1 avec épée et armure, même déroulé.
- **Last Man Standing** (LMS) : tout le monde en même temps, le dernier debout gagne.
Modes cachés du lobby : `event-sumo`, `event-brackets`, `event-lms`. 90 s au plus par combat (celui qui a le plus touché passe).

## Déroulé
- **Annonce dans le chat toutes les 20 s** jusqu'au lancement (demande du 21/11) : message centré entre deux lignes (« SUMO EVENT », « Hosted by … », l'event, « Starts in 40s • 3/40 players ») et un **[CLICK TO JOIN]** vert (tout le message lance `/join`) ; les inscrits lisent « You are in! ».
- S'inscrire **ne téléporte pas** : le joueur continue sa partie ou son entraînement. Au lobby, une **poudre rouge** prend le dernier slot de la barre : « Leave the event », ou « Cancel the event » pour l'hôte (`/event leave`, `/event cancel`).
- Après `attente-secondes`, l'event commence dès `joueurs-min` inscrits : les inscrits en partie la quittent comme avec `/spawn` (un abandon, avec ses suites : file classée fermée après une partie classée), puis tous y vont. 10 s d'attente au plus sur la plateforme.
- Gagnant annoncé à tout le serveur, `xp-gagnant` XP Network, les autres `xp-participation`. Un event à la fois, une pause entre deux, un délai par hôte (pas pour le staff). L'event tombe si son hôte part sans autre inscrit.
- `/event` dit où il en est. Réglages : `evenements:` du `config.yml` de RagoPractice.

## Events automatiques
Au nom de « Rago » : dès `joueurs-lobby` joueurs hors partie (6) et aucun event depuis `minutes` (20), le serveur annonce le suivant de `types` (sumo, brackets, lms à tour de rôle) de la même façon. Sans `joueurs-min` inscrits à la fin de l'attente, il tombe (sans un mot si personne ne s'était inscrit). Un PREMIUM peut lancer le sien à sa place tant que personne ne s'est inscrit. `evenements.automatiques.actif: false` les coupe.

Voir aussi [[Rangs, boutique et cosmétiques]], [[Replays et spectateurs]] (les events se regardent).
