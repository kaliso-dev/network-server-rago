---
type: pilotage
tags: [état, pilotage]
maj: 2026-11-28
---
# État actuel (28/11/2026)

Voir aussi [[Prochaines étapes]], [[Plan de sortie publique]], [[Questions ouvertes]], [[Journal]]. Retour à [[Projet]].

## En une phrase
Rago Practice n'est **pas encore ouvert au public**. Tout le travail sur le jeu depuis le 21/11 est dans la **PR #47, ouverte, pas fusionnée** dans `main` (191 commits au 28/11) ; le 27/11 et le 28/11, cinq petites PR y ont été fusionnées (#54 : liste d'Abel du 27/11 pour le lobby et les menus, et 2012 Bedwars ; #55 : Block edit en barrière ; #56 : Hypixel Bow Practice ; #57 : niveau RBW du Block in), puis, le 28/11, la #58 (Block Pattern Practice, les 10 premières techniques de Block Method), la #60 (README) et la #61 (**Block Method 1v1**, Bed Fight en entraînement contre un RagoBot moins fort) ; sa dernière version (`3316b1f`) est **installée sur le [[Serveur de test]]** (PC d'Abel) depuis le **28/11 vers 16h30**. **Abel n'a encore rien testé en jeu** de ces versions. En attente d'Abel : les 4 « Pattern » solo de la vidéo (trop rapides et floues pour être recopiées bloc par bloc). Le code du jeu de `main` s'est arrêté au 14/11 (PR #46) ; depuis, `main` n'a reçu que ce vault (PR #49) et le hook Rago Hub (PR #50 à #52).

