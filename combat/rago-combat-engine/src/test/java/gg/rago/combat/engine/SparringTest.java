package gg.rago.combat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.combat.model.KnockbackCalculator;
import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import org.junit.jupiter.api.Test;

class SparringTest {

    /** The server-side motion of a player standing still on the ground. */
    private static final Vec3 STANDING = new Vec3(0.0, -0.0784000015258789, 0.0);

    private final KnockbackCalculator calculator = new KnockbackCalculator(() -> {
        throw new AssertionError("the attacker never stands on the victim, no random direction is drawn");
    });

    @Test
    void theAttackerStandsInFrontAndFacesTheVictim() {
        // Yaw 0 looks south (+Z) in Minecraft.
        MeleeHit south = Sparring.hit(10.5, -4.5, 0f, STANDING, 0);
        assertEquals(10.5, south.attackerX(), 1e-9);
        assertEquals(-4.5 + Sparring.ATTACKER_DISTANCE, south.attackerZ(), 1e-9);
        assertEquals(-180f, south.attackerYaw(), 1e-4f);

        // Yaw 90 looks west (-X).
        MeleeHit west = Sparring.hit(0.0, 0.0, 90f, STANDING, 0);
        assertEquals(-Sparring.ATTACKER_DISTANCE, west.attackerX(), 1e-9);
        assertEquals(0.0, west.attackerZ(), 1e-9);
        assertEquals(-90f, west.attackerYaw(), 1e-4f);
        assertEquals(0.0, west.victimX());
        assertEquals(STANDING, west.victimMotion());
    }

    @Test
    void theVictimIsPushedStraightBackLikeAFaceToFaceHit() {
        KnockbackProfile vanilla = KnockbackProfile.vanilla();
        Vec3 normal = calculator.compute(vanilla, Sparring.hit(0.0, 0.0, 0f, STANDING, 0));
        Vec3 sprint = calculator.compute(vanilla, Sparring.hit(0.0, 0.0, 0f, STANDING, 1));

        // Looking south, pushed north (-Z), not sideways.
        assertEquals(0.0, normal.x(), 1e-6);
        assertEquals(-0.4, normal.z(), 1e-6);
        assertEquals(0.3608, normal.y(), 1e-4);
        assertEquals(0.0, sprint.x(), 1e-6);
        assertEquals(-0.9, sprint.z(), 1e-6);
        assertEquals(0.4608, sprint.y(), 1e-4);
    }

    @Test
    void patternsSayWhichHitsSprint() {
        assertTrue(Sparring.Pattern.SPRINT.sprints(0));
        assertTrue(Sparring.Pattern.SPRINT.sprints(1));
        assertFalse(Sparring.Pattern.NORMAL.sprints(0));
        assertTrue(Sparring.Pattern.ALTERNE.sprints(0));
        assertFalse(Sparring.Pattern.ALTERNE.sprints(1));
        assertTrue(Sparring.Pattern.ALTERNE.sprints(2));
        assertEquals(Sparring.Pattern.ALTERNE, Sparring.Pattern.parse("Alterne"));
        assertNull(Sparring.Pattern.parse("combo"));
    }

    @Test
    void intervalsStayWhereARealFightCanBe() {
        assertEquals(Integer.valueOf(9), Sparring.parseInterval("9"));
        assertEquals(Integer.valueOf(10), Sparring.parseInterval("10"));
        assertEquals(Integer.valueOf(200), Sparring.parseInterval("200"));
        assertNull(Sparring.parseInterval("8"));
        assertNull(Sparring.parseInterval("201"));
        assertNull(Sparring.parseInterval("vite"));
    }

    @Test
    void yawsAreWrappedLikeMinecraftKeepsThem() {
        assertEquals(-180f, Sparring.wrapDegrees(180f), 1e-4f);
        assertEquals(170f, Sparring.wrapDegrees(-190f), 1e-4f);
        assertEquals(-90f, Sparring.wrapDegrees(270f), 1e-4f);
        assertEquals(0f, Sparring.wrapDegrees(360f), 1e-4f);
    }
}
