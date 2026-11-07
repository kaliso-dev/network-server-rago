# RagoCombatEngine

Plugin Bukkit pour les serveurs 1.8.8 (PandaSpigot), compatible Java 8. Il remplace le knockback
des coups de mêlée entre joueurs par celui d'un **profil** : six constantes, calculées avec le
modèle exact de `rago-combat-model`. Le profil intégré `vanilla` donne au bit près le knockback de
Minecraft 1.8.8.

## Ce que fait le plugin, et ce qu'il ne touche pas

- Il ne change que les coups de mêlée **joueur contre joueur**, et seulement les coups complets :
  un coup porté pendant le délai d'invulnérabilité (10 ticks) n'envoie pas de knockback en
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
mode: override            # override : applique le profil ; observe : calcule et journalise, sans rien changer
active-profile: vanilla   # profil utilisé partout, sauf dans les mondes de world-profiles
world-profiles: {}        # par monde, par exemple { arena_a: vanilla, arena_b: test-a }
hit-delay-ticks: 10       # délai entre deux coups, en ticks (vanilla 10, Hypixel mesuré 9), de 1 à 20
ping-simulation-ms: 0     # ping simulé pour les coups (0 = désactivé, 500 au plus), expérimental
ping-simulation-delays-knockback: true  # false : le ping simulé ne retarde pas le kb
record-hits: false        # journal des hits (voir plus bas)
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

## Délai entre deux coups

Après un coup qui compte, un joueur est protégé pendant un cooldown ; un nouveau coup ne compte en
entier (dégâts, knockback, paquet de vitesse) qu'une fois la moitié du cooldown passée. Le
cooldown vanilla dure 20 ticks, d'où un coup toutes les 10 ticks (500 ms) au mieux. Hypixel en
compte un toutes les 9 ticks (450 ms) d'après les mesures.

`hit-delay-ticks` règle ce délai : le moteur donne à chaque joueur un cooldown de deux fois cette
valeur (`setMaximumNoDamageTicks`), à la connexion, une tick après une réapparition ou un
changement de monde (le Minecraft vanilla recrée alors le joueur avec 20 ticks ; CraftBukkit le
garde, mais le moteur ne dépend pas de ce détail), et dès que le réglage change. Le cooldown vaut pour tous les dégâts (chute, feu, flèches), comme en vanilla.

## Ping simulé (expérimental)

Les joueurs habitués à Hypixel y jouent souvent à 140-160 ms. Le ping y change deux choses au corps
à corps ([mesures, et test avec du ping ajouté](../../docs/combat/mesures-hypixel.md)) :

- **Les trades.** La victime n'est poussée qu'une fois que le kb a fait le trajet du serveur
  jusqu'à elle. Jusque-là, elle voit encore l'attaquant à portée et peut lui rendre le coup.
- **La hauteur des combos.** Après un coup, le serveur 1.8 envoie le kb à la victime mais garde son
  mouvement à lui. Il ne le met à jour qu'une fois que le client de la victime, ayant reçu le kb,
  renvoie ses déplacements, soit un aller-retour plus tard. Le coup suivant d'un combo dépend de
  ce mouvement : plus la victime a de ping, plus il est ancien, et plus elle part haut.

`ping-simulation-ms` (ou `/kb pingsim <ms>`) fait comme si chaque victime qui a moins de ping avait
celui-là, pour les coups au corps à corps entre joueurs seulement.

**Le kb part en retard**, du ping qui manque à la victime : avec 150, le paquet de kb part 150 ms
après le coup pour une victime à 0 ms, et 102 ms après pour une victime à 48 ms. Compté depuis le
coup sur le serveur, c'est ce que fait un vrai ping : la victime reçoit son kb et renvoie ses
déplacements un aller-retour plus tard, et ses attaques envoyées entre-temps comptent. Le serveur,
lui, garde le mouvement de la victime d'avant le coup, comme quand il envoie le kb tout de suite, et
ne l'envoie à personne d'autre.

Le ping de l'attaquant ne compte pas. Une première formule ajoutait la moitié de celui qui lui
manque : les combos montaient alors d'un tick de trop quand la victime avait plus de ping que
l'attaquant (mesure du Bedwars dans le document de mesures).

**Le mouvement plus ancien** ne sert que quand le kb part sans retard : avec
`ping-simulation-delays-knockback: false` (ou `/kb pingsim kb off`), hors du mode `override`, ou si
le moteur ne trouve pas les classes du serveur. Le kb est alors calculé avec le mouvement de la
victime d'il y a autant de ticks que le ping qui lui manque (de 50 ms, arrondi) : avec 150, une
victime à 30 ms est frappée avec son mouvement d'il y a 2 ticks. C'est la première version du
réglage, qui reproduit la hauteur des combos mais pas les trades.

