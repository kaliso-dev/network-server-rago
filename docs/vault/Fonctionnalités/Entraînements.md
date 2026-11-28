---
type: fonctionnalité
tags: [practice, entraînements, ragopractice]
maj: 2026-11-28
code: bukkit/rago-practice (Training et ses sous-classes)
---
# Entraînements

Les entraînements solo de la catégorie **Practice** (`entrainement`), joués par **RagoPractice**. Chaque entraînement est une suite d'essais sur une arène reconstruite à chaque essai (`Training`, 1 244 lignes), avec un record personnel et un record du serveur par variante ([[Records des entraînements]]), un menu **Settings** dans la barre (variante, réglages ; gardé par joueur dans `plugins/RagoPractice/reglages.yml`, `TrainingPrefs`), des objets **Trainings** (changer d'entraînement) et **Leave**. Une **poudre verte** passe au niveau suivant, une **poudre rouge** au niveau d'avant ; un **bâton de blaze** relance là où une chute ne relance pas toute seule ; aucun coup n'est donné pendant qu'un menu est ouvert (PR #28, #29). Le Block clutch est le mode « Start here » et le premier de la catégorie : depuis le 27/11, les entraînements suivent **l'ordre d'Abel** (celui du [[Catalogue des modes]]). Détail complet : `bukkit/rago-practice/README.md`, « Les entraînements ». Retour à [[Catalogue des modes]].

| Mode (id) | Ce qu'on travaille | Points notables |
|---|---|---|
| Block clutch (`clutch`) | au bord d'une petite île de Bed Wars (île de la map BlockClutchNew du propriétaire), un coup jette dans le vide : poser sa laine pour se sauver | knockback, moment du coup et distance réglables dans la barre ; le premier coup après une chute un peu plus tard (PR #29) ; top 3 commun et tableau des plus grands clutchs sur chaque île ; distances de clutch de la semaine |
| Block clutch : top fight (`clutch-top-fight`) | le même clutch sur une plateforme de top fight à la limite de hauteur | aucun bloc au-dessus |
| Block in (`block-in`) | tomber sur la défense d'un lit, s'enfermer, creuser jusqu'au lit, chrono par couche | **4 niveaux** changés aux teintures : 3 couches côté bois, 3 couches côté laine, 2 couches, et depuis le 28/11 **« RBW defense »** (PR #57, demande d'Abel du 27/11 : un niveau, pas un mode à part) : sur la map d'Abel « Block In Practice RBW » (section `block-in.rbw`), le lit rouge et sa défense de partie RBW (end stone, verre rouge) lus sur la map autour du lit et remis à chaque essai, l'île copiée une fois 64 blocs à l'est de celle du Block in ; même anneau de verre 10 blocs au-dessus du lit, mêmes départs tournés vers ce lit, même stuff, même ennemi ; sans ce monde, le niveau RBW se joue sur l'île du Block in (IMPLÉMENTÉ, `BlockInRbwTest`, installé le 28/11, pas encore joué en jeu). Depuis le 21/11 un **ennemi** sur un côté casse pour entrer et ne frappe que par le trou (`BlockInEnemy`) ; records remis à zéro le 21/11 (après sauvegarde) ; TESTÉ EN JEU le 21/11 (avant le niveau RBW) |
| Fast defense (`fast-defense`) | couvrir son lit le plus vite possible | défenses du propriétaire : Ranked (end stone, verre) ou duo (end stone, laine) |
| Block edit (`block-edit`) | casser un bloc du mur de laine, frapper le bot derrière, remettre le bloc avant qu'il frappe | comme la map Block Edit Practice du propriétaire (`EditStation`) ; depuis le 28/11 la **cage est en blocs barrière** (invisibles) au lieu du verre, pour toute la cage (murs, toit, bord), aussi au Block edit 1v1 et au Red Rover Block Edit (PR #55, demande d'Abel du 27/11 ; installé le 28/11, pas encore vu en jeu) |
| Extend practice (`extend-practice`) | doubles et triples extends (5+1, 6+0, 6+1, 7+0, 7-1, 7-2) ; « Chain » : 10 pads d'affilée | sauts du Clutch Training de Flarefin |
| Block extend (`block-extend`) | prendre un coup sur le pont, se sauver à la laine, comme dans un vrai bed fight | map BlockClutchExtend ; Gold, Diamond, Emerald : d'où vient le coup |
| Timed bridge (`bridge-chrono`) | bridger 16, 32 ou 64 blocs au chrono | droit ou diagonale, à plat ou en montée, et depuis le 22/11 **avec un virage** ; un record par combinaison |
| Ladder practice (`ladder-practice`) | block-ladder clutch : sauter de 4 à 24 blocs, laine et échelle avant le sol | map de lavy ; une échelle posée juste avant l'atterrissage compte |
| Fast Shop (`shop-practice`, joué par RagoBedwars) | acheter des listes d'objets au shop le plus vite possible, avec son propre quick buy Hypixel | records par liste (`FastShopRecords`, `fastshop.yml`) ; [[Import Hypixel]] |
| Hypixel Bow Practice (`bow-practice`, 28/11) | un zombie avance vers le joueur sur un pont de laine au-dessus du vide : la 1re flèche le fait tomber (elle ne compte pas), puis **2 touches en l'air** = victoire ; le zombie au bout du pont, ou dans le vide sans les 2 touches = défaite ; un nouveau zombie tout de suite, sons de victoire et de défaite | PR #56, demande d'Abel du 27/11 ; map d'Abel `BowPractice` (section `bow-practice`, classe `BowPractice`) ; **3 niveaux** changés aux teintures comme les autres entraînements : pont droit (blocs d'or de la map), pont en diagonale (diamant), pont en hauteur (émeraude) ; arc Infinity + Power IV, une flèche ; le joueur bouge sur 5×5 blocs autour de son départ ; record « wins in a row » par niveau ; sans le monde, 3 niveaux du plugin sur des blocs simples (IMPLÉMENTÉ, `BowPracticeTest`, installé le 28/11, pas encore joué en jeu) |
| Bow training (`bow-training`) | sur Archway, des bots bridgent du milieu vers sa base : les faire tomber à l'arc | touches, précision, plus long tir |
| Aim trainer (`aim-trainer`) | une cible de la taille d'un joueur qui strafe, saute ou tourne | XP par seconde sur la cible ; `/aimtop` |
| Block aim (`block-aim`, 22/11) | une face s'allume (sous les pieds au bout du pont, sur le côté du pont, sur le lit) : y poser un bloc | 5 niveaux de plus en plus rapides (3 s à 1 s, à confirmer), un record par niveau ; pas encore joué en jeu |

