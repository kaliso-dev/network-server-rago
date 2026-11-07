# RagoPractice : les modes d'entraînement

Un plugin Bukkit 1.8.8 qui joue des modes du lobby (`bukkit/rago-lobby`). Pour l'instant : le
**duel sumo**, le premier mode jouable de bout en bout (lobby, file, partie, retour au lobby).

## Le duel sumo

- Deux joueurs sur une plateforme ronde de quartz (rayon 7 par défaut). Le premier qui tombe
  donne un point à l'autre ; le premier à 3 points gagne.
- Pas de dégâts : les coups ne font que du knockback, celui de RagoCombatEngine.
- Chaque manche commence par un compte à rebours de 3 secondes, joueurs immobiles, face à face à
  6 blocs l'un de l'autre.
- Quitter le serveur ou taper `/spawn` pendant le duel, c'est perdre par abandon.
- À la fin, les deux joueurs reviennent au lobby après 3 secondes.

## Les arènes

Les parties se jouent dans un monde vide (`arenes`, créé s'il n'existe pas). Chaque partie a sa
zone, à 200 blocs de la suivante sur l'axe X : deux parties ne se voient jamais. Une plateforme
est construite la première fois que sa zone sert, puis réutilisée : rien n'est chargé ni généré
pendant le jeu. Au-delà de `arenes-max` parties à la fois, les joueurs gardent leur place dans la
file.

## Configuration

`plugins/RagoPractice/config.yml` : le monde des arènes, l'espacement, le nombre maximal de
parties, et pour le sumo les points pour gagner et le rayon de la plateforme.

## Construire et tester

```bash
./gradlew :rago-practice:build
```

Le score des duels et la vérification de la configuration ont des tests unitaires. Le duel
complet est joué par deux bots sur un vrai serveur : voir
[tools/lobby-check](../../tools/lobby-check/README.md).
