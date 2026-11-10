# RagoPractice : les modes d'entraînement

Un plugin Bukkit 1.8.8 qui joue les modes d'entraînement du lobby (`bukkit/rago-lobby`) : les
duels, les entraînements en solo, les arènes ouvertes (block placement, bridge fight) et les
scénarios classés.
Chaque mode prend son kit dans l'éditeur de kit du lobby, rangé comme le joueur l'a laissé.

## Les duels

Deux joueurs (ou deux équipes de deux), par la file ou par `/duel`. Le résultat compte dans les
stats du lobby. Quitter le serveur ou taper `/spawn` pendant le duel, c'est perdre par abandon.

- **Sumo** : sur les maps sumo du serveur (Ponsen, Fort Royale, White Crystal, Space Mine, Sumo,
  dans le dossier des mondes), tirées au hasard à chaque duel ; sans elles, une plateforme ronde.
  Des coups sans dégâts, le premier qui tombe (ou touche l'eau) donne un point ; premier à 3.
  Chaque manche commence par 3 secondes immobiles, face à face. Les maps sont copiées dans les
  zones du sumo au démarrage, quelques milliers de blocs par tick ; elles ne sont pas dans le dépôt.
- **Boxing**, comme sur Minemen : sur les arènes Minemen de 2022 du propriétaire (Cobble, Shy Guy,
  Redstone, Cave, Fishtank, Goku, Lollipop, Pokemon : les mondes `Minemen …` du dossier des
  mondes, `boxing.cartes`), tirées au hasard, chacun sur son bloc de diamant ; sans elles, un ring
  fermé. Speed II, une épée sans dégâts ; le premier à 100 coups gagne. Le scoreboard compte les
  coups et le combo. `/speed` enlève la Speed au joueur qui la tape, ou la lui rend (tout de
  suite en partie, sinon pour ses prochains boxings, jusqu'au redémarrage). Avec un nombre, c'est
  toujours la vitesse de vol des builders (`/vitesse` de RagoBuildTools). Les arènes sont copiées
  comme les maps du sumo et ne sont pas dans le dépôt.
- **KB displace**, comme un vrai top fight de Bed Fight : un pont d'un bloc de large, et le
  défenseur en haut de la défense, un bloc plus bas que le pont, le pont devant lui et un bloc
  derrière, rien sur les côtés. L'attaquant court sur le pont et, dans le tick du coup, tourne la
  tête d'un côté : le knockback du sprint envoie le défenseur sur le côté, dans le vide. Seul un
  coup donné de face compte (depuis le pont, jamais d'un côté ni de derrière). Chaque coup est un
  essai : défenseur tombé, un point pour l'attaquant ; les rôles changent à chaque coup (ou après
  15 secondes sans coup). Le défenseur reste à sa place, coups sans dégâts, pas de blocs. Premier
  à 3.
- **Block placement 1v1 et 2v2**, sur les maps de block placement du propriétaire
  (`block-fight.cartes` dans `config.yml` : chaque camp part de son bloc d'or, en 2v2 côte à
  côte), copiées comme celles du sumo : épée en bois Sharpness I, pioche et hache Efficiency I,
  cisailles, deux stacks de laine, 12 end stone, 16 échelles, armure en fer Protection IV.
  Construire, clutch, casser leurs blocs, faire tomber. Premier à 5. Sans ces mondes, deux
  petites plateformes au-dessus du vide.
- **Top fight 1v1 et 2v2** : une île dont le lit a une défense de trois couches (end stone, bois,
  laine) ; on grimpe et on se bat dessus, en stuff de Ranked Bedwars. Premier à 3.
- **Attack and defense 1v1 et 2v2**, comme un rush en solo sur Hypixel : le défenseur est chez
  lui, à côté de son lit sans défense, l'attaquant déjà sur un pont, à trois blocs de vide de
  l'île. Le défenseur doit couvrir son lit tout de suite et tenir 60 secondes (il revient au bout
  de 3 secondes s'il meurt) ; l'attaquant doit le casser, et s'il meurt, son rush est raté.
  L'attaquant a le stuff des premières minutes (épée en pierre, 48 laine, cisailles, pioche en
  bois), le défenseur une épée en bois et 32 laine. On change de côté à chaque manche ; le plus
  de lits cassés gagne, et à égalité le plus rapide. L'île est l'île rouge de Lotus
  (`attaque-defense` dans la config : le monde, la zone, le lit, le départ du défenseur et le
  pont) ; sans ce monde, une île simple.
- **Fireball fight** : chaque camp a son île et son lit sous une défense de bois et de laine. Le
  stuff des maps du propriétaire : épée en pierre, pioche et hache Efficiency I, 6 boules de feu,
  un stack de laine, 8 end stone, 2 TNT, cisailles, 8 échelles, armure en cuir. Tant que le lit
  tient, on revient au bout de 3 secondes ; ensuite, une mort est définitive. Le dernier camp
  debout gagne. Sur une map de `fireball.cartes` (`config.yml`), chaque camp part de son départ et
  les lits sont ceux de la map (un lit est au camp dont le départ est le plus proche).
- **Block edit 1v1** : la station du block edit avec une pièce de chaque côté du mur de laine.
  Casser un bloc du mur, frapper l'autre par le trou, et remettre un bloc dans le mur avant qu'il
  ne frappe : un point. S'il frappe en retour d'abord, le point passe de son côté. Coups sans
  dégâts, que le knockback. Premier à 5 (`points-pour-gagner.block-edit`).

## Les entraînements

Un joueur seul. Chaque entraînement a ses variantes (dans la hotbar, le colorant rouge baisse et le vert monte, le mode puis les réglages, le nombre sur le colorant dit où on en est ; la blaze rod recommence),
et chaque variante son record : le meilleur score du joueur et celui du serveur, gardés dans
`plugins/RagoPractice/records.yml` et affichés dans le scoreboard avec le rang du joueur.

- **Block clutch** : le joueur est au bord d'une petite île de Bed Wars, son lit et sa défense
  (end stone, laine) à côté ; un coup le jette dans le vide, il pose sa laine pour se rattraper.
  Les îles des joueurs sont côte à côte (28 blocs) : chacun voit les autres clutcher. La hotbar
  règle le knockback (1 à 5), le nombre de coups à la suite (1 à 3), le KB stick (bien plus loin)
  et un côté au hasard. Chaque réglage a son propre record. Série de clutchs.
- **Block clutch : top fight** : le même clutch sur une plateforme de top fight en laine bleue et
  rouge, à la limite de construction : aucun bloc au-dessus de la plateforme, il faut se
  rattraper à son niveau ou plus bas.
- **Block in** : s'enfermer le plus vite possible (les quatre côtés aux pieds et à la tête, et
  au-dessus), sur le sol, sur une défense de lit ou sur un bloc au-dessus du vide. Chrono.
