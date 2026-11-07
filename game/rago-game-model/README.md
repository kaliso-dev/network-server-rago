# rago-game-model : modes de jeu, files d'attente, arènes

Du Java pur, sans Bukkit : ce qui décide qui joue contre qui, testé sans serveur. Le lobby
(`bukkit/rago-lobby`) l'utilise aujourd'hui ; le matchmaker du proxy (`rago-proxy`, jalon Ranked)
le réutilisera tel quel.

| Classe | Rôle |
| --- | --- |
| `GameMode` | un mode : identifiant, nom, nombre d'équipes et de joueurs par équipe, classé ou non |
| `ModeCatalog` | les modes du réseau, dans l'ordre du sélecteur |
| `ModeQueue` | la file d'un mode et le matchmaking |
| `Matchmaker` | toutes les files, un joueur dans une seule file à la fois |
| `RatingWindow` | l'écart d'ELO accepté en classé, qui grandit avec l'attente |
| `MatchProposal` | une partie trouvée : le mode et les équipes |
| `ArenaGrid` | les emplacements d'arène d'un serveur de jeu, sur une ligne dans un monde vide |

## Le matchmaking

- **Non classé** : premier arrivé, premier servi. Les équipes sont distribuées à tour de rôle.
- **Classé** : le joueur qui attend depuis le plus longtemps est associé aux ELO les plus
  proches que les deux côtés acceptent. Chacun accepte un écart qui commence à 100 points et
  grandit de 50 toutes les 10 secondes d'attente, jusqu'à 500 (réglable dans le lobby). Les
  équipes sont ensuite équilibrées par ELO (1-2-2-1).
- Une partie qui ne peut pas démarrer (aucune arène libre) remet ses joueurs dans la file, à leur
  place d'origine.

Il n'y a pas encore de base de données : tant que `rago-data` et `rago-ranked` n'existent pas,
tous les joueurs ont le même ELO (`file.elo-par-defaut` du lobby).

## Construire et tester

```bash
./gradlew :rago-game-model:build
```
