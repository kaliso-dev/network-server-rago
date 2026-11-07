# kb-analyzer : comparer le knockback de deux serveurs

Un outil en ligne de commande qui lit les enregistrements de [kb-recorder](../kb-recorder/README.md)
et compare le knockback et le rythme des coups de plusieurs combats, par exemple le même 1v1 joué
sur Hypixel puis sur notre serveur. C'est lui qui a produit les chiffres de
[mesures-hypixel.md](../../docs/combat/mesures-hypixel.md).

Il tourne sur une machine de développement, jamais sur le réseau.

## Ce qu'il faut enregistrer

Un combat, c'est deux fichiers : **les deux joueurs** enregistrent en même temps avec le mod
(`/kbrec start` avant le combat, `/kbrec stop` après). L'enregistrement de la victime donne les
paquets de vitesse ; seul celui de l'attaquant dit avec quel sprint et quel regard le serveur a
calculé le coup. Avec un seul fichier, l'outil ne sait pas séparer les coups avec et sans sprint.

Le ping n'a pas besoin d'être le même sur les deux serveurs : il décale les paquets dans le temps
mais ne change pas leurs valeurs, et l'outil mesure le délai de chaque joueur pour le retirer.

## Lancer l'outil

```bash
./gradlew :kb-analyzer:installDist
tools/kb-analyzer/build/install/kb-analyzer/bin/kb-analyzer \
  --fight hypixel moi-hypixel.jsonl pote-hypixel.jsonl \
  --fight rago moi-rago.jsonl pote-rago.jsonl \
  --report rapport.md
```

`--fight <nom> <fichier> <fichier>` se répète, un par combat ; l'ordre des deux fichiers ne compte
pas. Le rapport (en Markdown) s'affiche et va aussi dans le fichier de `--report` s'il est donné.
Avec plusieurs combats, il commence par un tableau qui les compare.

Pour le serveur, le rapport ne donne que le domaine (`hypixel.net`), sans le sous-domaine tapé par
le joueur.

## Ce que donne le rapport

Pour chaque combat :

- **Les joueurs** : durée, ticks par seconde, paquets de vitesse reçus, combien sont venus avec
  l'animation de coup reçu, le délai entre une attaque et l'animation (le ping du joueur, en
  pratique), et combien de coups ont été retrouvés dans l'enregistrement de l'attaquant.
- **Vertical** : les valeurs verticales les plus fréquentes des paquets (8 000 unités = 1 bloc par
  tick), selon que la victime était au sol ou en l'air et que l'attaquant sprintait ou non. La
  différence au sol entre sprint et sans sprint est le « pas de sprint » : 560 sur Hypixel, 800 en
  vanilla.
- **Horizontal** : la longueur la plus fréquente sans sprint, et la poussée en plus avec sprint,
  calculée dans la direction du regard de l'attaquant.
- **Temps entre deux coups** : les intervalles entre deux animations de coup reçu d'un même joueur,
  vus par les deux joueurs, par tranche de 50 ms centrée sur un nombre entier de ticks.
- **Attaques qui ont compté**, pour chaque attaquant : selon le temps depuis sa dernière attaque
  qui a compté, combien de ses attaques ont touché. C'est ce qui montre le délai minimum entre deux
  coups du serveur (9 ticks sur Hypixel, 10 en vanilla).

## Comment les deux enregistrements sont reliés

1. **Un coup**, côté victime, c'est un paquet de vitesse pour le joueur avec l'animation de coup
   reçu (statut d'entité 2) à moins de 100 ms. Les autres paquets de vitesse (un saut lancé
   par un plugin, par exemple) sont comptés mais pas analysés.
2. **Les horloges** des deux ordinateurs sont alignées sur les animations de coup reçu de la
   victime, que les deux clients voient : l'écart le plus fréquent, puis la médiane des écarts
   proches.
3. **L'attaque qui a causé le coup** : le délai entre une attaque et l'animation est mesuré sur les
   attaques isolées (seule attaque dans les 700 ms avant l'animation), puis chaque animation est
   donnée à l'attaque envoyée à ce délai près (± 60 ms). Les autres attaques n'ont pas compté.
4. **Le sprint utilisé par le serveur** : les paquets de sprint de l'attaquant sont rejoués dans
   l'ordre, avec la règle vanilla qu'un coup avec sprint qui compte arrête le sprint côté serveur.
5. **Le regard** de l'attaquant est celui de son dernier paquet de regard avant l'attaque, ce qui
   permet de calculer la poussée en plus du sprint, qui suit le regard.

## Tests

```bash
./gradlew :kb-analyzer:test
```

Les tests rejouent un combat inventé (`SyntheticFight`) dont on connaît chaque valeur : deux
horloges décalées de 5 secondes, des pings différents, un coup sur deux avec sprint et trois clics
en trop après chaque coup, que l'outil doit reconnaître comme n'ayant pas compté.

## Limites

- L'outil ne voit que ce que voient les clients : il déduit le sprint et les attaques qui ont
  compté, il ne lit pas l'état du serveur. Les coups qu'il ne sait pas relier à une attaque sont
  rangés à part (« sprint unknown »).
- Le regard est celui du dernier paquet envoyé avant l'attaque ; avec un gros ping, le serveur peut
  en avoir reçu un autre entre-temps. C'est une des raisons pour lesquelles une partie des coups
  avec sprint ne tombe pas pile sur la valeur la plus fréquente.
- Les enchantements Knockback, les cannes à pêche, les projectiles et les explosions ne sont pas
  analysés. Un coup de projectile arrive aussi avec l'animation, mais sans attaque au corps à corps
  pour le causer : il tombe dans « sprint unknown ».
- Le décodage des paquets est aussi écrit dans `RecorderCheck` (kb-control), pour le test de bout
  en bout du mod. Les deux pourraient partager ce code plus tard.
