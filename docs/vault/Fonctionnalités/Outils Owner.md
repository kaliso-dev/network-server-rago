---
type: fonctionnalité
tags: [owner, reach, scaffold, test]
maj: 2026-11-25
code: bukkit/rago-lobby (OwnerTools, OwnerRules)
---
# Outils Owner

Plan 7.1, choix du propriétaire du 22/11 : des outils **de triche contrôlée** pour que l'Owner teste le PvP et les protections du serveur (`outils-owner:`). Retour à [[Staff et modération]].

- `/owner reach <3-6|off>` : ses coups portent jusqu'à cette distance (3 = normal) ; le premier joueur dans sa visée, sans bloc entre eux, prend le coup.
- `/owner scaffold [on|off]` : en sortant d'un bord, un bloc de celui qu'il tient se pose sous ses pieds (là où la partie autorise un bloc).
- `/owner off` coupe tout, `/owner` seul dit ce qui est actif.
- **Pour le compte `compte:` (qRago) seulement**, qui doit aussi avoir `rago.admin` : pour tous les autres (opérateurs compris), la commande n'existe pas (réponse de Spigot à une commande inconnue). Sur un serveur en mode offline, il faut l'UUID du compte dans `uuid:` (vide dans le dépôt), sinon les outils restent coupés.
- Coupés par défaut et à la déconnexion. **Jamais en classé** (ils n'y font rien). Une partie où un outil est actif ne compte pour personne : ni stats, ni XP, ni ELO, ni record.
- À qui est le compte qRago ? question **q122** ([[Questions ouvertes]], [[Personnes et comptes]]).
