# Le site de Rago Practice (site/web)

La première version du site : la page d'accueil (dix sections), les pages légales marquées « en
cours de relecture », une page 404, le référencement de base. Next.js (App Router), TypeScript,
Node 22. Les styles viennent du système de design ([../design](../design/README.md)) : les mêmes
`tokens.css` et `components.css`, sans copie.

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
npm run skins        # redessine les skins des PNJ (public/skins) depuis rago-lobby
```

Les tests (`tests/unit/game-data.test.ts`) relisent ces fichiers de leur côté : chaque rang montré
est vendu sur la boutique en ligne au prix de la config, aucun rang du staff n'apparaît, les 11
divisions ont leur seuil et leurs gains, chaque mode montré tourne dans un seul plugin qui l'a
vraiment.

## Les tests

```
npm run lint && npm run typecheck && npm test
npm run build:e2e    # les deux états, côte à côte : .next-soon et .next-online
npm run e2e          # Playwright : la page, la copie de l'IP, 360 à 1920 px, axe, le clavier,
                     # le mouvement réduit, aucune erreur, aucun cookie, aucune requête ailleurs
npm run lighthouse   # sur téléphone : au moins 90 en performance et 95 en accessibilité
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

La boutique (Tebex, J3 et J4), les pages de chaque mode et du Ranked (J2), les comptes. Les pages
légales attendent leurs textes, relus avant l'ouverture de la boutique.
