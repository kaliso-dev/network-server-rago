# Le système de design du site Rago Practice

La base visuelle du site officiel et de la boutique : couleurs, typographie, espacements,
mouvement et premiers composants. Piste choisie le 13 novembre 2026 : **un site sombre aux
couleurs du logo** (anthracite, le rouge du logo comme seule couleur de marque, le blanc et les
gris pour le texte), dans la suite des couleurs du jeu (« le rouge clair du logo, le blanc et le
gris », voir le README de `bukkit/rago-lobby`).

| Fichier | Rôle |
| --- | --- |
| `tokens.css` | toutes les valeurs (variables CSS) : couleurs, polices, tailles, espaces, formes, ombres, mouvement |
| `components.css` | la base de la page et les composants ci-dessous |
| `styleguide.html` | la page de style : chaque valeur et chaque composant, à ouvrir dans un navigateur |
| `assets/` | images provisoires de la page de style |
| `../public/brand/` | le logo : `rago-logo.svg` (complet), `rago-logo-flat.svg` (plat), `rago-mark.svg` (le R seul), `rago-logo-original.png` (l'original, 1254 × 1254) ; les icônes : `favicon-32.png`, `apple-touch-icon.png` (180 px), `icon-512.png` |

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
| Titres | **Chakra Petch** 600 et 700 | ses coupes droites rappellent le R ; le titre du hero en capitales |
| Texte | **Inter** 400 à 700 | chiffres tabulaires pour les prix et les stats |
| Adresse, commandes, noms du jeu | **JetBrains Mono** 500 et 600 | `rago.gg`, `/play duels`, `[12✫] [VIP] Steve` |

Tailles fluides (`clamp`), du téléphone au grand écran : display 44 à 88 px, H1 36 à 64, H2 28 à 44,
H3 20 à 24, texte d'accroche 17 à 20, texte 16, petit 14, légende 12 en capitales espacées.

Les trois polices sont sous licence OFL. La page de style les charge depuis Google Fonts ; **le
site les hébergera lui-même** (pas d'appel à Google à chaque visite, ce qui évite d'envoyer
l'adresse IP des visiteurs européens à un tiers).

Pas de police pixel pour le texte. Le style Minecraft vient des images, des icônes en pixels et
des noms du jeu reproduits avec leurs couleurs.

## Espaces, formes, profondeur

- Espaces par pas de 4 px (`--space-1` à `--space-10`), sections espacées de 64 à 128 px.
- Contenu sur 1200 px au plus, marges de 16 px sur un téléphone à 56 px sur un grand écran.
- Coins : 6 px (tags), 10 px (boutons, champs), 16 px (cartes), 22 px (cadres d'image).
- **Le coin coupé** (`.cut`) : l'angle à 45° de l'hexagone, en haut à gauche et en bas à droite.
  Réservé au bouton principal d'un écran (« Play now », « Buy PREMIUM+ ») : c'est la signature
  du site, elle doit rester rare.
- Ombres sombres et douces pour les cartes, une lueur rouge seulement sous le bouton principal.

## Mouvement

Court et calme : 120 ms pour un survol, 220 ms pour un retour (« Copied! »), 420 ms pour une
section qui apparaît. Rien ne tourne en boucle. Avec « réduire les animations » du système, tout
mouvement s'arrête (`prefers-reduced-motion`).

## Composants (version 0.1)

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
- Pas de photo de banque d'images, pas d'illustration futuriste sans rapport avec le jeu.

## Accessibilité

Contraste AA pour tout le texte, focus visible au clavier sur tout ce qui se clique, cibles de
44 px au moins sur un téléphone, texte d'image (`alt`) sur chaque image qui porte du sens.

## Voir la page de style

Ouvrir `styleguide.html` dans un navigateur (les polices viennent d'Internet). Elle est en
français pour l'équipe ; les textes des composants sont en anglais, comme le serveur.
