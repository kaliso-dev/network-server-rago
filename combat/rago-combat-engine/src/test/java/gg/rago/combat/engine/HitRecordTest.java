package gg.rago.combat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.combat.model.KnockbackCalculator;
import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HitRecordTest {

    @Test
    void jsonCarriesExactInputsAndOutputs() {
        Vec3 motion = new Vec3(0.1D / 3.0D, (0.0D - 0.08D) * 0.9800000190734863D, -1.0E-7D);
        MeleeHit hit = new MeleeHit(10.123456789012345D, -4.5D, 37.25F, 1, 11.0D, -2.25D, motion);
        Vec3 live = new Vec3(0.0D, 0.25D, 0.0D);
        PendingHit pending = new PendingHit(UUID.randomUUID(), 42L, "Att\"acker", "Victim", 7, true, 0, true, hit, live,
                3);
        KnockbackProfile profile = KnockbackProfile.of("test-a", 2.0D, 0.35D, 0.35D, 0.4D, 0.425D, 0.1D);
        Vec3 velocity = new KnockbackCalculator().compute(profile, hit);

        String json = new HitRecord(1_000L, 42L, EngineMode.OVERRIDE, profile, "arena", pending, velocity, true, 150)
                .toJson();
        JsonObject object = parse(json);

        assertTrue(!json.contains("\n"));
        assertEquals("melee", object.get("type").getAsString());
        assertEquals("Att\"acker", object.get("attacker").getAsString());
        assertEquals("override", object.get("mode").getAsString());
        assertEquals(7, object.get("victimEntityId").getAsInt());
        assertEquals(0.425F, (float) object.getAsJsonObject("profileValues").get("extraHorizontal").getAsDouble());
        JsonObject input = object.getAsJsonObject("input");
        assertEquals(10.123456789012345D, input.get("attackerX").getAsDouble());
        assertEquals(37.25F, (float) input.get("attackerYaw").getAsDouble());
        JsonArray victimMotion = input.getAsJsonArray("victimMotion");
        assertEquals(motion.x(), victimMotion.get(0).getAsDouble());
        assertEquals(motion.z(), victimMotion.get(2).getAsDouble());
        JsonArray outVelocity = object.getAsJsonArray("velocity");
        assertEquals(velocity.y(), outVelocity.get(1).getAsDouble());
        assertTrue(object.get("applied").getAsBoolean());
        assertEquals(150, object.get("pingSimulationMs").getAsInt());
        assertEquals(3, object.get("simulatedDelayTicks").getAsInt());
        assertEquals(0.25D, object.getAsJsonArray("liveVictimMotion").get(1).getAsDouble());
    }

    /**
     * The exact line tools/kb-control reads in its LogFormatTest. If this test fails, the log format
     * changed: update the control tool and its sample line in the same change.
     */
    @Test
    void writesTheLineTheControlToolReads() {
        Vec3 motion = new Vec3(0.1D / 3.0D, (0.0D - 0.08D) * 0.9800000190734863D, -1.0E-7D);
        MeleeHit hit = new MeleeHit(10.123456789012345D, -4.5D, 37.25F, 2, 11.0D, -2.25D, motion);
        PendingHit pending = new PendingHit(new UUID(1L, 2L), 42L, "RagoAttacker", "RagoVictim", 7, true, 1, false,
                hit, motion, 0);
        KnockbackProfile profile = KnockbackProfile.of("control-alt", 1.7D, 0.36D, 0.37D, 0.39D, 0.45D, 0.085D);
        Vec3 velocity = new KnockbackCalculator().compute(profile, hit);

        String json = new HitRecord(1700000000123L, 42L, EngineMode.OVERRIDE, profile, "kb-control", pending,
                velocity, true, 0).toJson();

        assertEquals("{\"type\":\"melee\",\"time\":1700000000123,\"tick\":42,\"mode\":\"override\","
                + "\"profile\":\"control-alt\",\"profileValues\":{\"friction\":1.7,\"horizontal\":0.36,\"vertical\":0.37,"
                + "\"verticalLimit\":0.39,\"extraHorizontal\":0.45,\"extraVertical\":0.085},\"world\":\"kb-control\","
                + "\"attacker\":\"RagoAttacker\",\"victim\":\"RagoVictim\",\"victimEntityId\":7,\"sprinting\":true,"
                + "\"knockbackEnchantment\":1,\"victimOnGround\":false,\"input\":{\"attackerX\":10.123456789012344,"
                + "\"attackerZ\":-4.5,\"attackerYaw\":37.25,\"knockbackLevel\":2,\"victimX\":11.0,\"victimZ\":-2.25,"
                + "\"victimMotion\":[0.03333333333333333,-0.0784000015258789,-1.0E-7]},"
                + "\"velocity\":[-0.3944655881428242,0.4088823520436006,1.0518537649986386],"
                + "\"packet\":[-3155,3271,8414],\"applied\":true,\"pingSimulationMs\":0,\"simulatedDelayTicks\":0,"
                + "\"liveVictimMotion\":[0.03333333333333333,-0.0784000015258789,-1.0E-7]}", json);
    }

    @SuppressWarnings("deprecation")
    private static JsonObject parse(String json) {
        // JsonParser's instance method is the only one available in the Gson bundled with 1.8.8.
        return new JsonParser().parse(json).getAsJsonObject();
    }
}