- **Fast defense** : couvrir son lit le plus vite possible, avec les défenses du propriétaire.
  Ranked : end stone (le dessus du lit, deux rangées de chaque côté, un bloc à chaque bout) puis
  du verre par-dessus, les rangées du bord restant nues (26 blocs). Solo : end stone sur le lit et
  deux de plus devant, puis de la laine sur le tout (32 blocs). Chrono.
- **Bridge** : 31 blocs de vide par technique (god bridge, breezily, ninja, telly, speed bridge),
  dont les règles sont vérifiées. Chrono.
- **Block extend**, comme dans un vrai bed fight, sur la map BlockClutchExtend du propriétaire
  (`block-extend` dans `config.yml`) : le joueur part d'une place exacte d'un pont, prend un coup
  et se sauve avec sa laine bleue. Trois niveaux, le coup de plus en plus fort : Or, un gros coup
  de face qui l'envoie vers les blocs d'or du pont plus bas, où il clutch ; Diamant, un coup
  normal par la droite, il se retourne et extend jusqu'aux blocs de diamant ; Émeraude, un gros
  coup de face depuis le haut, il atterrit et clutch sur les blocs d'émeraude plus bas. Les blocs
  d'or, de diamant et d'émeraude de la map sont de la laine dans le jeu. Chaque niveau donne sa
  place de départ (F3), le bloc où se sauver, la force du coup et d'où il vient (`coup` : devant,
  droite, gauche, derriere, ou `vers` un point). Un réglage ajoute des échelles. Série.
- **Ladder practice** : un mur d'end stone de 12 blocs à monter avec des échelles, ou la tour de
  laine sous les pieds. Chrono.
