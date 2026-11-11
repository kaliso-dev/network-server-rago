# rago-combat-model

Bibliothèque Java 8 sans dépendance : le calcul du knockback de mêlée de Minecraft 1.8.8 et la
physique du client 1.8.9, écrits pour donner exactement les mêmes nombres que le jeu. Le plugin
`rago-combat-engine` l'embarque, et les outils (`tools/kb-control`, puis l'analyse des mesures) la
réutilisent, pour que tout le projet calcule de la même façon.

## Contenu

| Classe | Rôle |
| --- | --- |
| `KnockbackProfile` | les six constantes d'un profil ; `vanilla()` donne celles de la 1.8.8 ; `fromMap` lit un profil de config.yml de façon stricte |
| `MeleeHit` | les entrées d'un coup : positions horizontales de l'attaquant et de la victime, orientation de l'attaquant, niveau de knockback, vitesse côté serveur de la victime |
| `KnockbackCalculator` | `compute(profil, coup)` : la vitesse que le serveur envoie, dans l'ordre exact des opérations vanilla (arrondis `float` compris) |
| `VelocityPacket` | encodage du paquet de vitesse (borne ±3.9, × 8000, troncature) et décodage |
| `HitRegistration` | la règle du délai d'invulnérabilité : coup complet ou simple complément de dégâts |
| `McMath` | la table de sinus de `MathHelper` et la racine en `float` |
| `ClientTrajectory` | trajectoire du client 1.8.9 après réception d'une vitesse, sans touche pressée, sur sol plat ; hauteur max, tick d'atterrissage, distance |
| `Vec3` | vecteur immuable |

## Exemple

```java
KnockbackCalculator calculator = new KnockbackCalculator();
// Attaquant au nord de la victime, sans sprint, victime immobile au sol.
MeleeHit hit = new MeleeHit(0.5, 0.5, 0.0F, 0, 0.5, 3.0, new Vec3(0, -0.0784000015258789, 0));
Vec3 velocity = calculator.compute(KnockbackProfile.vanilla(), hit);
VelocityPacket packet = VelocityPacket.encode(velocity);   // (0, 2886, 3200)

ClientTrajectory.Trajectory path = ClientTrajectory.simulate(packet.decode(), true, 40);
path.peakHeight();          // hauteur max en blocs
path.landingTick();         // tick où le joueur retouche le sol
```

Quand l'attaquant et la victime sont à moins d'un centième de bloc, le serveur tire une
direction au hasard ; le calculateur fait de même avec la source aléatoire qu'on lui donne
(`Math.random` par défaut), dans le même ordre d'appels.

## Tests

`./gradlew :rago-combat-model:test`

- `KnockbackCalculatorTest` compare le modèle à une transcription littérale du code vanilla
  (`VanillaReference`, écrite séparément, avec sa propre table de sinus) sur 200 000 coups
  aléatoires : les `double` doivent être identiques au bit près. Il vérifie aussi les valeurs
  connues, le plafond vertical, la friction, le tirage aléatoire et un profil personnalisé.
- `ClientTrajectoryTest` vérifie la physique client sur des repères connus (saut 1.8 :
  1.2491870787446813 bloc au sommet, retour au sol au 12e tick).
- `VelocityPacketTest`, `McMathTest`, `HitRegistrationTest`, `KnockbackProfileTest`.

La formule, ses sources et ses limites : [docs/combat/knockback-1.8.8.md](../../docs/combat/knockback-1.8.8.md).
