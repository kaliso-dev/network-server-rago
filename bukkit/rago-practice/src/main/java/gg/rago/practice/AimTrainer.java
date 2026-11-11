package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Aim trainer: a target the size of a player moves in front of the player as a player would in a
 * fight, and every click that lands on it counts. It strafes left and right, changing direction
 * at random; it strafes and jumps; or it circles around the player. The thirty seconds start with
 * the first hit; the score is the hits, and the accuracy (hits out of clicks) is shown.
 *
 * <p>Each second the player kept the crosshair on the target (most of the second) or hit it earns
 * XP, with a sound that rises as the streak goes on; a second off the target earns nothing. The
 * level of the XP bar is the XP of the attempt, the bar the time left. At the end the player gets
 * that XP and more for the score as Network XP, and sees the five best of the variant
 * (/aimtop shows them from anywhere).
 */
final class AimTrainer extends Training {

    private static final int DURATION_TICKS = 600;
    /** A second counts when the crosshair was on the target this many ticks of it (or one hit). */
    static final int LOOK_TICKS = 12;
    /** The box of the target, as a player's: half its width, its height; the reach of the eye. */
    static final double HALF_WIDTH = 0.4;
    static final double HEIGHT = 1.95;
    static final double REACH = 8;
    /** Every this many seconds in a row on the target, a chime. */
    private static final int STREAK_CHIME = 5;
    /** The boards of the top, in the order of the variants. */
    static final String[] VARIANTS = {"strafe", "jump", "circle"};
    private static final double STRAFE_SPEED = 0.22;
    private static final double CIRCLE_RADIUS = 2.8;

    private final Random random = new Random();
    private ArmorStand target;
    private int hits;
    private int clicks;
    private int startTick = -1;
    private boolean done;
    /** The target's place: its sideways offset and distance (strafe), or its angle (circle). */
    private double side;
    private double distance = 3;
    private double angle;
    private int direction = 1;
    private int turnIn;
    private double jumpY;
    private double jumpSpeed;
    private int jumpIn;
    /** Network XP for each second on the target, and one more for each this many hits at the end. */
    private final int xpPerSecond;
    private final int hitsPerXp;
    /** The second under way: its ticks with the crosshair on the target, its hits. */
    private int lookTicks;
    private int secondHits;
    private int secondsOn;
    private int streak;
    private int xp;

    /** The three targets, as the hotbar names them. */
    static final Variant[] TARGETS = {
        new Variant(VARIANTS[0], "Strafing target", false, "hits"),
        new Variant(VARIANTS[1], "Strafing and jumping", false, "hits"),
        new Variant(VARIANTS[2], "Circling target", false, "hits")};

    AimTrainer(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int xpPerSecond, int hitsPerXp) {
        super(host, mode, arena, teams, TARGETS);
        this.xpPerSecond = xpPerSecond;
        this.hitsPerXp = hitsPerXp;
    }

    /** The Network XP of an attempt: the seconds on the target, then one for each hitsPerXp hits. */
    static int xpOf(int secondsOn, int xpPerSecond, int hits, int hitsPerXp) {
        return secondsOn * xpPerSecond + (hitsPerXp <= 0 ? 0 : hits / hitsPerXp);
    }

    /** A second counts when the crosshair stayed on the target most of it, or when it was hit. */
    static boolean secondCounts(int lookTicks, int hits) {
        return hits > 0 || lookTicks >= LOOK_TICKS;
    }

    /** The pitch of the sound of a second on the target: higher as the streak goes on. */
    static float pitchOf(int streak) {
        return (float) Math.min(2.0, 0.8 + 0.08 * streak);
    }