- **Pearl practice** : sur un morceau de Sky Rise (l'île rouge, l'île aux diamants en face, l'île
  aux émeraudes du milieu), copié du monde `SkyRise` du dossier des mondes (`pearl` dans
  `config.yml`), sinon des îles simples aux mêmes places. Une perle et un stack de laine par
  essai : vers l'île aux diamants, vers le milieu, ou pour revenir sur son île après un coup qui
  envoie dans le vide. Une perle trop courte se rattrape avec des blocs (pearl block clutch), mais
  aucun bloc ne se pose sous la limite basse (`limite-basse`, y 55), comme sur Hypixel. L'essai
  compte quand le joueur tient debout sur l'île visée. Série.
- **Fireball practice** : le saut à la boule de feu sur une falaise de 4 blocs, au-dessus de 10
  blocs de vide, ou renvoyer les boules d'un canon. Série.
- **Aim trainer** : une cible de la taille d'un joueur qui strafe, saute ou tourne autour ; 30
  secondes, les coups qui touchent et la précision. Chaque seconde où le viseur reste sur la
  cible (12 ticks sur 20) ou la touche donne de l'XP, avec un son qui monte avec la série (et un
  carillon toutes les 5 secondes d'affilée) ; une seconde à côté ne donne rien. Le niveau de la
  barre d'XP montre l'XP de l'essai, la barre le temps qui reste. À la fin, le joueur reçoit
  cette XP en XP Network, plus 1 pour 10 coups (`aim-trainer` dans `config.yml`), et voit le top 5
  de la variante. `/aimtop [strafe|jump|circle]` montre le top de partout.
- **Combo bot** : un bot qui fonce et rend les coups, sans dégâts ; le plus long combo. Facile,
  normal (Speed I), difficile (Speed II). Sur les arènes du boxing : le joueur sur le premier
  bloc de diamant, le bot 8 blocs devant lui.
- **KB displace contre un bot** : le même pont, le bot à la place du défenseur. Après chaque coup
  on revient tout de suite au départ, pour reprendre de l'élan, jusqu'à ce que le bot tombe ; seul
  un coup de face compte. Le score est le temps, et le scoreboard compte les essais.
- **Block edit**, comme sur la map Block Edit Practice du propriétaire : une cage de verre, un mur
  de laine rouge de 5 sur 3, et derrière, un bot dans une petite pièce en bois. Casser un bloc du
  mur, frapper le bot par le trou, remettre le bloc avant qu'il ne frappe. Le bot frappe comme un
  joueur, à 3 blocs, une fois qu'il a vu le trou pendant son temps de réaction ; la hotbar règle
  sa vitesse (500, 350 ou 200 ms). Chaque edit propre ajoute à la série, un coup du bot la finit ;
  un record par vitesse. La station est construite par le plugin, la map n'est pas copiée.

## L'arène de block placement

Ouverte à tous : on entre et on sort quand on veut, du grès sans fin, le kit du block fight. Elle
se joue sur l'arène du propriétaire (`block-fight-arena.cartes` dans `config.yml`, le monde
`Arena`) : l'arène verte dans ses murs de barrières, copiée une fois. On arrive n'importe où sur
l'herbe ; le bloc de slime du cube noir, dans le coin, projette celui qui monte dessus 3 blocs en
haut et 2 devant lui (là où il regarde), pour sortir du trou. Le bloc d'or du milieu ne sert que de
repère : il n'est pas copié. Sans ce monde, une grande plateforme et quatre îles au-dessus du vide.
On revient tout de suite après une chute ou une mort, les blocs posés partent au bout de
15 secondes, les kills comptent dans les stats du mode.

## Le bridge fight

