package gg.rago.tools.kbanalyzer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A tick line: what the client knew at the end of one of its ticks. Fields missing from the line
 * are NaN (numbers) or false (flags), so that older or partial lines still load.
 */
public final class Tick {

    public final long ms;
    public final int tick;
    /** The player's entity id. It changes when the player moves to another server. */
    public final int entityId;
    public final double x;
    public final double y;
    public final double z;
    public final double motionX;
    public final double motionY;
    public final double motionZ;
    public final double yaw;
    public final boolean ground;
    public final boolean sprint;
    /** The player's ping in the tab list, or -1 when the line has none. */
    public final int ping;
    /** The players closer than 16 blocks. */
    public final List<Nearby> players;

    public Tick(long ms, int tick, int entityId, double x, double y, double z, double motionX, double motionY,
            double motionZ, double yaw, boolean ground, boolean sprint, int ping, List<Nearby> players) {
        this.ms = ms;
        this.tick = tick;
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.yaw = yaw;
        this.ground = ground;
        this.sprint = sprint;
        this.ping = ping;
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
    }

    static Tick fromJson(JsonObject json) {
        List<Nearby> players = new ArrayList<>();
        JsonArray array = json.has("players") && json.get("players").isJsonArray()
                ? json.getAsJsonArray("players") : new JsonArray();
        for (JsonElement element : array) {
            players.add(Nearby.fromJson(element.getAsJsonObject()));
        }
        double ping = Json.number(json, "ping");
        return new Tick(json.get("ms").getAsLong(), json.get("tick").getAsInt(), json.get("eid").getAsInt(),
                Json.number(json, "x"), Json.number(json, "y"), Json.number(json, "z"),
                Json.number(json, "mx"), Json.number(json, "my"), Json.number(json, "mz"),
                Json.number(json, "yaw"), Json.flag(json, "ground"), Json.flag(json, "sprint"),
                Double.isNaN(ping) ? -1 : (int) ping, players);
    }

    /** The sprint flag of the named player in this tick, or null when the player is not around. */
    public Boolean sprintOf(String name) {
        Nearby other = player(name);
        return other == null ? null : other.sprint;
    }

    /** The named player, or null when not around. */
    public Nearby player(String name) {
        for (Nearby other : players) {
            if (other.name != null && other.name.equals(name)) {
                return other;
            }
        }
        return null;
    }

    /** The player with this entity id, or null when not around. */
    public Nearby playerWithEntity(int entity) {
        for (Nearby other : players) {
            if (other.entityId == entity) {
                return other;
            }
        }
        return null;
    }

    /** Horizontal distance from the player to another one. */
    public double horizontalDistanceTo(Nearby other) {
        double dx = other.x - x;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Distance from the player's feet to another player's feet. */
    public double distanceTo(Nearby other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** A player close to the recording player, as seen by the client. */
    public static final class Nearby {
        public final int entityId;
        public final String name;
        public final double x;
        public final double y;
        public final double z;
        public final double yaw;
        public final boolean ground;
        public final boolean sprint;
        public final boolean swing;
        public final int hurt;

        public Nearby(int entityId, String name, double x, double y, double z, double yaw, boolean ground,
                boolean sprint, boolean swing, int hurt) {
            this.entityId = entityId;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.ground = ground;
            this.sprint = sprint;
            this.swing = swing;
            this.hurt = hurt;
        }

        static Nearby fromJson(JsonObject json) {
            double hurt = Json.number(json, "hurt");
            return new Nearby(json.get("eid").getAsInt(), Json.text(json, "name"),
                    Json.number(json, "x"), Json.number(json, "y"), Json.number(json, "z"),
                    Json.number(json, "yaw"), Json.flag(json, "ground"), Json.flag(json, "sprint"),
                    Json.flag(json, "swing"), Double.isNaN(hurt) ? 0 : (int) hurt);
        }
    }
}