## Tableau
| Sujet | État | Statut |
|---|---|---|
| Production | aucune : pas d'hébergeur, pas de proxy, pas de base de données, pas d'adresse publique ; domaine, Discord, site et hébergeur **mis de côté jusqu'à ce que le serveur soit fini** (Abel, 26/11) ([[Hébergement et production]]) | CONFIRMÉ (code, suivi 25/11) |
| Branche `main` | `97d674f` du 26/11 ; code du jeu au 14/11 (PR #46) ; ensuite seulement le vault et `CLAUDE.md` (PR #49, 25/11) et le hook Rago Hub dans `.claude/` (PR #50, #51, #52, 26/11) | CONFIRMÉ (git, 28/11) |
| PR #47 | branche `claude/project-thread-wi9w0x`, **191 commits d'avance** sur `main`, 7 de retard (le vault et le hook, aucun fichier en commun), sans conflit ; titre « Reset des arènes, spectateurs, kit Bed Fight, stats fiables, et checklist de la sortie publique » ; dernier commit `3316b1f` (fusion de la #61, Block Method 1v1) le 28/11 à 16h28 ; CI 14/14 verte sur la tête de la #61 (`f9d77be`) ; c'est l'état le plus récent du jeu | CONFIRMÉ (GitHub, 28/11) |
| PR #54 à #57 | fusionnées dans la branche de la #47 (pas dans `main`) : #54 le 27/11 (`1bf902d`), #55, #56, #57 le 28/11 au matin (`fefff6e`, `843c0d6`, `03beb92`) ; CI 14/14 sur chacune ([[Pull requests]]) | CONFIRMÉ (GitHub, 28/11) |
| PR #58 | Block Pattern Practice (10 premières techniques de Block Method), correction de l'ordre d'enregistrement des modes de RagoPractice et un bot qui la joue dans le test des arènes ; **fusionnée le 28/11 à 11h28** dans la branche de la #47 (`d050af8`, CI 14/14 sur sa tête `69c78ac`) et **installée vers 11h29** ([[Entraînements]]) | CONFIRMÉ (GitHub, 28/11) |
| PR #61 | **Block Method 1v1** (Duels) : Bed Fight en entraînement contre un RagoBot moins fort qui avance comme un joueur (pont accroupi au bord, sort d'une full box) ; chaque mort remet la map comme au début, bâton de blaze pour recommencer, carte pour changer de map (GodMethod1, GodMethod2, MapV2, MinemenBedFight) ; **fusionnée le 28/11 à 16h28** (`3316b1f`, CI 14/14 sur `f9d77be`) et **installée vers 16h30** ([[Duels]], [[RagoBot]]) | CONFIRMÉ (GitHub, 28/11) |
| PR du vault | #53 (réponses d'Abel du 26/11, vers `main`) **ouverte, la fusion attend Abel ou Sofiene** ; la PR #59 (vault du 28/11, `claude/vault-28-11`, base `claude/vault-abel-26-11`) est à fusionner après elle ([[Branches et PR]]) | CONFIRMÉ (GitHub, 28/11) |
| PR #48 et #10 | **fermées sans fusion le 26/11** (demande d'Abel, conseil du Chef) : #48 (events, teinture rouge) dépassée par #47 ; #10 (CI sous Java 25) ne change pas le jeu | CONFIRMÉ (GitHub, 26/11) |
| Accords | depuis le 26/11, **l'accord d'Abel suffit, au même titre que celui de Sofiene**, pour GitHub et pour le serveur de test ([[Règles de travail]]) | DÉCIDÉ (Sofiene, 26/11) |
| Serveur de test | PandaSpigot 1.8.8 build 226 sur le **PC d'Abel « FULL PC GAMER »** (profil Windows `rgabe`) ; dossier `C:\Users\rgabe\Downloads\rago-serveur-de-test (19)`, mondes dans `C:\Users\rgabe\Rago\mondes` ; **Java : JDK 17.0.21** (`JAVA_HOME` ; `java` seul = Java 8) ; dernière installation : **`3316b1f` le 28/11 vers 16h30** (sauvegarde `sauvegarde-avant-maj-3316b1f`), avant : `d050af8` le 28/11 vers 11h29, `03beb92` le 28/11 vers 10h33, la PR #54 le 27/11 au soir ; lobby à 92 modes au démarrage ([[Installer une version sur le serveur de test]]) | OBSERVÉ (PC d'Abel, 28/11) |
| Remise à zéro des stats | demandée le 23/11 (« Les stats seulement ») ; faite une seule fois au premier démarrage de la version installée le 24/11, après copie ([[Statistiques et classements]]) ; ensuite **plus aucune remise à zéro avant la sortie** (« on attend la release pour ça ») | DÉCIDÉ |
| Knockback | profil `hypixel-approx-v1` + 9 ticks entre deux coups, **verrouillé** (`locked: true`) entre joueurs ; mesuré sur Hypixel le 07/11 ([[Mesures Hypixel]]) | CONFIRMÉ (code) |
| Boules de feu et TNT | valeurs de BedWars1058, **jamais mesurées** ; protocole de mesure prêt (kb-recorder 0.2.0), séance de mesure à faire par Abel. Les deux plugins de fireball proposés par Abel (« BedWars1058 DirectionalFireballs », qui dépend de BedWars1058 que Rago n'utilise pas ; « Fireball Launcher », page illisible) : **avis du 28/11, ne pas les installer** (doublon ou conflit avec les boules de feu de RagoBedwars, valeurs non mesurées) ; la vraie solution reste la séance de mesure ([[Boules de feu et TNT]]) | BLOQUÉ |
| Modes | 94 modes dans le `config.yml` fourni avec le lobby (dont 10 cachés), 92 dans celui du serveur de test (sans les deux 2017 Bedwars cachés), 8 catégories ([[Catalogue des modes]]). Depuis le 27/11 : **2012 Bedwars** remplace 2017 Bedwars, **Bridge fight FFA** retiré des Arenas ; depuis le 28/11 : **Hypixel Bow Practice** (`bow-practice`), le niveau **RBW defense** du Block in, **Block Pattern Practice** (`block-pattern-practice`) et **Block Method 1v1** (`block-method-1v1`, Duels). KB displace, Pearl practice, Fireball practice, Combo bot et Find Your Sensitivity restent retirés des menus (code gardé) | CONFIRMÉ (code `3316b1f`) |
| Liste d'Abel du 27/11 | lobby et menus (boussole de 5 lignes centrée, joueurs en partie sous chaque objet, icônes en couleur, ordre d'Abel, `regard: point`, textes) et 2012 Bedwars (PR #54) ; Block edit en barrière (#55) ; Hypixel Bow Practice (#56) ; niveau RBW du Block in (#57) : **TESTÉ EN CI et installés**, pas encore joués en jeu ; Block Method : Block Pattern Practice (PR #58) et Block Method 1v1 (PR #61), TESTÉS EN CI et installés ([[Boussole et menus]], [[Bed Wars]], [[Entraînements]], [[Duels]]) | CONFIRMÉ (GitHub, 28/11) |
| Liste du 25/11 (9h19) | Private game refaite, party : 60 s pour revenir après une déconnexion, duels entre parties (2v2 faits) : **TESTÉ EN CI** et installés (25/11), pas encore joués en jeu ; duels entre parties « dans tous les modes » : **BLOQUÉ**, proposition du 25/11 à valider ([[Party, amis et messages]], [[Parties privées]]) | OBSERVÉ (suivi) |
| Propositions en attente de Sofiene | Propositions du 23/11 (RagoBot qui suit la map, RagoBot qui pose des blocs, build du lobby d'après son image, fireballs de Minemen) ; proposition du 25/11 (duels entre parties dans tous les modes, 8 questions) ; étape 8 (rejoindre une partie, anticheat, FPS benchmark : 9 questions) ([[Plan de sortie publique]]) | BLOQUÉ |
| Site officiel | Next.js dans `site/web`, état « bientôt » ; **aucun site Netlify** pour Rago Practice sur le compte (vérifié le 25/11) ; domaine pas acheté ; **mis de côté jusqu'à ce que le serveur soit fini** (Abel, 26/11) ([[Site]]) | CONFIRMÉ (Netlify) |
| Clé API Hypixel | refusée (« Invalid API key ») ; Sofiene refait une demande (22/11) ; l'[[Import Hypixel]] ne marche pas tant qu'il n'y a pas de clé | BLOQUÉ |
| CI GitHub | 9 jobs (14 avec les matrices), à chaque push sur `main` et chaque PR ; dépôt **public** donc minutes gratuites ([[CI GitHub Actions]]) | CONFIRMÉ |
| Vault | créé le 25/11 et fusionné dans `main` (PR #49) ; il n'existe pas sur la branche de la #47 : ses mises à jour passent par de petites PR vers `main` (#53 ouverte, puis la #59 du 28/11 qui part de la #53) ([[Branches et PR]]) | CONFIRMÉ (git) |

## Ce qui attend Abel (en jeu, sur le serveur de test)
1. **Tester en jeu ce qui est installé** (rien n'est encore joué, 28/11) : la **liste du 27/11** (boussole de 5 lignes, nombres sous les objets, icônes, ordre, PNJ tournés vers le centre, textes de la salle des remerciements et de RagoBot) ; **2012 Bedwars** (4v4 : cacher son lit, argile) ; le **Block edit en barrière** (entraînement, Block edit 1v1, Red Rover Block Edit) ; **Hypixel Bow Practice** (3 niveaux) ; le niveau **RBW defense** du Block in ; **Block Pattern Practice** (10 techniques, à régler en jeu) ; **Block Method 1v1** (seul dans la file de Duels : RagoBot vient, fait son pont et tue ; la mort et le bâton de blaze recommencent la manche, la carte change de map ; dire si RagoBot est trop fort ou trop faible). Et, toujours pas fait, la version du 25/11 : Private game de Bedwars Normal, déconnexion en party, duels entre parties, points « à voir en jeu » du [[Plan de sortie publique]].
2. Poser le lobby d'attente (`/bw edit <map>` puis `/bw lobbyfight`, qui écrit `salle-attente:`) sur **chaque map de Bedwars Normal** : seule ArchwayFFA en a un ([[Parties privées]]).
3. Poser les **spawns de First Rush** : pas encore faits ; seules Nebuc et Gelato en ont d'après le vault ([[Bed Wars]]).
4. Faire la **séance de mesure des boules de feu et de la TNT** avec kb-recorder 0.2.0 ([[Boules de feu et TNT]]).
5. Block Method : les 4 « Pattern » solo de la vidéo sont trop rapides et floues pour être recopiées bloc par bloc : les construire sur une map (ou les décrire) pour qu'ils soient recopiés à l'identique ; et dire en jeu ce qu'il faut changer aux formes des 10 techniques de Block Pattern Practice ([[Questions ouvertes]]).
6. Envoyer ce qu'il a fini (26/11) s'il ne l'a pas fait : la map de **Top Bed Fight 2v2** et **Attack and Defense**. (Sa liste de maps, modes et features est arrivée le 27/11.)

## Ce qui attend une décision ou un accord (Sofiene ou Abel)
1. Fusionner la PR #53 (vault) dans `main`, puis la PR #59 (vault du 28/11).
2. Les 4 « Pattern » solo de Block Method : attendent Abel ([[Questions ouvertes]]).
3. Répondre aux propositions en attente (23/11, 25/11, étape 8) et aux [[Questions ouvertes]] ; passer q122 à q126 en « traitée » sur la page des questions (compte qui y a accès).
4. Dire si la PR #47 doit être fusionnée dans `main` (rien n'est fusionné sans la demande de Sofiene ou d'Abel, [[Règles de travail]]).
5. Donner l'accord pour chaque installation sur le serveur de test ([[Installer une version sur le serveur de test]]).

## À vérifier
- Rien de nouveau : la version de Java du serveur de test est lue (JDK 17.0.21, 28/11) et les plugins de fireball ont reçu un avis (ne pas installer, 28/11).

## Mis de côté jusqu'à ce que le serveur soit fini (Abel, 26/11)
Domaine (`rago.gg` affiché en jeu ; q127 reportée, pas tranchée), invitation Discord, site, hébergeur ([[Hébergement et production]], [[Site]]).
