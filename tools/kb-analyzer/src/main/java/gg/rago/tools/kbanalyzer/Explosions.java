package gg.rago.tools.kbanalyzer;

import gg.rago.combat.model.VelocityPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The explosions of one recording (kb-recorder 0.2 and later), each with what caused it, the
 * blocks it broke and the ones it left, and how it pushed the player. Everything comes from the
 * packets the client received and the blocks it saw: the server's own rules are not assumed.
 */
public final class Explosions {

    /** A primed TNT or fireball is the source of an explosion when it is destroyed this close in time. */
    public static final long SOURCE_WINDOW_MILLIS = 150L;
    /**
     * A velocity packet for the player this soon after an explosion is counted as its push, for a
     * server that sends its own push instead of the explosion's.
     */
    public static final long PUSH_WINDOW_MILLIS = 150L;
    /** A right click this long before a primed TNT or fireball appears is the throw or the placing. */
    static final long THROW_WINDOW_MILLIS = 1000L;
    /** Block changes this long after an explosion, inside the cube read around it, are its own. */
    static final long BLOCK_CHANGE_WINDOW_MILLIS = 500L;
    /** Ticks after an explosion in which the player's flight is followed. */
    static final int FLIGHT_TICKS = 100;
    /** Height of a standing player's eyes above the feet. */
    static final double EYE_HEIGHT = 1.62;

    private Explosions() {
    }

    /** A primed TNT or a fireball, from its spawn to its destruction. */
    public static final class Projectile {
        public final Packet.SpawnedObject spawn;
        public final long spawnMs;
        /** The right click before the spawn, or null. */
        public final Long useMs;
        /** The item of that right click, or -1. */
        public final int useItem;
        /** The last position the server gave, and when. */
        public double x;
        public double y;
        public double z;
        public long positionMs;
        public int moves;
        public Long destroyedMs;

        Projectile(Packet.SpawnedObject spawn, long spawnMs, Long useMs, int useItem) {
            this.spawn = spawn;
            this.spawnMs = spawnMs;
            this.useMs = useMs;
            this.useItem = useItem;
            this.x = spawn.x;
            this.y = spawn.y;
            this.z = spawn.z;
            this.positionMs = spawnMs;
        }

        public String kind() {
            switch (spawn.type) {
                case Packet.OBJECT_PRIMED_TNT:
                    return "TNT";
                case Packet.OBJECT_FIREBALL:
                    return "fireball";
                case Packet.OBJECT_SMALL_FIREBALL:
                    return "small fireball";
                default:
                    return "object " + spawn.type;
            }
        }

