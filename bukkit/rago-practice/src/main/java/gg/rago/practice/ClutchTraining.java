package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Block clutch: the player stands at the edge of a small Bed Wars island, its bed and defense
 * beside them, and takes a hit that throws them off over the void; they place their wool to save
 * themselves. The island is the owner's (the clutch island of BlockClutchExtend, copied once), or
 * the plugin's own without the world. The hit comes from where the player looks and throws them
 * backwards. The islands of the players stand side by side, so that each one sees the others
 * clutch. The hotbar sets the knockback (1 to 5), the number of hits in a row (1 to 3), a
 * knockback stick, and a random side. Each clutch saved adds one to the streak; a fall ends it.
 *
 * <p>The top fight version plays on the wool platform of a top fight, at the build limit: no block
 * goes above the platform, so the player catches themselves at its level or lower.
 */
final class ClutchTraining extends Training {

    /** The zones of the players are this far apart: each one sees the next ones. */
    static final int SPACING = 28;
    /** Ticks on the ground after the last hit that make a clutch saved. */
    private static final int SAVED_TICKS = 16;
    /** Where the player may place blocks: this far from the centre along the row, and across it. */
    static final int PLACE_ALONG = 12;
    static final int PLACE_ACROSS = 20;
    /** The random side turns the hit this far at most from straight off the edge. */
    private static final double RANDOM_TURN = Math.toRadians(75);

    /** The island: the owner's map copied (or null for the plugin's own), and where the player stands. */
    static final class Island {
        final MapCopy copy;
        /** x, y, z, yaw from the centre. */
        final double[] player;

        Island(MapCopy copy, double[] player) {
            this.copy = copy;
            this.player = player;
        }
    }

    /** The plugin's own island: at its south edge, facing north, the void behind. */
    static Island plainIsland() {
        return new Island(null, new double[] {3.5, 1, 1.5, 180});
    }

    private final boolean topFight;
    private final Island island;
    private final Setting knockback;
    private final Setting hitCount;
    private final Setting stick;
    private final Setting randomSide;
    private final Random random = new Random();
    /** Ticks before the next hit; -1 once the hits of the attempt are given. */
    private int nextHit;
    private int hitsLeft;
    private int lastHitTick;
    private int groundTicks;
    private int placedSinceHit;
    private int streak;
    private long limitTold;
    private Vector way;

    static final Variant ISLAND = new Variant("island", "Island clutch", false, "clutches");
    static final Variant TOP = new Variant("top", "Top fight clutch", false, "clutches");

    ClutchTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, boolean topFight, Island island) {
        super(host, mode, arena, teams, topFight ? TOP : ISLAND);
        this.topFight = topFight;
        this.island = island == null ? plainIsland() : island;
        knockback = setting("Knockback", 1, 5, 3);
        hitCount = setting("Hits", 1, 3, 1);
        stick = toggle("KB stick", false);
        randomSide = toggle("Random side", false);
    }

    /**
     * How a hit throws the player, {horizontal, vertical} in blocks a tick: 3 is a hit of a fight,
     * 5 a sprint hit; a knockback stick throws much farther, as Knockback I does.
     */
    static double[] throwOf(int knockback, boolean stick) {
        double horizontal = 0.24 + 0.06 * knockback;
        double vertical = 0.33 + 0.01 * knockback;
        if (stick) {
            horizontal += 0.5;
            vertical += 0.08;
        }
        return new double[] {horizontal, vertical};
    }

    /** The board of the records: each setting has its own, a harder clutch being another score. */
    @Override
    String board() {
        return mode.id() + "." + current().id + ".kb" + knockback.value + ".hits" + hitCount.value
                + (stick.on() ? ".stick" : "") + (randomSide.on() ? ".random" : "");
    }

    @Override
    String describe() {
        String what = topFight ? "you stand on the top fight platform, at the build limit: no block above it."
                : "you stand at the edge of your island, next to your bed.";
        return what + " A hit throws you off: place your wool to save yourself.";
    }

    /** The places of the top fight platform, along x: blue on one half, red on the other. */
    static int halfWidth(int x) {
        int along = Math.abs(x);
        return along <= 5 ? 3 : along <= 7 ? 2 : along <= 9 ? 1 : along == 10 ? 0 : -1;
    }

    @Override
    void build() {
        if (topFight) {
            for (int x = -10; x <= 10; x++) {
                for (int z = -halfWidth(x); z <= halfWidth(x); z++) {
                    boolean blue = x < 0 || (x == 0 && z < 0);
                    arena.build(x, 0, z, Material.WOOL, blue ? Shapes.BLUE : Shapes.RED);
                }
            }
            return;
        }
        if (island.copy != null) {
            island.copy.build(arena);
            return;
        }
        Shapes.island(arena, -5, -6, 5, 1, Material.GRASS, 0, Material.DIRT, 0);
        Shapes.bed(arena, -1, 1, -2, 1, 0);
        Shapes.buildDefense(arena, Shapes.defense(-1, 1, -2, 1, 0, 2), new Material[] {Material.ENDER_STONE, Material.WOOL},
                new int[] {0, Shapes.RED});
    }

    @Override
    Location spawnOf(UUID player) {
        // at the edge, facing the hitter, the void behind
        double[] at = start();
        return arena.at(at[0], at[1], at[2], (float) at[3]);
    }

    /** Where the player stands: x, y, z, yaw from the centre. */
    private double[] start() {
        return topFight ? new double[] {2.5, 1, 3.5, 180} : island.player;
    }

    /**
     * The way a hit throws the player who looks along this yaw, turned by this angle: backwards,
     * the hitter in front of them. Returns x, z.
     */
    static double[] thrownWay(double yaw, double turn) {
        double a = Math.toRadians(yaw) + turn;
        return new double[] {Math.sin(a), -Math.cos(a)};
    }

    @Override
    double fallY() {
        return arena.floorY() - 8;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {stack(Material.WOOL)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void onFight() {
        nextHit = 25 + random.nextInt(35);
        hitsLeft = hitCount.value;
        groundTicks = 0;
        placedSinceHit = 0;
        lastHitTick = 0;
        double angle = randomSide.on() ? (random.nextDouble() * 2 - 1) * RANDOM_TURN : 0;
        // straight off the edge: backwards from where the player looks at the start
        double[] thrown = thrownWay(start()[3], angle);
        way = new Vector(thrown[0], 0, thrown[1]);
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null) {
            return;
        }
        if (hitsLeft > 0) {
            if (--nextHit <= 0) {
                hit(player);
                hitsLeft--;
                lastHitTick = tickCount;
                nextHit = 10;
            }
            return;
        }
        if (tickCount - lastHitTick < 8) {
            return;
        }
        if (onGround(player)) {
            groundTicks++;
        } else {
            groundTicks = 0;
        }
        if (groundTicks >= SAVED_TICKS) {
            if (placedSinceHit == 0) {
                player.sendMessage(ChatColor.GRAY + "No clutch needed: next hit.");
                startRound();
                return;
            }
            streak++;
            player.sendMessage(ChatColor.GREEN + "Clutch! " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
            sound(player, Sound.ORB_PICKUP, 1.2f);
            score(streak, true);
            hitsLeft = -1;
            groundTicks = Integer.MIN_VALUE;
            again(12L);
        }
    }

    /** A hit as a player would give it, off the edge. */
    private void hit(Player player) {
        double[] thrown = throwOf(knockback.value, stick.on());
        player.setVelocity(way.clone().multiply(thrown[0]).setY(thrown[1]));
        player.playEffect(EntityEffect.HURT);
        sound(player, Sound.HURT_FLESH, 1f);
        placedSinceHit = 0;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        int along = block.getX() - arena.centreX();
        int across = block.getZ() - arena.centreZ();
        int height = block.getY() - arena.floorY();
        if (Math.abs(along) > PLACE_ALONG || Math.abs(across) > PLACE_ACROSS || height < -20) {
            return false;
        }
        if (height > (topFight ? 0 : 8)) {
            if (topFight && now() - limitTold > 1000) {
                limitTold = now();
                player.sendMessage(ChatColor.RED + "Build height limit reached!");
            }
            return false;
        }
        return true;
    }

    @Override
    void placed(Player player, Block block) {
        placedSinceHit++;
    }

    @Override
    void fell(Player player) {
        String why = streak > 0 ? "You fell after " + streak + (streak == 1 ? " clutch" : " clutches") + ": the streak starts again."
                : "You fell.";
        streak = 0;
        failed(why);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        lines.add(Duel.cut(ChatColor.GRAY + "KB " + ChatColor.WHITE + knockback.value + ChatColor.GRAY + ", "
                + ChatColor.WHITE + hitCount.value + ChatColor.GRAY + (hitCount.value == 1 ? " hit" : " hits")
                + (stick.on() ? ", stick" : "") + (randomSide.on() ? ", random" : "")));
        lines.add(ChatColor.GRAY + (hitsLeft > 0 ? "Get ready..." : "Clutch!"));
    }
}
