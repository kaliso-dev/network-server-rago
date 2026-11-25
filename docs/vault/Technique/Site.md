---
type: technique
tags: [site, nextjs, netlify, design]
maj: 2026-11-25
code: site/web (Next.js), site/design (système de design)
---
# Site

Le site officiel de **Rago Practice** et, plus tard, sa boutique, dans le même dépôt (dossier `site/`, hors du build Gradle) pour partager les noms, couleurs et données du jeu. **Pas en ligne** au 25/11. Détail : `site/README.md`, `site/web/README.md`, `site/design/README.md`. Code : [[Code du site]]. Retour à [[Projet]].

## État (CONFIRMÉ dans les README)
| Partie | État |
|---|---|
| Analyse du serveur et des concurrents, stratégie | faite (fichiers du projet : `site-officiel/`, hors dépôt) |
| Système de design (logo SVG, couleurs, typo, composants) | version 0.1 (`site/design` : `tokens.css`, `components.css`, `styleguide.html`, `assets/`) |
| J1 : accueil, page des modes, six pages de modes phares, pages légales « en cours de relecture », 404, référencement | faite |
| J2 : pages des modes et du Ranked | à venir |
| J3-J4 : boutique **Tebex (API Headless)**, comptes, vidéos des cosmétiques, captures du jeu | à venir |

## Technique
- **Next.js 16.4.6** (App Router), React 19.3.1, TypeScript 6, Node ≥ 22 ; skinview3d + three pour le PNJ en 3D ; police du jeu tracée depuis `font/ascii.png`.
- Pages : `/` (hero « Master your skills », six modes phares, practice, parties privées, combat, Ranked, cosmétiques, communauté…), `/modes`, `/modes/<mode>`, `legal/`. Navigation : la barre d'objets en bas (`Hotbar.tsx`).
- **Données du jeu générées** : `scripts/import-game-data.mjs` lit le `config.yml` du lobby, `Cosmetic.java`, la config de RagoBedwars et le code de RagoPractice et écrit `src/data/game.json` (versionné) ; `npm run data:check` en CI échoue s'il n'est plus à jour. **Après tout changement de modes, rangs, divisions ou cosmétiques : `npm run data`.**
- `npm run skins` (skins des PNJ), `npm run mc` (textures et police de Minecraft 1.8.8, `minecraft-assets@1.19.0` sans l'enregistrer) ; l'usage des textures de Mojang est **à vérifier avant l'ouverture** (`src/data/MINECRAFT-TEXTURES.md`).
- Réglages par l'environnement au build : `RAGO_SITE_STATE` (`soon` par défaut, `online`), `RAGO_SERVER_ADDRESS` (obligatoire en online), `RAGO_DISCORD_URL` (défaut `https://discord.gg/FXcvN7mWX`), `RAGO_SITE_URL`, `RAGO_SITE_INDEX` (`no` par défaut : hors des moteurs de recherche).
- Tests : lint, typecheck, vitest (`game-data`, `mc-art`, `mc-font`), Playwright (360 à 1920 px, axe, clavier, aucun cookie ni requête ailleurs), Lighthouse sur téléphone (≥ 90 perf, ≥ 95 accessibilité).

## Hébergement
`netlify.toml` (racine) est prêt pour une **copie temporaire** (base `site/web`, `npm run build`, Node 22, reconstruit seulement si `site/` change). **Aucun site Netlify n'existe pour ce dépôt** (le compte Netlify connecté a rago-optimiser, peeclay, kaliso-dev, cesium-horlogerie-archive : vérifié le 25/11). Créer la copie = « Import an existing project » sur Netlify, choisir `kaliso-dev/network-server-rago` : **à faire seulement sur demande de Sofiene**.

## Points à trancher
- Le Discord par défaut du site (`discord.gg/FXcvN7mWX`) n'est pas celui affiché en jeu (`discord.gg/ragooptimizer`, lien officiel choisi le 22/11) : **À VÉRIFIER** lequel garder.
- Le domaine du site et du serveur (`rago.gg` est affiché en jeu) : question **q127**.
- Ne pas confondre avec le site de Rago Optimizer refait dans le projet « New Brand Rago » ([[Autres projets Rago]]).
