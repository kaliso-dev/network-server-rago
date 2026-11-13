# Le site officiel et la boutique de Rago Practice

Le site du serveur (présenter Rago Practice, ses modes, son Ranked, et faire rejoindre) et sa
boutique (rangs, Starter Pack, boosters, styles), dans un même site.

## Où en est le site

| Partie | État |
| --- | --- |
| Analyse du serveur et des concurrents, stratégie | faite (dans les fichiers du projet : `site-officiel/`) |
| Système de design : logo en SVG, couleurs, typographie, composants de base | **version 0.1**, voir [design/](design/README.md) |
| Première version du site (J1) : page d'accueil, pages légales en relecture, référencement, tests | **faite**, voir [web/](web/README.md) |
| Pages des modes et du Ranked (J2) | à venir |
| Boutique (Tebex, API Headless) | à venir |

Le serveur n'est pas encore ouvert : le site est dans l'état « bientôt » (Discord d'abord,
boutique fermée, pas d'adresse) et passera « en ligne » par un réglage. Une copie temporaire peut
être hébergée sur Netlify (`netlify.toml`), hors des moteurs de recherche.

## Structure

```
site/
  design/          le système de design : tokens.css, components.css, la page de style
  web/             le site (Next.js) : src/app les pages, src/components, scripts l'import des données
  web/public/brand le logo en SVG (complet, plat, le R seul) et l'original en PNG
  web/public/skins les skins des PNJ du lobby, dessinés depuis rago-lobby
```

Le site vit dans ce dépôt, à côté des plugins, pour partager les noms, les couleurs et les données
du jeu (rangs, divisions, modes). Il n'entre pas dans le build Gradle.
