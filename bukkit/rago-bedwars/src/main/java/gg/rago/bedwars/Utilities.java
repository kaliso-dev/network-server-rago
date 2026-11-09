package gg.rago.bedwars;

import gg.rago.bedwars.BedwarsGame.Gamer;
import gg.rago.bedwars.BedwarsGame.Team;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.scheduler.BukkitTask;

/**
 * The special items of the shop, as on Hypixel. The Bedbug: a snowball that lets out a silverfish
 * where it lands, for 15 seconds. The Dream Defender: an iron golem that defends the base for 4
 * minutes. The Bridge Egg: wool of the team under its flight, as it flies. The Magic Milk: no trap
 * set off for 30 seconds, the potions kept. The Compact Pop-up Tower: a tower of wool with its
 * ladder, built in a second where the player puts it. The creatures fight for their team: they go
 * for the nearest enemy, never for their own team, and their kills are their owner's. The blocks
 * go where a player could have put them, and go with the rest at the end. Main thread only.
 */
final class Utilities {

    static final int BEDBUG_SECONDS = 15;
    static final int DEFENDER_SECONDS = 4 * 60;
    static final long MILK_MS = 30_000L;
    /** How hard a Dream Defender hits: an iron golem's own hit (up to 21) would kill in two. */
    static final double DEFENDER_DAMAGE = 6.0;
    /** How far the creatures see the enemies they go for. */
    static final double BEDBUG_RANGE = 12;
    static final double DEFENDER_RANGE = 16;
    /** The Bridge Egg builds this far from where it was thrown, for this many ticks of its flight at most. */
    static final double EGG_RANGE = 27;
    static final int EGG_TICKS = 40;
    /** Where the Bridge Egg puts its wool each tick, from where it flies: two blocks under it, and beside. */
    static final int[][] EGG_BLOCKS = {{0, -2, 0}, {-1, -2, 0}, {0, -2, -1}};
    /** How high the walls of the Pop-up Tower are, under its top. */
    static final int TOWER_WALLS = 5;
    /** How many blocks of the tower go up each tick. */
    static final int TOWER_SPEED = 8;

    /** A creature that fights for a team. */
    static final class Summoned {
        final Team team;
        final UUID owner;
        final String name;
        final double range;
        int left;

        Summoned(Team team, UUID owner, String name, double range, int seconds) {
            this.team = team;
            this.owner = owner;
            this.name = name;
            this.range = range;
            this.left = seconds;
        }
    }

    /** A block of the Pop-up Tower, as the player who puts it sees it: to their right, up, in front. */
    static final class Piece {
        final int right;
        final int up;
        final int forward;
        final boolean ladder;

        Piece(int right, int up, int forward, boolean ladder) {
            this.right = right;
            this.up = up;
            this.forward = forward;
            this.ladder = ladder;
        }
    }

    /** The Pop-up Tower, bottom to top, the order it goes up in. */
    static final List<Piece> TOWER = Collections.unmodifiableList(towerLayout());

    private final BedwarsGame game;
    private final Map<LivingEntity, Summoned> summoned = new HashMap<>();

    Utilities(BedwarsGame game) {
        this.game = game;
    }

    // --- The creatures --------------------------------------------------------------------------

    /** The creature of a team this entity is, or null. */
    Summoned summoned(Entity entity) {
        return summoned.get(entity);
    }

    /** The Bedbug's snowball landed: a silverfish of the thrower's team comes out, for 15 seconds. */
    void bedbug(Player thrower, Location where) {
        Gamer gamer = game.gamer(thrower.getUniqueId());
        if (gamer != null) {
            summon(where.getWorld().spawn(where, Silverfish.class),
                    new Summoned(gamer.team, gamer.id, "Bedbug", BEDBUG_RANGE, BEDBUG_SECONDS));
        }
    }

    /** A Dream Defender put here: an iron golem of the player's team, for 4 minutes. */
    void defender(Player player, Location where) {
        Gamer gamer = game.gamer(player.getUniqueId());
        if (gamer != null) {
            summon(where.getWorld().spawn(where, IronGolem.class),
                    new Summoned(gamer.team, gamer.id, "Dream Defender", DEFENDER_RANGE, DEFENDER_SECONDS));
        }
    }

    private void summon(LivingEntity entity, Summoned summon) {
        entity.setRemoveWhenFarAway(false);
        entity.setCustomNameVisible(true);
        summoned.put(entity, summon);
        name(entity, summon);
        target(entity, summon);
    }

    /** Its name above it: its team, what it is, how long it has left. */
    private static void name(LivingEntity entity, Summoned summon) {
        entity.setCustomName(summon.team.color.chat() + summon.team.color.displayName() + " " + summon.name + " "
                + ChatColor.GRAY + BedwarsGame.clock(summon.left));
    }

