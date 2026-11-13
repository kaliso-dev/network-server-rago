# Le site officiel et la boutique de Rago Practice

Le site du serveur (présenter Rago Practice, ses modes, son Ranked, et faire rejoindre) et sa
boutique (rangs, Starter Pack, boosters, styles), dans un même site.

## Où en est le site

| Partie | État |
| --- | --- |
| Analyse du serveur et des concurrents, stratégie | faite (dans les fichiers du projet : `site-officiel/`) |
| Système de design : logo en SVG, couleurs, typographie, composants de base | **version 0.1**, voir [design/](design/README.md) |
| Pages du site | à venir |
| Boutique (Tebex, API Headless) | à venir |

Rien n'est en ligne. Le serveur n'est pas encore ouvert : le site aura d'abord un état « bientôt »
(Discord, boutique fermée), puis « en ligne ».

## Structure

```
site/
  design/      le système de design : tokens.css, components.css, la page de style
  public/brand le logo en SVG (complet, plat, le R seul) et l'original en PNG
```

Le site vit dans ce dépôt, à côté des plugins, pour partager les noms, les couleurs et les données
du jeu (rangs, divisions, modes). Il n'entre pas dans le build Gradle.
