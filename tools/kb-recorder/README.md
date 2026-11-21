# kb-recorder : enregistrer le knockback reçu

Un mod client Forge 1.8.9 qui enregistre ce que le client reçoit et envoie pendant un combat. Il
sert à mesurer le knockback d'un serveur, Hypixel en premier, pour construire un profil à partir de
mesures et non de valeurs devinées (voir le [document de la formule](../../docs/combat/knockback-1.8.8.md)).
Depuis la version 0.2.0, il enregistre aussi les boules de feu, les TNT et leurs explosions, pour
mesurer leur poussée et les blocs qu'elles cassent.

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
| `blocks` | à chaque explosion, juste avant que le client l'applique : son centre, sa puissance, et tous les blocs qui ne sont pas de l'air dans un cube autour du centre (`[x, y, z, id, métadonnée]`), jusqu'à 8 blocs du centre |
| `context` | serveur, titre du scoreboard (le mode de jeu sur Hypixel), connexion ouverte ou fermée |
| `error` | les dix premières erreurs d'enregistrement, s'il y en a |
| `end` | nombre de ticks, de lignes perdues et d'erreurs |

Paquets gardés, tels qu'ils passent sur le réseau (après décompression) :

- reçus : vitesse d'entité (le knockback), mouvements et téléportations d'entités, statut d'entité
  (coup reçu), métadonnées (sprint de l'adversaire), animation (bras de l'adversaire), explosion,
  apparition d'objet (seulement les TNT allumées et les boules de feu), disparition d'entités
  (seulement quand elle concerne l'une d'elles), changements de blocs, position forcée du joueur,
  vie, connexion au jeu, réapparition, keep-alive ;
- envoyés : attaque, mouvement et regard, clic droit (boule de feu lancée, TNT ou bloc posé),
  animation du bras, sprint et accroupi, keep-alive.

Les paquets qui concernent une entité ne sont gardés que pour le joueur, les joueurs à moins de
16 blocs, et les TNT et boules de feu depuis leur apparition jusqu'à leur disparition, pour qu'un
enregistrement reste petit même dans un lobby plein. Le décodage des octets est fait par l'outil
d'analyse, pas par le mod.

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

## Test de bout en bout

`run-e2e-test.sh` vérifie le mod dans un vrai client Minecraft 1.8.9, sur le serveur de test du
[test de contrôle](../kb-control/README.md) (PandaSpigot 1.8.8 avec le moteur de combat et son
journal des coups) :

1. le client Forge de développement (le même lancement que `./gradlew runClient`) se connecte au
   serveur au démarrage, et le mod commence à enregistrer à la première connexion ;
2. un bot attaquant (`tools/kb-control/bots/recorder-attacker.mjs`) frappe le client 24 fois, avec
   et sans sprint : d'abord avec le knockback du serveur, puis avec le profil de test `control-alt`
   appliqué par le moteur ; il fait ensuite apparaître une TNT allumée puis une boule de feu qui
   tombe sur le sol, à 20 blocs des joueurs ;
3. le client est arrêté (signal SIGTERM), ce qui ferme le fichier comme quand on quitte le jeu ;
4. `RecorderCheck` (`tools/kb-control`) compare l'enregistrement au journal du moteur. Pour chaque
   coup, il faut exactement un paquet de vitesse pour le joueur, identique à celui que le serveur
   a envoyé, l'animation de coup reçu (statut d'entité 2) juste avant ce paquet, et dans le dernier
   tick avant le coup l'attaquant avec le sprint que le serveur a utilisé. Le fichier doit aussi
   commencer par sa ligne de session, finir par sa ligne de fin sans ligne perdue ni erreur, et
   avoir un tick enregistré par tick client, sans trou. Pour chaque explosion, `ExplosionCheck` lit
   l'enregistrement avec [kb-analyzer](../kb-analyzer/README.md) : il faut l'apparition et la
   disparition de la TNT ou de la boule de feu, le paquet d'explosion avec la puissance du serveur,
   la ligne `blocks` lue avant l'explosion (chaque bloc détruit y est encore), et pour la TNT les
   changements de blocs du serveur qui mettent de l'air à leur place.

Le clic droit n'est pas couvert par ce test : personne ne joue le client.

En CI, le job « KB recorder end-to-end test » le lance sous un écran virtuel (Xvfb) ; le rapport
apparaît dans le résumé du job et l'enregistrement dans l'artefact `kb-recorder-e2e-run`. Sur
Linux, avec Java 8 et 17, Node.js 20+ et Xvfb :

```bash
./gradlew :rago-combat-engine:assemble :kb-control:installDist
(cd tools/kb-control/bots && npm ci)
KB_ACCEPT_EULA=true xvfb-run -a tools/kb-recorder/run-e2e-test.sh
```

Le test fait tourner le code du mod dans le client de développement, pas le jar remappé que l'on
installe : les deux viennent des mêmes sources, et le build vérifie que le remappage réussit.

## Limites

- Le mod enregistre ce que voit le client : la position des autres joueurs est celle que le serveur
  lui a envoyée, avec le retard du ping.
- Le ping vient du tab, tel que le serveur le calcule ; il n'est mis à jour que de temps en temps.
- Les blocs autour d'une explosion ne sont lus que si le serveur envoie un paquet d'explosion. Un
  serveur qui casserait les blocs autrement ne laisserait que ses changements de blocs (vers l'air),
  sans le type des blocs cassés : les repères (`/kbrec mark`) disent alors sur quoi on a tiré.
