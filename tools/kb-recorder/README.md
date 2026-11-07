# kb-recorder : enregistrer le knockback reçu

Un mod client Forge 1.8.9 qui enregistre ce que le client reçoit et envoie pendant un combat. Il
sert à mesurer le knockback d'un serveur, Hypixel en premier, pour construire un profil à partir de
mesures et non de valeurs devinées (voir le [document de la formule](../../docs/combat/knockback-1.8.8.md)).

Le mod ne fait que lire : il ne modifie aucun paquet, n'automatise rien et n'affiche rien en jeu en
dehors de ses messages de commande. Rien n'est enregistré tant qu'on n'a pas tapé `/kbrec start`.

## Installer

1. Installer Forge 1.8.9 (11.15.1.2318) pour le compte qui va recevoir les coups.
2. Copier `rago-kb-recorder-<version>.jar` (artefact `rago-kb-recorder` de la CI) dans le dossier
   `mods` de Minecraft.

Seul le joueur qui reçoit les coups a besoin du mod : le paquet de vitesse qu'il reçoit est la
mesure. Si l'attaquant l'a aussi, ses attaques envoyées sont enregistrées, ce qui permet de mesurer
le délai entre le coup et le knockback.

Hypixel n'autorise pas officiellement ce mod. Il est passif, mais mieux vaut l'utiliser sur un
deuxième compte.

## Commandes

| Commande | Effet |
| --- | --- |
| `/kbrec start [nom]` | démarre un enregistrement ; le nom se retrouve dans le nom du fichier |
| `/kbrec mark [texte]` | ajoute un repère, par exemple « premier hit sprint sol face » |
| `/kbrec stop` | arrête l'enregistrement et ferme le fichier |
| `/kbrec status` | dit si un enregistrement est en cours |

Les fichiers vont dans le dossier `rago-kb-recorder` de Minecraft (sur Windows,
`%appdata%\.minecraft\rago-kb-recorder`), un fichier `.jsonl` par enregistrement. Quitter le jeu
ferme proprement l'enregistrement en cours.

## Ce qui est enregistré

Une ligne JSON par événement. Chaque ligne a `type`, `ms` (heure, pour aligner deux clients),
`nano` (horloge monotone, pour les intervalles) et `tick` (ticks client depuis le début).

| `type` | Contenu |
| --- | --- |
| `session` | version du format, du mod, joueur, serveur, nom donné à `/kbrec start` |
| `tick` | à la fin de chaque tick client : position, mouvement, sol, regard, sprint, touches, vie, ping du tab, et les joueurs à moins de 16 blocs (position, regard, sprint, sol) |
| `packet` | un paquet reçu (`in`) ou envoyé (`out`), avec son id et son nom du protocole 47 et ses octets bruts en base64 |
| `mark` | un repère de `/kbrec mark` |
| `context` | serveur, titre du scoreboard (le mode de jeu sur Hypixel), connexion ouverte ou fermée |
| `error` | les dix premières erreurs d'enregistrement, s'il y en a |
| `end` | nombre de ticks, de lignes perdues et d'erreurs |

Paquets gardés, tels qu'ils passent sur le réseau (après décompression) :

- reçus : vitesse d'entité (le knockback), mouvements et téléportations d'entités, statut d'entité
  (coup reçu), métadonnées (sprint de l'adversaire), animation (bras de l'adversaire), explosion,
  position forcée du joueur, vie, connexion au jeu, réapparition, keep-alive ;
- envoyés : attaque, mouvement et regard, animation du bras, sprint et accroupi, keep-alive.

Les paquets qui concernent une entité ne sont gardés que pour le joueur et les joueurs à moins de
16 blocs, pour qu'un enregistrement reste petit même dans un lobby plein. Le décodage des octets est
fait par l'outil d'analyse, pas par le mod.

L'écriture se fait sur un thread à part : le jeu et le réseau ne font que mettre des lignes dans une
file. Si la file déborde (200 000 lignes en attente), les lignes en trop sont comptées dans `end`
au lieu de ralentir le jeu.

## Construire

Le mod a son propre build Gradle, parce que l'outil Forge 1.8.9 (le fork d'Architectury Loom
d'Essential) demande Gradle 8 et un JDK 8, alors que les modules serveur utilisent Gradle 9.

```bash
cd tools/kb-recorder
./gradlew build
```

Il faut un JDK 17 pour lancer Gradle et un JDK 8 pour compiler (Gradle le télécharge s'il n'en
trouve pas). Le jar à installer est dans `build/libs/`. La CI le construit à chaque push et pull
request (job « KB recorder mod »).

## Limites

- Le mod enregistre ce que voit le client : la position des autres joueurs est celle que le serveur
  lui a envoyée, avec le retard du ping.
- Le ping vient du tab, tel que le serveur le calcule ; il n'est mis à jour que de temps en temps.