Un FFA ouvert à tous, comme l'arène de block placement, en stuff de Ranked Bedwars (armure de fer,
épée en pierre, pommes d'or), vrais dégâts, sans poser de bloc. Il se joue sur la map du
propriétaire (`bridge-fight.cartes` dans `config.yml`, le monde `Bridge Fight Plateform`) : son
étage du bas, copié une fois, avec ses cinq plateformes (deux bleu et jaune, deux noir et vert, le
H rouge et jaune) et les trois portails entre elles. Les plateformes sont ses sols de laine ou
d'argile à la hauteur du `centre` ; un portail, c'est la cabine autour d'une lanterne aquatique
posée au sol (7 sur 7, 9 de haut). Un clic sur un portail (clic droit en le visant, ou clic
gauche sur un de ses blocs) emmène sur la plateforme de l'autre côté, celle qu'on regarde à
travers lui, au bout le plus proche du portail ; le portail du milieu, qui n'a rien en face côté
nord, ouvre le menu des plateformes. Pas de portail pendant un combat. Les barrières de la map ne
sont pas copiées : celui qui tombe tombe dans le vide. Sans ce monde, quatre plateformes de laine
côte à côte au-dessus du vide (bleu et jaune, rouge et blanc, orange et noir, vert et vert clair) :
un pont relie chaque bout de plateforme à un petit carré, et une passerelle le long de chaque bout
relie les carrés, donc on passe d'une plateforme à l'autre à pied.

La plateforme d'un joueur suit l'endroit où il est (sauf dans une cabine, entre deux plateformes) ;
la boussole emmène sur n'importe laquelle (pas pendant un combat). En arrivant, et après une mort ou
une chute, on apparaît dans la cabine la plus proche de sa plateforme (la petite île blanche en
quartz), sur sa lanterne au milieu du losange de quartz, face à la plateforme ; l'armure prend la
couleur du bout de la plateforme côté cabine. Sans la map, on revient à un bout de la plateforme. Trois secondes sans dégâts au retour, sauf si on
frappe le premier. Les kills comptent dans les stats du mode.

`bridge-fight-1v1` est le vrai 1v1, à part : une seule plateforme (rouge à l'ouest, bleu à l'est)
avec ses carrés et ses ponts, le même stuff avec Protection II sur l'armure
(`bridge-fight.protection-1v1`, 0 pour aucune), aucun bloc. Une mort ou une chute donne le point à
l'autre ; le premier à `points-pour-gagner.bridge-fight` (5) gagne.

## Les scénarios classés

`scenarios-ranked` : les moments qui décident une partie de Ranked Bedwars, rejoués encore et
encore, chacun avec son record.

- **Bed defense 1v2** : seul sur son île, avec une épée en fer, de la laine et l'armure de Ranked
  Bedwars, contre deux rushers qui traversent le pont pour casser le lit (end stone sous de la
  laine). Près du joueur, ils se battent ; sinon, ils creusent la défense depuis l'endroit où ils
  sont, couche par couche (la laine vite, l'end stone plus lentement, puis le lit : environ deux
  secondes), et un coup arrête leur creusage. On peut reboucher le trou avec sa laine. Les tuer ou
  les faire tomber dans le vide avant que le lit tombe : le temps est le score. Lit cassé, mort,
  chute ou 90 secondes sans en finir : l'essai est perdu.
- **Final clutch, 2 hearts** : le dernier combat, à 2 cœurs, sans lit et sans régénération,
  contre un adversaire en pleine vie (aussi rapide qu'un joueur qui marche) sur une île au-dessus
  du vide ; deux coups et c'est fini. Chaque combat gagné, à l'épée ou par le vide, ajoute un à la
  série.

## Les arènes

Les parties se jouent dans un monde vide (`arenes`, créé s'il n'existe pas). Chaque mode a sa
rangée, chaque partie sa zone, à `espacement` blocs (200) de la suivante : deux parties ne se
voient jamais. Une zone est construite la première fois qu'elle sert, puis réutilisée ; chaque
manche la remet comme elle était construite. Au-delà de `arenes-max` parties à la fois, les
joueurs gardent leur place dans la file. Un nouveau mode prend la rangée après les autres, pour
que les zones déjà construites ne bougent pas.

## Configuration

`plugins/RagoPractice/config.yml` : le monde des arènes, l'espacement, le nombre maximal de
parties, les points pour gagner de chaque duel, le rayon du sumo et ses maps (`sumo.cartes` :
le monde, le centre de la plateforme, la boîte copiée), la map du block in, et l'île et le temps de
l'attack and defense.

## Construire et tester

```bash
./gradlew :rago-practice:build
```

Le score des duels, les records, les formes des arènes (les couches d'une défense, le souffle des
boules de feu), les entraînements, les scénarios classés (où et dans quel ordre les rushers
creusent) et la vérification de la configuration ont des tests unitaires. Un duel sumo complet
est joué par deux bots sur un vrai serveur : voir
[tools/lobby-check](../../tools/lobby-check/README.md).
