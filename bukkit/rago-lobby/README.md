# RagoLobby : le lobby principal

Un plugin Bukkit 1.8.8 : le lobby où arrivent les joueurs de Rago Practice, et d'où ils entrent
dans les files d'attente des modes de jeu.

## Ce qu'il fait

- **Le spawn.** Chaque joueur qui se connecte arrive au spawn du lobby, en aventure, avec sa vie
  et sa faim pleines. Tomber dans le vide (sous `hauteur-vide`) ramène au spawn.
- **La protection.** Dans le monde du lobby : pas de dégâts, pas de faim, rien à poser, casser,
  jeter ou ramasser, pas de coffres ni de portes, pas de pluie, pas de nuit, pas de monstres. Les
  autres mondes (arènes, monde du test de knockback, mondes de build) ne sont pas touchés.
- **La barre d'objets.** La boussole ouvre le sélecteur des modes ; le colorant rouge quitte la
  file ; le colorant vert ou gris cache ou montre les autres joueurs du lobby.
- **Le sélecteur.** Un menu avec un objet par mode, sept par ligne : son format (1v1, 2v2, solo),
  sa description, le nombre de joueurs en file. Un clic entre dans la file, un deuxième en sort.
  Un mode qu'aucun plugin de jeu ne prend en charge affiche « Bientôt disponible ».
- **Les files d'attente.** Le matchmaking de `rago-game-model`, une fois par seconde. Un joueur
  n'attend que dans une file à la fois.
- **Le scoreboard.** Les joueurs connectés, la file et le temps d'attente. Une ligne n'est
  renvoyée que quand elle change.

## Commandes

| Commande | Effet | Permission |
| --- | --- | --- |
| `/spawn` (ou `/hub`) | retour au spawn ; pendant une partie, c'est un abandon | tout le monde |
| `/file <mode>`, `/file quitter` | entrer dans une file, en sortir | tout le monde |
| `/lobby modes` | les modes et leur file (aussi depuis la console) | tout le monde |
| `/lobby setspawn` | place le spawn du lobby là où tu es (écrit dans `spawn.yml`) | `rago.lobby.admin` |
| `/lobby build` | mode construction dans le lobby (créatif, protection levée) | `rago.lobby.admin` |

## Configuration

`plugins/RagoLobby/config.yml` : le monde du lobby, la hauteur du vide, le titre et la dernière
ligne du scoreboard, les réglages de la file classée, et la liste des modes (nom, icône, équipes,
joueurs par équipe, classé, description). Le fichier fourni explique chaque réglage. Un fichier
invalide désactive le plugin avec un message qui dit quoi corriger.

Si le monde du lobby n'existe pas, le plugin le crée : un monde vide avec une plateforme de
quartz. Pour utiliser une vraie map, mets le nom de son monde dans `monde`, puis `/lobby setspawn`.
Le plugin se charge après RagoBuildTools (`softdepend`), qui charge les mondes de build avec leur
générateur vide ; un monde de build peut donc servir de lobby.

## Pour les plugins de jeu

Un plugin de jeu dépend de RagoLobby (`depend: [RagoLobby]` dans son `plugin.yml`, et
`compileOnly` de `rago-lobby` et `rago-game-model` dans son build, sans les copier dans son jar)
et récupère le service :

```java
LobbyService lobby = getServer().getServicesManager().load(LobbyService.class);
lobby.registerHost(this, "sumo", host);   // ouvre la file du mode "sumo"
```

`MatchHost.start(match)` reçoit chaque partie trouvée, sur le thread principal, avec les joueurs
connectés et au lobby ; il renvoie `false` s'il ne peut pas la démarrer (aucune arène libre), et
les joueurs gardent leur place dans la file. Pendant la partie, les joueurs appartiennent au jeu,
qui les rend avec `lobby.sendToLobby(player)`. `MatchHost.abandon(player)` est appelé quand un
joueur tape `/spawn` en pleine partie.

Sur le réseau, les files passeront dans le proxy (`rago-proxy`) et les serveurs de jeu recevront
leurs parties de lui ; le côté `MatchHost` d'un plugin de jeu ne changera pas.

## Ce qui n'est pas encore là

- L'ELO réel, les statistiques et les classements : ils viendront avec la base de données
  (`rago-data`) et `rago-ranked`.
- Les PNJ et les hologrammes du lobby (avec PacketEvents), les groupes et les cosmétiques.

## Construire et tester

```bash
./gradlew :rago-lobby:build
```

Les tests unitaires lisent le `config.yml` fourni et des configurations invalides, `spawn.yml`, la
disposition du sélecteur et l'horloge du scoreboard. Le comportement en jeu est testé par deux
bots sur un vrai serveur : voir [tools/lobby-check](../../tools/lobby-check/README.md).
