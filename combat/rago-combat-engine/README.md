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
record-hits: false        # journal des hits (voir plus bas)
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
minuscules, chiffres, `-` et `_`, 32 caractères au plus. `vanilla` est intégré et ne peut pas être
redéfini.

### Hitreg : `player-tracker-ticks`

Minecraft 1.8.8 n'envoie la position d'un joueur aux autres qu'un tick sur deux : on voit
l'adversaire jusqu'à 100 ms en retard, et on vise là où il était. Avec `1` (la valeur fournie,
comme sur les serveurs practice), la position part à chaque tick : on touche là où on voit le
joueur. Le knockback n'est pas touché. L'API 1.8.8 n'a pas ce réglage : le plugin change l'entrée
du joueur dans le tracker du serveur, à son arrivée, à chaque changement de monde et à chaque
respawn. Si le serveur n'a pas ce tracker, rien n'est changé et la console le dit une fois.
Les deux valeurs se testent sans risque : le réglage se lit au démarrage.

Aucun profil « Hypixel » n'est fourni : ses valeurs ne sont pas publiques et seront mesurées
(voir la doc de la formule).

## Commande `/kb`

Permission `rago.combat.admin` (opérateurs par défaut).

| Commande | Effet |
| --- | --- |
| `/kb` ou `/kb status` | mode, profil par défaut, profils par monde, état du journal |
| `/kb profiles` | liste des profils et de leurs valeurs |
| `/kb profile <nom>` | change le profil par défaut |
| `/kb world <monde> <profil\|default>` | profil d'un monde, ou retour au profil par défaut |
| `/kb mode <override\|observe>` | change le mode |
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
- L'intervalle va de 10 ticks (le plus rapide qu'un vrai combat permette, puisqu'un joueur ne
  reprend un coup plein que 10 ticks après le précédent) à 200 ticks ; 20 par défaut (une seconde).
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
  [tools/kb-control](../../tools/kb-control/README.md).