    /**
     * True if the ray from the eye (ex, ey, ez) along (dx, dy, dz) meets the box of a target standing
     * at (bx, by, bz) within reach: the slab test, axis by axis.
     */
    static boolean looksAt(double ex, double ey, double ez, double dx, double dy, double dz, double bx, double by, double bz) {
        double[] eye = {ex, ey, ez};
        double[] dir = {dx, dy, dz};
        double[] min = {bx - HALF_WIDTH, by, bz - HALF_WIDTH};
        double[] max = {bx + HALF_WIDTH, by + HEIGHT, bz + HALF_WIDTH};
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) {
            return false;
        }
        double near = 0;
        double far = REACH / length;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dir[i]) < 1e-12) {
                if (eye[i] < min[i] || eye[i] > max[i]) {
                    return false;
                }
                continue;
            }
            double t1 = (min[i] - eye[i]) / dir[i];
            double t2 = (max[i] - eye[i]) / dir[i];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) {
                return false;
            }
        }
        return true;
    }

    @Override
    String describe() {
        switch (current().id) {
            case "jump":
                return "the target strafes and jumps: keep your crosshair on it.";
            case "circle":
                return "the target circles around you: follow it.";
            default:
                return "the target strafes left and right: hit it as often as you can in 30 seconds."
                        + " Each second on it gives XP.";
        }
    }

    @Override
    void build() {
        Shapes.floor(arena, -8, -8, 8, 8, 0, Material.STAINED_CLAY, Shapes.WHITE);
        Shapes.walls(arena, -8, -8, 8, 8, 1, 3, Material.BARRIER, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(-2.5, 1, 0.5, -90f);
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.DIAMOND_SWORD)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return false;
    }

    @Override
    void roundReset() {
        hits = 0;
        clicks = 0;
        startTick = -1;
        done = false;
        side = 0;
        distance = 3;
        angle = 0;
        direction = 1;
        turnIn = 10;
        jumpY = 0;
        jumpSpeed = 0;
        jumpIn = 20;
        lookTicks = 0;
        secondHits = 0;
        secondsOn = 0;
        streak = 0;
        xp = 0;
        Player player = player();
        if (player != null) {
            player.setLevel(0);
            player.setExp(0f);
        }
    }

    @Override
    void onFight() {
        if (target == null || !target.isValid()) {
            // a zone used before may have its chunk unloaded: no entity goes into one
            Location at = place();
            at.getChunk().load();
            target = arena.world().spawn(at, ArmorStand.class);
            target.setGravity(false);
            target.setBasePlate(false);
            target.setArms(true);
            target.setCustomName(ChatColor.RED + "Target");
            target.setCustomNameVisible(true);
            target.setHelmet(leatherPiece(Material.LEATHER_HELMET, Color.RED));
            target.setChestplate(leatherPiece(Material.LEATHER_CHESTPLATE, Color.RED));
            target.setLeggings(leatherPiece(Material.LEATHER_LEGGINGS, Color.RED));
            target.setBoots(leatherPiece(Material.LEATHER_BOOTS, Color.RED));
            target.setItemInHand(new ItemStack(Material.IRON_SWORD));
            host.own(target, this);
        } else {
            target.teleport(place());
        }
    }

    @Override
    void ended() {
        if (target != null) {
            host.disown(target);
            target.remove();
            target = null;
        }
    }

    /** Where the target stands now, looking at the player. */
    private Location place() {
        Player player = player();
        Location centre = player == null ? spawnOf(me) : player.getLocation();
        Location at;
        if (current().id.equals("circle")) {
            at = centre.clone().add(Math.cos(angle) * CIRCLE_RADIUS, 0, Math.sin(angle) * CIRCLE_RADIUS);
            at.setY(arena.floorY() + 1);
        } else {
            at = arena.at(-2.5 + distance, 1, 0.5 + side);
        }
        at.add(0, jumpY, 0);
        // inside the walls
        double[] offset = arena.offset(at);
        at.setX(arena.centreX() + Math.max(-7.2, Math.min(7.2, offset[0])));
        at.setZ(arena.centreZ() + Math.max(-7.2, Math.min(7.2, offset[2])));
        double dx = centre.getX() - at.getX();
        double dz = centre.getZ() - at.getZ();
        at.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        at.setPitch(0f);
        return at;
    }

    @Override
    void tick() {
        if (target == null || !target.isValid()) {
            if (phase == Phase.PLAYING && !done && tickCount % 10 == 0) {
                onFight();
            }
            return;
        }
        move();
        target.teleport(place());
        if (startTick < 0 || done) {
            return;
        }
        Player player = player();
        int elapsed = tickCount - startTick;
        if (player != null) {
            Location eye = player.getEyeLocation();
            Vector look = eye.getDirection();
            Location at = target.getLocation();
            if (looksAt(eye.getX(), eye.getY(), eye.getZ(), look.getX(), look.getY(), look.getZ(), at.getX(), at.getY(), at.getZ())) {
                lookTicks++;
            }
        }
        if (elapsed > 0 && elapsed % 20 == 0) {
            second(player);
        }
        if (player != null) {
            player.setLevel(xp);
            player.setExp(Math.max(0f, Math.min(1f, 1f - (float) elapsed / DURATION_TICKS)));
        }
        if (elapsed >= DURATION_TICKS) {
            end(player);
            return;
        }
        refresh();
    }

    /** A second is over: XP and a sound if the player was on the target, nothing if not. */
    private void second(Player player) {
        boolean on = secondCounts(lookTicks, secondHits);
        lookTicks = 0;
        secondHits = 0;
        if (!on) {
            streak = 0;
            return;
        }
        secondsOn++;
        streak++;
        xp += xpPerSecond;
        if (player == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 0.7f, pitchOf(streak));
        if (streak % STREAK_CHIME == 0) {
            player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, pitchOf(streak));
            player.sendMessage(ChatColor.AQUA + "" + streak + " seconds in a row on the target!");
        }
    }

    /** The aim trainer gives its own XP (each second on the target), not that of the trainings. */
    @Override
    boolean earnsXp() {
        return false;
    }

    /** The thirty seconds are over: the score, the XP, the top. */
    private void end(Player player) {
        done = true;
        score(hits);
        int earned = xpOf(secondsOn, xpPerSecond, hits, hitsPerXp);
        if (player != null) {
            player.setLevel(earned);
            player.setExp(0f);
            player.sendMessage(ChatColor.GRAY + "Accuracy: " + ChatColor.WHITE + accuracy() + ChatColor.GRAY + " (" + hits
                    + " hits out of " + clicks + " clicks). On the target: " + ChatColor.WHITE + secondsOn + "/"
                    + DURATION_TICKS / 20 + " s" + ChatColor.GRAY + ".");
            for (String line : topLines(host.records().book(), board(), current().label, me, 5)) {
                player.sendMessage(line);
            }
        }
        host.plugin().lobby().giveXp(mode.id(), me, earned, "aim trainer");
        again(80L);
    }

    /** The best of a board of the aim trainer, as the chat shows it: a title, then the best first. */
    static List<String> topLines(RecordBook book, String board, String label, UUID viewer, int count) {
        List<String> lines = new ArrayList<>();
        List<RecordBook.Entry> ranked = book.ranked(board);
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Top aim" + ChatColor.GRAY + " - " + ChatColor.WHITE + label);
        if (ranked.isEmpty()) {
            lines.add(ChatColor.GRAY + "  Nobody yet.");
            return lines;
        }
        boolean shown = false;
        for (int i = 0; i < Math.min(count, ranked.size()); i++) {
            RecordBook.Entry entry = ranked.get(i);
            boolean mine = entry.player.equals(viewer);
            shown |= mine;
            lines.add(topLine(i + 1, entry, mine));
        }
        if (!shown && viewer != null) {
            for (int i = count; i < ranked.size(); i++) {
                if (ranked.get(i).player.equals(viewer)) {
                    lines.add(topLine(i + 1, ranked.get(i), true));
                }
            }
        }
        return lines;
    }

    private static String topLine(int rank, RecordBook.Entry entry, boolean mine) {
        ChatColor colour = rank == 1 ? ChatColor.GOLD : rank == 2 ? ChatColor.WHITE : rank == 3 ? ChatColor.RED : ChatColor.GRAY;
        return "  " + colour + "#" + rank + " " + (mine ? ChatColor.GREEN : ChatColor.YELLOW) + entry.name
                + ChatColor.GRAY + " - " + ChatColor.WHITE + entry.value + " hits";
    }

    /** One tick of the target's moves: strafes that change direction at random, jumps. */
    private void move() {
        if (--turnIn <= 0) {
            direction = random.nextInt(3) == 0 ? direction : -direction;
            turnIn = 6 + random.nextInt(16);
        }
        if (current().id.equals("circle")) {
            angle += direction * (0.07 + random.nextDouble() * 0.03);
        } else {
            side += direction * STRAFE_SPEED;
            if (Math.abs(side) > 3) {
                side = Math.signum(side) * 3;
                direction = -direction;
            }
            distance += (random.nextDouble() - 0.5) * 0.12;
            distance = Math.max(2.2, Math.min(3.8, distance));
        }
        if (current().id.equals("jump")) {
            if (jumpY <= 0 && --jumpIn <= 0) {
                jumpSpeed = 0.42;
                jumpIn = 12 + random.nextInt(20);
            }
            if (jumpSpeed != 0 || jumpY > 0) {
                jumpY += jumpSpeed;
                jumpSpeed = (jumpSpeed - 0.08) * 0.98;
                if (jumpY <= 0) {
                    jumpY = 0;
                    jumpSpeed = 0;
                }
            }
        }
    }

    @Override
    void hitEntity(Player attacker, Entity entity, EntityDamageEvent event) {
        event.setCancelled(true);
        if (entity != target || done) {
            return;
        }
        if (startTick < 0) {
            startTick = tickCount;
            clicks = 1;
            attacker.sendMessage(ChatColor.GREEN + "Go! 30 seconds.");
        }
        hits++;
        secondHits++;
        sound(attacker, Sound.SUCCESSFUL_HIT, 1.6f);
    }

    @Override
    void swung(Player player) {
        if (startTick >= 0 && !done) {
            clicks++;
        }
    }

    private String accuracy() {
        return clicks == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * Math.min(hits, clicks) / clicks);
    }

    @Override
    void trainingLines(List<String> lines) {
        int left = startTick < 0 ? DURATION_TICKS : Math.max(0, DURATION_TICKS - (tickCount - startTick));
        lines.add(ChatColor.WHITE + "Time left: " + ChatColor.GREEN + clock((left + 19) / 20));
        lines.add(ChatColor.WHITE + "Hits: " + ChatColor.GREEN + hits);
        lines.add(ChatColor.WHITE + "Accuracy: " + ChatColor.GREEN + accuracy());
        lines.add(ChatColor.WHITE + "XP: " + ChatColor.AQUA + xp + (streak > 1 ? ChatColor.GRAY + " (x" + streak + ")" : ""));
        if (startTick < 0) {
            lines.add(ChatColor.GRAY + "Hit the target to start");
        }
    }
}
