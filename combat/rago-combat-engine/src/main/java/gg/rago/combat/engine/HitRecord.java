package gg.rago.combat.engine;

import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import gg.rago.combat.model.VelocityPacket;

/** Everything the engine knows about one melee hit, for the hit log and the debug messages. */
final class HitRecord {

    final long epochMillis;
    final long tick;
    final EngineMode mode;
    final KnockbackProfile profile;
    final String world;
    final String attacker;
    final String victim;
    final int victimEntityId;
    final boolean sprinting;
    final int knockbackEnchantment;
    final boolean victimOnGround;
    final MeleeHit hit;
    final Vec3 velocity;
    final VelocityPacket packet;
    final boolean applied;

    HitRecord(long epochMillis, long tick, EngineMode mode, KnockbackProfile profile, String world, PendingHit pending,
            Vec3 velocity, boolean applied) {
        this.epochMillis = epochMillis;
        this.tick = tick;
        this.mode = mode;
        this.profile = profile;
        this.world = world;
        this.attacker = pending.attackerName;
        this.victim = pending.victimName;
        this.victimEntityId = pending.victimEntityId;
        this.sprinting = pending.sprinting;
        this.knockbackEnchantment = pending.knockbackEnchantment;
        this.victimOnGround = pending.victimOnGround;
        this.hit = pending.hit;
        this.velocity = velocity;
        this.packet = VelocityPacket.encode(velocity);
        this.applied = applied;
    }

    /**
     * One JSON object on one line. The format is read by tools/kb-control; add fields rather than
     * renaming them. Doubles are written with {@link Double#toString(double)}, which reads back to
     * the exact same value.
     */
    String toJson() {
        Vec3 motion = hit.victimMotion();
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        field(json, "type", "melee").append(',');
        number(json, "time", epochMillis).append(',');
        number(json, "tick", tick).append(',');
        field(json, "mode", mode.configName()).append(',');
        field(json, "profile", profile.name()).append(',');
        json.append("\"profileValues\":{");
        decimal(json, "friction", profile.friction()).append(',');
        decimal(json, "horizontal", profile.horizontal()).append(',');
        decimal(json, "vertical", profile.vertical()).append(',');
        decimal(json, "verticalLimit", profile.verticalLimit()).append(',');
        json.append("\"extraHorizontal\":").append(Float.toString(profile.extraHorizontal())).append(',');
        decimal(json, "extraVertical", profile.extraVertical()).append("},");
        field(json, "world", world).append(',');
        field(json, "attacker", attacker).append(',');
        field(json, "victim", victim).append(',');
        number(json, "victimEntityId", victimEntityId).append(',');
        json.append("\"sprinting\":").append(sprinting).append(',');
        number(json, "knockbackEnchantment", knockbackEnchantment).append(',');
        json.append("\"victimOnGround\":").append(victimOnGround).append(',');
        json.append("\"input\":{");
        decimal(json, "attackerX", hit.attackerX()).append(',');
        decimal(json, "attackerZ", hit.attackerZ()).append(',');
        json.append("\"attackerYaw\":").append(Float.toString(hit.attackerYaw())).append(',');
        number(json, "knockbackLevel", hit.knockbackLevel()).append(',');
        decimal(json, "victimX", hit.victimX()).append(',');
        decimal(json, "victimZ", hit.victimZ()).append(',');
        json.append("\"victimMotion\":[").append(motion.x()).append(',').append(motion.y()).append(',')
                .append(motion.z()).append("]},");
        json.append("\"velocity\":[").append(velocity.x()).append(',').append(velocity.y()).append(',')
                .append(velocity.z()).append("],");
        json.append("\"packet\":[").append(packet.x()).append(',').append(packet.y()).append(',')
                .append(packet.z()).append("],");
        json.append("\"applied\":").append(applied);
        return json.append('}').toString();
    }

    private static StringBuilder field(StringBuilder json, String name, String value) {
        json.append('"').append(name).append("\":\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                json.append('\\').append(c);
            } else if (c < 0x20) {
                json.append(String.format("\\u%04x", (int) c));
            } else {
                json.append(c);
            }
        }
        return json.append('"');
    }

    private static StringBuilder number(StringBuilder json, String name, long value) {
        return json.append('"').append(name).append("\":").append(value);
    }

    private static StringBuilder decimal(StringBuilder json, String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " is not finite: " + value);
        }
        return json.append('"').append(name).append("\":").append(value);
    }
}
