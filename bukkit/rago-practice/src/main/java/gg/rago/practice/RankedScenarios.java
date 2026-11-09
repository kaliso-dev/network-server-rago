package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Ranked scenarios: the moments that decide a Ranked Bedwars game, played again and again, each
 * with its score. Bed defense 1v2: alone on your island, two rushers cross the bridge for your bed
 * (end stone under wool). Close to you they fight you; else they dig their way in from where they
 * stand, block after block, and a hit stops their digging. Kill them both or knock them into the
 * void before the bed goes, as fast as you can. Final clutch: the last fight, on 2 hearts with no
 * bed, against an opponent at full health: win it, by the sword or the void; each one won adds one
 * to the streak.
 */
final class RankedScenarios extends Training {

    /** The foot of the bed; its head is one block west, away from the bridge. */
    static final int BED_X = -8;
    static final int BED_Y = 1;
    /** The middle of the bed (the foot covers x -8 to -7, the head -9 to -8). */
    static final double BED_MIDDLE_X = BED_X;
    static final double BED_MIDDLE_Y = BED_Y + 0.5;
    static final double BED_MIDDLE_Z = 0.5;
    /** The defense a team has by the first rush: end stone against the bed, wool over it. */
    static final Material[] DEFENSE = {Material.ENDER_STONE, Material.WOOL};
    static final int[] DEFENSE_DATA = {0, Shapes.BLUE};
    /** The player's island, from x -14 to this edge. */
    static final int ISLAND_EDGE = -2;
    /** The bridge at z 0, from the player's island to the rushers' one. */
    static final int BRIDGE_FROM = ISLAND_EDGE + 1;
    static final int BRIDGE_TO = 16;
    /** On the bridge a rusher keeps to the middle of it. */
    static final double LANE_Z = 0.5;
    /** Blocks a tick of a rusher on the ground (a sprinting player: 0.28). */
    static final double RUSHER_SPEED = 0.26;
    /** A rusher this close to the player, about their height, goes for them. */
    static final double ENGAGE = 4.5;
    /** This close it fights them rather than dig. */
    static final double CLOSE = 2.5;
    /** This close to the middle of the bed (from its own middle), a rusher digs at the defense. */
    static final double DIG_RANGE = 4.0;
    /** A rusher flies this long with the knockback of a hit before it walks again. */
    static final int KNOCKED_TICKS = 10;
    /** A rusher hit this recently goes after the player, closer than {@link #ANGRY_RANGE}. */
    static final int ANGRY_TICKS = 60;
    static final double ANGRY_RANGE = 10;
    /** The damage of a hit of a bot, before the armour (a wooden sword's). */
    static final double BOT_DAMAGE = 4.0;
    /** The rushers must be stopped within this time. */
    static final long DEFENSE_LIMIT_MS = 90_000L;
    /** The player's health in the final clutch: 2 hearts. */
    static final double CLUTCH_HEALTH = 4.0;
    /** Under 18 food there is no natural regeneration: the 2 hearts stay 2. */
    static final int NO_REGENERATION_FOOD = 17;
    /** The island of the final clutch, away from the bed defense's. */
    static final int CLUTCH_Z = 40;

    /** A rusher and what it is doing. */
    private static final class Rusher {
        final Zombie bot;
        boolean engaged;
        int hitAt = -1000;
        Block digging;
        Material diggingType;
        int digTicks;

        Rusher(Zombie bot) {
            this.bot = bot;
        }
    }

    private final List<List<int[]>> layers = Shapes.defense(BED_X, BED_Y, 0, -1, 0, DEFENSE.length);
    private final List<Rusher> rushers = new ArrayList<>();
    private Zombie opponent;
    private long started;
    private long endedAt;
    private boolean done;
    private boolean bedLost;
    private boolean bedUnderAttack;
    private int streak;
    private int bestStreak;

