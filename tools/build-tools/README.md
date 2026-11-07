# RagoBuildTools : construire sur le serveur de test

Un plugin Bukkit 1.8.8 pour construire les maps et les lobbys sur le
[serveur de test local](../local-server/README.md), à côté de WorldEdit. Il n'est fait que pour ce
serveur de test : il n'ira jamais sur un serveur de production.

## Ce qu'il fait

- **Des mondes de build.** `/monde creer <nom> vide` crée un monde vide (de l'air partout, un bloc
  de bedrock sous le point d'apparition), `/monde creer <nom> plat` un monde plat de Minecraft.
  Une map téléchargée s'ajoute avec `/monde importer <dossier>`.
- **Le bon mode de jeu.** En entrant dans un monde de build, on passe en créatif. Dans les autres
  mondes (le monde plat du test de knockback), on repasse en survie.
- **La protection.** Dans un monde de build, seuls les constructeurs (`rago.build.construire`, les
  opérateurs par défaut) posent et cassent des blocs, vident des seaux, posent ou cassent des
  tableaux et touchent aux porte-armures. Un ami qui vient tester le knockback peut visiter sans
  rien abîmer.
- **La nature laisse les builds tranquilles.** Pas de pluie, pas de cycle jour-nuit (midi en
  permanence), pas de feu qui se propage, pas de feuilles qui disparaissent, pas de glace qui fond,
  pas d'herbe qui s'étend, pas d'explosion qui casse des blocs, pas de monstres ni d'animaux qui
  apparaissent seuls. Un briquet allume encore un feu, qui ne se propage pas.
- **Un enregistrement fréquent.** Tous les mondes sont écrits sur le disque toutes les 2 minutes
  (`sauvegarde-auto-minutes` dans `config.yml`), en plus de l'enregistrement de Minecraft toutes
  les 5 minutes. `/monde sauver` le fait tout de suite.
- **Deux commandes de confort.** `/vitesse <1-10>` règle la vitesse de vol (1 est la vitesse
  normale ; la vitesse de marche ne change jamais, pour ne pas fausser les tests de knockback) et
  `/nv` active ou enlève la vision nocturne.

## Commandes

| Commande | Effet |
| --- | --- |
| `/monde` ou `/monde liste` | les mondes, leur type et leurs joueurs, et les maps à importer |
| `/monde creer <nom> [vide\|plat]` | crée un monde de build (vide par défaut) et t'y emmène |
| `/monde importer <dossier>` | ajoute une map copiée dans le dossier des mondes et t'y emmène |
| `/monde tp <nom>` | t'emmène au point d'apparition d'un monde |
| `/monde spawn` | place le point d'apparition du monde là où tu es |
| `/monde sauver` | écrit tout de suite tous les mondes sur le disque |
| `/vitesse <1-10>` | vitesse de vol |
| `/nv` | vision nocturne |

Toutes les commandes demandent `rago.build.admin` (les opérateurs). `liste`, `creer`, `importer`
et `sauver` marchent aussi depuis la console du serveur.

Un nom de monde ne contient que des minuscules sans accent, des chiffres, `-` et `_`, 32 caractères
au plus : c'est aussi le nom de son dossier, et Windows ne distingue pas « Lobby » de « lobby ».

## Où sont les mondes

Le serveur de test range ses mondes hors de son propre dossier (`~/Rago/mondes`, voir le
[mode d'emploi](../local-server/README.md)), pour qu'une nouvelle version du serveur les retrouve.
Chaque monde de build a dans son dossier un fichier `rago-monde.yml` :

```yaml
generateur: vide   # ce que le serveur crée là où rien n'est construit : vide ou plat
mode: creatif      # le mode de jeu de ceux qui entrent : creatif ou survie
protege: true      # si true, seuls les constructeurs modifient le monde
```

Au démarrage, le plugin charge chaque dossier qui contient un `level.dat` et ce fichier. Comme
les réglages sont dans le dossier du monde, copier le dossier (ou restaurer une sauvegarde)
ramène le monde tel quel. Un monde importé a le générateur `vide` : rien ne pousse autour de la
map quand on vole au-delà de ses bords.

## Importer une map

`/monde importer` lit d'abord la version de Minecraft qui a enregistré la map (`DataVersion` dans
son `level.dat`) :

- sans `DataVersion` (1.8 ou plus ancien) : la map s'ouvre telle quelle ;
- de 1.9 à 1.12 : elle s'ouvre, mais les blocs ajoutés après 1.8 disparaissent (le plugin
  l'écrit dans la console) ;
- 1.13 ou plus récent : l'import est refusé, parce qu'un serveur 1.8 viderait ses chunks. Il faut
  une version de la map faite pour 1.8 à 1.12, ou un schematic `.schematic` (le format de
  WorldEdit 6) à coller avec WorldEdit dans un monde vide.

## Pourquoi un plugin à nous

Multiverse-Core n'a jamais eu de version stable pour 1.8 (la seule qui la cite est la bêta
2.5-b699 de 2015), et les générateurs de monde vide pour 1.8 ne sont plus maintenus. Ce dont un
constructeur a besoin tient en quelques classes testées : ce plugin évite une base abandonnée.

Le plugin écrit les mondes sur le disque depuis le thread principal, comme Minecraft le fait pour
son propre enregistrement. C'est acceptable pour un serveur de build, pas pour un serveur de jeu :
c'est une raison de plus pour qu'il ne quitte pas le serveur de test.

## Construire et tester

```bash
./gradlew :rago-build-tools:build
```

Le jar est dans `tools/build-tools/build/libs/`. Les tests unitaires couvrent les noms de monde,
la lecture et l'écriture de `rago-monde.yml`, la lecture de `DataVersion` dans un `level.dat`
(avec chaque type de tag NBT), le générateur vide et `/vitesse`. La CI démarre deux fois le serveur
de test avec le plugin. Sur Linux, elle crée un monde vide depuis la console, puis vérifie qu'il
est retrouvé au lancement suivant et qu'il est dans la sauvegarde faite à ce lancement. Sur
Windows, où elle ne peut pas taper dans la console, elle vérifie que le plugin et WorldEdit
s'activent et que le deuxième lancement sauvegarde les mondes.
