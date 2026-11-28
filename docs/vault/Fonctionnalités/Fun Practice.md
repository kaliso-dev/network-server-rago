---
type: fonctionnalité
tags: [fun, fireball, spleef, parkour, ctf, 2012-bedwars]
maj: 2026-11-28
code: bukkit/rago-practice (FireballKnockback, SnowballSpleef, ParkourDuel, ParkourCourse), bukkit/rago-bedwars (CaptureTheFlagGame, CaptureFlags, CtfMaps, Bedwars2012Game, FastBedwarsGame)
---
# Fun Practice

La catégorie **Fun Practice** (`fun`) : des modes pour s'amuser, plan 6.1 et 6.2. Son PNJ a son propre point `/lobby point funpractice` depuis le 23/11 (il était sur le point `prive`, repris par le PNJ Ranked) et n'apparaît pas tant que ce point n'est pas posé. Détail : `bukkit/rago-practice/README.md` (« FUN PRACTICE ») et `bukkit/rago-bedwars/README.md` (« Capture the Flag » ; ce README ne parle pas encore de 2012 Bedwars : voir le `config.yml` de RagoBedwars et `Bedwars2012Game`). Retour à [[Catalogue des modes]].

**Toutes les valeurs ci-dessous sont des valeurs de départ à régler avec Sofiene en jeu** (le code le dit, les README aussi). Aucun de ces modes n'a de dégâts, sauf CTF et 2012 Bedwars qui sont de vraies parties.

| Mode (id) | Plugin | Règles |
|---|---|---|
| **Fireball Fight** (`fireball-knockback`, 3 joueurs) | RagoPractice | chacun pour soi (rouge, bleu, vert), party de 3 ou file ; plateforme ronde de quartz au-dessus du vide (`rayon` 20 depuis le 22/11, « agrandi beaucoup », à confirmer) ; fireballs illimitées (une par demi-seconde), recul 1,5× horizontal et 1,2× vertical par rapport au Bed Wars ; **double saut** toutes les 3 s (`souffle-secondes`, barre d'XP) ; laine sans fin, blocs partis au bout de 3 s ; aucune frappe ; le dernier sur la plateforme gagne la manche ; maps du propriétaire possibles dans `fireball-knockback.cartes` |
| **Snowball Spleef** (`snowball-spleef`, 1v1) | RagoPractice | 2 sols de neige (`ecart-etages` 9 blocs, `rayon` 18, à confirmer) + un 3e étage plus étroit 5 blocs sous le 2e (`etage-etroit-rayon` 9, à confirmer) ; pelle diamant Efficacité V, 1 boule par bloc cassé ; **une boule casse toujours le bloc de neige touché** (23/11, le serveur suit son chemin bloc par bloc) ; une boule qui touche un joueur le pousse sans dégât ; on ne casse pas l'étage au-dessus de soi ; tomber sous le dernier étage = manche perdue |
| **Parkour Duel** (`parkour-duel`, 1v1) | RagoPractice | course sur un parcours construit par le plugin (`ParkourCourse`), deux pistes côte à côte (10 blocs) ; 5 sections (quartz, verre bleu qui monte, zigzag de prismarine, lanternes aquatiques, ligne d'or) et 4 checkpoints ; une chute ramène au dernier ; sauts vérifiés avec prismarine-physics (3 blocs de vide au plus) |
| **CTF The Bridge** (`ctf-bridge-1v1`, `ctf-bridge-2v2`) | RagoBedwars | map **CtfBridge** construite par le plugin : pont d'argile d'1 bloc × 41, construction de 4 blocs sous à 4 au-dessus ; bases en quartz incassables ; kit du The Bridge d'Hypixel (cuir sans casque, épée fer, arc, pioche diamant Eff. II, 2 stacks d'argile, 8 pommes d'or, une flèche qui revient en 3,5 s) ; retour immédiat ; `ctf.minutes-bridge` 15 |
| **CTF Castle** (`ctf-castle-2v2`, `ctf-castle-4v4`) | RagoBedwars | map **CtfCastle** construite par le plugin (151 blocs de long, deux châteaux 31×31, mid avec émeraude et 2 diamants) ; vraie partie de Bed Wars sans lit (forge, shop, améliorations, pièges, générateurs) ; retour après `reapparition-secondes` ; `ctf.minutes-chateau` 20 |
| **2012 Bedwars** (`bedwars-2012`, `bedwars-2012-classique`, 4v4, depuis le 27/11) | RagoBedwars | un rush de 2012 sur sa propre map `Bedwars2012` : chaque équipe **cache son lit** dans la première minute (sinon le lit de la map, derrière sa base) ; argile à la place de la laine ; shop et forges d'une partie, pas d'améliorations ; avec diamants et émeraudes (`bedwars-2012`) ou sans (`bedwars-2012-classique`, « 2012 Bedwars Classic »). Détail : [[Bed Wars]] |

## CTF : règles communes
Drapeau = bannière sur un bloc d'or au point du lit ; un ennemi qui marche dessus le prend (bannière sur la tête) ; il marque en le ramenant sur son propre drapeau s'il est chez lui. Porteur tué : le drapeau tombe, un ennemi le reprend, un allié le renvoie en le touchant, sinon il rentre seul après `ctf.retour-drapeau-secondes` (30) ; dans le vide ou porteur déconnecté : retour immédiat. Aucun bloc sur un drapeau. Premier à `ctf.captures` (3) ; sinon le plus de captures à la fin du temps, égalité possible. Sidebar : score, état de chaque drapeau (« Safe », « Taken », « Dropped 24s »).

**2017 Bedwars** (`fast-bedwars-1v1`, `fast-bedwars-2v2`, l'ancien Fast Bedwars) n'est plus dans les menus depuis le 27/11 : 2012 Bedwars a pris sa place (demande d'Abel). Il reste caché (`cache: true`) dans le `config.yml` fourni, seulement pour les bots de la CI (`fast-bots.mjs`, parties privées de `test-equipes`) ; ses réglages restent dans `fast-bedwars:` du `config.yml` de RagoBedwars.

## À vérifier / à décider
- Le dessin des maps CtfBridge et CtfCastle est **à valider** par Sofiene (modifiables en jeu ; le plugin ne les reconstruit pas tant que leur dossier existe).
- 2012 Bedwars n'a pas encore été joué en jeu (installé le 27/11) : règles et map à voir par Abel ([[État actuel]]).
- Le Fireball Fight des duels (`fireball`, avec lits) est un autre mode : voir [[Duels]].
