# Rago Practice : le code des serveurs

Le réseau Minecraft 1.8.9 **Rago Practice**, hébergé en Europe, pour s'entraîner au Ranked
Bedwars avec un knockback aussi proche que possible de celui d'Hypixel, mesuré et non deviné.
Ce dépôt contient les plugins, les outils et la documentation des serveurs.

Le plan d'ensemble (architecture, coûts, feuille de route) est dans le
[document d'architecture](https://claude.ai/artifact/JHXQTtaRJU7REysX4xrvxo).

## Où en est le projet

Jalon en cours : **M1, « Labo KB »**, le moteur de combat et de quoi le vérifier.

| Partie | État |
| --- | --- |
| `rago-combat-model` : calcul exact du knockback 1.8.8 et de la trajectoire client 1.8.9 | fait, testé (identique au code vanilla sur 200 000 coups) |
| `rago-combat-engine` : plugin qui applique un profil de knockback, commande `/kb`, journal des hits | fait, testé ; test de contrôle sur un vrai serveur en CI |
| `tools/kb-control` : test de contrôle avec bots sur un vrai PandaSpigot | fait, lancé par la CI : 108 coups sur 108 exacts sur PandaSpigot 1.8.8 (build 226) |
| `tools/local-server` : serveur de test à lancer sur son PC (Windows, Mac, Linux) pour essayer le knockback en jeu | fait, assemblé et démarré par la CI |
| Mesure du knockback d'Hypixel (module d'enregistrement dans Rago Client) | prochaine étape |
| Proxy, lobby, base de données, Bedwars, Ranked, statistiques, cosmétiques | à venir, voir la feuille de route |

Rien n'est encore en production. **Aucun profil « Hypixel » n'existe pour l'instant** : il sera
construit à partir de mesures, et le document de la formule explique comment.

## Structure

```
build-logic/                 conventions Gradle partagées (Java 8, tests, plugins Bukkit)
combat/rago-combat-model/    calcul exact du knockback 1.8.8 et physique du client 1.8.9 (sans dépendance)
combat/rago-combat-engine/   plugin RagoCombatEngine : profils de knockback, /kb, journal des hits
tools/kb-control/            test de contrôle sur un vrai serveur PandaSpigot, avec deux bots
tools/kb-analyzer/           compare le knockback de deux serveurs à partir des enregistrements
tools/kb-recorder/           mod client Forge 1.8.9 qui enregistre les coups reçus, pour les mesures
tools/local-server/          serveur de test à lancer sur son PC, avec le moteur
docs/                        formule du knockback, compatibilité et dépendances
```

Chaque module a son README : [combat-model](combat/rago-combat-model/README.md),
[combat-engine](combat/rago-combat-engine/README.md), [kb-control](tools/kb-control/README.md),
[kb-analyzer](tools/kb-analyzer/README.md), [kb-recorder](tools/kb-recorder/README.md),
[local-server](tools/local-server/README.md).

## Construire et tester

Il faut un JDK 17 ou plus récent (tout est compilé pour Java 8, la version des serveurs 1.8.8).

```bash
./gradlew build
```

Le jar du plugin se trouve ensuite dans `combat/rago-combat-engine/build/libs/`. Le test de
contrôle sur un vrai serveur se lance comme indiqué dans [tools/kb-control](tools/kb-control/README.md) ;
la CI GitHub le lance à chaque push et pull request, avec la compilation et les tests unitaires.

## Règles du projet

- Des modules séparés, pas un plugin géant. Pas de plugin abandonné comme base.
- Tout ce qui tourne dans un serveur 1.8.8 reste compatible Java 8, et le thread principal ne
  touche jamais au disque ni à la base de données.
- Chaque dépendance est vérifiée et listée dans [docs/compatibility.md](docs/compatibility.md).
- Chaque mécanique importante est testée ; ce qui n'est qu'une ébauche n'est jamais présenté
  comme terminé.
- Les paramètres de knockback d'Hypixel ne sont jamais inventés : ils viennent de mesures.

## Documentation

- [Knockback de mêlée en 1.8.8 : la formule vérifiée](docs/combat/knockback-1.8.8.md)
- [Compatibilité et dépendances](docs/compatibility.md)
