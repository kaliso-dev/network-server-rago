---
type: index
tags: [index]
maj: 2026-11-26
---
# Rago Practice : le projet

> **Nouveau Claude ? Lis cette note, puis [[État actuel]], puis [[Règles de travail]].**
> **Règle n°1 : ce vault doit correspondre en tout point à l'état actuel du projet. Avant de finir une tâche, suis [[Mettre à jour le vault]] : mets à jour, supprime ce qui est abandonné, demande en cas de doute.**

**Rago Practice** est un serveur Minecraft **1.8.9** (PvP 1.8), hébergé en Europe, pour **s'entraîner au Bed Wars et au Ranked Bedwars (RBW)** : des entraînements solo (clutch, bridge, block in…), des duels, des arènes ouvertes, des modes Bed Wars inspirés d'Hypixel, un Ranked avec ELO, et un lobby façon Hypixel / Minemen. Sa promesse : un **knockback aussi proche que possible de celui d'Hypixel, mesuré et non deviné** ([[Knockback et combat]]). Slogan en jeu : **« Train. Clutch. Climb. »**. Tout le jeu est en **anglais** ; le code, les commits récents, la doc et ce vault sont en français (les commits sont en anglais).

Le dépôt GitHub s'appelle **`kaliso-dev/network-server-rago`** (public depuis le 10/11, voir [[CI GitHub Actions]]). Le projet est aussi appelé « Rago Minecraft server » par Sofiene. Décideur : [[Personnes et comptes|Sofiene]] (GitHub `kaliso-dev`), qui valide les actions GitHub et les installations. Essais en jeu, réglages de maps et séances de mesure : **Abel** (propriétaire du compte qRago et du PC du serveur de test). C'est un **projet à part** de la famille Rago, à côté de Rago Optimizer (dépôt `kaliso-dev/Rago`, son propre vault) et de Rago Client (dépôt `kaliso-dev/Rago-Client`), dont il ne fait pas partie : voir [[Autres projets Rago]].

**Rien n'est en production** au 26/11/2026 : le serveur tourne seulement comme [[Serveur de test]] sur le PC Windows d'Abel, et le [[Site]] n'est pas en ligne (domaine, Discord, site et hébergeur sont mis de côté jusqu'à ce que le serveur soit fini). La sortie publique est préparée par le plan de Sofiene du 21/11, suivi dans `docs/suivi-sortie-publique.md` ([[Plan de sortie publique]]).

## Les briques
| Brique | Note | Où dans le dépôt |
|---|---|---|
| Moteur de combat (knockback mesuré d'Hypixel) | [[Knockback et combat]] | `combat/` |
| Modèle de jeu (modes, files, matchmaking, ELO) | [[Files, Ranked et ELO]] | `game/rago-game-model/` |
| Lobby (RagoLobby) | [[Lobby]] | `bukkit/rago-lobby/` |
| Practice : duels, entraînements, arènes, events (RagoPractice) | [[Entraînements]], [[Duels]], [[Arènes ouvertes]] | `bukkit/rago-practice/` |
| Bed Wars et ses variantes (RagoBedwars) | [[Bed Wars]] | `bukkit/rago-bedwars/` |
| Outils de build (RagoBuildTools) | [[Serveur de test]] | `tools/build-tools/` |
| Outils de mesure et tests avec bots | [[Outils de mesure du knockback]], [[Tests et bots]] | `tools/` |
| Site officiel (Next.js) | [[Site]] | `site/` |

Vue technique : [[Architecture]]. **Pour coder ou dépanner : commencer par [[Reprendre le développement]].**

## Carte du vault
- **Pilotage** : [[État actuel]] · [[Prochaines étapes]] · [[Plan de sortie publique]] · [[Questions ouvertes]] · [[Journal]]
- **Règles** : [[Règles de travail]] · [[Décisions]] · [[Clés et secrets]] · [[Mettre à jour le vault]]
- **Le jeu** : [[Catalogue des modes]] · [[Lobby]] · [[Boussole et menus]] · [[Entraînements]] · [[Duels]] · [[Arènes ouvertes]] · [[Bed Wars]] · [[Fun Practice]] · [[Red Rover et 3 Players]] · [[Parties privées]] · [[Events host]] · [[RagoBot]]
- **Joueurs et progression** : [[Party, amis et messages]] · [[Files, Ranked et ELO]] · [[Statistiques et classements]] · [[Records des entraînements]] · [[Progression et récompenses]] · [[Rangs, boutique et cosmétiques]] · [[Replays et spectateurs]] · [[Import Hypixel]] · [[Staff et modération]] · [[Outils Owner]]
- **Combat** : [[Knockback et combat]] · [[Mesures Hypixel]] · [[Boules de feu et TNT]]
- **Technique** : [[Reprendre le développement]] · [[Architecture]] · [[Construire et tester]] · [[Tests et bots]] · [[CI GitHub Actions]] · [[Serveur de test]] · [[Installer une version sur le serveur de test]] · [[Configuration]] · [[Commandes et permissions]] · [[Maps et mondes]] · [[Outils de mesure du knockback]] · [[Site]] · [[Branches et PR]] · [[Hébergement et production]] · [[Dépendances et compatibilité]]
- **Code, une note par module** (dossier `Code/`, chaque classe y est décrite) : [[Code de rago-combat-model]] · [[Code de rago-combat-engine]] · [[Code de rago-game-model]] · [[Code de rago-lobby]] · [[Code de rago-practice]] · [[Code de rago-bedwars]] · [[Code de rago-build-tools]] · [[Code des outils]] · [[Code du site]] · [[Code de la CI et du build]]
- **Suivi** : [[Personnes et comptes]] · [[Bugs et problèmes connus]] · [[Sources]] · [[Autres projets Rago]]
- **Historique** (pour comprendre d'où vient une chose, pas pour décider) : [[Chronologie du projet]] · [[Pull requests]] · [[Plan d'architecture v1]]

## Statuts utilisés dans les notes
CONFIRMÉ (vérifié dans une source primaire : code, git, PR) · DÉCIDÉ (décision datée de Sofiene) · OBSERVÉ (lu dans un document de suivi ou un fil, non revérifié) · IMPLÉMENTÉ (code écrit et testé automatiquement, pas encore joué en jeu) · TESTÉ EN CI · TESTÉ EN JEU · À VÉRIFIER · À DÉCIDER · BLOQUÉ (il manque une info ou une décision). **La décision la plus récente de Sofiene prime.**
