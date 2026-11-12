# RagoCombatEngine

Plugin Bukkit pour les serveurs 1.8.8 (PandaSpigot), compatible Java 8. Il remplace le knockback
des coups de mêlée entre joueurs par celui d'un **profil** : six constantes, calculées avec le
modèle exact de `rago-combat-model`. Le profil intégré `vanilla` donne au bit près le knockback de
Minecraft 1.8.8 ; le serveur utilise `hypixel-approx-v1`, le knockback mesuré sur Hypixel, avec
9 ticks entre deux coups, verrouillés (voir la commande `/kb`).

## Ce que fait le plugin, et ce qu'il ne touche pas

- Il ne change que les coups de mêlée **joueur contre joueur**, et seulement les coups complets :
  un coup porté pendant le délai d'invulnérabilité (9 ticks, 10 en vanilla) n'envoie pas de knockback en
  vanilla, et le plugin n'en ajoute pas.
- Explosions, flèches, cannes à pêche, mobs et vitesses posées par d'autres plugins ne sont
  jamais modifiés.
- Sur un serveur 1.8.8, un coup déclenche dans l'ordre : `EntityDamageByEntityEvent`, le calcul
  du serveur, puis `PlayerVelocityEvent` juste avant l'envoi du paquet. Le plugin lit les entrées
  au premier événement (priorité `MONITOR`, positions, orientation, sprint, enchantement, vitesse
  de la victime), calcule la vitesse avec le profil, et la pose au second (priorité `HIGHEST`),
  seulement si c'est le même tick et la même victime.
- Il pose la vitesse sur l'événement **et** sur le joueur : selon les forks 1.8, l'événement ne
  part pas avec la même vitesse de départ, et cette double écriture rend le résultat identique
  partout. Le serveur remet de lui-même l'ancienne vitesse sur sa copie de la victime après
  l'envoi, comme en vanilla.
- Le calcul se fait sur le thread principal, sans allocation coûteuse ni accès disque. Le
  journal des hits s'écrit sur un thread à part.

Le détail de la formule et ses sources : [docs/combat/knockback-1.8.8.md](../../docs/combat/knockback-1.8.8.md).

## Installation

```bash
./gradlew :rago-combat-engine:build
# puis copier combat/rago-combat-engine/build/libs/rago-combat-engine-<version>.jar dans plugins/
```

Au premier démarrage, le plugin crée `plugins/RagoCombatEngine/config.yml`. Si ce fichier est
invalide (profil inconnu, clé manquante ou en trop, valeur hors limites), le plugin l'écrit dans
la console et **se désactive** : un serveur compétitif ne doit pas tourner sur des réglages à
moitié lus.

## Configuration

```yaml
mode: override                    # override : applique le profil ; observe : calcule et journalise, sans rien changer
active-profile: hypixel-approx-v1 # profil utilisé partout, sauf dans les mondes de world-profiles
world-profiles: {}                # par monde, par exemple { arena_a: vanilla, arena_b: test-a }
hit-delay-ticks: 9                # délai entre deux coups, en ticks (Hypixel mesuré 9, vanilla 10), de 1 à 20
locked: true                      # true : /kb ne change rien en jeu, seul ce fichier règle le knockback
record-hits: false                # journal des hits (voir plus bas)
player-tracker-ticks: 1   # positions des joueurs envoyées aux autres : 1 (chaque tick) ou 2 (vanilla)
profiles: {}              # profils de test, en plus de vanilla
```

