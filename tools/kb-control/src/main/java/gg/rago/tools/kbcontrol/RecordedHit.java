package gg.rago.tools.kbcontrol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import gg.rago.combat.model.VelocityPacket;

/**
 * One line of the engine's hit log (hits-YYYY-MM-DD.jsonl, written by HitRecord in
 * rago-combat-engine). Floats are read with {@code getAsFloat}, which parses the written text
 * directly as a float, so every value comes back exactly as the server had it.
 */
final class RecordedHit {

    final long time;
    final String mode;
    final KnockbackProfile profile;
    final int victimEntityId;
    final boolean sprinting;
    final boolean victimOnGround;
    final MeleeHit hit;
    final Vec3 velocity;
    final VelocityPacket packet;
    final boolean applied;
    /** The ping simulation setting, 0 when off or in a log written before it existed. */
    final int pingSimulationMs;
    /** How long the engine held the knockback packet back, in ms (ping simulation), else 0. */
    final int knockbackDelayMs;

    RecordedHit(long time, String mode, KnockbackProfile profile, int victimEntityId, boolean sprinting,
            boolean victimOnGround, MeleeHit hit, Vec3 velocity, VelocityPacket packet, boolean applied) {
        this(time, mode, profile, victimEntityId, sprinting, victimOnGround, hit, velocity, packet, applied, 0, 0);
    }

    RecordedHit(long time, String mode, KnockbackProfile profile, int victimEntityId, boolean sprinting,
            boolean victimOnGround, MeleeHit hit, Vec3 velocity, VelocityPacket packet, boolean applied,
            int pingSimulationMs, int knockbackDelayMs) {
        this.time = time;
        this.mode = mode;
        this.profile = profile;
        this.victimEntityId = victimEntityId;
        this.sprinting = sprinting;
        this.victimOnGround = victimOnGround;
        this.hit = hit;
        this.velocity = velocity;
        this.packet = packet;
        this.applied = applied;
        this.pingSimulationMs = pingSimulationMs;
        this.knockbackDelayMs = knockbackDelayMs;
    }

    static RecordedHit fromJson(JsonObject json) {
        JsonObject values = json.getAsJsonObject("profileValues");
        KnockbackProfile profile = KnockbackProfile.of(
                json.get("profile").getAsString(),
                values.get("friction").getAsDouble(),
                values.get("horizontal").getAsDouble(),
                values.get("vertical").getAsDouble(),
                values.get("verticalLimit").getAsDouble(),
                values.get("extraHorizontal").getAsFloat(),
                values.get("extraVertical").getAsDouble());
        JsonObject input = json.getAsJsonObject("input");
        MeleeHit hit = new MeleeHit(
                input.get("attackerX").getAsDouble(),
                input.get("attackerZ").getAsDouble(),
                input.get("attackerYaw").getAsFloat(),
                input.get("knockbackLevel").getAsInt(),
                input.get("victimX").getAsDouble(),
                input.get("victimZ").getAsDouble(),
                vec(input.getAsJsonArray("victimMotion")));
        JsonArray packet = json.getAsJsonArray("packet");
        return new RecordedHit(
                json.get("time").getAsLong(),
                json.get("mode").getAsString(),
                profile,
                json.get("victimEntityId").getAsInt(),
                json.get("sprinting").getAsBoolean(),
                json.get("victimOnGround").getAsBoolean(),
                hit,
                vec(json.getAsJsonArray("velocity")),
                new VelocityPacket(packet.get(0).getAsInt(), packet.get(1).getAsInt(), packet.get(2).getAsInt()),
                json.get("applied").getAsBoolean(),
                optionalInt(json, "pingSimulationMs"),
                optionalInt(json, "knockbackDelayMs"));
    }

    private static int optionalInt(JsonObject json, String name) {
        return json.has(name) ? json.get(name).getAsInt() : 0;
    }

    private static Vec3 vec(JsonArray array) {
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    /** Category used in the report, for example "override / vanilla / sprint". */
    String category() {
        return mode + " / " + profile.name() + " / " + (sprinting ? "sprint" : "no sprint");
    }

    @Override
    public String toString() {
        return category() + " at " + time + " (victim entity " + victimEntityId + ", " + hit + ")";
    }
}
