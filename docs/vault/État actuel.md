---
type: pilotage
tags: [état, pilotage]
maj: 2026-11-25
---
# État actuel (25/11/2026)

Voir aussi [[Prochaines étapes]], [[Plan de sortie publique]], [[Questions ouvertes]], [[Journal]]. Retour à [[Projet]].

## En une phrase
Rago Practice n'est **pas encore ouvert au public**. Tout le travail depuis le 21/11 (172 commits, 311 fichiers, plan de sortie publique de Sofiene) est dans la **PR #47, ouverte, pas fusionnée** dans `main` ; la dernière version de cette PR (commit `2de1bca`, plus deux commits de suivi jusqu'à `ce8edc5`) est **installée sur le [[Serveur de test]]** depuis le 25/11, **pas encore jouée en jeu**. `main` s'est arrêtée au 14/11 (PR #46).

## Tableau
| Sujet | État | Statut |
|---|---|---|
| Production | aucune : pas d'hébergeur, pas de proxy, pas de base de données, pas d'adresse publique ([[Hébergement et production]]) | CONFIRMÉ (code, suivi 25/11) |
| Branche `main` | `e09d9dc` du 14/11 (PR #46 « Invis practice et Bed Break Simulator en 1v1 à 1v4 ») ; 40 PR fusionnées entre le 07/11 et le 14/11 (#1, #2, #4, #9, #11 à #46) | CONFIRMÉ (git) |
| PR #47 | branche `claude/project-thread-wi9w0x`, **172 commits d'avance** sur `main`, 0 de retard, « mergeable clean » ; titre « Reset des arènes, spectateurs, kit Bed Fight, stats fiables, et checklist de la sortie publique » ; dernier commit `ce8edc5` le 25/11 à 13:51 UTC ; c'est l'état le plus récent du jeu | CONFIRMÉ (GitHub) |
| PR #48 | « Events: red dye to leave or cancel… » (21/11), 1 commit, **dépassée** : la même demande est codée dans #47 (le suivi le dit : « la PR #48 partait de l'ancienne version des events ») ; à fermer ou non : À DÉCIDER ([[Questions ouvertes]]) | CONFIRMÉ (GitHub) |
| PR #10 | « CI : vérifier aussi le serveur sous Java 25 » (11/11), ouverte, 1 commit, 83 de retard sur `main` ; sort de l'objet ? À DÉCIDER | CONFIRMÉ (GitHub) |
| Serveur de test | PandaSpigot 1.8.8 build 226 sur un PC Windows (mondes dans `C:\Users\rgabe\Rago\mondes`, serveur `rago-serveur-de-test (19)`), Java 17 au démarrage ; dernière installation : **`2de1bca` le 25/11** (après le « Tu peux installer oui » de 14h47), serveur arrêté, sauvegarde `sauvegarde-avant-maj-2de1bca\plugins\` ; à qui est ce PC : À VÉRIFIER | OBSERVÉ (suivi du 25/11) |
| Remise à zéro des stats | demandée le 23/11 (« Les stats seulement ») ; faite une seule fois au premier démarrage de la version installée le 24/11, après copie ([[Statistiques et classements]]) ; ensuite **plus aucune remise à zéro avant la sortie** (« on attend la release pour ça ») | DÉCIDÉ |
| Knockback | profil `hypixel-approx-v1` + 9 ticks entre deux coups, **verrouillé** (`locked: true`) entre joueurs ; mesuré sur Hypixel le 07/11 ([[Mesures Hypixel]]) | CONFIRMÉ (code) |
| Boules de feu et TNT | valeurs de BedWars1058, **jamais mesurées** ; protocole de mesure prêt (kb-recorder 0.2.0), séance sur Hypixel ou Minemen à faire par Sofiene ([[Boules de feu et TNT]]) | BLOQUÉ |
| Modes | 90 modes dans le `config.yml` du lobby (dont 9 cachés), 8 catégories ([[Catalogue des modes]]) ; KB displace, Pearl practice, Fireball practice, Combo bot et Find Your Sensitivity retirés des menus (code gardé) | CONFIRMÉ (code PR #47) |
| Dernière liste de Sofiene (25/11, 9h19) | Private game refaite (la map d'abord au lobby, puis lobby d'attente au-dessus de la map), party : 60 s pour revenir après une déconnexion, duels entre parties (2v2 faits) : **TESTÉ EN CI** (14 jobs sur 14) et installés ; duels entre parties « dans tous les modes » : **BLOQUÉ**, proposition du 25/11 à valider ([[Party, amis et messages]], [[Parties privées]]) | OBSERVÉ (suivi) |
| Propositions en attente de Sofiene | Propositions du 23/11 (RagoBot qui suit la map, RagoBot qui pose des blocs, build du lobby d'après son image, fireballs de Minemen) ; proposition du 25/11 (duels entre parties dans tous les modes, 8 questions) ; étape 8 (rejoindre une partie, anticheat, FPS benchmark : 9 questions) ([[Plan de sortie publique]]) | BLOQUÉ |
| Site officiel | Next.js dans `site/web`, état « bientôt » ; **aucun site Netlify** pour Rago Practice sur le compte (vérifié le 25/11 : seuls `rago-optimiser`, `peeclay`, `kaliso-dev`, `cesium-horlogerie-archive`) ; domaine pas acheté ([[Site]]) | CONFIRMÉ (Netlify) |
| Clé API Hypixel | refusée (« Invalid API key ») ; Sofiene refait une demande (22/11) ; l'[[Import Hypixel]] ne marche pas tant qu'il n'y a pas de clé | BLOQUÉ |
| CI GitHub | 9 jobs (14 avec les matrices), à chaque push sur `main` et chaque PR ; dépôt **public** donc minutes gratuites ([[CI GitHub Actions]]) | CONFIRMÉ |
| Vault | créé le 25/11 (cette note) à la demande de Sofiene, pour passer sur un nouveau compte Claude Team | DÉCIDÉ |

## Ce qui attend Sofiene
1. Jouer en jeu la version installée le 25/11 (rien n'a été joué depuis l'installation de `dfae3b7` le 23/11) : Private game de Bedwars Normal, déconnexion en party, duels entre parties, et les points « à voir en jeu » du [[Plan de sortie publique]].
2. Poser le lobby d'attente (`salle-attente:` avec `/bw lobbyfight` dans `/bw edit <map>`) sur **chaque map de Bedwars Normal** : seule ArchwayFFA en a un ([[Parties privées]]).
3. Répondre aux propositions en attente (23/11, 25/11, étape 8) et aux [[Questions ouvertes]].
4. Faire la séance de mesure des boules de feu et des TNT ([[Boules de feu et TNT]]).
5. Envoyer la map de Top Bed Fight 2v2, expliquer sa vision d'Attack and Defense (« je t'expliquerai demain », 23/11).
6. Acheter le domaine, choisir l'hébergeur (budget 30 €/mois) ([[Hébergement et production]]).
7. Dire si la PR #47 doit être fusionnée dans `main` (rien n'est fusionné sans sa demande, [[Règles de travail]]).