        /** Average speed between the spawn and the last position the server gave, in blocks per tick; NaN without a move. */
        public double speedPerTick() {
            if (moves == 0 || positionMs <= spawnMs) {
                return Double.NaN;
            }
            double dx = x - spawn.x;
            double dy = y - spawn.y;
            double dz = z - spawn.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz) / ((positionMs - spawnMs) / 50.0);
        }
    }

    /** One explosion and everything recorded around it. */
    public static final class Explosion {
        public final long ms;
        public final Packet.ExplosionPacket packet;
        public final String mark;
        public Projectile source;
        /** The blocks around the explosion just before it, or null (recordings before 0.2). */
        public BlockSnapshot before;
        /** Block changes that followed, inside the cube read around it, as {@code [x, y, z, id, meta]}. */
        public final List<int[]> changes = new ArrayList<>();
        /** A velocity packet for the player right after the explosion, or null. */
        public VelocityPacket velocity;
        public long velocityDelayMs;
        /** The player at the explosion (the last tick before it), or null. */
        public Tick player;
        /** The player's flight after the explosion: highest rise, landing, ticks in the air. */
        public double rise = Double.NaN;
        public double landingDistance = Double.NaN;
        public int airTicks;

        Explosion(long ms, Packet.ExplosionPacket packet, String mark) {
            this.ms = ms;
            this.packet = packet;
            this.mark = mark;
        }

        /** Distance from the centre to the player's feet, or NaN. */
        public double feetDistance() {
            return player == null ? Double.NaN : distance(player.x, player.y, player.z);
        }

        /** Distance from the centre to the player's eyes, or NaN. */
        public double eyeDistance() {
            return player == null ? Double.NaN : distance(player.x, player.y + EYE_HEIGHT, player.z);
        }

        private double distance(double px, double py, double pz) {
            double dx = px - packet.x;
            double dy = py - packet.y;
            double dz = pz - packet.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** The push in blocks per tick: the explosion's own, else the velocity packet's; null when none. */
        public double[] push() {
            double[] own = packet.push;
            if (own[0] != 0 || own[1] != 0 || own[2] != 0) {
                return own;
            }
            if (velocity != null) {
                return new double[] {velocity.x() / VelocityPacket.UNITS_PER_BLOCK,
                        velocity.y() / VelocityPacket.UNITS_PER_BLOCK, velocity.z() / VelocityPacket.UNITS_PER_BLOCK};
            }
            return null;
        }

        /**
         * Angle in degrees between the horizontal push and the horizontal line from the centre to
         * the player: 0 when the player is pushed straight away from the explosion. NaN when unknown.
         */
        public double pushAngle() {
            double[] push = push();
            if (push == null || player == null) {
                return Double.NaN;
            }
            double awayX = player.x - packet.x;
            double awayZ = player.z - packet.z;
            double pushLength = Math.hypot(push[0], push[2]);
            double awayLength = Math.hypot(awayX, awayZ);
            if (pushLength < 1e-9 || awayLength < 1e-9) {
                return Double.NaN;
            }
            double cos = (push[0] * awayX + push[2] * awayZ) / (pushLength * awayLength);
            return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
        }

        /** The block the explosion broke at a position, from the blocks read before it, or null. */
        public int[] brokenBlock(int[] position) {
            return before == null ? null : before.at(position[0], position[1], position[2]);
        }
    }

    /** Finds the explosions of a recording, in time order. */
    public static List<Explosion> find(Recording recording) {
        List<Packet> packets = recording.packetsByTime();
        List<Explosion> explosions = new ArrayList<>();
        List<Projectile> projectiles = new ArrayList<>();
        Map<Integer, Projectile> alive = new HashMap<>();
        Long useMs = null;
        int useItem = -1;
        for (Packet packet : packets) {
            try {
                if (!packet.inbound) {
                    if (packet.id == Packet.OUT_BLOCK_PLACEMENT) {
                        useMs = packet.ms;
                        useItem = packet.placedItem();
                    }
                    continue;
                }
                switch (packet.id) {
                    case Packet.IN_JOIN_GAME:
                    case Packet.IN_RESPAWN:
                        alive.clear();
                        break;
                    case Packet.IN_SPAWN_OBJECT:
                        Packet.SpawnedObject spawn = packet.spawnedObject();
                        boolean thrown = useMs != null && packet.ms - useMs <= THROW_WINDOW_MILLIS;
                        Projectile projectile = new Projectile(spawn, packet.ms, thrown ? useMs : null,
                                thrown ? useItem : -1);
                        projectiles.add(projectile);
                        alive.put(spawn.entity, projectile);
                        if (thrown) {
                            useMs = null;
                        }
                        break;
                    case Packet.IN_ENTITY_RELATIVE_MOVE:
                    case Packet.IN_ENTITY_LOOK_RELATIVE_MOVE:
                        Projectile moved = alive.get(packet.movedEntity());
                        if (moved != null) {
                            double[] move = packet.relativeMove();
                            moved.x += move[0];
                            moved.y += move[1];
                            moved.z += move[2];
                            moved.positionMs = packet.ms;
                            moved.moves++;
                        }
                        break;
                    case Packet.IN_ENTITY_TELEPORT:
                        Projectile teleported = alive.get(packet.movedEntity());
                        if (teleported != null) {
                            double[] position = packet.teleport();
                            teleported.x = position[0];
                            teleported.y = position[1];
                            teleported.z = position[2];
                            teleported.positionMs = packet.ms;
                            teleported.moves++;
                        }
                        break;
                    case Packet.IN_DESTROY_ENTITIES:
                        for (int id : packet.destroyedEntities()) {
                            Projectile destroyed = alive.remove(id);
                            if (destroyed != null) {
                                destroyed.destroyedMs = packet.ms;
                            }
                        }
                        break;
                    case Packet.IN_EXPLOSION:
                        explosions.add(new Explosion(packet.ms, packet.explosion(), recording.markAt(packet.ms)));
                        break;
                    default:
                        break;
                }
            } catch (IllegalArgumentException e) {
                recording.problems.add("packet " + packet.id + " at " + packet.ms + " cannot be decoded: " + e.getMessage());
            }
        }
        for (Explosion explosion : explosions) {
            explosion.source = source(explosion, projectiles);
            explosion.before = snapshot(explosion, recording.blockSnapshots);
            explosion.player = recording.tickAtOrBefore(explosion.ms);
            followUp(explosion, packets, recording);
        }
        return explosions;
    }

    /** The primed TNT or fireball destroyed closest in time to the explosion, within the window. */
    private static Projectile source(Explosion explosion, List<Projectile> projectiles) {
        Projectile best = null;
        for (Projectile projectile : projectiles) {
            if (projectile.destroyedMs == null || projectile.spawnMs > explosion.ms) {
                continue;
            }
            long gap = Math.abs(projectile.destroyedMs - explosion.ms);
            if (gap <= SOURCE_WINDOW_MILLIS && (best == null || gap < Math.abs(best.destroyedMs - explosion.ms))) {
                best = projectile;
            }
        }
        return best;
    }

    private static BlockSnapshot snapshot(Explosion explosion, List<BlockSnapshot> snapshots) {
        BlockSnapshot best = null;
        for (BlockSnapshot snapshot : snapshots) {
            if (snapshot.isFor(explosion.packet.x, explosion.packet.y, explosion.packet.z)
                    && (best == null || Math.abs(snapshot.ms - explosion.ms) < Math.abs(best.ms - explosion.ms))) {
                best = snapshot;
            }
        }
        return best;
    }

    private static void followUp(Explosion explosion, List<Packet> packets, Recording recording) {
        int reach = explosion.before != null ? explosion.before.reach
                : (int) Math.ceil(vanillaReach(explosion.packet.strength)) + 1;
        int centreX = (int) Math.floor(explosion.packet.x);
        int centreY = (int) Math.floor(explosion.packet.y);
        int centreZ = (int) Math.floor(explosion.packet.z);
        Integer player = explosion.player == null ? null : explosion.player.entityId;
        for (Packet packet : packets) {
            if (packet.ms < explosion.ms || !packet.inbound) {
                continue;
            }
            long delay = packet.ms - explosion.ms;
            if (delay > Math.max(BLOCK_CHANGE_WINDOW_MILLIS, PUSH_WINDOW_MILLIS)) {
                break;
            }
            try {
                if ((packet.id == Packet.IN_BLOCK_CHANGE || packet.id == Packet.IN_MULTI_BLOCK_CHANGE)
                        && delay <= BLOCK_CHANGE_WINDOW_MILLIS) {
                    for (int[] change : packet.blockChanges()) {
                        if (Math.abs(change[0] - centreX) <= reach && Math.abs(change[1] - centreY) <= reach
                                && Math.abs(change[2] - centreZ) <= reach) {
                            explosion.changes.add(change);
                        }
                    }
                } else if (packet.id == Packet.IN_ENTITY_VELOCITY && delay <= PUSH_WINDOW_MILLIS
                        && explosion.velocity == null && player != null && packet.velocityEntity() == player) {
                    explosion.velocity = packet.velocity();
                    explosion.velocityDelayMs = delay;
                }
            } catch (IllegalArgumentException e) {
                // Already listed in the recording's problems by find().
            }
        }
        flight(explosion, recording);
    }

    /**
     * Follows the player from the explosion: the highest point above where they stood, and where
     * they landed, once they left the ground within the first ticks.
     */
    private static void flight(Explosion explosion, Recording recording) {
        Tick start = explosion.player;
        if (start == null) {
            return;
        }
        List<Tick> ticks = recording.ticks;
        int index = ticks.indexOf(start);
        boolean airborne = false;
        double highest = start.y;
        for (int i = index + 1; i < ticks.size() && i <= index + FLIGHT_TICKS; i++) {
            Tick tick = ticks.get(i);
            if (tick.entityId != start.entityId) {
                return;
            }
            if (!tick.ground) {
                airborne = true;
                explosion.airTicks++;
                highest = Math.max(highest, tick.y);
            } else if (airborne) {
                explosion.rise = highest - start.y;
                explosion.landingDistance = Math.hypot(tick.x - start.x, tick.z - start.z);
                return;
            } else if (i - index > 10) {
                // Still on the ground: the explosion did not lift the player.
                return;
            }
        }
        if (airborne) {
            explosion.rise = highest - start.y;
        }
    }

    /** Farthest a vanilla explosion's rays can reach: 1.3 times the strength, 0.225 lost every 0.3 block. */
    public static double vanillaReach(double strength) {
        return strength * 1.3 / 0.225 * 0.3;
    }

    /** Names of the blocks met in Bed Wars, by block id; others are written as their id. */
    static String blockName(int id, int meta) {
        switch (id) {
            case 1: return "stone";
            case 2: return "grass";
            case 3: return "dirt";
            case 4: return "cobblestone";
            case 5: return "planks";
            case 7: return "bedrock";
            case 12: return "sand";
            case 17: return "log";
            case 20: return "glass";
            case 24: return "sandstone";
            case 26: return "bed";
            case 35: return "wool";
            case 44: return "slab";
            case 46: return "TNT";
            case 49: return "obsidian";
            case 54: return "chest";
            case 65: return "ladder";
            case 85: return "fence";
            case 95: return "stained glass";
            case 102: return "glass pane";
            case 121: return "end stone";
            case 126: return "wood slab";
            case 130: return "ender chest";
            case 159: return "stained clay";
            case 160: return "stained glass pane";
            case 162: return "log";
            case 172: return "hardened clay";
            default: return "block " + id + (meta != 0 ? ":" + meta : "");
        }
    }
}
