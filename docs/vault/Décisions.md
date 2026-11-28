---
type: pilotage
tags: [décisions]
maj: 2026-11-28
---
# Décisions

Les décisions sur Rago Practice, datées, avec les mots de leur auteur quand ils sont connus : celles de Sofiene (décideur) et, depuis le 26/11, les réponses d'Abel données dans Rago Hub. Source : les PR, les commits (« the owner, 23/11 »), `docs/suivi-sortie-publique.md` et Rago Hub. La plus récente prime ; les actions GitHub et les installations attendent l'accord de Sofiene ou, depuis le 26/11, celui d'Abel. Retour à [[Projet]], voir [[Règles de travail]].

## Fondations (06/11 au 12/11)
| Date | Décision | Note |
|---|---|---|
| 06/11 | Architecture v1 : réseau 1.8.8, budget **30 €/mois**, une machine en Europe ; commencer par le **labo du knockback** (M1) avant l'infrastructure | [[Plan d'architecture v1]] |
| 07/11 | Profil `hypixel-approx-v1` et **9 ticks** entre deux coups, issus de la mesure en Housing et en Bed Wars | [[Mesures Hypixel]] |
| 07/11 | **Pas de ping simulé** : jugé « horrible » et injouable en jeu (PR #8 fermée) | [[Knockback et combat]] |
| 08/11 | Tout le texte du jeu en **anglais** (console, menus, commandes) ; anciennes commandes françaises gardées en alias | [[Commandes et permissions]] |
| 08/11 | La liste des modes du lobby suit **la liste de Sofiene** | [[Catalogue des modes]] |
| 10/11 | Rendre le dépôt **public** pour les minutes GitHub Actions | [[CI GitHub Actions]] |
| 11/11 | Prix en **euros** | [[Rangs, boutique et cosmétiques]] |
| 12/11 | Le knockback d'Hypixel entre joueurs, **verrouillé** (`locked: true`) | [[Knockback et combat]] |
| 12/11 | Couleurs du logo : **rouge, blanc et gris** (le logo rouge revient) | [[Lobby]] |
| 12/11 | 5 minutes sans file classée après une partie classée abandonnée | [[Files, Ranked et ELO]] |
| 13/11 | Les files classées **ignorent la priorité** des rangs payants | [[Files, Ranked et ELO]] |
| 13/11 | Site : sombre aux couleurs du logo, puis refonte « Master your skills » qui parle Minecraft ; le practice d'abord | [[Site]] |

## Plan de sortie publique (21/11 au 25/11)
| Date | Décision | Note |
|---|---|---|
| 21/11 | **Plan complet** de la sortie publique (phases 0 à 9), suivi dans `docs/suivi-sortie-publique.md` | [[Plan de sortie publique]] |
| 21/11 | Stats : « toute game compte » (une égalité compte une partie jouée) ; kills Bed Wars : « reset, on repart de zéro » | [[Statistiques et classements]] |
| 21/11 | Lobby Fight : le même kit en 1v1 et FFA ; 1v1 gagné au premier à 3 (« Bo3, le premier à 3 win ») | [[Arènes ouvertes]] |
| 21/11 | Bed Break : bridge « droit, pas en diagonale » ; plateforme du Top Bridge comme départ | [[Bed Wars]] |
| 21/11 | Network Level par défaut au-dessus des joueurs, ELO en partie classée | [[Statistiques et classements]] |
| 21/11 | /spectate ouvert à tous, sauf un joueur sous nick | [[Replays et spectateurs]] |
| 21/11 | Events : les inscrits encore en partie la quittent au lancement (« Les sortir de leur partie ») | [[Events host]] |
| 21/11 | « commence par ces 3 mods » : Fireball Fight, Snowball Spleef, Red Rover | [[Fun Practice]] |
| 21/11 | Clutch Scenario : à partir du 1v2, le joueur seul part sans lit avec une vie | [[Bed Wars]] |
| 21/11 | Catégorie 3 Players dans la boussole seulement, sans PNJ | [[Red Rover et 3 Players]] |
| 22/11 | Liens officiels : « rago-optimizer.com et discord.gg/ragooptimizer » | [[Lobby]] |
| 22/11 | Organisation Solo / Party / Ranked : « Build », avec « les grands duels 1v1 » classés | [[Files, Ranked et ELO]] |
| 22/11 | RagoBot : un vrai joueur invité en party ; ordre en 6 étapes validé ; ping de 40 ms (23/11) | [[RagoBot]] |
| 22/11 | Outils Owner : « Enchaine » (qRago seul, Reach et Scaffold, coupés par défaut, jamais en Ranked) | [[Outils Owner]] |
| 22/11 | Texte de remerciement : « Le texte est parfait » ; puis (17h56) « supprime woryes et directement » (les PNJ modérateurs retirés) | [[Lobby]] |
| 22/11 | Boussole : d'abord comme Minemen, puis (16h22) « remettre la boussole comme avant avec les différentes catégories », puis (17h54) Red Rover, Party, 3 Players et Ranked en haut | [[Boussole et menus]] |
| 22/11 | Moins de rouge (18h22) : le rouge pour la signature, le jaune pour ce qu'on peut faire | [[Lobby]] |
| 22/11 | Daily Chest « impressionnant », probabilités au choix de Claude | [[Progression et récompenses]] |
| 22/11 | Snowball Spleef : « agrandi la map fait 2 étape » ; Parkour Duel « dans une map stylée » ; Find Your Sensitivity puis Block Aim d'abord ; Bed Fight 1v2 pour le stuff amélioré ; CTF en deux types | [[Fun Practice]] |
| 22/11 | « le combo bot on le supprime » (retiré des menus) ; Find Your Sensitivity retiré des menus (19h51) | [[Entraînements]] |
| 22/11 | Clutch Scenario, Invis et Bed Break en 1v1 à 1v4 (à la place des 2v2, 3v3, 4v4) ; une party reste ensemble | [[Bed Wars]] |
| 23/11 | Remise à zéro : « Les stats seulement » (20h55) ; puis plus aucune remise à zéro avant la sortie | [[Statistiques et classements]] |
| 23/11 | Retirer KB Displace, Pearl practice, Fireball Practice des menus | [[Entraînements]] |
| 23/11 | « Renomme le mod Fast bedwars en 2017 Bedwars » ; « RBW Mod » devient « Bedwars Modes » ; `/ranked` ; PNJ Ranked à la place du PNJ Fun Practice | [[Fun Practice]], [[Boussole et menus]] |
| 23/11 | Déconnexion en Bed Wars : la partie continue, 3 minutes pour revenir | [[Bed Wars]] |
| 23/11 | FFA Archway en partie privée : « 30 minutes, puis classement des kills », « Fait tout » ; One Hit One Kill : « le leader choisit les armes » | [[Parties privées]] |
| 25/11 | Private game : la map se choisit au lobby, puis la party va au lobby d'attente au-dessus de la map (remplace le chemin du 23/11) | [[Parties privées]] |
| 25/11 | Party : 60 secondes pour revenir après une déconnexion | [[Party, amis et messages]] |
| 25/11 | Duels entre parties : « Tous les modes, une équipe par party » (9h48) → proposition d'abord | [[Party, amis et messages]] |
| 25/11 | « Tu peux installer oui » (14h47) : installation de `2de1bca` | [[Installer une version sur le serveur de test]] |
| 25/11 | Créer ce vault pour passer sur un compte Claude Team | [[Mettre à jour le vault]] |
| 26/11 | **« l'accord d'abel suffira désormais »** : l'accord d'Abel suffit, au même titre que celui de Sofiene, pour GitHub (push, PR, fermeture, fusion) et pour le serveur de test (écrit dans la conversation principale de Rago Hub, relayé par le chef) | [[Règles de travail]] |

## Réponses d'Abel (26/11, Rago Hub)
| Date | Décision | Note |
|---|---|---|
| 26/11 | Domaine (`rago.gg`), invitation Discord, site et hébergeur **mis de côté jusqu'à ce que le serveur soit fini** (q127 reportée, pas tranchée) | [[Hébergement et production]], [[Site]] |
| 26/11 | **Fermer sans fusion** la PR #48 (events, teinture rouge : dépassée par #47) et la PR #10 (CI sous Java 25 : ne change pas le jeu) ; demande d'Abel (« Je valide »), conseil du Chef ; **fait le 26/11** | [[Branches et PR]] |
| 26/11 | Les mises à jour du vault passent par une **petite PR vers `main`** (option A, « Go A ») : le vault n'existe pas sur la branche de la PR #47 | [[Branches et PR]] |

## Liste et réponses d'Abel (27/11)
| Date | Décision | Note |
|---|---|---|
| 27/11 | **Liste d'Abel pour le lobby et les menus** (détail dans la PR #54) : boussole de 5 lignes, tout centré ; le nombre de joueurs en partie sous chaque objet, comme sur Minemen ; Arenas en laine rouge, Block Placement en laine jaune, Sumo une laisse, Top BedFight un bloc de fer ; Practice et Duels dans son ordre (Block clutch en premier) ; **Bridge fight FFA retiré des Arenas** ; le Shop, les Leaderboards et le coffre du jour tournés vers le centre du lobby ; nouveau texte à gauche de la salle des remerciements, texte de RagoBot | [[Boussole et menus]], [[Lobby]] |
| 27/11 | **2012 Bedwars à la place de 2017 Bedwars** : 4v4 sur sa propre map, lit caché dans la première minute, argile, pas d'améliorations, avec ou sans diamants et émeraudes | [[Bed Wars]], [[Fun Practice]] |
| 27/11 | Demandes d'Abel : la cage du Block edit en blocs barrière ; un nouvel entraînement Hypixel Bow Practice sur sa map BowPractice ; un niveau RBW dans Block in sur sa map « Block In Practice RBW » (PR #55, #56, #57) | [[Entraînements]] |
| 27/11 | Bow Practice : « 2 touche » (2 touches en l'air après la flèche qui fait tomber le zombie) ; niveaux : « Teinture oui comme les autres niveaux » | [[Entraînements]] |
| 27/11 | Block in RBW : « Niveau » (un niveau de Block in, pas un mode à part) | [[Entraînements]] |
| 27/11 | Block edit en barrière partout (entraînement, Block edit 1v1, Red Rover Block Edit) : « Oui » | [[Duels]], [[Red Rover et 3 Players]] |
| 27/11 | Block Method : « Oui je suis d'accord » : d'abord 10 techniques, **Block Pattern Practice** dans Practice, **Block Method 1v1** dans Duels | [[Entraînements]], [[RagoBot]] |

## Abandonné
- 07/11 : ping simulé au corps à corps (PR #8 fermée) : injouable en jeu.
- 22/11 : PNJ des modérateurs jncl (« directement ») et nativ (« woryes ») au lobby, retirés à la demande de Sofiene ; le rang Mod en jaune clair reste.
- 22/11 : boussole « comme sur Minemen » (catégories en haut, modes en dessous) : remplacée par la boussole d'avant.
- 22/11 : Combo bot retiré des menus (code et records gardés).
- 22/11 : Find Your Sensitivity retiré des menus (code gardé).
- 23/11 : KB displace (1v1 et contre un bot), Pearl practice, Fireball practice, titre KB Displace retirés des menus (code et records gardés).
- 23/11 : Private game qui envoie la party au lobby d'attente d'Archway dès le clic : remplacée le 25/11 (la map d'abord, au lobby).
- 26/11 : CI sous Java 25 (PR #10 fermée sans fusion) : pas utile pour l'instant ; à refaire si l'hébergeur de production tourne sous Java 25. (La PR #48, fermée le même jour, n'abandonne rien : son contenu est dans la #47.)
- 27/11 : Bridge fight FFA (`bridge-fight`) retiré des Arenas et du `config.yml` du lobby (code et records gardés, le mode reste fermé) : liste d'Abel ; les Bridge fight 1v1 et 2v2 restent.
- 27/11 : 2017 Bedwars (l'ancien Fast Bedwars) retiré des menus : remplacé par 2012 Bedwars (demande d'Abel) ; gardé caché seulement pour les bots de la CI.
- 27/11 : la cage de verre du Block edit, remplacée par des blocs barrière (demande d'Abel).
