# RagoBedwars : les modes Bed Wars

Un plugin Bukkit 1.8.8 qui joue les modes Bed Wars du lobby (`bukkit/rago-lobby`) : pour
l'instant **Ranked Bedwars 1v1, 2v2, 3v3 et 4v4**, avec les règles d'une partie classique
d'Hypixel. Les autres modes (Private Game, First Rush, Sky Rise, FFA) viendront sur le même
moteur.

## La partie

- Chaque équipe apparaît sur son île, avec une épée en bois et une armure en cuir à sa couleur.
  Compte à rebours de 3 secondes, immobile, puis « Fight! ».
- **Générateurs** : chaque île a son générateur de fer et d'or ; les générateurs de diamant et
  d'émeraude affichent leur tier et le temps avant le prochain objet. Ils passent tier II puis III
  pendant la partie.
- **Événements** (réglables) : Diamond II à 6 min, Emerald II à 12, Diamond III à 18, Emerald III
  à 24, tous les lits détruits à 30, fin de la partie (égalité) à 45. Le scoreboard montre le
  prochain, les équipes (✔ tant que le lit tient, sinon le nombre de joueurs encore en vie), et
  les kills, final kills et lits cassés du joueur.
- **Shop** : le villageois « ITEM SHOP » de chaque île. Le Quick Buy et les sept catégories en
  haut (Blocks, Melee, Armor, Tools, Ranged, Potions, Utility), trois rangées de sept objets, aux
  prix d'Hypixel en solo et en duo. Le Quick Buy est celui que le joueur a copié d'Hypixel
  (`/hypixel import`, ou l'émeraude du livre du lobby), sinon un Quick Buy par défaut. Les objets
  achetés vont dans la case que leur donne le hotbar manager Hypixel du joueur, si elle est libre.
  L'armure achetée reste jusqu'à la fin ; la pioche et la hache montent tier par tier et perdent un
  tier à chaque mort ; les cisailles restent. TNT allumée en la posant, boules de feu lancées au
  clic droit, seau d'eau à usage unique.
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

Une map à N équipes complètes sert aux modes de N équipes, et à ceux qui en ont moins si aucune
map n'a exactement leur nombre (les premières équipes jouent). Une équipe est complète avec son
spawn, son lit, son générateur et son shop.

**La map de démo** : au premier démarrage, le plugin crée `bw-demo`, une map à lui (deux îles
rouge et bleue face à face, un milieu avec un générateur d'émeraude et deux îles de diamant), pour
jouer tout de suite. `carte-demo: false` dans le config.yml l'empêche de la créer.

## Les copies

Chaque partie se joue dans une copie de sa map (`bw_<map>_<n>`), jamais dans la map elle-même.
La première copie est faite quelques secondes après le démarrage, les suivantes quand il n'y en a
plus de libre (pendant ce temps, les joueurs gardent leur place dans la file). Après une partie, la
copie retire les blocs posés, remet les lits et les blocs que l'eau ou les blocs avaient
remplacés, et sert à la partie suivante. Les copies sont supprimées quand le serveur s'arrête.
`parties-max` limite le nombre de copies, donc de parties en même temps.

## Configuration

`plugins/RagoBedwars/config.yml` : les modes joués (leurs ids dans le config.yml du lobby),
`parties-max`, `carte-demo`, le temps de réapparition, la vitesse et le maximum de chaque
générateur (trois tiers pour le diamant et l'émeraude) et les minutes des événements.

## Construire et tester

```bash
./gradlew :rago-bedwars:build
```

Les maps (lecture et écriture de `bedwars.yml`), le catalogue du shop et la lecture des ids
Hypixel, la configuration et la map de démo ont des tests unitaires. Une partie de Ranked Bedwars
1v1 sur la map de démo est jouée par deux bots sur un vrai serveur : voir
[tools/lobby-check](../../tools/lobby-check/README.md).
