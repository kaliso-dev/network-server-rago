# Knockback de mêlée en 1.8.8 : la formule vérifiée

Ce document décrit exactement ce que fait un serveur 1.8.8 quand un joueur en frappe un autre, et
ce que fait ensuite le client 1.8.9 de la victime. C'est la base du RagoCombatEngine : le moteur
reproduit ce calcul au bit près, puis remplace ses constantes par celles d'un profil.

Rien ici ne vient d'une supposition sur Hypixel. Les valeurs d'Hypixel ne sont pas publiques ; elles
se mesurent (voir « Mesurer un autre serveur » plus bas). Les mesures faites sont dans
[mesures-hypixel.md](mesures-hypixel.md).

## Sources

- Le code vanilla est celui que retire le patch serveur 0014 « Configurable knockback » de
  PandaSpigot (hpfxd, 2021), lu dans le dépôt PandaSpigot au commit `1afa565` (28/09/2026) :
  méthodes de knockback d'`EntityLiving` et `EntityHuman.attack`. Les lignes retirées sont le code
  d'origine de CraftBukkit 1.8.8.
- La table de sinus est celle de `MathHelper` (65 536 valeurs `float`).
- L'ordre des événements Bukkit (`EntityDamageByEntityEvent`, `PlayerVelocityEvent`) vient du code
  de CraftBukkit 1.8.8 dans `EntityHuman.attack` et `EntityLiving.damageEntity`.
- La physique du client vient de `EntityLivingBase.onLivingUpdate` et `moveEntityWithHeading`
  (client 1.8.9).

La transcription littérale est dans
`combat/rago-combat-model/src/test/java/gg/rago/combat/model/VanillaReference.java`. Le test
`KnockbackCalculatorTest` vérifie sur 200 000 coups aléatoires que le modèle du projet donne
exactement les mêmes nombres (`double` identiques au bit près).

## Un coup, côté serveur, dans l'ordre

1. **Niveau de knockback.** `i` = niveau de l'enchantement Knockback de l'objet tenu, plus 1 si
   l'attaquant sprinte. Le niveau est calculé avant les dégâts.
2. **Dégâts et délai d'invulnérabilité.** Si la victime a été touchée il y a moins de 10 ticks
   (`noDamageTicks > maxNoDamageTicks / 2`), le coup ne fait que compléter les dégâts s'il est plus
   fort : pas de knockback, pas de paquet. Sinon, c'est un coup complet. `EntityDamageByEntityEvent`
   est lancé à ce moment, avant que quoi que ce soit ne bouge.
