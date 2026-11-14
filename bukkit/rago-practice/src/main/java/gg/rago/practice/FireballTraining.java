package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Fireball practice on the owner's Bed Wars maps, in a loop as the pearl practice: each attempt on
 * the next map, at another place, in another direction, a situation of a real game found on the
 * bridges between its islands (see {@link MapParts}), with the fireballs of Hypixel:
 *
 * <ul>
 *   <li>a fireball jump over the gap of a broken bridge, to the island it goes to; the same with an
 *       enemy rushing from behind (an escape); a fireball jump from a bridge up onto an island, a
 *       few blocks over it;
 *   <li>an enemy bridging to the player's island, running over a whole bridge to it, or bridging
 *       past it between two other islands: three fireballs to knock him into the void (or the
 *       bridge under him: the blast breaks wool) before he gets there;
 *   <li>an enemy on another island throwing three fireballs at the player on their bridge: hit
 *       them back.
 * </ul>
 *
 * The enemies are bots (zombies in leather) that bridge, run and throw as players do, and a
 * fireball's blast pushes them as it pushes a player. Each good attempt adds to the streak, a
 * miss ends it; the levels Jumps, Knock them off and Send them back play their kinds only.
 */
final class FireballTraining extends MapLoopTraining {

    /** The kinds of the loop, in turn. */
    static final MapParts.Kind[] LOOP = {MapParts.Kind.JUMP, MapParts.Kind.BRIDGER, MapParts.Kind.SEND, MapParts.Kind.CLIMB,
        MapParts.Kind.RUSHER, MapParts.Kind.ESCAPE, MapParts.Kind.SIDE};
    static final MapParts.Kind[] JUMPS = {MapParts.Kind.JUMP, MapParts.Kind.CLIMB, MapParts.Kind.ESCAPE};
    static final MapParts.Kind[] KNOCKS = {MapParts.Kind.BRIDGER, MapParts.Kind.RUSHER, MapParts.Kind.SIDE};
    static final MapParts.Kind[] SENDS = {MapParts.Kind.SEND};

    /** Ticks on the ground after a fireball jump: landed. */
    private static final int STAND_TICKS = 6;
    /** A fireball thrown that did not push the player in this many ticks: missed. */
    private static final int NO_PUSH_TICKS = 40;
    /** The player this far under the start (and the island aimed at): fallen. */
    private static final int BELOW_START = 8;
    /** The enemy this far under the lowest step of his way: knocked into the void. */
    static final int ENEMY_FALLEN = 4;
    /** Ticks the enemy flies after a blast before he walks again (if he is still on something). */
    static final int KNOCK_TICKS = 10;
    /** A bridging enemy places a block every so many ticks: 3.3 blocks a second. */
    static final int BRIDGE_TICKS = 6;
    /** How fast the enemy walks on blocks, and runs (sprints): blocks a tick. */
    static final double WALK_SPEED = 0.2;
    static final double RUN_SPEED = 0.26;
    /** An escape: the enemy starts running this many ticks after the start. */
    private static final int CHASE_DELAY = 10;
    /** Ticks before an enemy to knock off sets off: the time to see where he comes from, on a new map each time. */
    private static final int SET_OFF = 15;
    /** An escape: the enemy this close to the player has caught them. */
    private static final double CAUGHT = 1.4;
    /** The enemy this close to the end of his way has got there. */
    private static final double ARRIVED = 0.8;
    /** Sending back: the fireballs of the enemy in an attempt, the ticks before the first, and between two. */
    static final int THROWS = 3;
    private static final int FIRST_THROW = 30;
    private static final int THROW_EVERY = 40;
    /** The enemy this long on his way without getting nearer to his next step is stuck: he is put on it. */
    private static final int STUCK_TICKS = 40;
    /** The fireballs of the knocks: three in an attempt. */
    static final int KNOCK_FIREBALLS = 3;
    private static final String KNOCK_HINT = "Hit him or the wool under him: a blast pushes away from where it lands.";
    /** A fireball still flying after this long is gone. */
    private static final int FIREBALL_LIFE = 120;

