---
type: technique
tags: [secrets, sécurité]
maj: 2026-11-26
---
# Clés et secrets

**Règle fixe : aucun secret (clé, mot de passe, jeton) n'est écrit dans le dépôt, ce vault, une PR, un commit ou un commentaire.** Le dépôt est **public** depuis le 10/11. Retour à [[Règles de travail]].

## Ce qui existe
| Secret | Où il va | Dans le dépôt |
|---|---|---|
| Clé d'API Hypixel | `plugins/RagoLobby/config.yml`, `hypixel.cle-api`, sur le serveur seulement | `""` |
| Clé MineSkin (facultative, account.mineskin.org/keys) | `cle-mineskin:` du lobby | `""` |
| UUID du compte Owner (mode offline) | `outils-owner.uuid` | `""` (pas un secret, mais propre au serveur) |
| Variables du site (`RAGO_SERVER_ADDRESS`…) | environnement Netlify | aucune |
| Jeton et adresse de Rago Hub (`HUB_TOKEN` ou `RAGO_HUB_TOKEN`, `HUB_URL` ou `RAGO_HUB_URL`) | environnement de la session Claude lancée par Rago Hub, lus par `.claude/hooks/` | aucune ([[Branches et PR]]) |

- La CI n'utilise **aucun secret** et ne déploie rien.
- Pas de clés de signature dans ce projet (contrairement à Rago Optimizer : ne jamais changer les clés de signature de l'app, voir son vault).
- Futures : mots de passe MariaDB, jeton BungeeGuard, clés Tebex : à garder hors du dépôt (gestionnaire de secrets de l'hébergeur), **À DÉCIDER** avec la production ([[Hébergement et production]]).
- Si un secret a été commité par erreur : le considérer comme compromis (le dépôt est public), le révoquer et prévenir Sofiene.