**Quelle valeur.** D'après la mesure du Bedwars, un joueur à 150 ms sur Hypixel y a les combos
d'environ 120 ms ici : le ping que mesure le mod sur Hypixel compte aussi un temps que le serveur
d'Hypixel passe avant de traiter l'attaque, qui ne retarde pas le coup suivant. 120 est donc la
valeur à essayer d'abord.

Une victime qui a déjà ce ping ou plus ne change pas. Les dégâts, l'animation
de coup reçu, le délai entre deux coups et la position des joueurs restent ceux du serveur : la
victime voit donc l'animation avant d'être poussée. Le ping de chaque joueur est celui que le
serveur mesure (aller-retour du keep-alive, lissé). Tant que le réglage vaut 0, le moteur
n'enregistre aucun mouvement.

L'API Bukkit de 1.8 ne sait ni envoyer un paquet de vitesse plus tard, ni empêcher le serveur de
l'envoyer sans changer son mouvement. Le moteur passe donc par les classes du serveur, trouvées par
réflexion (`DelayedKnockback`). S'il ne les trouve pas, il le dit une fois dans la console et le kb
part sans retard.

## Commande `/kb`

Permission `rago.combat.admin` (opérateurs par défaut).

| Commande | Effet |
| --- | --- |
| `/kb` ou `/kb status` | mode, profil par défaut, profils par monde, délai entre deux coups, ping simulé, état du journal |
| `/kb profiles` | liste des profils et de leurs valeurs |
| `/kb profile <nom>` | change le profil par défaut |
| `/kb world <monde> <profil\|default>` | profil d'un monde, ou retour au profil par défaut |
| `/kb mode <override\|observe>` | change le mode |
| `/kb hitdelay <ticks>` | change le délai entre deux coups (vanilla 10, Hypixel mesuré 9) |
| `/kb pingsim <ms>` | change le ping simulé pour les coups (0 = désactivé) |
| `/kb pingsim kb <on\|off>` | retarde ou non le kb dans le ping simulé |
| `/kb debug` | affiche dans le chat de celui qui tape la commande chaque coup et son paquet |
| `/kb reload` | relit config.yml ; s'il est invalide, les réglages actuels restent en place |

Les changements faits par `/kb` durent jusqu'au prochain redémarrage ou `/kb reload` ; pour les
garder, il faut les écrire dans config.yml. Chaque changement est écrit dans la console avec
`[audit]` et le nom de son auteur.

## Journal des hits

Avec `record-hits: true`, chaque coup est écrit dans
`plugins/RagoCombatEngine/hits/hits-AAAA-MM-JJ.jsonl`, un objet JSON par ligne. Les nombres sont
écrits de façon à être relus exactement (`Double.toString`, `Float.toString`). Champs :

- `type` (`melee`), `time` (millisecondes), `tick`, `mode`, `profile`, `profileValues` ;
- `world`, `attacker`, `victim`, `victimEntityId`, `sprinting`, `knockbackEnchantment`,
  `victimOnGround` (état au sol envoyé par le client de la victime, gardé pour l'analyse
  seulement) ;
- `input` : `attackerX`, `attackerZ`, `attackerYaw`, `knockbackLevel`, `victimX`, `victimZ`,
  `victimMotion` (vitesse côté serveur de la victime utilisée pour le calcul) ;
- `velocity` (calculée avec le profil), `packet` (la même en unités de paquet), `applied`
  (`true` si le moteur l'a posée, `false` en mode observe) ;
- `pingSimulationMs` (le réglage), `simulatedDelayTicks` (l'âge en ticks de `victimMotion`, 0 sans
  ping simulé), `knockbackDelayMs` (le retard du paquet de kb, 0 sans ping simulé) et
  `liveVictimMotion` (la vitesse côté serveur au moment du coup).

L'écriture passe par une file de 10 000 lignes ; si le disque ne suit pas, les lignes en trop
sont perdues et comptées (`/kb status`), le thread principal n'attend jamais. Ce format est lu par
`tools/kb-control` : on ajoute des champs, on n'en renomme pas (un test le vérifie).

## Tests

- Tests unitaires : `./gradlew :rago-combat-engine:test` (réglages, format du journal).
- Test de contrôle sur un vrai serveur PandaSpigot, avec des bots :
  [tools/kb-control](../../tools/kb-control/README.md). Il vérifie le knockback envoyé, puis le
  délai entre deux coups (10 ticks, 9 ticks, et 9 ticks encore après une réapparition).