Un profil a exactement ces six clés (aucune autre n'est acceptée) :

| Clé | Rôle | Vanilla |
| --- | --- | --- |
| `friction` | la vitesse côté serveur de la victime est divisée par ce nombre (0 exclu, 100 max) | 2.0 |
| `horizontal` | poussée horizontale à l'opposé de l'attaquant | 0.4000000059604645 |
| `vertical` | poussée verticale | 0.4000000059604645 |
| `vertical-limit` | plafond de la vitesse verticale après la poussée | 0.4000000059604645 |
| `extra-horizontal` | poussée en plus par niveau (sprint = 1, Knockback = 1 par niveau), dans la direction où regarde l'attaquant ; stockée en `float` comme en vanilla | 0.5 |
| `extra-vertical` | poussée verticale en plus quand le niveau vaut au moins 1 | 0.1 |

Les composantes doivent rester entre −3.9 et 3.9, la limite du paquet. Le nom d'un profil :
minuscules, chiffres, `-` et `_`, 32 caractères au plus.

Deux profils sont intégrés et ne peuvent pas être redéfinis :

- `vanilla` : le knockback exact de Minecraft 1.8.8 ;
- `hypixel-approx-v1` : mesuré sur Hypixel le 7 novembre 2026, en Housing
  ([mesures](../../docs/combat/mesures-hypixel.md)). C'est le vanilla, sauf `extra-vertical` :
  0.07 au lieu de 0.1. Les valeurs d'Hypixel ne sont pas publiques : un profil Hypixel ne vient
  que de mesures, et une nouvelle mesure donnera un `hypixel-approx-v2`.

### Hitreg : `player-tracker-ticks`

Minecraft 1.8.8 n'envoie la position d'un joueur aux autres qu'un tick sur deux : on voit
l'adversaire jusqu'à 100 ms en retard, et on vise là où il était. Avec `1` (la valeur fournie,
comme sur les serveurs practice), la position part à chaque tick : on touche là où on voit le
joueur. Le knockback n'est pas touché. L'API 1.8.8 n'a pas ce réglage : le plugin change l'entrée
du joueur dans le tracker du serveur, à son arrivée, à chaque changement de monde et à chaque
respawn. Si le serveur n'a pas ce tracker, rien n'est changé et la console le dit une fois.
Les deux valeurs se testent sans risque : le réglage se lit au démarrage.

## Délai entre deux coups

Après un coup qui compte, un joueur est protégé pendant un cooldown ; un nouveau coup ne compte en
entier (dégâts, knockback, paquet de vitesse) qu'une fois la moitié du cooldown passée. Le
cooldown vanilla dure 20 ticks, d'où un coup toutes les 10 ticks (500 ms) au mieux. Hypixel en
compte un toutes les 9 ticks (450 ms) d'après les mesures.

`hit-delay-ticks` règle ce délai : le moteur donne à chaque joueur un cooldown de deux fois cette
valeur (`setMaximumNoDamageTicks`), à la connexion, une tick après une réapparition ou un
changement de monde (le Minecraft vanilla recrée alors le joueur avec 20 ticks ; CraftBukkit le
garde, mais le moteur ne dépend pas de ce détail), et dès que le réglage change. Le cooldown vaut pour tous les dégâts (chute, feu, flèches), comme en vanilla.

## Commande `/kb`

Permission `rago.combat.admin` (opérateurs par défaut). Les joueurs ne peuvent pas s'en servir.

Le knockback du serveur est celui d'Hypixel (`hypixel-approx-v1`, 9 ticks entre deux coups), et
il est verrouillé : avec `locked: true` (le réglage livré, et celui d'un config.yml sans la clé),
`/kb profile`, `/kb world`, `/kb mode` et `/kb hitdelay` sont refusés, même pour un opérateur.
Seul config.yml change le knockback, puis `/kb reload`. `locked: false` laisse le staff essayer
d'autres réglages jusqu'au prochain redémarrage, comme le fait le test de contrôle.

| Commande | Effet |
| --- | --- |
| `/kb` ou `/kb status` | mode, profil par défaut, profils par monde, délai entre deux coups, état du journal |
| `/kb profiles` | liste des profils et de leurs valeurs |
| `/kb profile <nom>` | change le profil par défaut |
| `/kb world <monde> <profil\|default>` | profil d'un monde, ou retour au profil par défaut |
| `/kb mode <override\|observe>` | change le mode |
| `/kb hitdelay <ticks>` | change le délai entre deux coups (vanilla 10, Hypixel mesuré 9) |
| `/kb debug` | affiche dans le chat de celui qui tape la commande chaque coup et son paquet |
| `/kb sparring [sprint\|normal\|alterne] [ticks]` | un attaquant invisible frappe celui qui tape la commande (voir plus bas) |
| `/kb sparring stop` | arrête le sparring |
| `/kb reload` | relit config.yml ; s'il est invalide, les réglages actuels restent en place |

Les changements faits par `/kb` durent jusqu'au prochain redémarrage ou `/kb reload` ; pour les
garder, il faut les écrire dans config.yml. Chaque changement est écrit dans la console avec
`[audit]` et le nom de son auteur.

## Sparring : sentir un profil sans adversaire

`/kb sparring` fait frapper le joueur qui tape la commande par un attaquant que personne ne voit,
placé à 3 blocs devant lui, dans la direction horizontale où il regarde, et tourné vers lui : le
joueur est poussé droit en arrière, comme dans un combat face à face. Il suffit de regarder dans
une direction et de se laisser pousser, au sol, en marchant ou en sautant.

- `sprint` (par défaut) : des coups sprint ; `normal` : sans sprint ; `alterne` : un coup sprint
  puis un coup normal, pour sentir la différence.
- L'intervalle va de 9 ticks (le plus rapide qu'un vrai combat permette, puisqu'un joueur ne
  reprend un coup plein que 9 ticks après le précédent sur Hypixel) à 200 ticks ; 20 par défaut
  (une seconde).
- Le sparring s'arrête avec `/kb sparring stop` ou quand le joueur se déconnecte.

Chaque coup est calculé exactement comme un vrai coup, avec le profil du monde du joueur, quel
que soit le mode (`observe` ou `override`) : mêmes entrées (positions, angle, sprint, mouvement du
joueur côté serveur), même calcul, même paquet. Le joueur voit et entend le coup (animation et son
de dégât), sans perdre de vie. Les coups vont dans `/kb debug` et dans le journal, avec
`"attacker":"(sparring)"`.

Une différence avec un vrai coup : après un vrai coup, le serveur envoie le paquet de vitesse puis
remet aussitôt le mouvement que la victime avait avant, pour que le knockback ne compte pas deux
fois au coup suivant. Un plugin ne peut pas envoyer un paquet de vitesse sans changer ce
mouvement : le sparring remet donc le mouvement d'avant au tick suivant, et annule l'événement de
vitesse de cette remise pour qu'aucun deuxième paquet ne parte. Le paquet reçu par le joueur est
exactement celui du profil ; seul le mouvement côté serveur garde le knockback un tick de plus.
Le test de contrôle vérifie qu'il n'y a qu'un paquet par coup et que le coup suivant part bien du
mouvement remis.

## Journal des hits

Avec `record-hits: true`, chaque coup est écrit dans
`plugins/RagoCombatEngine/hits/hits-AAAA-MM-JJ.jsonl`, un objet JSON par ligne. Les nombres sont
écrits de façon à être relus exactement (`Double.toString`, `Float.toString`). Champs :

- `type` (`melee`), `time` (millisecondes), `tick`, `mode`, `profile`, `profileValues` ;
- `world`, `attacker`, `victim`, `victimEntityId`, `sprinting`, `knockbackEnchantment`,
  `victimOnGround` (état au sol envoyé par le client de la victime, gardé pour l'analyse
  seulement) ;
- `input` : `attackerX`, `attackerZ`, `attackerYaw`, `knockbackLevel`, `victimX`, `victimZ`,
  `victimMotion` (vitesse côté serveur de la victime) ;
- `velocity` (calculée avec le profil), `packet` (la même en unités de paquet), `applied`
  (`true` si le moteur l'a posée, `false` en mode observe).

L'écriture passe par une file de 10 000 lignes ; si le disque ne suit pas, les lignes en trop
sont perdues et comptées (`/kb status`), le thread principal n'attend jamais. Ce format est lu par
`tools/kb-control` : on ajoute des champs, on n'en renomme pas (un test le vérifie).

## Tests

- Tests unitaires : `./gradlew :rago-combat-engine:test` (réglages, format du journal).
- Test de contrôle sur un vrai serveur PandaSpigot, avec des bots :
  [tools/kb-control](../../tools/kb-control/README.md). Il vérifie le knockback envoyé, puis le
  délai entre deux coups (10 ticks, 9 ticks, et 9 ticks encore après une réapparition).
