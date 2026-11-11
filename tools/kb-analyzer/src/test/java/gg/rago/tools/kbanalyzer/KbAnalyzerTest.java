package gg.rago.tools.kbanalyzer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.combat.model.VelocityPacket;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KbAnalyzerTest {

    private static PlayerView alice() {
        return new PlayerView(Recording.parse(SyntheticFight.alice()));
    }

    private static PlayerView bob() {
        return new PlayerView(Recording.parse(SyntheticFight.bob()));
    }

    @Test
    void theRecordingsAreReadWithoutProblems() {
        Recording recording = Recording.parse(SyntheticFight.bob());
        assertTrue(recording.problems.isEmpty(), recording.problems.toString());
        PlayerView bob = new PlayerView(recording);
        assertTrue(bob.problems.isEmpty(), bob.problems.toString());
        assertEquals(SyntheticFight.BOB, bob.entityId);
        assertEquals("Bob", bob.player);
        assertEquals(SyntheticFight.ALICE, bob.opponent());
        assertEquals(24, bob.attacks.get(SyntheticFight.ALICE).size());
        assertEquals(3, bob.sprintChanges.size());
        assertEquals(1, bob.looks.size());
        assertEquals(0.0, bob.yawBefore(1000L));
        assertTrue(Double.isNaN(bob.yawBefore(SyntheticFight.BOB_CLOCK + 400)));
    }

    @Test
    void velocityPacketsComeWithTheirHurtAnimation() {
        PlayerView alice = alice();
        assertEquals(SyntheticFight.HITS, alice.velocities.size());
        for (PlayerView.Velocity velocity : alice.velocities) {
            assertTrue(velocity.hurt);
            assertTrue(velocity.ground);
        }
        assertEquals(new VelocityPacket(0, 3446, 7200), alice.velocities.get(0).packet);
        assertEquals(0.9, alice.velocities.get(0).horizontal(), 1e-9);
    }

    @Test
    void theCountedAttacksAreTheOnesTheServerTook() {
        Registration registration = new Registration(bob(), SyntheticFight.ALICE);

        assertEquals(100.0, registration.delayMillis);
        assertEquals(1, registration.isolated);
        assertEquals(SyntheticFight.HITS, registration.countedAttacks());
        for (int a = 0; a < registration.attacks.size(); a++) {
            assertEquals(a % 4 == 0, registration.counted[a], "attack " + a);
        }
        Map<Long, int[]> curve = registration.curve();
        assertArrayEquals(new int[] {SyntheticFight.HITS * 3, 0}, curve.get(0L));
        assertArrayEquals(new int[] {SyntheticFight.HITS - 1, SyntheticFight.HITS - 1}, curve.get(450L));
        assertArrayEquals(new int[] {0, 0}, curve.get(400L));
    }

    @Test
    void theAttackerRecordingGivesTheServerSprint() {
        Fight fight = new Fight("test", alice(), bob());
        Fight.Direction bobOnAlice = fight.first;

        assertEquals(4970.0, bobOnAlice.clockOffsetMillis);
        assertEquals(SyntheticFight.HITS, bobOnAlice.sharedAnimations);
        assertEquals(SyntheticFight.HITS, bobOnAlice.matched());
        for (int k = 0; k < SyntheticFight.HITS; k++) {
            Fight.Hit hit = bobOnAlice.hits.get(k);
            assertEquals(SyntheticFight.sprintHit(k), hit.sprint, "hit " + k);
            assertEquals(0.0, hit.attackerYaw);
        }
        assertTrue(fight.second.hits.isEmpty());
        assertEquals(fight.second, fight.other(fight.first));
    }

    @Test
    void theStatsFindTheSprintStepTheExtraPushAndTheHitDelay() {
        FightStats stats = new FightStats(new Fight("test", alice(), bob()));

        assertEquals(Long.valueOf(2886), stats.vertical.get("ground, no sprint").mode());
        assertEquals(Long.valueOf(3446), stats.vertical.get("ground, sprint").mode());
        assertEquals(Long.valueOf(560), stats.sprintStep());
        assertEquals(Long.valueOf(400), stats.horizontalWithoutSprint.mode());
        assertEquals(Long.valueOf(500), stats.extraHorizontal.mode());
        assertEquals(3, stats.sprintHitsWithLook);
        assertEquals(3, stats.sprintHitsNearExtraMode);
        // Alice's five intervals, seen by Alice and by Bob.
        assertEquals(10, stats.intervalTotal);
        assertEquals(1.0, stats.intervalShare(425));
    }

    @Test
    void theExtraPushIsSolvedFromTheLook() {
        Fight fight = new Fight("test", alice(), bob());
        Fight.Hit sprintHit = fight.first.hits.get(0);

        double[] roots = FightStats.extraPush(sprintHit, 0.4);

        assertEquals(1, roots.length);
        assertEquals(0.5, roots[0], 1e-9);
    }

    @Test
    void theReportComparesFights() {
        Fight hypixel = new Fight("hypixel", alice(), bob());
        Fight other = new Fight("other", alice(), bob());

        String report = KbAnalyzer.report(Arrays.asList(hypixel, other));

        assertTrue(report.contains("| hypixel | 2886 | 3446 | 560 (0.0700 block/tick) | 0.500 | 100 % | 0 % |"), report);
        assertTrue(report.contains("| ground, sprint | 3 | 3446 (3) |"), report);
        assertTrue(report.contains("### Attacks of Bob that counted"), report);
        assertTrue(report.contains("| 450-474 ms | 5 of 5 (100 %) |"), report);
        assertTrue(KbAnalyzer.report(Collections.singletonList(hypixel)).indexOf("## Comparison") < 0);
    }

    @Test
    void theReportShowsTheServerDomainOnly() {
        assertEquals("hypixel.net", KbAnalyzer.domain("anything.hypixel.net"));
        assertEquals("hypixel.net", KbAnalyzer.domain("MC.Hypixel.net:25565"));
        assertEquals("hypixel.net", KbAnalyzer.domain("hypixel.net."));
        assertEquals("test.local", KbAnalyzer.domain("test.local"));
        assertEquals("localhost", KbAnalyzer.domain("localhost:25565"));
        assertEquals("100.64.0.7", KbAnalyzer.domain("100.64.0.7:25565"));
        assertEquals("-", KbAnalyzer.domain(null));
        String report = KbAnalyzer.report(Collections.singletonList(new Fight("test", alice(), bob())));
        assertTrue(report.contains("| Alice | test.local |"), report);
    }

    @Test
    void sentLookAndSprintPacketsAreDecoded() {
        java.nio.ByteBuffer positionLook = java.nio.ByteBuffer.allocate(33);
        positionLook.putDouble(1.0).putDouble(2.0).putDouble(3.0).putFloat(-91.5F).putFloat(12.0F).put((byte) 0);
        Packet packet = new Packet(0L, false, Packet.OUT_PLAYER_POSITION_LOOK, positionLook.array());
        assertEquals(-91.5F, packet.lookYaw());
        assertTrue(packet.isSentLook());

        Packet action = new Packet(0L, false, Packet.OUT_ENTITY_ACTION, new byte[] {(byte) 0xAC, 0x02, 4, 0});
        assertEquals(Packet.ACTION_STOP_SPRINTING, action.entityAction());
    }
}
