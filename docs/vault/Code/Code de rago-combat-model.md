---
type: code
tags: [code, rago-combat-model]
maj: 2026-11-25
code: combat/rago-combat-model
---
# Code de rago-combat-model

Java pur, sans dépendance : le calcul **exact** du knockback de Minecraft 1.8.8 et de la trajectoire du client 1.8.9, embarqué dans RagoCombatEngine et utilisé par kb-control. Identique au code vanilla sur 200 000 coups (tests). Ne jamais y mettre une valeur devinée : les profils mesurés sont dans `MeasuredProfiles`. Fonction : [[Knockback et combat]]. README : `combat/rago-combat-model/README.md`; formule : `docs/combat/knockback-1.8.8.md`.

Packages : `gg.rago.combat.model`. **9 classes, 854 lignes** (branche de la PR #47, `ce8edc5`, 25/11). Retour à [[Architecture]].

## Les classes
Description = la première phrase de la javadoc de la classe, **en anglais comme le code** (le code et ses commentaires sont en anglais ; les README sont en français).

| Classe | Lignes | Ce qu'elle fait |
|---|---|---|
| `ClientTrajectory` | 193 | Simulates how a 1.8.9 client moves after it receives a velocity, when the player presses no key and stands on flat, solid, normal ground (slipperiness 0.6) with nothing around. |
| `HitRegistration` | 27 | Rules of the 1.8 damage cooldown, from EntityLiving.damageEntity. |
| `KnockbackCalculator` | 77 | Computes the velocity a player receives from a melee hit. |
| `KnockbackProfile` | 229 | The parameters of a melee knockback formula with the same structure as Minecraft 1.8. |
| `McMath` | 51 | The trigonometry and square root of Minecraft 1.8, reproduced bit for bit. |
| `MeasuredProfiles` | 45 | Knockback profiles built from recordings of another server, never from a guess. |
| `MeleeHit` | 82 | Everything the server knows about a melee hit at the moment it computes knockback. |
| `Vec3` | 60 | An immutable vector, in blocks or blocks per tick. |
| `VelocityPacket` | 90 | Encoding of the 1.8 entity velocity packet (PacketPlayOutEntityVelocity, known as S12PacketEntityVelocity on the client). |

## Les tests (7 fichiers)
`ClientTrajectoryTest`, `HitRegistrationTest`, `KnockbackCalculatorTest`, `KnockbackProfileTest`, `McMathTest`, `MeasuredProfilesTest`, `VelocityPacketTest`

Lancer : `./gradlew :rago-combat-model:test` ([[Construire et tester]]).
