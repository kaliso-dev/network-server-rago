---
type: pilotage
tags: [état, pilotage]
maj: 2026-11-26
---
# État actuel (26/11/2026)

Voir aussi [[Prochaines étapes]], [[Plan de sortie publique]], [[Questions ouvertes]], [[Journal]]. Retour à [[Projet]].

## En une phrase
Rago Practice n'est **pas encore ouvert au public**. Tout le travail sur le jeu depuis le 21/11 (172 commits, 311 fichiers, plan de sortie publique de Sofiene) est dans la **PR #47, ouverte, pas fusionnée** dans `main` ; la dernière version de cette PR (commit `2de1bca`, plus deux commits de suivi jusqu'à `ce8edc5`) est **installée sur le [[Serveur de test]]** (PC d'Abel) depuis le 25/11. Le 26/11, Abel dit que toutes les maps et tous les modes dont il avait parlé sont finis ; il lui reste trois choses à faire en jeu (plus bas). Le code du jeu de `main` s'est arrêté au 14/11 (PR #46) ; depuis, `main` n'a reçu que ce vault (PR #49) et le hook Rago Hub (PR #50 à #52).

## Tableau
| Sujet | État | Statut |
|---|---|---|
| Production | aucune : pas d'hébergeur, pas de proxy, pas de base de données, pas d'adresse publique ; domaine, Discord, site et hébergeur **mis de côté jusqu'à ce que le serveur soit fini** (Abel, 26/11) ([[Hébergement et production]]) | CONFIRMÉ (code, suivi 25/11) |
| Branche `main` | `97d674f` du 26/11 ; code du jeu au 14/11 (PR #46) ; ensuite seulement le vault et `CLAUDE.md` (PR #49, 25/11) et le hook Rago Hub dans `.claude/` (PR #50, #51, #52, 26/11) | CONFIRMÉ (git, 26/11) |
| PR #47 | branche `claude/project-thread-wi9w0x`, **172 commits d'avance** sur `main`, 7 de retard (le vault et le hook, aucun fichier en commun), « mergeable clean » ; titre « Reset des arènes, spectateurs, kit Bed Fight, stats fiables, et checklist de la sortie publique » ; dernier commit `ce8edc5` le 25/11 à 13:51 UTC, **CI 14/14 verte** ; c'est l'état le plus récent du jeu | CONFIRMÉ (GitHub, 26/11) |
| PR #48 | « Events: red dye to leave or cancel… » (21/11), 1 commit, **dépassée** : la même demande est codée dans #47 ; **à fermer sans fusion** (demande d'Abel du 26/11, conseil du Chef), en attente du « GO » de Sofiene | DÉCIDÉ, pas encore fait |
| PR #10 | « CI : vérifier aussi le serveur sous Java 25 » (11/11), 1 commit, ne change pas le jeu ; **à fermer sans fusion** (même décision du 26/11), en attente du « GO » de Sofiene | DÉCIDÉ, pas encore fait |
| Serveur de test | PandaSpigot 1.8.8 build 226 sur le **PC d'Abel « FULL PC GAMER »** (profil Windows `rgabe`) ; dernière version dans `C:\Users\rgabe\Downloads\rago-serveur-de-test (19)`, mondes dans `C:\Users\rgabe\Rago\mondes` ; Java : **À VÉRIFIER** (17 d'après le suivi, 25 d'après la PR #10) ; dernière installation : **`2de1bca` le 25/11** (après le « Tu peux installer oui » de 14h47), sauvegarde `sauvegarde-avant-maj-2de1bca\plugins\` | CONFIRMÉ (Abel, 26/11) pour le PC et le dossier ; OBSERVÉ (suivi) pour le reste |
| Remise à zéro des stats | demandée le 23/11 (« Les stats seulement ») ; faite une seule fois au premier démarrage de la version installée le 24/11, après copie ([[Statistiques et classements]]) ; ensuite **plus aucune remise à zéro avant la sortie** (« on attend la release pour ça ») | DÉCIDÉ |
| Knockback | profil `hypixel-approx-v1` + 9 ticks entre deux coups, **verrouillé** (`locked: true`) entre joueurs ; mesuré sur Hypixel le 07/11 ([[Mesures Hypixel]]) | CONFIRMÉ (code) |
| Boules de feu et TNT | valeurs de BedWars1058, **jamais mesurées** ; protocole de mesure prêt (kb-recorder 0.2.0), **séance de mesure à faire par Abel** ([[Boules de feu et TNT]]) | BLOQUÉ |
| Modes | 90 modes dans le `config.yml` du lobby (dont 9 cachés), 8 catégories ([[Catalogue des modes]]) ; KB displace, Pearl practice, Fireball practice, Combo bot et Find Your Sensitivity retirés des menus (code gardé). Abel (26/11) : toutes les maps et tous les modes dont il avait parlé sont finis ; il va envoyer une **liste de maps, modes et features à ajouter** | CONFIRMÉ (code PR #47) |
| Dernière liste (25/11, 9h19) | Private game refaite (la map d'abord au lobby, puis lobby d'attente au-dessus de la map), party : 60 s pour revenir après une déconnexion, duels entre parties (2v2 faits) : **TESTÉ EN CI** (14 jobs sur 14) et installés ; duels entre parties « dans tous les modes » : **BLOQUÉ**, proposition du 25/11 à valider ([[Party, amis et messages]], [[Parties privées]]) | OBSERVÉ (suivi) |
| Propositions en attente de Sofiene | Propositions du 23/11 (RagoBot qui suit la map, RagoBot qui pose des blocs, build du lobby d'après son image, fireballs de Minemen) ; proposition du 25/11 (duels entre parties dans tous les modes, 8 questions) ; étape 8 (rejoindre une partie, anticheat, FPS benchmark : 9 questions) ([[Plan de sortie publique]]) | BLOQUÉ |
| Site officiel | Next.js dans `site/web`, état « bientôt » ; **aucun site Netlify** pour Rago Practice sur le compte (vérifié le 25/11) ; domaine pas acheté ; **mis de côté jusqu'à ce que le serveur soit fini** (Abel, 26/11) ([[Site]]) | CONFIRMÉ (Netlify) |
| Clé API Hypixel | refusée (« Invalid API key ») ; Sofiene refait une demande (22/11) ; l'[[Import Hypixel]] ne marche pas tant qu'il n'y a pas de clé | BLOQUÉ |
| CI GitHub | 9 jobs (14 avec les matrices), à chaque push sur `main` et chaque PR ; dépôt **public** donc minutes gratuites ([[CI GitHub Actions]]) | CONFIRMÉ |
| Vault | créé le 25/11 et fusionné dans `main` (PR #49) ; fils de travail lancés depuis Rago Hub depuis le 26/11 ; il n'existe pas sur la branche de la #47 ([[Branches et PR]]) | CONFIRMÉ (git) |

## Ce qui attend Abel (en jeu, sur le serveur de test)
1. Poser le lobby d'attente (`/bw edit <map>` puis `/bw lobbyfight`, qui écrit `salle-attente:`) sur **chaque map de Bedwars Normal** : seule ArchwayFFA en a un ([[Parties privées]]).
2. Poser les **spawns de First Rush** : pas encore faits ; seules Nebuc et Gelato en ont d'après le vault ([[Bed Wars]]).
3. Faire la **séance de mesure des boules de feu et de la TNT** avec kb-recorder 0.2.0 ([[Boules de feu et TNT]]).
4. Envoyer sa liste de maps, modes et features à ajouter (annoncée le 26/11).

## Ce qui attend Sofiene (décisions et accords)
1. Donner son « GO » pour fermer les PR #48 et #10 sans fusion (décidé le 26/11) et dire où pousser la mise à jour du vault ([[Questions ouvertes]]).
2. Répondre aux propositions en attente (23/11, 25/11, étape 8) et aux [[Questions ouvertes]].
3. Dire si la PR #47 doit être fusionnée dans `main` (rien n'est fusionné sans sa demande, [[Règles de travail]]).
4. Donner l'accord pour chaque installation sur le serveur de test ([[Installer une version sur le serveur de test]]).

## À vérifier
- L'essai en jeu de la version installée le 25/11 (Private game de Bedwars Normal, déconnexion en party, duels entre parties, points « à voir en jeu » du [[Plan de sortie publique]]) n'est pas dans la liste d'Abel du 26/11 : fait ou encore à faire ? (q129 préparée)
- Top Bed Fight 2v2 (la map à envoyer) et Attack and Defense (« je t'expliquerai demain », 23/11) : Abel dit le 26/11 que toutes les maps et tous les modes dont il avait parlé sont finis ; ces deux points sont-ils encore attendus ? (q129 préparée)
- Java du serveur de test : 17 ou 25 ? (q128 préparée)

## Mis de côté jusqu'à ce que le serveur soit fini (Abel, 26/11)
Domaine (`rago.gg` affiché en jeu ; q127 reportée, pas tranchée), invitation Discord, site, hébergeur ([[Hébergement et production]], [[Site]]).