    RankedScenarios(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("bed-defense", "Bed defense 1v2", true, ""),
                new Variant("final-clutch", "Final clutch, 2 hearts", false, "clutches"));
    }

    private boolean defense() {
        return current().id.equals("bed-defense");
    }

    @Override
    String describe() {
        return defense()
                ? "two rushers cross the bridge for your bed (end stone under wool): kill them or knock them into the void before it goes."
                : "the last fight, on 2 hearts and with no bed, against an opponent at full health: win it.";
    }

    @Override
    void build() {
        // the bed defense: your island, the bridge, the island of the rushers
        Shapes.island(arena, -14, -5, ISLAND_EDGE, 5, Material.STAINED_CLAY, Shapes.BLUE, Material.STAINED_CLAY, Shapes.GRAY);
        Shapes.floor(arena, BRIDGE_FROM, 0, BRIDGE_TO, 0, 0, Material.WOOL, Shapes.WHITE);
        Shapes.island(arena, BRIDGE_TO + 1, -3, BRIDGE_TO + 7, 3, Material.STAINED_CLAY, Shapes.RED, Material.STAINED_CLAY, Shapes.GRAY);
        // the final clutch: a middle island over the void
        Shapes.island(arena, -5, CLUTCH_Z - 4, 5, CLUTCH_Z + 4, Material.STAINED_CLAY, Shapes.WHITE, Material.STAINED_CLAY, Shapes.GRAY);
    }

    @Override
    Location spawnOf(UUID player) {
        return defense() ? arena.at(-3.5, 1, 0.5, -90f) : arena.at(-3.5, 1, CLUTCH_Z + 0.5, -90f);
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.IRON_SWORD), stack(Material.WOOL, Shapes.BLUE)};
    }

    @Override
    void armour(Player player) {
        ironArmour(player, Color.BLUE);
    }

    @Override
    void effects(Player player) {
        if (!defense()) {
            player.setHealth(CLUTCH_HEALTH);
            player.setFoodLevel(NO_REGENERATION_FOOD);
        }
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return !done && arena.contains(block, CLUTCH_Z + 10, 6, 12);
    }

    /** Your blocks and those of your defense, as in a game; never your own bed. */
    @Override
    boolean canBreak(Player player, Block block) {
        return arena.isPlaced(block) && block.getType() != Material.BED_BLOCK;
    }

    @Override
    void roundReset() {
        removeBots();
        started = 0;
        endedAt = 0;
        done = false;
        bedLost = false;
        bedUnderAttack = false;
        if (defense()) {
            Shapes.putBed(arena, BED_X, BED_Y, 0, -1, 0);
            Shapes.putDefense(arena, layers, DEFENSE, DEFENSE_DATA);
        }
    }

    @Override
    void onFight() {
        started = now();
        if (defense()) {
            rushers.add(rusher(arena.at(19.5, 1, LANE_Z, 90f)));
            rushers.add(rusher(arena.at(21.5, 1, LANE_Z, 90f)));
            title(ChatColor.RED + "" + ChatColor.BOLD + "RUSH!", ChatColor.WHITE + "Two rushers are coming for your bed");
        } else {
            opponent = Bots.spawn(this, arena.at(3.5, 1, CLUTCH_Z + 0.5, 90f), "Opponent", Color.RED, 2);
            Bots.chase(opponent, player());
            title(ChatColor.GOLD + "" + ChatColor.BOLD + "FINAL CLUTCH", ChatColor.WHITE + "2 hearts, no bed: win the fight");
        }
    }

    /** A rusher walks as this training steers it, not as a zombie would: its own pace is none. */
    private Rusher rusher(Location at) {
        Zombie bot = Bots.spawn(this, at, "Rusher", Color.RED, 0);
        bot.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 6, false, false), true);
        return new Rusher(bot);
    }

    @Override
    void ended() {
        removeBots();
    }

    private void removeBots() {
        for (Rusher rusher : rushers) {
            Bots.remove(this, rusher.bot);
        }
        rushers.clear();
        Bots.remove(this, opponent);
        opponent = null;
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done) {
            return;
        }
        if (defense()) {
            defend(player);
        } else {
            clutch(player);
        }
        refresh();
    }

    // --- Bed defense ----------------------------------------------------------------------------

    private void defend(Player player) {
        bedUnderAttack = false;
        boolean bedGone = false;
        for (Iterator<Rusher> it = rushers.iterator(); it.hasNext();) {
            Rusher rusher = it.next();
            Location at = rusher.bot.getLocation();
            if (!rusher.bot.isValid() || at.getY() < arena.floorY() - 4 || !arena.near(at, 60)) {
                it.remove();
                Bots.remove(this, rusher.bot);
                stopped(player, "was knocked into the void by");
                continue;
            }
            if (!bedGone && step(player, rusher, at)) {
                bedGone = true;
            }
        }
        if (bedGone) {
            breakBed();
            bedLost = true;
            lose(player, "BED DESTROYED!", "The rushers broke your bed.");
            return;
        }
        if (rushers.isEmpty()) {
            defended(player);
            return;
        }
        if (now() - started > DEFENSE_LIMIT_MS) {
            lose(player, "TIME'S UP!", "The rushers are still there after " + DEFENSE_LIMIT_MS / 1000 + " seconds.");
            return;
        }
        if (tickCount % 5 == 0) {
            for (Rusher rusher : rushers) {
                name(rusher.bot, "Rusher");
            }
        }
    }

    /** One tick of a rusher: whom it fights, where it walks, what it digs. True if it broke the bed. */
    private boolean step(Player player, Rusher rusher, Location at) {
        Zombie bot = rusher.bot;
        Location to = player.getLocation();
        double px = to.getX() - at.getX();
        double pz = to.getZ() - at.getZ();
        double distance = Math.sqrt(px * px + pz * pz);
        boolean angry = tickCount - rusher.hitAt < ANGRY_TICKS && distance < ANGRY_RANGE;
        rusher.engaged = Math.abs(to.getY() - at.getY()) < 3 && (distance < ENGAGE || angry);
        if (rusher.engaged) {
            Bots.chase(bot, player);
        } else if (bot.getTarget() != null) {
            bot.setTarget(null);
        }
        double rx = at.getX() - arena.centreX();
        double ry = at.getY() + 1 - arena.floorY();
        double rz = at.getZ() - arena.centreZ();
        boolean atBed = atBed(rx, ry, rz);
        boolean fighting = rusher.engaged && distance < CLOSE;
        double wx = 0;
        double wz = 0;
        if (rusher.engaged && (fighting || !atBed)) {
            if (distance > 1.0) {
                wx = px;
                wz = pz;
            }
        } else if (!atBed) {
            if (rx > BRIDGE_TO + 0.5) {
                // on their island: straight to the start of the bridge
                wx = BRIDGE_TO + 0.5 - rx;
                wz = LANE_Z - rz;
            } else if (rx > BRIDGE_FROM - 0.5) {
                // on the bridge: along its middle
                wx = -2;
                wz = LANE_Z - rz;
            } else {
                wx = BED_MIDDLE_X - rx;
                wz = BED_MIDDLE_Z - rz;
            }
        }
        double length = Math.sqrt(wx * wx + wz * wz);
        Block dig = null;
        if (length > 1e-6) {
            dig = walk(rusher, at, wx / length, wz / length);
        } else {
            halt(rusher);
        }
        if (atBed && !fighting) {
            dig = tunnel(rx, ry, rz);
            if (dig != null) {
                bedUnderAttack = true;
            }
        }
        return dig(rusher, dig);
    }

    /** True if a rusher with its middle there (from the arena's centre) can dig at the defense. */
    static boolean atBed(double x, double y, double z) {
        double dx = x - BED_MIDDLE_X;
        double dy = y - BED_MIDDLE_Y;
        double dz = z - BED_MIDDLE_Z;
        return dx * dx + dy * dy + dz * dz <= DIG_RANGE * DIG_RANGE;
    }

    /**
     * The rusher walks this way (a unit step): up a block in its way, or it digs through a block
     * the player placed there; it never walks off an edge. Returns the block to dig, or null.
     */
    private Block walk(Rusher rusher, Location at, double wx, double wz) {
        Block feet = at.clone().add(wx * 0.6, 0.1, wz * 0.6).getBlock();
        Block head = feet.getRelative(0, 1, 0);
        if (head.getType().isSolid()) {
            halt(rusher);
            return arena.isPlaced(head) ? head : null;
        }
        boolean jump = false;
        if (feet.getType().isSolid()) {
            if (feet.getRelative(0, 2, 0).getType().isSolid() || at.clone().add(0, 2.1, 0).getBlock().getType().isSolid()) {
                halt(rusher);
                return arena.isPlaced(feet) ? feet : null;
            }
            jump = true;
        } else {
            Block floor = feet.getRelative(0, -1, 0);
            if (!floor.getType().isSolid() && !floor.getRelative(0, -1, 0).getType().isSolid()) {
                halt(rusher);
                return null;
            }
        }
        if (free(rusher)) {
            rusher.bot.setVelocity(new Vector(wx * RUSHER_SPEED, jump ? 0.42 : rusher.bot.getVelocity().getY(), wz * RUSHER_SPEED));
        }
        return null;
    }

    /** On the ground, and not flying from a hit: the rusher walks as it is told. */
    private boolean free(Rusher rusher) {
        return rusher.bot.isOnGround() && tickCount - rusher.hitAt > KNOCKED_TICKS;
    }

    private void halt(Rusher rusher) {
        if (free(rusher)) {
            rusher.bot.setVelocity(new Vector(0, rusher.bot.getVelocity().getY(), 0));
        }
    }

    /**
     * What a rusher at the bed digs, from where it stands: the place of the outer layer nearest to
     * it; once that is open, the nearest one of the layer under it; and so on down to the bed, that
     * goes when nothing is left over it on that side. Null when the bed is gone already.
     */
    private Block tunnel(double x, double y, double z) {
        int[] place = tunnel(layers, p -> arena.block(p[0], p[1], p[2]).getType() != Material.AIR, x, y, z);
        if (place != null) {
            return arena.block(place[0], place[1], place[2]);
        }
        Block foot = arena.block(BED_X, BED_Y, 0);
        Block head = arena.block(BED_X - 1, BED_Y, 0);
        Block nearer = Math.abs(x - (BED_X + 0.5)) <= Math.abs(x - (BED_X - 0.5)) ? foot : head;
        return nearer.getType() == Material.BED_BLOCK ? nearer : null;
    }

    /** The place to dig of {@link #tunnel(double, double, double)}, or null for the bed itself. */
    static int[] tunnel(List<List<int[]>> layers, Predicate<int[]> solid, double x, double y, double z) {
        for (int layer = layers.size() - 1; layer >= 0; layer--) {
            int[] place = nearest(layers.get(layer), x, y, z);
            if (place != null && solid.test(place)) {
                return place;
            }
        }
        return null;
    }

    /** The place nearest to this point (from the middle of each block). */
    static int[] nearest(List<int[]> places, double x, double y, double z) {
        int[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int[] place : places) {
            double dx = place[0] + 0.5 - x;
            double dy = place[1] + 0.5 - y;
            double dz = place[2] + 0.5 - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = place;
            }
        }
        return best;
    }

    /** Ticks for a rusher to break a block: shears on wool, a pickaxe on end stone, an axe on wood. */
    static int digTicks(Material type) {
        switch (type) {
            case WOOL:
                return 8;
            case ENDER_STONE:
                return 30;
            case WOOD:
                return 14;
            case BED_BLOCK:
                return 5;
            default:
                return 12;
        }
    }

    private static Sound digSound(Material type) {
        switch (type) {
            case WOOL:
                return Sound.DIG_WOOL;
            case WOOD:
            case BED_BLOCK:
                return Sound.DIG_WOOD;
            default:
                return Sound.DIG_STONE;
        }
    }

    /** One tick of digging this block (null: none). True if it was the bed, now broken. */
    private boolean dig(Rusher rusher, Block block) {
        if (block == null) {
            rusher.digging = null;
            rusher.digTicks = 0;
            return false;
        }
        Material type = block.getType();
        if (!block.equals(rusher.digging) || type != rusher.diggingType) {
            rusher.digging = block;
            rusher.diggingType = type;
            rusher.digTicks = 0;
        }
        rusher.digTicks++;
        if (rusher.digTicks % 4 == 1) {
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), digSound(type), 0.8f, 0.8f);
        }
        if (rusher.digTicks < digTicks(type)) {
            return false;
        }
        rusher.digging = null;
        rusher.digTicks = 0;
        if (type == Material.BED_BLOCK) {
            return true;
        }
        breakBlock(block);
        return false;
    }

    @SuppressWarnings("deprecation") // Material#getId: the block of the break effect
    private void breakBlock(Block block) {
        Material type = block.getType();
        arena.removed(block);
        block.setType(Material.AIR);
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, type.getId());
    }

    /** Both halves of the bed go at once: no physics, so that no bed drops. */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData and Material#getId: the 1.8 way
    private void breakBed() {
        Block foot = arena.block(BED_X, BED_Y, 0);
        Block head = arena.block(BED_X - 1, BED_Y, 0);
        arena.removed(foot);
        arena.removed(head);
        foot.getWorld().playEffect(foot.getLocation(), Effect.STEP_SOUND, Material.BED_BLOCK.getId());
        head.setTypeIdAndData(0, (byte) 0, false);
        foot.setTypeIdAndData(0, (byte) 0, false);
    }

    /** A rusher is out, said as Hypixel says it. */
    private void stopped(Player player, String how) {
        int left = rushers.size();
        player.sendMessage(ChatColor.RED + "Rusher " + ChatColor.GRAY + how + " " + ChatColor.BLUE + player.getName()
                + ChatColor.GRAY + ". " + ChatColor.AQUA + ChatColor.BOLD + "FINAL KILL!"
                + (left > 0 ? ChatColor.GRAY + " " + left + (left == 1 ? " rusher" : " rushers") + " left." : ""));
        sound(player, Sound.ORB_PICKUP, 1.4f);
    }

    private void defended(Player player) {
        done = true;
        endedAt = now();
        long time = endedAt - started;
        title(ChatColor.GREEN + "" + ChatColor.BOLD + "BED DEFENDED!", ChatColor.WHITE + time(time) + " s");
        score(time);
        again(60L);
    }

    // --- Final clutch ---------------------------------------------------------------------------

    private void clutch(Player player) {
        if (opponent == null) {
            return;
        }
        Location at = opponent.getLocation();
        if (!opponent.isValid() || at.getY() < arena.floorY() - 4 || !arena.near(at, CLUTCH_Z + 30)) {
            won(player, "was knocked into the void by");
            return;
        }
        if (tickCount % 20 == 0) {
            Bots.chase(opponent, player);
        }
        if (tickCount % 5 == 0) {
            name(opponent, "Opponent");
        }
    }

    private void won(Player player, String how) {
        done = true;
        endedAt = now();
        Bots.remove(this, opponent);
        opponent = null;
        streak++;
        bestStreak = Math.max(bestStreak, streak);
        player.sendMessage(ChatColor.RED + "Opponent " + ChatColor.GRAY + how + " " + ChatColor.BLUE + player.getName()
                + ChatColor.GRAY + ". " + ChatColor.AQUA + ChatColor.BOLD + "FINAL KILL!");
        title(ChatColor.GOLD + "" + ChatColor.BOLD + "CLUTCH!", ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        sound(player, Sound.ORB_PICKUP, 1.2f);
        score(streak, true);
        again(50L);
    }

    // --- Both -----------------------------------------------------------------------------------

    /** The bot's name, with its health under it as Hypixel shows a player's. */
    private static void name(Zombie bot, String name) {
        bot.setCustomName(ChatColor.RED + name + " " + ChatColor.WHITE + (int) Math.ceil(bot.getHealth()) + ChatColor.RED + "❤");
    }

    private Rusher rusherOf(Entity entity) {
        for (Rusher rusher : rushers) {
            if (rusher.bot.equals(entity)) {
                return rusher;
            }
        }
        return null;
    }

    /** The attempt is lost: the bots go, the player is told why, and starts again in a moment. */
    private void lose(Player player, String title, String why) {
        done = true;
        endedAt = now();
        removeBots();
        String end = "";
        if (!defense() && streak > 0) {
            end = " The streak of " + streak + " ends.";
            streak = 0;
        }
        title(ChatColor.RED + "" + ChatColor.BOLD + title, ChatColor.GRAY + why);
        player.sendMessage(ChatColor.RED + why + ChatColor.GRAY + end);
        sound(player, bedLost ? Sound.ENDERDRAGON_GROWL : Sound.VILLAGER_NO, 1f);
        again(50L);
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        Entity damager = damager(event);
        if (done || damager == null || (!damager.equals(opponent) && rusherOf(damager) == null)) {
            return false;
        }
        event.setDamage(BOT_DAMAGE);
        return true;
    }

    @Override
    void lethal(Player victim, Player killer) {
        if (!done) {
            lose(victim, "YOU DIED!", defense() ? "Alone against two: your bed is theirs." : "The final fight is lost.");
        }
    }

    @Override
    void fell(Player player) {
        if (done) {
            player.teleport(spawnOf(me));
            player.setFallDistance(0f);
            return;
        }
        lose(player, "YOU FELL!", defense() ? "Into the void: your bed is theirs." : "Into the void: the final fight is lost.");
    }

    /** The player's hits hurt the bots for real; a hit on a rusher stops its digging. */
    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (done) {
            event.setCancelled(true);
            return;
        }
        if (target.equals(opponent)) {
            return;
        }
        Rusher rusher = rusherOf(target);
        if (rusher == null) {
            event.setCancelled(true);
            return;
        }
        rusher.hitAt = tickCount;
        rusher.digging = null;
        rusher.digTicks = 0;
    }

    @Override
    boolean mayTarget(Entity bot, Entity target) {
        if (!(target instanceof Player) || !target.getUniqueId().equals(me)) {
            return false;
        }
        Rusher rusher = rusherOf(bot);
        return rusher == null || rusher.engaged;
    }

    @Override
    void entityDied(Entity entity) {
        Player player = player();
        if (entity.equals(opponent)) {
            opponent = null;
            if (player != null && !done) {
                won(player, "was killed by");
            }
            return;
        }
        Rusher rusher = rusherOf(entity);
        if (rusher != null) {
            rushers.remove(rusher);
            if (player != null && !done) {
                stopped(player, "was killed by");
            }
        }
    }

    @Override
    void trainingLines(List<String> lines) {
        if (defense()) {
            long elapsed = started == 0 ? 0 : (done ? endedAt : now()) - started;
            lines.add(ChatColor.WHITE + "Rushers: " + ChatColor.RED + rushers.size() + ChatColor.GRAY + "/2");
            lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
            lines.add(ChatColor.WHITE + "Bed: " + (bedLost ? ChatColor.RED + "✘" : bedUnderAttack
                    ? ChatColor.RED + "under attack!" : ChatColor.GREEN + "✔"));
        } else {
            lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
            lines.add(ChatColor.WHITE + "Best today: " + ChatColor.GREEN + bestStreak);
            lines.add(ChatColor.GRAY + "No bed: one death ends it");
        }
    }
}
