# Le site de Rago Practice (site/web)

Le site : la page d'accueil (« Master your skills », le practice d'abord), la page des modes de
jeu, une page pour chacun des six modes phares, les pages légales marquées « en cours de
relecture », une page 404, le référencement de base. Next.js (App Router), TypeScript, Node 22.
Les styles viennent du système de design ([../design](../design/README.md)) : les mêmes
`tokens.css` et `components.css`, sans copie.

| Page | Ce qu'elle montre |
| --- | --- |
| `/` | le hero et son PNJ en 3D, les six modes phares (un onglet par objet), les autres façons de jouer, le combat, le Ranked, les cosmétiques, le soutien au serveur, la communauté, comment rejoindre |
| `/modes` | les six modes phares, puis chaque catégorie dans un inventaire du jeu, et les événements |
| `/modes/<mode>` | un des six : comment une partie se passe, ses chiffres lus dans le code, les kits d'Attack and defense, les niveaux du combo bot |

La barre d'objets en bas de l'écran (`src/components/Hotbar.tsx`) est la navigation principale.

## Lancer le site

```
cd site/web
npm ci
npm run dev        # http://localhost:3000
```

## Les réglages

Tout se règle par l'environnement, au moment du build. Chaque réglage a une valeur sûre par
défaut : le site n'affiche jamais une adresse ou un état qui n'est pas vrai.

| Variable | Rôle | Par défaut |
| --- | --- | --- |
| `RAGO_SITE_STATE` | `soon` avant l'ouverture (Discord d'abord, boutique fermée), `online` ensuite | `soon` |
| `RAGO_SERVER_ADDRESS` | l'adresse à taper dans Minecraft ; obligatoire en `online` | aucune : la page dit que l'adresse arrive avec l'ouverture |
| `RAGO_DISCORD_URL` | l'invitation du Discord | `https://discord.gg/FXcvN7mWX` |
| `RAGO_SITE_URL` | l'adresse publique du site (liens partagés, sitemap) | sur Netlify, l'adresse du déploiement ; sinon `http://localhost:3000` |
| `RAGO_SITE_INDEX` | `yes` laisse les moteurs de recherche indexer le site | `no` : une copie temporaire n'apparaît pas dans Google |

À la sortie : `RAGO_SITE_URL` sur le vrai domaine, `RAGO_SITE_INDEX=yes`, et le domaine et
l'invitation définitive remplacent les valeurs provisoires (le plan du site, § 12).

## Les données du jeu

Ce que la page dit du jeu vient des fichiers des plugins, pas d'une copie écrite à la main :
`scripts/import-game-data.mjs` lit le `config.yml` du lobby, `Cosmetic.java`, la config de
RagoBedwars et le code de RagoPractice, et écrit `src/data/game.json` (versionné).

```
npm run data         # régénère game.json après un changement du jeu
npm run data:check   # échoue si game.json n'est plus à jour (la CI le lance)
npm run skins        # redessine les skins des PNJ (public/skins et public/skins/3d) depuis rago-lobby
npm run mc           # recopie les textures de Minecraft 1.8.8 (public/mc, src/data/mc-items.json)
```

`npm run mc` lit le paquet `minecraft-assets`, trop gros pour être une dépendance (365 Mo) :
l'installer d'abord avec `npm install --no-save minecraft-assets@1.19.0`. Les textures sont à
Mojang ; leur usage est à vérifier avant l'ouverture
([src/data/MINECRAFT-TEXTURES.md](src/data/MINECRAFT-TEXTURES.md)).

Les six modes phares (`src/lib/flagship.ts`) sont écrits à la main, mais chaque phrase a été lue
dans le code des plugins : chaque mode nomme les classes d'où il vient.

Les tests (`tests/unit/game-data.test.ts`) relisent ces fichiers de leur côté : chaque rang montré
est vendu sur la boutique en ligne au prix de la config, aucun rang du staff n'apparaît, les 11
divisions ont leur seuil et leurs gains, chaque mode montré tourne dans un seul plugin qui l'a
vraiment. `tests/unit/mc-art.test.ts` vérifie que chaque objet montré a sa texture et que les six
modes phares sont de vrais modes du jeu, tirés de classes qui existent.

## Les tests

```
npm run lint && npm run typecheck && npm test
npm run build:e2e    # les deux états, côte à côte : .next-soon et .next-online
npm run e2e          # Playwright : les pages, la copie de l'IP, 360 à 1920 px, axe, le clavier
                     # (onglets, barre d'objets), le mouvement réduit, aucune erreur, aucun
                     # cookie, aucune requête ailleurs
npm run lighthouse   # sur téléphone : au moins 90 en performance et 95 en accessibilité,
                     # sur l'accueil, /modes, une page de mode et une page légale
```

En local, Lighthouse a besoin de Chrome : `CHROME_PATH=/chemin/vers/chrome npm run lighthouse`.

## Héberger une copie sur Netlify

Le fichier `netlify.toml`, à la racine du dépôt, contient déjà les réglages (dossier `site/web`,
Node 22). Pour une copie à montrer :

1. Sur [app.netlify.com](https://app.netlify.com), « Add new project », puis « Import an existing
   project », puis GitHub, et choisir `kaliso-dev/network-server-rago`.
2. Choisir la branche à suivre, garder les réglages lus dans `netlify.toml`, et déployer.

Netlify reconstruit le site à chaque push sur cette branche. Sans `RAGO_SITE_INDEX=yes`, la copie
reste hors des moteurs de recherche.

## Ce qui n'est pas encore là

La boutique (Tebex, J3 et J4), la page du Ranked, les comptes, les vidéos des cosmétiques et les
captures du jeu (la liste des captures à faire est dans le doc de direction artistique). Les
pages légales attendent leurs textes, relus avant l'ouverture de la boutique.
