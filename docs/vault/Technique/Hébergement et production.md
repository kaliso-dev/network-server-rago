---
type: technique
tags: [production, hébergement, réseau]
maj: 2026-11-25
---
# Hébergement et production

**Rien n'est en production au 25/11/2026.** Pas de machine louée, pas de domaine en service connu, pas de proxy, pas de base de données, pas de boutique. Le seul serveur est le [[Serveur de test]] sur le PC de Sofiene. Retour à [[Projet]].

## Ce qui est prévu (plan d'architecture v1 du 06/11, OBSERVÉ, pas décidé en détail)
Résumé ici, détail dans [[Plan d'architecture v1]] :
- **Une machine en Europe** (Allemagne, France ou Pays-Bas), 4 cœurs à forte fréquence, 16 Go, NVMe : **20 à 28 €/mois** ; total estimé 24 à 34 €/mois avec TCPShield (gratuit), sauvegardes hors machine (3-5 €), domaine (~1 €). Budget annoncé : 30 €/mois.
- Cinq processus : **Velocity** (proxy, matchmaker, files dans Redis), **lobby-01** et **games-01** (PandaSpigot 1.8.8), **MariaDB 11**, **Redis 7** ; plus tard `rago-api` (Java 21) pour le site, Discord et Rago Client. Backends sur 127.0.0.1, seul le proxy public derrière **TCPShield**, **BungeeGuard** pour le forwarding.
- Clients 1.8.x seulement au lancement (vanilla, Lunar, Badlion, Rago Client) ; ViaVersion repoussé.
- Le choix de Java pour la production (8 ou 21) se fera au jalon infrastructure ([[Dépendances et compatibilité]]).

## Ce qui manque avant d'ouvrir (voir [[Plan de sortie publique]])
Hébergeur et machine, domaine (`rago.gg` affiché en jeu : **q127**), réseau (proxy, base de données, stats partagées), anticheat (plan 7.3, BLOQUÉ), clé d'API Hypixel de production, boutique Tebex et pages légales, site en ligne, essais en jeu de tout ce qui n'est que TESTÉ EN CI, mesure des boules de feu. **Staging d'abord, production après validation de Sofiene** (règle fixe).