**Retirés des menus** (code et records gardés, hors de `/records` et du mur) : KB displace et KB displace contre un bot, Pearl practice, Fireball practice (23/11) ; Combo bot (22/11, « le combo bot on le supprime ») ; Find Your Sensitivity (22/11, 19h51).

## Commun à tous les entraînements
- XP Network des entraînements (`xp-entrainement:` de RagoPractice, `PracticeXp`) : un peu par minute, par essai, plus par réussite, plafonné.
- **Défi du jour** (`/challenge`, `defi-du-jour:`) : un entraînement pour tout le monde chaque jour (heure de Paris), le top 3 gagne de l'XP ([[Progression et récompenses]]).
- Les arènes sont des zones du monde `arenes`, refait à chaque démarrage (`rago-arenas.txt`) ; chaque bloc changé est remis à la fin d'un essai ou d'une partie (`Arena`, corrigé le 21/11 : bug 1.1).
- Spectateurs possibles (`/spectate`), jusqu'à 10 en même temps testés en CI ([[Replays et spectateurs]]).
- Rien ne s'use (`Unbreakable`).

## Block Pattern Practice (`block-pattern-practice`, depuis le 28/11)
D'après la vidéo « Block Method » d'Abel (34 techniques avec un adversaire, [[Sources]]) ; plan validé par Abel le 27/11 (« Oui je suis d'accord ») : un entraînement de Practice, **un niveau par technique**, du plus simple au plus dur, **d'abord 10 techniques**. **PR #58**, fusionnée et installée le 28/11 (TESTÉ EN CI : un bot pose le #1 puis touche RagoBot en premier, le #2 arrive seul, RagoBot court et touche quand rien n'est posé, la teinture rouge mène au #10 et son tunnel ; `pattern-bots.mjs`) ; **pas encore joué en jeu** :
- Mode `block-pattern-practice` (« Block Pattern Practice », Practice, icône laine bleu clair `"WOOL:3"`), classes `BlockPatternPractice`, `BlockPatterns`, `PatternBot` ([[Code de rago-practice]]).
- Les 10 techniques, changées aux teintures : #1 Block in front, #2 Pillar, #3 Hook, #4 T, #5 Arch, #6 Roof, #7 Door, #8 Far hook, #9 Window, #10 Tunnel. Leurs formes et le trajet du bot sont **lus dans la vidéo, à régler avec Abel en jeu**.
- « RagoBot » est ici un **bot d'entraînement** (un zombie avec la tête de qRago, comme le Combo bot), pas le vrai client [[RagoBot]] : il court vers le joueur sur le trajet de la technique avec la physique d'un joueur 1.8 (`PatternBot`) : il saute un bloc qui a de la place au-dessus, contourne un mur qu'il ne peut pas sauter, casse pour passer dans le tunnel.
- Le joueur pose les blocs de la technique (une poussière bleue les montre tant qu'ils manquent), puis **touche RagoBot en premier** : réussite, le temps va sur le tableau de la technique. RagoBot touche en premier (seulement ce qu'il voit, à 3 blocs), temps écoulé ou chute : la même technique à refaire.
- Réglages : « Next technique on success » (activé : passage automatique à la suivante) et « Show the pattern » (activé).
- Records : un tableau par technique (temps) ; la liste des entraînements à records passe à 18 ([[Records des entraînements]]).
Son pendant en duel, **Block Method 1v1** (PR #61, installé le 28/11), est une Bed Fight sans fin contre un RagoBot moins fort ([[Duels]]).

Voir aussi [[Duels]], [[Arènes ouvertes]], [[Code de rago-practice]].