    private final List<Fireball> thrown = new ArrayList<>();
    private final List<Fireball> incoming = new ArrayList<>();
    private final Set<UUID> sentBack = new HashSet<>();
    private Zombie enemy;
    /** The way of the walking enemy (x, y, z of the block under each step), or null. */
    private int[] path;
    private int pathLow;
    private int knockedAt = -100;
    /** The enemy's way so far: the step he is on, how far from the next, since when (-1: from now on). */
    private int progressStep = -1;
    private double progressLeft;
    private int progressTick;
    private int lastPlaced = -100;
    private int throwsLeft;
    private int nextThrow;
    private int startTick;
    private int thrownTick = -1;
    /** The tick the player's own fireball pushed them; -1 before. */
    private int blastTick = -1;
    private int standing;
    private long lastThrow;
    /** The attempt is won or lost: nothing more counts until the next one. */
    private boolean decided;

    FireballTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Places places) {
        super(host, mode, arena, teams, places, variants());
    }

    static Variant[] variants() {
        return new Variant[] {
            new Variant("loop", "Loop", false, "in a row"),
            new Variant("jumps", "Fireball jumps", false, "jumps"),
            new Variant("knock", "Knock them off", false, "knocked off"),
            new Variant("sendback", "Send them back", false, "in a row"),
        };
    }

    /** The kinds of a level, in turn. */
    static MapParts.Kind[] kinds(String variantId) {
        switch (variantId) {
            case "jumps":
                return JUMPS;
            case "knock":
                return KNOCKS;
            case "sendback":
                return SENDS;
            default:
                return LOOP;
        }
    }

    /** The kind of this level for the attempt after so many. */
    static MapParts.Kind kind(String variantId, int turn) {
        MapParts.Kind[] kinds = kinds(variantId);
        return kinds[Math.floorMod(turn, kinds.length)];
    }

    @Override
    MapParts.Kind kindOf(String variantId, int turn) {
        return kind(variantId, turn);
    }

    @Override
    int kindsOf(String variantId) {
        return kinds(variantId).length;
    }

    @Override
    String describe() {
        switch (current().id) {
            case "jumps":
                return "fireball jump over the gap of a broken bridge, up onto an island, away from a rusher: a new map each time.";
            case "knock":
                return "an enemy bridges or runs to an island: knock him into the void with your fireballs before he gets"
                        + " there, a new map each time.";
            case "sendback":
                return "on your bridge, an enemy throws fireballs at you from his island: hit them back, a new map each time.";
            default:
                return "a new map each time: fireball jumps over a broken bridge and up onto an island, enemies to knock"
                        + " off their bridge, fireballs to send back.";
        }
    }

    private MapParts.Kind kind() {
        return situation == null ? MapParts.Kind.JUMP : situation.kind;
    }

    private boolean jumping() {
        MapParts.Kind kind = kind();
        return kind == MapParts.Kind.JUMP || kind == MapParts.Kind.ESCAPE || kind == MapParts.Kind.CLIMB;
    }

    private boolean knocking() {
        return kind().enemyBridge();
    }

    /** The wool of the enemy: blue, or red against a blue player. */
    private int enemyWool() {
        return part != null && part.wool == Shapes.BLUE ? Shapes.RED : Shapes.BLUE;
    }

    // --- The attempt ----------------------------------------------------------------------------

    @Override
    ItemStack[] kit() {
        if (kind() == MapParts.Kind.SEND) {
            return new ItemStack[] {new ItemStack(Material.WOOD_SWORD)};
        }
        return new ItemStack[] {new ItemStack(Material.FIREBALL, knocking() ? KNOCK_FIREBALLS : 1)};
    }

    @Override
    void roundReset() {
        clear();
        decided = false;
        thrownTick = -1;
        blastTick = -1;
        standing = 0;
        knockedAt = -100;
        lastPlaced = -100;
        progressStep = -1;
        startTick = tickCount;
        throwsLeft = 0;
        if (situation == null || part == null) {
            return;
        }
        int wool = situation.kind.enemyBridge() ? enemyWool() : part.wool;
        int[] bridge = situation.bridge;
        for (int i = 0; i + 2 < bridge.length; i += 3) {
            arena.put(bridge[i], bridge[i + 1], bridge[i + 2], Material.WOOL, wool);
        }
        int[] own = situation.platform;
        for (int i = 0; own != null && i + 2 < own.length; i += 3) {
            arena.put(own[i], own[i + 1], own[i + 2], Material.WOOL, part.wool);
        }
        path = situation.path;
        pathLow = Integer.MAX_VALUE;
        if (path != null) {
            for (int i = 1; i < path.length; i += 3) {
                pathLow = Math.min(pathLow, path[i]);
            }
        }
        if (situation.enemy != null) {
            double[] e = situation.enemy;
            String name = situation.kind == MapParts.Kind.SEND ? "Enemy"
                    : situation.kind == MapParts.Kind.RUSHER || situation.kind == MapParts.Kind.ESCAPE ? "Rusher" : "Bridger";
            Color colour = enemyWool() == Shapes.RED ? Color.RED : Color.BLUE;
            enemy = Bots.spawn(this, arena.at(e[0], e[1], e[2], (float) e[3]), name, colour, 0);
            // it never walks by itself: the game moves it
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false), true);
        }
        if (situation.kind == MapParts.Kind.SEND) {
            throwsLeft = THROWS;
            nextThrow = FIRST_THROW + random.nextInt(15);
        }
    }

    /** The enemy and the fireballs of the attempt go. */
    private void clear() {
        Bots.remove(this, enemy);
        enemy = null;
        path = null;
        for (Fireball fireball : thrown) {
            fireball.remove();
        }
        thrown.clear();
        for (Fireball fireball : incoming) {
            host.disown(fireball);
            fireball.remove();
        }
        incoming.clear();
        sentBack.clear();
    }

    @Override
    void leaving() {
        clear();
    }

    @Override
    void ended() {
        clear();
    }

    @Override
    boolean mayTarget(Entity bot, Entity target) {
        // the enemies go where the game moves them, they do not hunt
        return false;
    }

    /** A fireball thrown from the hand, half a second at least after the last one. */
    @Override
    void use(Player player, PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREBALL
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - lastThrow < Fireballs.COOLDOWN_MS || decided || loading || situation == null) {
            return;
        }
        lastThrow = now;
        thrown.add(Fireballs.launch(player));
        if (thrownTick < 0) {
            thrownTick = tickCount;
        }
    }

    /** No blocks: the fireballs do it all. */
    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return false;
    }

    @Override
    void tick() {
        Player player = player();
        expire();
        if (player == null || situation == null || loading || decided) {
            return;
        }
        double[] at = arena.offset(player.getLocation());
        if (at[1] < Math.min(situation.start[1], situation.targetFloor) - BELOW_START) {
            missed(fellWhy());
            return;
        }
        switch (situation.kind) {
            case ESCAPE:
                if (chase(player)) {
                    return;
                }
                landing(player, at);
                break;
            case JUMP:
            case CLIMB:
                landing(player, at);
                break;
            case SEND:
                sending(player);
                break;
            default:
                knocks(player);
                break;
        }
    }

    /** The fireballs gone or flying too long go. */
    private void expire() {
        for (Iterator<Fireball> it = thrown.iterator(); it.hasNext();) {
            Fireball fireball = it.next();
            if (!fireball.isValid() || fireball.getTicksLived() > FIREBALL_LIFE) {
                fireball.remove();
                it.remove();
            }
        }
        for (Iterator<Fireball> it = incoming.iterator(); it.hasNext();) {
            Fireball fireball = it.next();
            if (!fireball.isValid() || fireball.getTicksLived() > FIREBALL_LIFE) {
                host.disown(fireball);
                fireball.remove();
                it.remove();
            }
        }
    }

    // --- The jumps ------------------------------------------------------------------------------

    /** After the player's own blast, where they land: on the far side (or on top of the island), or short. */
    private void landing(Player player, double[] at) {
        if (blastTick < 0) {
            if (thrownTick >= 0 && tickCount - thrownTick > NO_PUSH_TICKS) {
                missed("Your fireball did not push you: throw it at your feet as you jump.");
            }
            return;
        }
        if (tickCount - blastTick < 5) {
            return;
        }
        if (!onGround(player)) {
            standing = 0;
            return;
        }
        if (++standing < STAND_TICKS) {
            return;
        }
        if (landed(situation, part, at)) {
            decided = true;
            succeeded(player, situation.kind == MapParts.Kind.CLIMB ? "Up!" : situation.kind == MapParts.Kind.ESCAPE
                    ? "Escaped!" : "Over the gap!");
        } else if (situation.kind == MapParts.Kind.CLIMB) {
            missed("Not on top: the fireball right under your feet as you jump, then forward onto the island.");
        } else {
            missed("Short of the gap: throw it just behind your feet as you jump, and keep going forward.");
        }
    }

    /** True if the player, standing at this place (from the origin), made the jump of this situation. */
    static boolean landed(MapParts.Situation situation, MapParts.Part part, double[] at) {
        if (situation.kind == MapParts.Kind.CLIMB) {
            return at[1] >= situation.targetFloor - 0.5 && part.near(situation.target, at[0], at[2], 1);
        }
        double yaw = Math.toRadians(situation.start[3]);
        double along = (at[0] - situation.start[0]) * -Math.sin(yaw) + (at[2] - situation.start[2]) * Math.cos(yaw);
        return along >= situation.reach - 0.6 && at[1] >= situation.start[1] - 3;
    }

    /** The enemy of an escape runs at the player; true once he has caught them before their jump. */
    private boolean chase(Player player) {
        if (enemy == null || !enemy.isValid() || blastTick >= 0) {
            return false;
        }
        if (enemy.getLocation().distance(player.getLocation()) < CAUGHT) {
            player.playEffect(EntityEffect.HURT);
            sound(player, Sound.HURT_FLESH, 1f);
            missed("He caught you: fireball jump over the gap at once!");
            return true;
        }
        if (tickCount - startTick >= CHASE_DELAY) {
            walk(RUN_SPEED, false);
        }
        return false;
    }

    // --- The knocks -----------------------------------------------------------------------------

    /** The enemy bridges or runs on: knocked into the void, the player wins; at the end of his way, he loses. */
    private void knocks(Player player) {
        if (enemy == null) {
            return;
        }
        if (!enemy.isValid() || arena.offset(enemy.getLocation())[1] < pathLow + 1 - ENEMY_FALLEN) {
            decided = true;
            Bots.remove(this, enemy);
            enemy = null;
            succeeded(player, "Knocked off!");
            return;
        }
        if (thrown.isEmpty() && !player.getInventory().contains(Material.FIREBALL) && enemy.isOnGround()
                && tickCount - knockedAt > KNOCK_TICKS + 10) {
            // nothing left to stop him
            missed("No fireball left: he gets through. " + KNOCK_HINT);
            return;
        }
        if (tickCount - startTick < SET_OFF) {
            return;
        }
        double speed = situation.kind == MapParts.Kind.RUSHER ? RUN_SPEED : WALK_SPEED;
        if (walk(speed, true)) {
            missed((situation.kind == MapParts.Kind.SIDE ? "He got across. " : "He reached your island. ") + KNOCK_HINT);
        }
    }

    /**
     * A step of the walking enemy along his way: to the middle of the next block of it, at this
     * speed; when that block is not there (not built yet, or blown up), he places it, a block every
     * {@link #BRIDGE_TICKS} ticks, if he bridges. Not while he flies from a blast. True once he
     * stands at the end of his way.
     */
    private boolean walk(double speed, boolean bridges) {
        if (enemy == null || path == null || tickCount - knockedAt < KNOCK_TICKS || !enemy.isOnGround()) {
            return false;
        }
        Location location = enemy.getLocation();
        double[] e = arena.offset(location);
        int steps = path.length / 3;
        int on = nearestStep(path, e[0], e[2]);
        double[] last = {path[3 * (steps - 1)] + 0.5, path[3 * (steps - 1) + 2] + 0.5};
        if (on == steps - 1 && Math.hypot(e[0] - last[0], e[2] - last[1]) < ARRIVED) {
            enemy.setVelocity(new Vector(0, enemy.getVelocity().getY(), 0));
            return true;
        }
        int next = Math.min(on + 1, steps - 1);
        int nx = path[3 * next];
        int ny = path[3 * next + 1];
        int nz = path[3 * next + 2];
        double tx = nx + 0.5;
        double tz = nz + 0.5;
        double walkAt = speed;
        if (arena.block(nx, ny, nz).getType() == Material.AIR) {
            if (!bridges) {
                // nowhere to go: he waits on his block
                enemy.setVelocity(new Vector(0, enemy.getVelocity().getY(), 0));
                return false;
            }
            if (tickCount - lastPlaced >= BRIDGE_TICKS) {
                lastPlaced = tickCount;
                arena.put(nx, ny, nz, Material.WOOL, enemyWool());
            } else {
                // to the edge of his block, the next one coming
                tx = path[3 * on] + 0.5 + (nx - path[3 * on]) * 0.3;
                tz = path[3 * on + 2] + 0.5 + (nz - path[3 * on + 2]) * 0.3;
                walkAt = 1.0 / BRIDGE_TICKS;
            }
        }
        double dx = tx - e[0];
        double dz = tz - e[2];
        double distance = Math.hypot(dx, dz);
        if (on > progressStep || distance < progressLeft - 0.1) {
            progressStep = on;
            progressLeft = distance;
            progressTick = tickCount;
        } else if (tickCount - progressTick > STUCK_TICKS) {
            // stuck against something (a ceiling, a corner): on to his next step, as a player finds his way
            progressStep = -1;
            enemy.teleport(arena.at(nx + 0.5, ny + 1, nz + 0.5, location.getYaw()));
            return false;
        }
        double move = Math.min(walkAt, distance);
        double vy = enemy.getVelocity().getY();
        if (ny > path[3 * on + 1] && distance < 1.3) {
            // a block up: he jumps
            vy = 0.42;
        }
        enemy.setVelocity(distance < 1e-6 ? new Vector(0, vy, 0) : new Vector(dx / distance * move, vy, dz / distance * move));
        return false;
    }

    /** The step of a way (x, y, z of the block under each) whose middle is nearest to (x, z). */
    static int nearestStep(int[] path, double x, double z) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i + 2 < path.length; i += 3) {
            double d = Math.hypot(path[i] + 0.5 - x, path[i + 2] + 0.5 - z);
            if (d < bestDistance) {
                bestDistance = d;
                best = i / 3;
            }
        }
        return best;
    }

    // --- Sending back ---------------------------------------------------------------------------

    /** The enemy throws his fireballs, one at a time; once all are sent back (none got the player), the attempt is won. */
    private void sending(Player player) {
        if (enemy == null || !enemy.isValid()) {
            return;
        }
        if (arena.offset(enemy.getLocation())[1] < situation.enemy[1] - ENEMY_FALLEN) {
            // sent back right into him
            decided = true;
            Bots.remove(this, enemy);
            enemy = null;
            succeeded(player, "Sent back into him: he fell!");
            return;
        }
        if (throwsLeft > 0 && --nextThrow <= 0) {
            throwsLeft--;
            nextThrow = THROW_EVERY + random.nextInt(15);
            throwAt(player);
        }
        if (throwsLeft == 0 && incoming.isEmpty()) {
            decided = true;
            succeeded(player, "Sent them all back!");
        }
    }

    /** The enemy turns to the player and throws a fireball at them, as a player throws one: from his eyes, at speed 1. */
    private void throwAt(Player player) {
        Location target = player.getEyeLocation().add(random.nextDouble() * 0.6 - 0.3, random.nextDouble() * 0.4 - 0.3,
                random.nextDouble() * 0.6 - 0.3);
        Fireball fireball = Fireballs.throwFrom(enemy, target, Fireballs.SPEED);
        host.own(fireball, this);
        incoming.add(fireball);
        sound(player, Sound.GHAST_FIREBALL, 1f);
    }

    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (!(target instanceof Fireball) || !incoming.contains(target) || decided) {
            event.setCancelled(true);
            return;
        }
        // not cancelled: the fireball flies where the player looks, as in a game
        if (sentBack.add(target.getUniqueId())) {
            incoming.remove(target);
            host.disown(target);
            thrown.add((Fireball) target);
            attacker.sendMessage(ChatColor.GREEN + "Sent back! " + ChatColor.GRAY + "(" + sentBack.size() + "/" + THROWS + ")");
            sound(attacker, Sound.ORB_PICKUP, 1.4f);
        }
    }

    /** A fireball of the enemy landed without being sent back: it pushed the player if it got them (see {@link #blasted}). */
    @Override
    void ownLanded(Projectile projectile) {
        incoming.remove(projectile);
    }

    // --- The blasts -----------------------------------------------------------------------------

    /** A fireball's blast pushes the enemy as it pushes a player. */
    @Override
    void blast(Fireball fireball, Player thrower) {
        super.blast(fireball, thrower);
        if (enemy == null || !enemy.isValid() || phase != Phase.PLAYING) {
            return;
        }
        Location centre = fireball.getLocation();
        Location at = enemy.getLocation();
        if (!at.getWorld().equals(centre.getWorld())
                || !Fireballs.reaches(at.getX() - centre.getX(), at.getY() - centre.getY(), at.getZ() - centre.getZ())) {
            return;
        }
        double[] push = Fireballs.push(centre.getX(), centre.getY(), centre.getZ(), at.getX(), at.getY(), at.getZ());
        enemy.setVelocity(new Vector(push[0], push[1], push[2]));
        enemy.playEffect(EntityEffect.HURT);
        at.getWorld().playSound(at, Sound.ZOMBIE_HURT, 1f, 1f);
        knockedAt = tickCount;
        progressStep = -1;
    }

    /** The player is pushed: by their own fireball (a jump), or by the enemy's (it got them). */
    @Override
    void blasted(Player player, Player thrower, double damage) {
        if (decided || situation == null) {
            return;
        }
        if (thrower == null) {
            if (situation.kind == MapParts.Kind.SEND) {
                missed("The fireball got you: hit it back before it reaches you.");
            }
            return;
        }
        if (thrower.equals(player) && blastTick < 0) {
            blastTick = tickCount;
            standing = 0;
        }
    }

    @Override
    String fellWhy() {
        if (situation == null) {
            return "You fell.";
        }
        switch (situation.kind) {
            case JUMP:
                return "You fell: throw the fireball just behind your feet as you jump.";
            case CLIMB:
                return "You fell: the fireball right under your feet as you jump.";
            case SEND:
                return "Pushed off your bridge: hit the fireballs back before they reach you.";
            default:
                return "You fell.";
        }
    }

    @Override
    void trainingLines(List<String> lines) {
        super.trainingLines(lines);
        if (situation != null && situation.kind == MapParts.Kind.SEND) {
            lines.add(ChatColor.WHITE + "Sent back: " + ChatColor.YELLOW + sentBack.size() + "/" + THROWS);
        }
    }
}
