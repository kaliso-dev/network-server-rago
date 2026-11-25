---
type: fonctionnalité
tags: [mesures, hypixel, knockback]
maj: 2026-11-25
code: docs/combat/mesures-hypixel.md, tools/kb-recorder, tools/kb-analyzer
---
# Mesures Hypixel

Les mesures dont viennent `hypixel-approx-v1` et `hit-delay-ticks: 9`. Source complète : `docs/combat/mesures-hypixel.md` (296 lignes). Outils : [[Outils de mesure du knockback]]. Retour à [[Knockback et combat]].

## Mesure 1 : 07/11/2026, Housing, 1v1 (CONFIRMÉ)
- **qRago et godemy** à l'épée, chacun avec le mod **kb-recorder 0.1.0** ; ping mesuré 163 ms (qRago) et 140 ms (godemy). Même combat sur le serveur de test (vanilla) pour étalonner : qRago en local (0 ms), godemy par **Tailscale** (46 ms).
- **544 paquets de vitesse** reçus sur Hypixel (518 chez nous), 0 ligne perdue ; analyse par kb-analyzer.
- **Vertical** (unités de paquet, 8 000 = 1 bloc/tick) : sans sprint au sol **2 886** (= vanilla) ; sprint au sol **3 446** (vanilla 3 686) ; le sprint ajoute **0,07** bloc/tick au lieu de 0,1, après le plafond. Hauteur d'un coup en sprint : 1,31 bloc (1,49 en vanilla).
- **Horizontal** : 0,400 sans sprint, +0,50 en sprint dans la direction du regard = **vanilla**.
- **Délai** : un coup compte **9 ticks (450 ms)** après le précédent (vanilla 10). Pas de « double coup » sur Hypixel.
- **Combos en l'air** : un tiers des coups en l'air des combos prennent le plafond (3 200 / 3 760) sur Hypixel, presque aucun chez nous : **c'est l'effet du ping**, pas une autre règle. Confirmé par un test avec clumsy (+150 ms) : 43 % au plafond, « vraiment Hypixel » mais tout le jeu lagge.
- Contrôle le même jour avec `hypixel-approx-v1` + 9 ticks : redonne les valeurs d'Hypixel.

## Mesure 2 : 07/11/2026, Bedwars, 1v1 (CONFIRMÉ)
Les deux mêmes joueurs dans une partie de Bed Wars d'Hypixel (42 s, 68 coups ; ping 171 et 147 ms) : **mêmes valeurs qu'en Housing**. Pas besoin de v2. Un joueur à 150 ms sur Hypixel a à peu près les combos d'un joueur à 110-130 ms chez nous. Petit écart non reproduit : 2 898 contre 2 886 sur un coup en l'air juste après un saut (0,0015 bloc/tick).

## Limites
Deux joueurs, quelques minutes ; non mesurés : enchantement Knockback, canne à pêche, projectiles, explosions et boules de feu. Le protocole des explosions est prêt mais **pas encore joué** ([[Boules de feu et TNT]]).
