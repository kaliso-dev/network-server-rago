# RagoBedwars : les modes Bed Wars

Un plugin Bukkit 1.8.8 qui joue les modes Bed Wars du lobby (`bukkit/rago-lobby`) : **Ranked
Bedwars 1v1, 2v2, 3v3 et 4v4**, **RBW Sky Rise** en solo et en duo, avec les règles d'une partie
classique d'Hypixel, et **First Rush** en solo et en duo (la même partie, sur deux îles voisines).
Chaque mode a ses maps. Les autres modes (Private Game, FFA) viendront sur le même moteur.

## La partie

- Chaque équipe apparaît sur son île, avec une épée en bois et une armure en cuir à sa couleur.
  Compte à rebours de 3 secondes, immobile, puis « Fight! ». Sur une map qui a plus d'îles que la
  partie n'a d'équipes, les équipes prennent des îles éloignées (un duel sur une map à 4 se joue sur
  deux îles opposées, rouge contre vert), et les lits des îles vides sont enlevés.
- **Forges** : chaque île a sa forge de fer et d'or, à la vitesse de sa map comme sur Hypixel. Un
  « nœud » de fer donne un lingot toutes les 3 secondes ; une forge a 1 nœud sur les maps lentes,
  2 sur les moyennes, 3 sur les rapides, et un de plus sur les maps à 4 équipes ; l'or tombe toutes
  les 12 secondes ; au plus 48 fers et 16 ors par terre ; deux fois plus vite pendant les 30
  premières secondes. Toutes les maps sont rapides, sauf Lighthouse et Lotus (lentes en solo,
  moyennes en duo), comme sur Hypixel (réglable dans `forge:`). Les générateurs de diamant et
  d'émeraude affichent leur tier et le temps avant le prochain objet. Ils passent tier II puis III
  pendant la partie.
- **Événements** (réglables) : Diamond II à 6 min, Emerald II à 12, Diamond III à 18, Emerald III
  à 24, tous les lits détruits à 30, fin de la partie (égalité) à 45. Le scoreboard montre le
  prochain, les équipes (✔ tant que le lit tient, sinon le nombre de joueurs encore en vie), et
  les kills, final kills et lits cassés du joueur.
- **Shop** : le villageois « ITEM SHOP » de chaque île (son nom flotte au-dessus de lui sur deux
  lignes, « ITEM SHOP » et « RIGHT CLICK », comme sur Hypixel, et il regarde droit devant). Le Quick Buy et les sept catégories en
  haut (Blocks, Melee, Armor, Tools, Ranged, Potions, Utility), trois rangées de sept objets, aux
  prix d'Hypixel en solo et en duo. Le Quick Buy est celui que le joueur a copié d'Hypixel
  (`/hypixel import`, ou l'émeraude du livre du lobby), sinon un Quick Buy par défaut. Les objets
  achetés vont dans la case que leur donne le hotbar manager Hypixel du joueur, si elle est libre.
  L'armure achetée reste jusqu'à la fin ; la pioche et la hache montent tier par tier et perdent un
  tier à chaque mort ; les cisailles restent. TNT allumée en la posant, seau d'eau à usage unique.
- **Boules de feu** : lancées au clic droit, comme sur Hypixel (les valeurs de BedWars1058, la copie
  ouverte du Bed Wars d'Hypixel). Leur souffle pousse les joueurs à 3 blocs ou moins : loin de lui
  à l'horizontale (1,0) et vers le haut (0,65 × 1,5), ce qui fait le saut à la boule de feu ; il
  fait 1 cœur, armure ou pas, à celui qui la lance et aux autres équipes, rien à ses coéquipiers.
  Réglable dans `boule-de-feu:`.
- **Morts** : un coup qui tuerait, une chute dans le vide ou une chute mortelle. Le kill revient au
  dernier joueur qui a frappé dans les 10 secondes, avec le fer, l'or, les diamants et les
  émeraudes du mort. Tant que son lit tient, le joueur revient au bout de 5 secondes (il regarde la
  partie en spectateur en attendant) ; sans lit, c'est un **final kill** et il est éliminé.
- **Lits** : on ne casse pas le sien. Casser celui d'une autre équipe l'annonce à tous, et ses
  joueurs ne reviendront plus.
- **Blocs** : on ne casse que les blocs posés pendant la partie (et les lits). Pas de bloc posé à
  moins de 3 blocs d'un spawn, à moins de 2 d'un générateur, ni au-dessus de la hauteur maximale.
  Les explosions ne cassent que les blocs posés, jamais le verre « blast-proof », l'obsidienne ni
  les lits. Pas de tir ami.
- **Fin** : la dernière équipe en vie gagne. Le résultat va dans les stats du lobby (victoires,
  défaites, ELO du mode classé, kills), puis tout le monde revient au lobby. Quitter le serveur ou
  taper `/spawn` pendant la partie, c'est l'abandonner (une défaite).

## First Rush

`first-rush` (1v1) et `first-rush-duo` (2v2) sont de vraies parties, comme RBW Sky Rise, mais les
deux équipes jouent sur des **îles voisines** au lieu d'îles opposées (`iles-voisines:` du
config.yml) : le premier rush décide souvent de la partie. Le joueur choisit sa map parmi les dix
maps duo.

Pas encore dans cette version : les améliorations d'équipe (le villageois « TEAM UPGRADES »
répond qu'elles arrivent), les pièges, le Bedbug, le Dream Defender, le Bridge Egg, le Magic
Milk et la Pop-up Tower (affichés « Coming soon » dans le shop), les coffres d'équipe et
l'ender chest (fermés), le chat d'équipe et les pseudos à la couleur de l'équipe.

## Les maps

Une map est un monde du dossier des mondes du serveur, avec un fichier `bedwars.yml` dans son
dossier, qui garde ses points. Les points se placent en jeu avec `/bw` (opérateurs, permission
`rago.bedwars.admin`), en se tenant à l'endroit voulu. Dans l'ordre, pour une map à deux équipes
red et blue :

1. `/monde import NomDeLaMap` si la map vient d'ailleurs (le dossier du monde copié dans
   `mondes`), puis `/bw edit NomDeLaMap` pour y aller (en créatif).