    /** Once a second while the game is on: the creatures count down and go for the nearest enemy; the milk wears off. */
    void second() {
        for (Iterator<Map.Entry<LivingEntity, Summoned>> it = summoned.entrySet().iterator(); it.hasNext();) {
            Map.Entry<LivingEntity, Summoned> entry = it.next();
            LivingEntity entity = entry.getKey();
            Summoned summon = entry.getValue();
            summon.left--;
            if (!entity.isValid() || summon.left <= 0) {
                entity.remove();
                it.remove();
                continue;
            }
            name(entity, summon);
            target(entity, summon);
        }
        long now = System.currentTimeMillis();
        for (Gamer gamer : game.gamers.values()) {
            if (gamer.milkUntil != 0 && now >= gamer.milkUntil) {
                gamer.milkUntil = 0;
                Player player = Bukkit.getPlayer(gamer.id);
                if (player != null && game.present(gamer)) {
                    player.sendMessage(ChatColor.RED + "Your Magic Milk wore off!");
                }
            }
        }
    }

    /** The creature goes for the nearest enemy it sees, unless the one it goes for is still at hand. */
    private void target(LivingEntity entity, Summoned summon) {
        if (!(entity instanceof Creature)) {
            return;
        }
        Creature creature = (Creature) entity;
        LivingEntity now = creature.getTarget();
        if (now != null && now.isValid() && enemy(summon, now) && near(entity, now, summon.range)) {
            return;
        }
        Player nearest = null;
        double best = summon.range * summon.range;
        for (Gamer gamer : game.gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null || !enemy(summon, player) || !player.getWorld().equals(entity.getWorld())) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        creature.setTarget(nearest);
    }

    /**
     * True if a creature of this team may go for that entity: a player of another team who plays
     * in the game, or a creature of another team.
     */
    boolean enemy(Summoned summon, Entity target) {
        if (target instanceof Player) {
            Gamer gamer = game.gamer(target.getUniqueId());
            return gamer != null && gamer.team != summon.team && !gamer.eliminated && !gamer.respawning
                    && game.present(gamer) && ((Player) target).getGameMode() != GameMode.SPECTATOR;
        }
        Summoned other = summoned.get(target);
        return other != null && other.team != summon.team;
    }

