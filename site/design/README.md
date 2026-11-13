# Le système de design du site Rago Practice

La base visuelle du site officiel et de la boutique : couleurs, typographie, espacements,
mouvement et composants. Piste choisie le 13 novembre 2026 : **un site sombre aux couleurs du
logo** (anthracite, le rouge du logo comme seule couleur de marque, le blanc et les gris pour le
texte), dans la suite des couleurs du jeu (« le rouge clair du logo, le blanc et le gris », voir
le README de `bukkit/rago-lobby`).

Refonte du même jour (« Master your skills ») : **le site parle Minecraft**. La police du jeu,
les vraies textures du jeu, les menus du jeu (inventaire, info-bulle, barre d'objets) et
les PNJ du lobby en 3D. Le practice passe d'abord, le Ranked après.

| Fichier | Rôle |
| --- | --- |
| `tokens.css` | toutes les valeurs (variables CSS) : couleurs, polices, tailles, espaces, formes, ombres, mouvement |
| `components.css` | la base de la page et les composants ci-dessous |
| `styleguide.html` | la page de style : chaque valeur et chaque composant, à ouvrir dans un navigateur |
| `assets/` | images provisoires de la page de style |
| `../web/public/brand/` | le logo : `rago-logo.svg` (complet), `rago-logo-flat.svg` (plat), `rago-mark.svg` (le R seul), `rago-logo-original.png` (l'original, 1254 × 1254) ; les icônes : `favicon-32.png`, `apple-touch-icon.png` (180 px), `icon-512.png` |

## Le logo

Le logo officiel (un hexagone rouge en dégradé, un grand R anguleux blanc) est redessiné en
vecteur à partir de l'original : les sommets de l'hexagone et les 13 points du R sont relevés sur
l'image de 1254 px, le dégradé et le liseré sont mesurés sur ses pixels.

- **Logo complet** (`rago-logo.svg`) : dès 48 px. Dégradé radial `#F20808` → `#A80103` → `#5A0004`,
  liseré `#F00404`, ombre douce sous le R.
- **Logo plat** (`rago-logo-flat.svg`) : sous 48 px (favicon, barre de navigation sur mobile),
  un seul rouge `#D3141A`.
- **R seul** (`rago-mark.svg`) : en une couleur (`currentColor`), pour un filigrane ou un bouton.
- Ne pas déformer, recolorer l'hexagone, ajouter de lueur au logo ni le poser sur une image
  chargée sans fond sombre. Garder autour de lui au moins un quart de sa largeur.
- La variante violette sur fond noir n'est pas utilisée.

## Couleurs

**Un seul rouge de marque**, pris sur le logo : du `#F00000` du liseré au `#600000` de l'ombre.

| Jeton | Valeur | Usage |
| --- | --- | --- |
| `--red-400` | `#FF5555` | le rouge clair du jeu (`&c`) : liens, accents, anneau de focus |
| `--red-500` | `#E8191E` | boutons principaux, marque ; le blanc dessus passe l'AA (4,6:1) |
| `--red-600` | `#C40F14` | bouton pressé, bas du dégradé des boutons |
| `--red-700`, `--red-800` | `#9C0002`, `#6C0003` | milieu et ombre du logo, lueurs du hero |
| `--ink-900` | `#0C0D10` | fond des pages |
| `--ink-850`, `--ink-800` | `#121418`, `#181B20` | surfaces, cartes |
| `--ink-750`, `--ink-600` | `#1E2128`, `#2F343D` | champs et tags, traits |
| `--white` | `#FFFFFF` | texte principal |
| `--grey-300` | `#A3A8B2` | texte secondaire (8,2:1) |
| `--grey-400` | `#80868F` | texte discret, légendes (5,3:1) |

- Le texte est blanc ou gris. Le rouge sert aux actions, aux liens et à la marque, jamais à un
  paragraphe.
- **Les couleurs du jeu restent des touches** (un tag, un nom, une icône) : Practice `#55FFFF`,
  RBW Modes `#FFAA00`, Duels `#55FF55`, Arenas `#FF5555`, VIP `#55FFFF`, PREMIUM+ `#FF55FF`, vert
  pour jouer et réussir, or pour les Rago Points, jaune pour l'XP. Ce sont celles des menus du jeu.
- **Les 11 divisions** gardent les couleurs de `elo:` dans le config.yml du lobby. Leur nom en
  texte est éclairci juste assez pour se lire sur le fond sombre (4,5:1) ; l'icône garde la
  couleur exacte.
- Pas de néon, pas de dégradé au hasard : un seul dégradé, celui du rouge du logo, dans les
  boutons principaux et la lumière derrière le logo du hero.

## Typographie

| Rôle | Police | Détail |
| --- | --- | --- |
| Tout ce que le jeu écrirait : titres, boutons, menus, noms des modes, adresse, commandes, tags, info-bulles, tableau de bord | **la police de Minecraft 1.8.8**, normale et grasse | tracée pixel par pixel depuis `font/ascii.png` du jeu, mesurée comme le jeu la mesure ; le gras est celui du jeu (la lettre deux fois, décalée d'un pixel) ; le titre du hero en capitales |
| Texte | **Inter** 400 à 700 (variable) | les paragraphes restent dans une police de lecture ; chiffres tabulaires pour les prix et les stats |

Une lettre du jeu fait 8 pixels de haut : ses tailles sont des multiples de 8 px (8, 16, 24, 32…),
pour que chaque pixel du jeu tombe sur des pixels entiers de l'écran. Les tailles fluides
(`clamp`) sont arrondies au multiple de 8 le plus proche (`round()`, là où le navigateur le sait) :
display 40 à 80 px, H1 32 à 56, H2 24 à 40, H3 16 à 24, les petits textes du jeu 16. L'ombre est
celle du jeu : un pixel du jeu en bas à droite (`--mc-shadow`). Une ligne de titre fait 10 pixels
du jeu pour 8 (`--leading-heading: 1.25`). Le texte d'accroche fait 17 à 20 px, le texte 16, le
petit 14 (Inter).

**La police du jeu est à Mojang**, comme les textures : `npm run mc` la construit depuis le même
paquet `minecraft-assets` (`scripts/mc-font.mjs`), dans `public/mc/font`. Elle est sous la même
réserve, **à trancher avant l'ouverture** (voir `web/src/data/MINECRAFT-TEXTURES.md`). Inter est
sous licence **SIL Open Font License 1.1** (vérifié dans `@fontsource-variable/inter`). **Le site
héberge ses polices lui-même** (`next/font/local`), sans appel à Google : l'adresse IP des
visiteurs n'est envoyée à personne.

- Pas de police du jeu pour les paragraphes : ils restent en Inter, pour se lire vite.
- Pas d'espacement entre les lettres : le jeu met un pixel entre deux lettres, la police aussi.
- Les signes que `ascii.png` n'a pas (€, ✦, ✫, →, —) viennent d'Inter.

## Espaces, formes, profondeur

- Espaces par pas de 4 px (`--space-1` à `--space-10`), sections espacées de 64 à 128 px.
- Contenu sur 1200 px au plus, marges de 16 px sur un téléphone à 56 px sur un grand écran.
- **Le pixel** (`--px`, 4 px) : un pixel des menus du jeu à la taille où le site les dessine.
  Les coins ne sont pas arrondis : ils marchent d'un pixel (`--steps`, un `clip-path`), comme les
  boutons du jeu.
- **Les boutons** ont le relief des boutons du jeu : clair en haut à gauche, sombre en bas à
  droite. Le relief et les marches sont dessinés sur `::before`, pour que l'anneau de focus ne
  soit jamais coupé.
- Les cartes ont le même relief, plus discret. Une lueur rouge seulement sous le bouton principal.
- Les bandes de couleur entre deux sections ont un bord en marches de pixels.
- L'ancien coin coupé (`.cut`) reste dans `components.css` mais n'est plus utilisé.

## Mouvement

Court et calme : 120 ms pour un survol, 220 ms pour un retour (« Copied! »), 420 ms pour une
section qui apparaît. Avec « réduire les animations » du système, tout mouvement s'arrête
(`prefers-reduced-motion`), la 3D comprise : les vues restent immobiles.

Deux mouvements en boucle seulement, ceux du jeu : l'objet posé au sol qui tourne et flotte
(1 tour en 6,3 s, comme `RenderEntityItem`) et le PNJ qui respire (l'animation d'attente de
skinview3d).

### La 3D

- **Les PNJ** : skinview3d 3.4.2 (MIT) avec les vrais skins du lobby ; on tourne autour à la
  souris ou au doigt, sans zoom.
- **Les objets** : un modèle extrudé, construit comme le jeu construit un objet tenu (chaque
  pixel de la texture devient un petit cube), avec la lumière du jeu.
- Chargée seulement quand c'est utile : la vue doit être à l'écran et WebGL disponible. Sur
  ordinateur, après le chargement de la page ; sur téléphone et tablette, au premier geste
  (toucher, défiler, une touche). D'ici là, l'image plate du skin ou de l'objet est montrée.
- Hors de l'écran, le rendu s'arrête.

## Composants (version 0.2)

| Composant | Classe | Règles |
| --- | --- | --- |
| Boutons | `.btn--primary`, `--secondary`, `--ghost`, `--sm` | un seul principal par écran ; textes en capitales, verbe d'abord |
| Tags | `.tag`, `--red`, `--practice`, `--rbw`, `--duels`, `--arenas`, `--start` | une catégorie prend sa couleur du jeu ; « ✦ Start here » comme dans la boussole |
| Adresse du serveur | `.ip-chip` | copie en un clic, « Copied! » en vert, l'adresse reste lisible si la copie est refusée |
| État du serveur | `.status`, `--online`, `--soon` | **un nombre de joueurs n'est affiché que s'il vient du serveur** ; sinon « Opening soon » ou « Server offline » |
| Divisions | `.divisions`, `.division` | les 11 divisions, leur icône en pixels (8 × 8) et leur seuil d'ELO ; 4 colonnes sur un téléphone |
| Rangs | `.ranks`, `.rank`, `.rank--featured` | la colonne gratuite d'abord ; prix en euros TVA comprise, « once » (les rangs sont permanents) ; avantages tirés de `rangs:` |
| Noms du jeu | `.mc`, `.mc-name--free`, `--vip`, `--premium` | le niveau `[12✫]`, le rang et le pseudo dans leurs couleurs du jeu |
| Chat du jeu | `.mc-chat` | une annonce ou un message tel que le joueur le verra |
| Mode de jeu | `.mode` | une vraie image de la map, la catégorie, le format, ce que le mode entraîne |
| Produit | `.product` | icône en pixels, contenu exact, prix ou « Price to set » tant qu'il n'est pas fixé |
| Mentions | `.notice` | « not affiliated with Mojang Studios, Microsoft or Hypixel » en bas de chaque page |
| Inventaire du jeu | `.gui`, `.gui__title`, `.gui__slots`, `.slot` | le gris clair des menus, ses cases ; la case survolée s'éclaircit par-dessus l'objet, la case choisie garde un cadre blanc |
| Info-bulle du jeu | `.mc-tooltip`, `.mc-tooltip__name` | le fond violet très sombre et le liseré des info-bulles ; le nom dans sa couleur du jeu |
| Objet du jeu | `McItem` (`.mc-item`) | la vraie texture de l'objet ; les blocs et les têtes en cube, vus comme dans l'inventaire |
| Barre d'objets | `Hotbar` | la navigation du site, en bas de l'écran : la boussole mène aux modes, le livre à « How to join »… ; les touches 1 à 9 quand elle a le focus |
| Tableau de bord | `.scoreboard` | le tableau de droite du jeu, avec ses vrais intitulés ; des valeurs d'exemple sont dites « Example values » |
| Pseudo au-dessus de la tête | `.nametag` | le fond gris transparent du jeu |

## Images

- **Seulement les maps du propriétaire** : Bridge Fight, Block Clutch, Block Extend, Fireball
  Fight, KB Displace, Arena, Bed Fight, Top Bed Fight (les READMEs des plugins les disent « du
  propriétaire »). **Jamais** les maps Bed Wars d'Hypixel (Ranked, First Rush, Sky Rise) ni les
  arènes « Minemen ». Pour les autres (les maps sumo, Block-In), vérifier d'abord leur origine.
- Des images propres : rendues depuis les fichiers des mondes (Chunky, par exemple) ou capturées
  en jeu sans HUD (F1), lumière douce. Les captures actuelles du serveur de test ne servent qu'à
  la page de style.
- Les 16 skins originaux des PNJ (`bukkit/rago-lobby/src/main/resources/skins`) incarnent les
  modes et les catégories, en 3D quand c'est utile.
- **Les textures et la police de Minecraft 1.8.8** (objets, blocs, barre d'objets, `font/ascii.png`)
  sont dans `web/public/mc`, copiées ou construites par `npm run mc`. Elles sont à Mojang : **leur usage sur le site est à
  vérifier avant l'ouverture** (`web/src/data/MINECRAFT-TEXTURES.md` dit comment les retirer).
- Pas de fausse capture du jeu : là où une capture manque, la page le dit ou montre un menu
  reconstruit avec les vraies textures et les vrais textes du jeu.
- Pas de photo de banque d'images, pas d'illustration futuriste sans rapport avec le jeu.

## Accessibilité

Contraste AA pour tout le texte, focus visible au clavier sur tout ce qui se clique, cibles de
44 px au moins sur un téléphone, texte d'image (`alt`) sur chaque image qui porte du sens.

## Voir la page de style

Ouvrir `styleguide.html` dans un navigateur. Elle montre les couleurs, les tailles et les
composants de base ; les polices viennent du site (`web/public/mc/font` pour celle du jeu,
`web/node_modules/@fontsource-variable/inter` pour Inter, après un `npm install` dans `web`). Elle est en français pour l'équipe ; les textes des composants sont en
anglais, comme le serveur. Les composants React (inventaire, barre d'objets, 3D) se voient sur le
site lui-même (`npm run dev` dans `web`).
