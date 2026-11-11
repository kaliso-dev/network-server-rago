package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * KB displace as in a real Bed Fight top fight: a bridge one block wide, and the defender on the
 * top of the defense, one block below the bridge, the bridge in front of them and a block behind
 * them, nothing on the sides. The attacker runs along the bridge and, in the tick of the hit,
 * turns their head to one side: the knockback of the sprint sends the defender sideways, off the
 * top. Only a hit from the front of the defender counts (from the bridge, never from a side or
 * from behind), with the look on the defender: a hit with the head turned away from them (which
 * only a cheat lands) does not count. Invisible walls (barriers) line both sides of the bridge in
 * front of the defender: the attacker stays on it, in its middle, and cannot hit from a side. Each hit is one attempt: a displaced defender is a point for the attacker, and
 * the roles swap at each hit (or after {@link #ATTEMPT_SECONDS} seconds without one). The
 * defender stays in their notch, no damage, no blocks. First to the target. The practice against
 * a bot is {@link KbDisplaceBot}. Both play on the owner's map when the server has it (the
 * attacker on its gold block, the defender in the notch between its two raised blocks, see
 * {@link Place}), else on the plugin's own bridge.
 */
final class KbDisplace extends Duel {

    /** The attacker starts this far along the bridge from the defender: a run to get the momentum. */
    static final int RUN = 10;
    /** The bridge goes on this far behind the defender. */
    static final int BEHIND = 4;
    /** After a hit, the defender has this long to fall; past it, they held. */
    static final int KNOCK_TICKS = 30;
    /**
     * Once back on the ground this long after a hit (the knockback is over), the defender held at
     * once, without waiting for the end of {@link #KNOCK_TICKS}.
     */
    static final int SETTLE_TICKS = 10;
    /** An attempt without a hit ends after this long. */
    static final int ATTEMPT_SECONDS = 15;
    /** The invisible walls along the bridge are this high: a jump does not clear them. */
    static final int WALL_HEIGHT = 3;

    /**
     * Where the duel and the drill play: the plugin's own bridge, or a copy of the owner's map with
     * the defender's notch and the attacker's start on it. Offsets from the centre of the zone.
     */
    static final class Place {
        /** The plugin's own bridge: along x, the defender at x = 0, one block below it. */
        static final Place PLAIN = new Place(null, new double[] {-RUN + 0.5, 1, 0.5, -90}, new double[] {0.5, 0, 0.5, 90});

        /** The copy of the map, or null: the plugin's own bridge. */
        final MapCopy copy;
        /** x, y, z of the feet and yaw: where the attacker starts their run. */
        final double[] attacker;
        /** x, y, z of the feet and yaw: the defender's notch. */
        final double[] defender;
        /** The front of the defender, from them towards the attacker's start, on x and z, length 1. */
        final double[] front;

        Place(MapCopy copy, double[] attacker, double[] defender) {
            this.copy = copy;
            this.attacker = attacker;
            this.defender = defender;
            double dx = attacker[0] - defender[0];
            double dz = attacker[2] - defender[2];
            double length = Math.sqrt(dx * dx + dz * dz);
            this.front = length < 0.01 ? new double[] {-1, 0} : new double[] {dx / length, dz / length};
        }

        void build(Arena arena) {
            if (copy != null) {
                copy.build(arena);
            } else {
                bridge(arena);
            }
            for (int[] wall : walls()) {
                if (arena.block(wall[0], wall[1], wall[2]).getType() == Material.AIR) {
                    arena.build(wall[0], wall[1], wall[2], Material.BARRIER);
                }
            }
        }

        /**
         * The blocks of the invisible walls, offsets from the centre: on both sides of the bridge,
         * from the block in front of the defender to just behind the attacker's start,
         * {@value #WALL_HEIGHT} high from the attacker's feet, so that the attacker runs in the
         * middle of the bridge and never reaches a side of the defender.
         */
        List<int[]> walls() {
            List<int[]> walls = new ArrayList<>();
            double dx = attacker[0] - defender[0];
            double dz = attacker[2] - defender[2];
            int length = (int) Math.round(Math.sqrt(dx * dx + dz * dz)) + 2;
            int feet = (int) Math.floor(attacker[1] + 0.01);
            for (int t = 1; t <= length; t++) {
                double x = defender[0] + front[0] * t;
                double z = defender[2] + front[1] * t;
                for (int side = -1; side <= 1; side += 2) {
                    int bx = (int) Math.floor(x - front[1] * side);
                    int bz = (int) Math.floor(z + front[0] * side);
                    for (int y = 0; y < WALL_HEIGHT; y++) {
                        walls.add(new int[] {bx, feet + y, bz});
                    }
                }
            }
            return walls;
        }

        Location attackerStart(Arena arena) {
            return arena.at(attacker[0], attacker[1], attacker[2], (float) attacker[3]);
        }

        Location defenderSpot(Arena arena) {
            return arena.at(defender[0], defender[1], defender[2], (float) defender[3]);
        }

        /** True if the defender, their feet at this height from the centre, fell off the top. */
        boolean offTheTop(double feet) {
            return feet < defender[1] - 0.9;
        }

        /**
         * True if a hit from this offset of the attacker to the defender comes from the front: from
         * the bridge the attacker runs along, in its line, not from a side or from behind.
         */
        boolean fromTheFront(double dx, double dz) {
            double along = dx * front[0] + dz * front[1];
            double across = -dx * front[1] + dz * front[0];
            return along >= 0.4 && Math.abs(across) <= 0.9;
        }

        /** True if a defender who was not hit left their notch (a step, a jump): they are put back. */
        boolean outOfTheNotch(double x, double y, double z) {
            return Math.abs(x - defender[0]) > 0.45 || Math.abs(z - defender[2]) > 0.45 || y > defender[1] + 0.6;
        }

        /** Under this height a player fell into the void. */
        double fallY(Arena arena) {
            return arena.floorY() + defender[1] - 6;
        }
    }

    private final Place place;
    /** The tick until which the hit defender flies, or -1 before the hit of this attempt. */
    private int knockedUntil = -1;
    /** The tick of the last hit. */
    private int knockedAt;
    private long warnedAt;

    KbDisplace(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target, Place place) {
        super(host, mode, arena, teams, target);
        this.place = place;
    }

    @Override
    String title() {
        return "KB displace";
    }

    @Override
    void intro(Player player) {
        super.intro(player);
        player.sendMessage(ChatColor.GRAY + "The defender holds their place on the bridge, nothing on their sides."
                + " Run, turn your head to the side and hit them in the same tick: they go off sideways."
                + " Only a hit from the front, with your look on them, counts: invisible walls keep you on the bridge."
                + " The roles swap at each hit.");
    }

    // --- The place, shared with the bot -----------------------------------------------------------

    /**
     * The bridge along x at z = 0, its blocks at the height of the floor; the defender's notch at
     * x = 0, one block lower; a pad at the attacker's start; under the notch and behind it, the
     * end stone of a defense.
     */
    static void bridge(Arena arena) {
        for (int x = -RUN - 1; x <= BEHIND; x++) {
            if (x != 0) {
                arena.build(x, 0, 0, Material.WOOL, Shapes.WHITE);
            }
        }
        Shapes.floor(arena, -RUN - 4, -1, -RUN - 2, 1, 0, Material.WOOL, Shapes.WHITE);
        arena.build(0, -1, 0, Material.ENDER_STONE);
        for (int x = 1; x <= BEHIND; x++) {
            arena.build(x, -1, 0, Material.ENDER_STONE);
        }
    }

    /** True if the defender of the plugin's own bridge, at this height above the floor, fell off the top. */
    static boolean offTheTop(double feet) {
        return Place.PLAIN.offTheTop(feet);
    }

    /** {@link Place#fromTheFront} on the plugin's own bridge (the attacker at smaller x). */
    static boolean fromTheFront(double dx, double dz) {
        return Place.PLAIN.fromTheFront(dx, dz);
    }

    /** How far a hit reaches, a little more than the game's 3 blocks for the lag. */
    static final double REACH = 3.6;
    /** The body of a player: half its width, its height; and the margin the game adds to aim at it. */
    static final double HALF_WIDTH = 0.3;
    static final double HEIGHT = 1.8;
    static final double AIM_MARGIN = 0.15;

    /**
     * True if the look of the attacker (eyes, and the unit way they look) meets the body of the
     * defender standing at (x, y, z), within {@value #REACH} blocks: the defender is where they aim.
     */
    static boolean aimedAt(double eyeX, double eyeY, double eyeZ, double wayX, double wayY, double wayZ,
            double x, double y, double z) {
        double grow = HALF_WIDTH + AIM_MARGIN;
        double[] low = {x - grow, y - AIM_MARGIN, z - grow};
        double[] high = {x + grow, y + HEIGHT + AIM_MARGIN, z + grow};
        double[] eye = {eyeX, eyeY, eyeZ};
        double[] way = {wayX, wayY, wayZ};
        double near = 0;
        double far = REACH;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(way[axis]) < 1e-9) {
                if (eye[axis] < low[axis] || eye[axis] > high[axis]) {
                    return false;
                }
                continue;
            }
            double one = (low[axis] - eye[axis]) / way[axis];
            double two = (high[axis] - eye[axis]) / way[axis];
            near = Math.max(near, Math.min(one, two));
            far = Math.min(far, Math.max(one, two));
            if (near > far) {
                return false;
            }
        }
        return true;
    }

    /** Why a hit of the attacker does not count (null when it does): from where, how, where they look. */
    static String refused(Place place, Player attacker, double[] by, double[] at, Location victim) {
        if (!place.fromTheFront(by[0] - at[0], by[2] - at[2])) {
            return "Only a hit from the front counts: from the bridge, in front of them.";
        }
        Location eye = attacker.getEyeLocation();
        org.bukkit.util.Vector way = eye.getDirection();
        if (!aimedAt(eye.getX(), eye.getY(), eye.getZ(), way.getX(), way.getY(), way.getZ(), victim.getX(), victim.getY(),
                victim.getZ())) {
            return "Only a hit with your look on them counts: no hit from the side.";
        }
        return null;
    }

    /** {@link Place#outOfTheNotch} on the plugin's own bridge. */
    static boolean outOfTheNotch(double x, double y, double z) {
        return Place.PLAIN.outOfTheNotch(x, y, z);
    }

    /** The side that attacks in this attempt: the first side first, then each in turn. */
    static int attackerOf(int round) {
        return (round + 1) % 2;
    }

    private int attacker() {
        return attackerOf(round);
    }

    // --- The duel ---------------------------------------------------------------------------------

    @Override
    void build() {
        place.build(arena);
    }

    /** A countdown before the first attempt only: the next ones start at once, the roles swapped. */
    @Override
    int countdownSeconds() {
        return round <= 1 ? 3 : 0;
    }

    @Override
    void roundReset() {
        super.roundReset();
        knockedUntil = -1;
    }

    @Override
    void onFight() {
        for (Player player : online()) {
            boolean attacking = sideOf(player.getUniqueId()) == attacker();
            player.sendMessage(attacking ? ChatColor.YELLOW + "Attack: run and knock them off sideways!"
                    : ChatColor.AQUA + "Defend: hold your place.");
        }
    }

    @Override
    Location spawnOf(UUID player) {
        return sideOf(player) == attacker() ? place.attackerStart(arena) : place.defenderSpot(arena);
    }

    @Override
    double fallY() {
        return place.fallY(arena);
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD)};
    }

    @Override
    boolean colouredBlocks() {
        return false;
    }

    /** Only the attacker's hit, from the front, once per attempt; no damage, only the knockback. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || phase != Phase.PLAYING || sideOf(attacker.getUniqueId()) != attacker()
                || knockedUntil >= 0) {
            return false;
        }
        double[] by = arena.offset(attacker.getLocation());
        double[] at = arena.offset(victim.getLocation());
        String refused = refused(place, attacker, by, at, victim.getLocation());
        if (refused != null) {
            long now = System.currentTimeMillis();
            if (now - warnedAt > 1500L) {
                warnedAt = now;
                attacker.sendMessage(ChatColor.RED + refused);
            }
            return false;
        }
        event.setDamage(0.0);
        knockedUntil = tickCount + KNOCK_TICKS;
        knockedAt = tickCount;
        return true;
    }

    @Override
    void tick() {
        if (phase != Phase.PLAYING) {
            return;
        }
        List<Player> defenders = online(1 - attacker());
        if (defenders.isEmpty()) {
            return;
        }
        Player defender = defenders.get(0);
        double[] at = arena.offset(defender.getLocation());
        if (place.offTheTop(at[1])) {
            displaced(defender);
            return;
        }
        if (knockedUntil >= 0) {
            if (tickCount >= knockedUntil || held(tickCount - knockedAt, onGround(defender))) {
                broadcast(CHAT[1 - attacker()] + defender.getDisplayName() + ChatColor.GRAY + " held: the roles swap.", null);
                startRound();
            }
            return;
        }
        if (place.outOfTheNotch(at[0], at[1], at[2])) {
            Location back = place.defenderSpot(arena);
            back.setYaw(defender.getLocation().getYaw());
            back.setPitch(defender.getLocation().getPitch());
            defender.teleport(back);
            defender.setFallDistance(0f);
        }
    }

    /** True if the defender held a hit this many ticks ago: back on the ground once the knockback is over. */
    static boolean held(int ticksSinceHit, boolean onGround) {
        return onGround && ticksSinceHit >= SETTLE_TICKS;
    }

    /** The defender fell off the top: a point for the attacker, a kill in the stats. */
    private void displaced(Player defender) {
        int side = attacker();
        List<Player> attackers = online(side);
        if (!attackers.isEmpty() && counts()) {
            plugin.lobby().recordKill(mode.id(), attackers.get(0).getUniqueId(), defender.getUniqueId());
        }
        broadcast(CHAT[side] + namesOf(side) + ChatColor.GRAY + " displaced " + CHAT[1 - side] + defender.getDisplayName()
                + ChatColor.GRAY + "!", Sound.NOTE_PLING);
        point(side);
    }

    /** A fall of the defender is a displace; a fall of the attacker ends their attempt. */
    @Override
    void fell(Player player) {
        if (phase != Phase.PLAYING) {
            player.teleport(spawnOf(player.getUniqueId()));
            return;
        }
        if (sideOf(player.getUniqueId()) == attacker()) {
            broadcast(CHAT[attacker()] + player.getDisplayName() + ChatColor.GRAY + " fell: the roles swap.", null);
            startRound();
        } else {
            displaced(player);
        }
    }

    @Override
    void lethal(Player victim, Player killer) {
        fell(victim);
    }

    @Override
    void onSecond() {
        if (knockedUntil < 0 && seconds >= ATTEMPT_SECONDS) {
            broadcast(ChatColor.GRAY + "No hit in " + ATTEMPT_SECONDS + " seconds: the roles swap.", null);
            startRound();
        }
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        boolean attacking = sideOf(viewer) == attacker();
        lines.add(attacking ? ChatColor.YELLOW + "Attack: off sideways!" : ChatColor.AQUA + "Defend: hold your place");
        lines.add(ChatColor.WHITE + "Left: " + ChatColor.GREEN + Math.max(0, ATTEMPT_SECONDS - seconds) + " s");
    }
}