2. Sur l'île rouge : `/bw spawn red` là où les joueurs apparaissent, en regardant vers où ils
   doivent regarder ; `/bw bed red` en visant le lit ; `/bw generator red` sur le générateur de fer
   et d'or ; `/bw shop red` là où se tient le villageois du shop, tourné vers où il doit regarder ;
   `/bw upgrades red` pour le villageois des améliorations (facultatif).
3. La même chose pour `blue` (et `green`, `yellow`, `aqua`, `white`, `pink`, `gray` pour une map à
   plus d'équipes).
4. `/bw diamond` sur chaque générateur de diamant, `/bw emerald` sur chaque générateur
   d'émeraude.
5. `/bw spectators` là d'où les morts regardent la partie (sinon au-dessus du milieu des spawns).
6. Si besoin, `/bw height 100` (hauteur maximale de construction ; sinon 25 blocs au-dessus du
   spawn le plus haut) et `/bw void 40` (la hauteur où l'on meurt ; sinon 30 blocs sous le spawn le
   plus bas).
7. `/bw info` dit ce qui manque ; `/bw enable` ouvre la map aux parties.

`/bw maps` liste les maps, `/bw remove red|diamond|emerald|diamonds|emeralds` enlève une équipe ou
un générateur (le plus proche, à 5 blocs), `/bw disable` ferme la map. Chaque changement est
enregistré tout de suite ; les parties en cours gardent la map comme elle était.

**Les maps de chaque mode** : dans `modes:` du config.yml, chaque mode a sa liste de maps (les noms
de leurs dossiers), ou `toutes`. Par défaut : les Ranked sur Archway, Invasion, Lectus, Extinction,
Paladin et Rise ; RBW Sky Rise sur SkyRise ; First Rush sur Acropolis, Ambush, Aqil, Cascade,
Crogorm, Frosted, Gelato, Lighthouse, Lotus et Nebuc. Une map absente du serveur est sautée (la
console le dit au démarrage). Quand un mode a deux maps prêtes ou plus, le joueur **choisit sa
map** en cliquant le mode dans le sélecteur (ou `/queue first-rush Gelato`), ou prend « Random
map » pour attendre moins ; il ne joue qu'avec ceux qui ont choisi la même map ou n'importe
laquelle. Les modes classés (Ranked) tirent toujours la map au hasard.

Une map sert à un mode si elle a au moins autant d'équipes complètes que le mode a d'équipes ;
celles qui en ont exactement autant passent d'abord (les équipes prennent des îles éloignées, ou
voisines en First Rush). Une équipe est complète avec son spawn, son lit, son générateur et son shop.

Pour une map d'Hypixel, [tools/bedwars-maps](../../tools/bedwars-maps/README.md) trouve tous ces
points à partir des lits, des coffres et des îles, et écrit le `bedwars.yml` : il ne reste qu'à
les vérifier en jeu.

**La map de démo** : au premier démarrage, le plugin crée `bw-demo`, une map à lui (deux îles
rouge et bleue face à face, un milieu avec un générateur d'émeraude et deux îles de diamant), pour
jouer tout de suite. Elle ne sert que tant qu'aucune des maps du mode ne peut l'accueillir.
`carte-demo: false` dans le config.yml l'empêche de la créer.

## Les copies

Chaque partie se joue dans une copie de sa map (`bw_<map>_<n>`), jamais dans la map elle-même.
La première copie est faite quelques secondes après le démarrage, les suivantes quand il n'y en a
plus de libre (pendant ce temps, les joueurs gardent leur place dans la file). Après une partie, la
copie retire les blocs posés, remet les lits et les blocs que l'eau ou les blocs avaient
remplacés, et sert à la partie suivante. Les copies sont supprimées quand le serveur s'arrête (ou,
s'il a été coupé net, au démarrage suivant).
`parties-max` limite le nombre de copies, donc de parties en même temps. À la limite, une partie
qui attend une map sans copie libre fait de la place : une copie libre d'une autre map est
retirée.

## Configuration

`plugins/RagoBedwars/config.yml` : les modes joués (leurs ids dans le config.yml du lobby) avec
leurs maps, les modes joués sur des îles voisines, `parties-max`, `carte-demo`, le temps de
réapparition, la forge (le fer d'un nœud, l'or, les maximums, la vitesse de chaque map), la vitesse
et le maximum des générateurs de diamant et d'émeraude (trois tiers), les minutes des événements,
la vitesse, le recul et les dégâts des boules de feu. L'ancienne forme de `modes:` (une
simple liste) se lit encore : chaque mode sur toutes les maps.

## Construire et tester

```bash
./gradlew :rago-bedwars:build
```

Les maps (lecture et écriture de `bedwars.yml`, les maps de chaque mode, les îles d'un duel et
de First Rush), la forge de chaque map, le recul des boules de feu, le catalogue du shop et la
lecture des ids Hypixel, la configuration et la map de démo ont des tests unitaires. Une partie de Ranked Bedwars 1v1 et un
First Rush sur la map de démo sont joués par deux bots sur un vrai serveur : voir
[tools/lobby-check](../../tools/lobby-check/README.md).