    private static boolean near(Entity a, Entity b, double range) {
        return a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation()) <= range * range;
    }

    /** The player behind a creature's hit, for the kill: its owner, while in the game. Null for anything else. */
    Player owner(Entity damager) {
        Summoned summon = summoned.get(damager);
        if (summon == null) {
            return null;
        }
        Gamer gamer = game.gamer(summon.owner);
        return gamer != null && game.present(gamer) ? Bukkit.getPlayer(summon.owner) : null;
    }

    /** A creature died: it goes from the game. */
    void died(Entity entity) {
        summoned.remove(entity);
    }

    /** The game is over: the creatures go. */
    void clear() {
        for (LivingEntity entity : summoned.keySet()) {
            entity.remove();
        }
        summoned.clear();
    }

    // --- The Magic Milk -------------------------------------------------------------------------

    /** The Magic Milk drunk: this player sets off no trap for 30 seconds. */
    void milk(Player player, Gamer gamer) {
        gamer.milkUntil = System.currentTimeMillis() + MILK_MS;
        player.sendMessage(ChatColor.GREEN + "You drank Magic Milk: no trap will go off for you for 30 seconds!");
        player.playSound(player.getLocation(), Sound.DRINK, 1f, 1f);
    }

    // --- The Bridge Egg -------------------------------------------------------------------------

    /** A Bridge Egg thrown: wool of the thrower's team goes down under it as it flies, until it lands or is far. */
    void bridgeEgg(final Player thrower, final Egg egg) {
        final Gamer gamer = game.gamer(thrower.getUniqueId());
        if (gamer == null) {
            return;
        }
        final Location from = egg.getLocation();
        final byte wool = (byte) gamer.team.color.woolData();
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = game.repeat(1L, 1L, new Runnable() {
            private int ticks;

            @Override
            public void run() {
                Location at = egg.getLocation();
                if (!egg.isValid() || ++ticks > EGG_TICKS || game.phase() != BedwarsGame.Phase.PLAYING
                        || at.distanceSquared(from) > EGG_RANGE * EGG_RANGE) {
                    BedwarsGame.cancel(task[0]);
                    return;
                }
                boolean placed = false;
                for (int[] offset : EGG_BLOCKS) {
                    Block block = at.getWorld().getBlockAt(at.getBlockX() + offset[0], at.getBlockY() + offset[1],
                            at.getBlockZ() + offset[2]);
                    placed |= place(thrower, block, Material.WOOL, wool);
                }
                if (placed) {
                    at.getWorld().playSound(at, Sound.CHICKEN_EGG_POP, 1f, 1f);
                }
            }
        });
    }

    // --- The Compact Pop-up Tower ---------------------------------------------------------------

    /** A Compact Pop-up Tower put on this block: it goes up around it, facing the way the player looks. */
    void tower(final Player player, Block origin) {
        Gamer gamer = game.gamer(player.getUniqueId());
        if (gamer == null) {
            return;
        }
        int[] forward = forward(player.getLocation().getYaw());
        final int fx = forward[0];
        final int fz = forward[1];
        // to the right of someone who looks along (fx, fz)
        final int rx = -fz;
        final int rz = fx;
        final byte wool = (byte) gamer.team.color.woolData();
        final byte ladder = ladderData(fx, fz);
        final World world = origin.getWorld();
        final int ox = origin.getX();
        final int oy = origin.getY();
        final int oz = origin.getZ();
        final Location sound = origin.getLocation();
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = game.repeat(1L, 1L, new Runnable() {
            private int next;

            @Override
            public void run() {
                if (game.phase() != BedwarsGame.Phase.PLAYING) {
                    BedwarsGame.cancel(task[0]);
                    return;
                }
                for (int i = 0; i < TOWER_SPEED && next < TOWER.size(); i++, next++) {
                    Piece piece = TOWER.get(next);
                    Block block = world.getBlockAt(ox + piece.right * rx + piece.forward * fx, oy + piece.up,
                            oz + piece.right * rz + piece.forward * fz);
                    place(player, block, piece.ladder ? Material.LADDER : Material.WOOL, piece.ladder ? ladder : wool);
                }
                world.playSound(sound, Sound.CHICKEN_EGG_POP, 1f, 1f);
                if (next >= TOWER.size()) {
                    BedwarsGame.cancel(task[0]);
                }
            }
        });
    }

    /**
     * The tower, around a shaft of two blocks going forward from where it is put: the way in at the
     * front, two high, the ladder against the back wall up to the top. On the walls, a floor wider
     * by a block all around, with the hole of the ladder; on its edge, battlements.
     */
    static List<Piece> towerLayout() {
        List<Piece> pieces = new ArrayList<>();
        for (int up = 0; up < TOWER_WALLS; up++) {
            for (int forward = -1; forward <= 2; forward++) {
                for (int right = -1; right <= 1; right++) {
                    boolean shaft = right == 0 && (forward == 0 || forward == 1);
                    boolean door = right == 0 && forward == -1 && up < 2;
                    if (!shaft && !door) {
                        pieces.add(new Piece(right, up, forward, false));
                    }
                }
            }
            pieces.add(new Piece(0, up, 1, true));
        }
        for (int forward = -2; forward <= 3; forward++) {
            for (int right = -2; right <= 2; right++) {
                boolean hole = right == 0 && forward == 1;
                pieces.add(new Piece(right, TOWER_WALLS, forward, hole));
            }
        }
        for (int forward = -2; forward <= 3; forward++) {
            for (int right = -2; right <= 2; right++) {
                boolean edge = right == -2 || right == 2 || forward == -2 || forward == 3;
                if (edge) {
                    pieces.add(new Piece(right, TOWER_WALLS + 1, forward, false));
                }
            }
        }
        for (int forward = -2; forward <= 3; forward++) {
            for (int right = -2; right <= 2; right++) {
                boolean edge = right == -2 || right == 2 || forward == -2 || forward == 3;
                if (edge && (right + forward) % 2 == 0) {
                    pieces.add(new Piece(right, TOWER_WALLS + 2, forward, false));
                }
            }
        }
        return pieces;
    }

    /** The way a player looks, to the nearest of the four: x and z, one of them 0 (yaw 0 is south, +z). */
    static int[] forward(float yaw) {
        int quarter = Math.round((((yaw % 360) + 360) % 360) / 90f) % 4;
        switch (quarter) {
            case 0:
                return new int[] {0, 1};
            case 1:
                return new int[] {-1, 0};
            case 2:
                return new int[] {0, -1};
            default:
                return new int[] {1, 0};
        }
    }

    /** The data of a ladder on the wall in front of someone who looks along (fx, fz): it faces them. */
    static byte ladderData(int fx, int fz) {
        if (fz > 0) {
            return 2;
        }
        if (fz < 0) {
            return 3;
        }
        return (byte) (fx > 0 ? 4 : 5);
    }

    // --- Blocks ---------------------------------------------------------------------------------

    /** A block of a special item, where a player could put it: in the air, never in a player. True if it went. */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    private boolean place(Player player, Block block, Material type, byte data) {
        if (block.getY() < 0 || block.getY() > 255 || block.getType() != Material.AIR || game.cannotBuildAt(block) != null
                || occupied(block)) {
            return false;
        }
        game.instance().markPlaced(block);
        block.setTypeIdAndData(type.getId(), data, false);
        game.placed(player, block);
        return true;
    }

    /** True if a player of the game is in this block. */
    private boolean occupied(Block block) {
        for (Gamer gamer : game.gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null || !game.present(gamer) || player.getGameMode() == GameMode.SPECTATOR
                    || !player.getWorld().equals(block.getWorld())) {
                continue;
            }
            Location at = player.getLocation();
            if (overlaps(at.getX(), at.getY(), at.getZ(), block.getX(), block.getY(), block.getZ())) {
                return true;
            }
        }
        return false;
    }

    /** True if a player standing there (0.6 wide, 1.8 high) is partly in this block. */
    static boolean overlaps(double x, double y, double z, int bx, int by, int bz) {
        return bx < x + 0.3 && bx + 1 > x - 0.3 && bz < z + 0.3 && bz + 1 > z - 0.3 && by < y + 1.8 && by + 1 > y;
    }
}