3. **Direction.** `d0 = attaquant.x − victime.x`, `d1 = attaquant.z − victime.z`. Si
   `d0² + d1² < 1.0E-4` (moins d'un centième de bloc), le serveur tire une direction au hasard :
   `d0 = (random − random) × 0.01`, idem pour `d1`, jusqu'à sortir de ce cas.
4. **Knockback de base**, sur la vitesse côté serveur de la victime (`mot`) :

   ```java
   float f1 = MathHelper.sqrt(d0 * d0 + d1 * d1);   // racine en double, arrondie en float
   float f2 = 0.4F;
   motX /= 2.0D;  motY /= 2.0D;  motZ /= 2.0D;      // « friction »
   motX -= d0 / (double) f1 * (double) f2;
   motY += (double) f2;
   motZ -= d1 / (double) f1 * (double) f2;
   if (motY > 0.4000000059604645D) motY = 0.4000000059604645D;
   ```

5. **Supplément de sprint et d'enchantement**, seulement si `i > 0`, dans la direction où
   **regarde** l'attaquant (pas selon les positions) :

   ```java
   motX += (double) (-MathHelper.sin(yaw * 3.1415927F / 180.0F) * (float) i * 0.5F);
   motY += 0.1D;
   motZ += (double) (MathHelper.cos(yaw * 3.1415927F / 180.0F) * (float) i * 0.5F);
   // puis : vitesse horizontale de l'attaquant × 0.6, et fin de son sprint
   ```

   `MathHelper.sin(f)` lit la table `SIN[(int) (f * 10430.378F) & 0xFFFF]`, et `cos` ajoute
   `16384.0F` à l'indice. Il n'y a pas de plafond vertical après ce supplément.
6. **Envoi.** Si la victime est un joueur, `PlayerVelocityEvent` est lancé, puis le paquet
   `PacketPlayOutEntityVelocity` part : chaque composante est bornée à ±3.9, multipliée par 8000
   et tronquée vers zéro (`short`). Le client divise par 8000. Le serveur remet ensuite la
   vitesse d'avant le coup sur sa copie de la victime : c'est le client qui calcule la trajectoire.

La vitesse côté serveur d'un joueur immobile au sol n'est pas nulle : c'est un tick de gravité et
de frottement, `motY = (0 − 0.08) × 0.98F = −0.0784000015258789`. C'est pour cela que le premier
coup vertical vaut 0.3608 et non 0.4.

### Valeurs de référence (profil vanilla)

| Situation | Vitesse envoyée (blocs/tick) | Paquet |
| --- | --- | --- |
| Victime immobile, coup sans sprint, attaquant au nord | (0, 0.36080000519752503, 0.4000000059604645) | (0, 2886, 3200) |
| Même chose avec sprint, attaquant tourné vers le sud | (0, 0.46080000519752506, 0.9000000059604645) | (0, 3686, 7200) |

Ces valeurs sont vérifiées par `KnockbackCalculatorTest`.

### Les variantes de serveurs 1.8

Les forks 1.8 ne font pas tous ce calcul de la même façon, et c'est une des raisons pour
lesquelles le projet a son propre moteur :

- **PandaSpigot** (le serveur retenu) rend le knockback configurable. Avec ses réglages par
  défaut, il écrit les constantes en `double` (0.4 au lieu de `0.4F`). Le résultat ne diffère du
  vanilla que d'une unité de paquet (1/8000 de bloc par tick), environ une fois sur 14 000 coups
  (mesuré sur 2 millions de coups simulés). Le test de contrôle vérifie les deux.
- **NachoSpigot** calcule l'angle du supplément de sprint en `double`
  (`(float) (yaw × Math.PI / 180.0D)`), ce qui n'est plus le calcul vanilla.
- La vitesse de départ de `PlayerVelocityEvent` n'est pas la même partout : CraftBukkit d'origine
  y met la vitesse d'avant le coup, d'autres forks celle d'après. Le moteur ne dépend pas de ce
  détail (voir le README du moteur).

## Côté client 1.8.9

Une fois la vitesse reçue, le client de la victime déplace le joueur à chaque tick, dans cet
ordre (`ClientTrajectory` le reproduit) :

1. les composantes de vitesse plus petites que 0.005 sont mises à zéro ;
2. le frottement horizontal est choisi selon l'état « au sol » d'avant le déplacement :
   0.6 × 0.91 au sol (bloc ordinaire), 0.91 en l'air ;
3. déplacement, avec collisions ; un choc vertical remet la vitesse verticale à zéro, et le joueur
   n'est « au sol » que si le déplacement bloqué allait vers le bas ;
4. gravité puis frottement vertical : `motY = (motY − 0.08) × 0.9800000190734863` ;
5. vitesse horizontale × frottement.

Repère connu : un saut 1.8 (vitesse verticale 0.42) culmine à 1.2491870787446813 bloc et retombe
au 12e tick (`ClientTrajectoryTest`). La simulation ne gère ni les touches du joueur, ni les murs,
ni la glace, l'eau, les échelles ou les effets de potion.

## Ce que le projet vérifie, et où

| Vérification | Où |
| --- | --- |
| Le modèle = le code vanilla, au bit près | `KnockbackCalculatorTest` (200 000 coups) |
| Le modèle = le code de PandaSpigot par défaut | `ControlCheckTest`, et le test de contrôle sur un vrai serveur |
| Le plugin envoie exactement le paquet du profil | test de contrôle (`tools/kb-control`), en CI |
| La trajectoire client 1.8.9 | `ClientTrajectoryTest` |

## Mesurer un autre serveur (Hypixel)

Pour s'approcher d'Hypixel, il faut des mesures, pas des valeurs trouvées sur un forum :

1. le mod kb-recorder (tools/kb-recorder) note chaque paquet de vitesse reçu par son joueur, avec
   la position, l'état au sol et la vitesse du client, et les attaques envoyées ;
2. deux joueurs se battent, chacun avec le mod : chaque coup est vu des deux côtés, et le sprint
   de l'attaquant se lit dans son propre enregistrement ;
3. on cherche le profil (les six constantes) qui reproduit les paquets mesurés, en refaisant la
   même analyse sur notre serveur en vanilla pour vérifier la méthode, et on documente ce qui ne
   colle pas ;
4. le test de contrôle vérifie ensuite que notre serveur envoie exactement ce que dit ce profil.

La première mesure (Housing, 7 novembre 2026) a donné le profil `hypixel-approx-v1` et un délai de
9 ticks entre deux coups : voir [mesures-hypixel.md](mesures-hypixel.md).

Limite connue : un paquet mesuré donne la vitesse que le serveur a envoyée, mais pas la vitesse
« côté serveur » qu'il avait pour la victime juste avant (terme de friction). Les mesures sur
victime immobile au sol, où cette vitesse est connue, servent à séparer les constantes.
