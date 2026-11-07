# kb-control : le test de contrôle du moteur de combat

Ce test répond à une seule question : **sur un vrai serveur 1.8.8, le client reçoit-il exactement
le knockback que le profil prévoit ?** Il ne compare rien à Hypixel ; il prouve que la chaîne
« profil → plugin → paquet reçu » ne déforme rien, ce qui est la condition pour qu'un profil
mesuré plus tard soit vraiment celui que les joueurs ressentent.

## Ce qui se passe

1. `run-control-test.sh` télécharge PandaSpigot 1.8.8, monte un serveur de test (monde plat,
   mode hors ligne, accessible seulement depuis la machine, Java 8 en CI) avec RagoCombatEngine et
   son journal des hits.
2. Deux bots (`bots/control-bots.mjs`, avec [mineflayer](https://github.com/PrismarineJS/mineflayer)) :
   `RagoAttacker` frappe `RagoVictim` en trois phases de 36 coups :
   - `observe-vanilla` : le moteur observe, le serveur garde son propre knockback ;
   - `override-vanilla` : le moteur applique le profil vanilla ;
   - `override-control-alt` : le moteur applique `control-alt`, un profil de test volontairement
     différent du vanilla (valeurs arbitraires, ce n'est pas un réglage proposé).

   Chaque phase mélange coups avec et sans sprint, à main nue et avec des bâtons Knockback I et
   II, sur une victime immobile, qui marche ou en plein saut, à des distances et angles variés.
   Les bots écrivent chaque paquet de vitesse reçu et chaque attaque dans `received.jsonl`.
   Avant le premier coup, ils attendent la fin de l'invulnérabilité de la victime qui vient de se
   connecter (60 ticks en 1.8.8, pendant lesquels un coup ne fait rien).
3. `ControlCheck` associe chaque paquet reçu au coup journalisé par le moteur, puis vérifie :
   - **coups appliqués** : la vitesse du journal est exactement celle que donne le modèle avec
     les entrées du journal, et le client a reçu exactement ce paquet ;
   - **coups observés** : le paquet du serveur est exactement celui que donne le modèle avec les
     réglages par défaut de PandaSpigot, et à une unité au plus du vanilla exact. Cela vérifie
     aussi que le moteur lit les bonnes entrées : une erreur de position, d'angle, de sprint ou
     de vitesse se verrait ici ;
   - chaque attaque des bots a donné un coup journalisé (le rapport liste celles qui n'en ont
     donné aucun), et chaque paquet reçu appartient à un coup (un paquet en double ferait échouer
     le test) ;
   - la couverture : les trois phases, sprint et sans sprint, enchantement, victime en l'air.

Le test échoue au moindre écart. Le rapport (`report.md`) donne, par mode, profil et sprint, le
nombre de coups vérifiés et l'écart maximal avec le vanilla exact.

## Lancer le test

En CI, le job « KB control test » du workflow `CI` le lance à chaque push et pull request ; le
rapport apparaît dans le résumé du job et les fichiers (journal, paquets, logs du serveur) dans
l'artefact `kb-control-run`.

Sur une machine Linux ou macOS avec Java 8 (ou plus récent), Node.js 20+, curl et python3 :

```bash
./gradlew build :kb-control:installDist
(cd tools/kb-control/bots && npm ci)
KB_ACCEPT_EULA=true tools/kb-control/run-control-test.sh
```

Lancer un serveur Minecraft implique d'accepter le [CLUF de Minecraft](https://aka.ms/MinecraftEULA) :
le script refuse de démarrer sans `KB_ACCEPT_EULA=true`. Réglages possibles (variables
d'environnement) : `KB_SERVER_JAVA`, `PANDASPIGOT_URL`, `PANDASPIGOT_SHA256`, `KB_PORT`,
`KB_HITS_PER_PHASE`, `KB_RUN_DIR` (détails en tête du script). Les fichiers produits vont dans
`control-run/` (ignoré par git).

Le vérificateur seul, sur des fichiers déjà produits :

```bash
tools/kb-control/build/install/kb-control/bin/kb-control \
  --hits control-run/server/plugins/RagoCombatEngine/hits --received control-run/received.jsonl \
  --server-knockback pandaspigot --min-hits 108 --report report.md
```

## Limites

- Les bots ne reproduisent pas un vrai joueur : ils se téléportent avant chaque coup et frappent
  à intervalle fixe (une seconde). Le test vérifie le knockback envoyé, pas la sensation en jeu,
  qui reste à juger avec de vrais joueurs.
- La victime a l'effet Résistance V pour ne pas mourir pendant le test ; les dégâts ne changent
  pas le knockback.
- Le serveur testé est PandaSpigot avec ses réglages de knockback par défaut ; pour un autre
  serveur, `--server-knockback vanilla` compare les coups observés au vanilla exact.
