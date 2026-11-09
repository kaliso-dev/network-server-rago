# RagoPractice : les modes d'entraînement

Un plugin Bukkit 1.8.8 qui joue les modes d'entraînement du lobby (`bukkit/rago-lobby`) : les
duels, les entraînements en solo, l'arène ouverte de block placement et les scénarios classés.
Chaque mode prend son kit dans l'éditeur de kit du lobby, rangé comme le joueur l'a laissé.

## Les duels

Deux joueurs (ou deux équipes de deux), par la file ou par `/duel`. Le résultat compte dans les
stats du lobby. Quitter le serveur ou taper `/spawn` pendant le duel, c'est perdre par abandon.

- **Sumo** : sur les maps sumo du serveur (Ponsen, Fort Royale, White Crystal, Space Mine, Sumo,
  dans le dossier des mondes), tirées au hasard à chaque duel ; sans elles, une plateforme ronde.
  Des coups sans dégâts, le premier qui tombe (ou touche l'eau) donne un point ; premier à 3.
  Chaque manche commence par 3 secondes immobiles, face à face. Les maps sont copiées dans les
  zones du sumo au démarrage, quelques milliers de blocs par tick ; elles ne sont pas dans le dépôt.
- **Boxing**, comme sur Minemen : un ring fermé, Speed II, une épée sans dégâts ; le premier à
  100 coups gagne. Le scoreboard compte les coups et le combo.
- **KB displace** : un pont d'un bloc au-dessus du vide, que du knockback ; faire tomber l'autre,
  se rattraper avec sa laine. Premier à 3.
- **Bridge fight** : deux îles de Bed Wars à 27 blocs, en stuff de Ranked Bedwars (armure de fer,
  épée en pierre, laine, cisailles, pommes d'or) ; on bridge et on se bat sur le pont, vrais
  dégâts. Premier à 3.
- **Block placement 1v1 et 2v2**, comme le BlockFight de Minemen : deux petites plateformes au-dessus
  du vide, une épée en bois et des blocs sans fin ; construire, clutch, se bloquer, faire tomber.
  Premier à 5.
- **Top fight 1v1 et 2v2** : une île dont le lit a une défense de trois couches (end stone, bois,
  laine) ; on grimpe et on se bat dessus, en stuff de Ranked Bedwars. Premier à 3.
- **Attack and defense 1v1 et 2v2**, comme en Ranked Bedwars : 30 secondes aux défenseurs pour
  construire leur défense, puis 2 minutes aux attaquants pour casser le lit ; on change de côté à
  chaque manche ; le plus de lits cassés gagne, et à égalité le plus rapide.
- **Fireball fight** : chaque camp a son île et son lit sous une défense de bois et de laine, des
  boules de feu, de la TNT, de la laine et une épée. Tant que le lit tient, on revient au bout de
  3 secondes ; ensuite, une mort est définitive. Le dernier camp debout gagne.

## Les entraînements

Un joueur seul. Chaque entraînement a ses variantes (dans la hotbar, le colorant rouge baisse et le vert monte, le mode puis les réglages, le nombre sur le colorant dit où on en est ; la blaze rod recommence),
et chaque variante son record : le meilleur score du joueur et celui du serveur, gardés dans
`plugins/RagoPractice/records.yml` et affichés dans le scoreboard avec le rang du joueur.

- **Block clutch** : un coup de côté sur un pont d'un bloc ; poser sa laine pour se rattraper.
  Simple, double (un deuxième coup en l'air), knockback au hasard, long knockback. Série de clutchs.
- **Block in** : s'enfermer le plus vite possible (les quatre côtés aux pieds et à la tête, et
  au-dessus), sur le sol, sur une défense de lit ou sur un bloc au-dessus du vide. Chrono.
- **Fast defense** : couvrir son lit le plus vite possible, avec les défenses du propriétaire.
  Ranked : end stone (le dessus du lit, deux rangées de chaque côté, un bloc à chaque bout) puis
  du verre par-dessus, les rangées du bord restant nues (26 blocs). Solo : end stone sur le lit et
  deux de plus devant, puis de la laine sur le tout (32 blocs). Chrono.
- **Bridge** : 31 blocs de vide par technique (god bridge, breezily, ninja, telly, speed bridge),
  dont les règles sont vérifiées. Chrono.
- **Ladder practice** : un mur d'end stone de 12 blocs à monter avec des échelles, ou la tour de
  laine sous les pieds. Chrono.
- **Pearl practice** : une perle par essai, vers une île qui change de place, vers une île
  lointaine, ou pour revenir sur son île après un coup qui envoie dans le vide. Série.
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
  normal (Speed I), difficile (Speed II).
- **KB displace contre un bot** : le pont du KB displace, faire tomber le bot sans tomber. Série.

## L'arène de block placement

Ouverte à tous : on entre et on sort quand on veut, une grande plateforme et quatre îles au-dessus
du vide, du grès sans fin, le kit du block fight. On revient tout de suite après une chute ou une
mort, les blocs posés partent au bout de 15 secondes, les kills comptent dans les stats du mode.

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
le monde, le centre de la plateforme, la boîte copiée), la map du block in, et les temps de
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
