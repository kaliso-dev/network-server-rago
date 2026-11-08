# bedwars-maps : trouver les points d'une map Bed Wars

Un outil en Python qui lit une map Bed Wars au format d'Hypixel (un monde Minecraft 1.8) et écrit
son `bedwars.yml` pour RagoBedwars, sans `/bw` : les équipes avec leur spawn, leur lit, leur
générateur, leur shop et leur villageois des améliorations, les générateurs de diamant et
d'émeraude, et le point des spectateurs. Il ne touche pas au monde, il ne fait que le lire.

```bash
pip install numpy scipy pillow
python3 bwsetup.py chemin/vers/Archway Archway Archway/bedwars.yml apercu.png
python3 runall.py sortie/ maps/*      # plusieurs maps, quatre à la fois
```

Il affiche un résumé (équipes, couleurs, comment chaque point a été trouvé) et, avec le dernier
argument, une vue de dessus au niveau des lits avec les points : S le spawn, G le générateur, I le
shop, U les améliorations, la lettre de l'équipe sur son lit, D et E les générateurs de diamant et
d'émeraude. Le monde et son `bedwars.yml` vont ensuite dans le dossier des mondes du serveur ; la
map est jouable au démarrage suivant. Les points se vérifient en jeu avec `/bw edit <map>` et
`/bw info`, et chacun se déplace avec sa commande `/bw`.

Les maps ne sont pas dans ce dépôt.

## Comment chaque point est trouvé

- **Les équipes** : les lits qui ont un coffre ou un ender chest à moins de 20 blocs (les lits de
  décoration n'en ont pas), un par île.
- **Les couleurs** : la laine, l'argile et le verre de chaque île, comparés à ceux des autres îles
  (la couleur du thème de la map est partout, celle de l'équipe seulement chez elle). Les cartes à
  2, 4 et 8 équipes prennent les 2, 4 ou 8 premières couleurs (red, blue, green, yellow, aqua,
  white, pink, gray), comme sur Hypixel.
- **Le générateur** : la fosse de dalles un demi-bloc sous le sol où tombent les objets ; sinon une
  fosse d'un bloc ; sinon le fond de l'île, dans l'axe du lit. Une marche entre deux sols n'est pas
  une fosse.
- **Le spawn** : entre le coffre et l'ender chest, sinon entre le lit et le générateur, tourné vers
  le lit.
- **Le shop et les améliorations** : de chaque côté de l'île, entre le spawn et le générateur,
  tournés vers le spawn.
- **Diamant et émeraude** : les blocs de diamant et d'émeraude seuls que les maps d'Hypixel posent
  sous leurs générateurs ; sinon les groupes de blocs de diamant (ou d'émeraude) qui décorent leurs
  îles ; sinon la forme de la map (les îles les plus éloignées pour le diamant, le milieu pour
  l'émeraude). Quatre générateurs de diamant ; quatre d'émeraude sur une map à 8 équipes, deux sinon.

Sur 54 maps solo/doubles et 4 équipes, toutes ont eu leurs équipes complètes. Les points trouvés
par la forme de la map sont les moins sûrs : à regarder en premier.
