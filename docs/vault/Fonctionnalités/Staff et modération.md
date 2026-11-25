---
type: fonctionnalité
tags: [staff, modération, sanctions]
maj: 2026-11-25
code: bukkit/rago-lobby (Punishments, StaffTools, StaffPanel, Visibility, Nicks)
---
# Staff et modération

Retour à [[Lobby]]. Commandes détaillées : [[Commandes et permissions]].

- **Rangs du staff** : OWNER, ADMIN, DEV, MOD, HELPER, BUILDER (et MEDIA, rang de créateur) ; permissions `rago.staff` (helper et plus), `rago.mod`, `rago.admin` ([[Rangs, boutique et cosmétiques]]).
- `rago.staff` : `/staffchat` (`/sc`), `/vanish` (`/v`), `/freeze <joueur>` (pour une vérification), `/clearchat`, `/reports` (les 20 derniers), `/realname <nick>`, `/staff [joueur]` (panel : joueurs en ligne, où ils sont, ping, figés ou mute ; se téléporter, amener, figer, mute, ban), `/mute`, `/unmute`, `/kick`, `/warn`, `/history`.
- `rago.mod` : `/ban`, `/unban`, `/mutechat`.
- `rago.admin` : `/rank`, `/points`, `/booster give`, `/pack give`, `/style give|take` (aussi depuis la console, pour la future boutique en ligne).
- Durées : `30m`, `2h`, `7d`, `2w` ou `perm`. Sanctions et historique dans `sanctions.yml`. Un membre du staff ne peut pas sanctionner un autre membre du staff, sauf un admin. Chaque sanction est annoncée au staff.
- `/report <joueur> <raison>` (`/wdr`) pour tous.
- Chat : sans rang, 2 s entre deux messages et pas deux fois le même ; `rago.perk.chat` lève la limite.
- **Pas d'anticheat** dans le projet (aucun code anticheat). Plan 7.3 du suivi : **BLOQUÉ**, proposition du 22/11 = Grim (gratuit, Java 17+, alertes seules pendant un essai), compatible avec le KB custom, les fireballs, la TNT et le practice, Owner exempté ; à choisir (Grim, Vulcan…) ([[Plan de sortie publique]]).
- Les modérateurs `jncl` et `nativ` ont été retirés de la salle des remerciements le 22/11 (17h56), gardés en commentaire dans le `config.yml`.
