package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Block Pattern Practice (the owner's ask of 27/11): the techniques of his video Block Method, one
 * level each ({@link BlockPatterns}), the first ten for now. RagoBot runs at the player along the
 * way of the technique, with a player's physics; the player places the technique's blocks (light
 * blue wool, shown by blue dust until placed), then hits RagoBot first: a success, its time on the
 * technique's board. RagoBot hits the player first, the time runs out or the player falls: the same
 * technique again at once. After a success the next technique comes by itself (a setting turns it
 * off). Its hits hurt no one; it hits only what it sees, through no block. Main thread only.
 */
final class BlockPatternPractice extends Training {

    /** The skin on RagoBot's head: the owner's account, the skin of RagoBot in the lobby. */
    static final String SKIN = "qRago";
    /** Ticks after its start a try may last, besides its wait. */
    static final int LIMIT_TICKS = 15 * 20;
    /** Ticks RagoBot's shears take on a block of wool, as a player's (0.25 s and a little). */
    static final int BREAK_TICKS = 6;
    /** Ticks RagoBot sees the player in its reach before its first click: its reaction. */
    static final int REACTION_TICKS = 4;
    /** Its clicks a second once it may hit, and the chance that one lands. */
    static final double CPS = 9;
    static final double ACCURACY = 0.9;
    /** Its reach, from its eyes to the player's box, as a fair player's. */
    static final double REACH = 3.0;
    /** Ticks after a success before the next try. */
    static final int NEXT_TICKS = 30;

    static final Variant[] LEVELS = variants();

    private final Random random = new Random();
    private final Setting autoNext = toggle("Next technique on success", true);
    private final Setting guide = toggle("Show the pattern", true);
    private Zombie bot;
    private PatternBot brain;
    /** The decor of this try (a tunnel): placed for the round, never broken. */
    private final Set<Block> decor = new HashSet<>();
    /** True once every block of the technique was there in this try. */
    private boolean built;
    private boolean told;
    private boolean done;
    private int inReach;
    private double clicks;
    private int breakTicks;
    private int crackId = BlockCracks.newId();
    private Block breaking;
    private long startedAt;
    private int successes;
    private int fails;

    BlockPatternPractice(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, LEVELS);
    }

    static Variant[] variants() {
        Variant[] variants = new Variant[BlockPatterns.TECHNIQUES.length];
        for (int i = 0; i < variants.length; i++) {
            BlockPatterns.Technique t = BlockPatterns.TECHNIQUES[i];
            variants[i] = new Variant(t.id, t.name, true, "");
        }
        return variants;
    }

    private BlockPatterns.Technique technique() {
        return BlockPatterns.TECHNIQUES[Math.min(variant, BlockPatterns.TECHNIQUES.length - 1)];
    }

    /** A fall or a hit of RagoBot gives the next try by itself: no blaze rod. */
    @Override
    boolean restartUsed() {
        return false;
    }

    @Override
    String describe() {
        return technique().help;
    }

    @Override
    void build() {
        Shapes.island(arena, -6, -5, 6, 15, Material.STAINED_CLAY, 6, Material.STAINED_CLAY, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(0.5, 1, 0.5, 0f);
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD), stack(Material.WOOL, Shapes.LIGHT_BLUE),
            new ItemStack(Material.SHEARS)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        removeBot();
        built = false;
        told = false;
        done = false;
        inReach = 0;
        clicks = 0;
        stopBreaking();
        decor.clear();
        BlockPatterns.Technique technique = technique();
        for (int[] box : technique.decor) {
            for (int x = Math.min(box[0], box[3]); x <= Math.max(box[0], box[3]); x++) {
                for (int y = Math.min(box[1], box[4]); y <= Math.max(box[1], box[4]); y++) {
                    for (int z = Math.min(box[2], box[5]); z <= Math.max(box[2], box[5]); z++) {
                        int ax = PatternBot.arenaX(x);
                        arena.put(ax, 1 + y, z, Material.STAINED_CLAY, Shapes.LIGHT_GRAY);
                        decor.add(arena.block(ax, 1 + y, z));
                    }
                }
            }
        }
    }

    @Override
    void onFight() {
        BlockPatterns.Technique technique = technique();
        brain = new PatternBot(technique);
        double[] start = brain.start();
        bot = Bots.spawn(this, arena.at(start[0], 1, start[1], 180f), "RagoBot", Color.AQUA, 0);
        // its own walking off: the game moves it
        bot.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false), true);
        bot.getEquipment().setHelmet(head());
        bot.getEquipment().setItemInHand(new ItemStack(Material.STONE_SWORD));
        Bots.chase(bot, player());
        startedAt = now();
        Player player = player();
        if (player != null) {
            player.sendMessage(ChatColor.AQUA + technique.name + ChatColor.GRAY + ": " + technique.help);
        }
    }

    /** RagoBot's head: the owner's skin. */
    private static ItemStack head() {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwner(SKIN);
        head.setItemMeta(meta);
        return head;
    }

    @Override
    void ended() {
        removeBot();
        stopBreaking();
    }

    private void removeBot() {
        if (bot != null) {
            Bots.remove(this, bot);
            bot = null;
        }
        brain = null;
    }

    @Override
    boolean mayTarget(Entity entity, Entity target) {
        return target instanceof Player && target.getUniqueId().equals(me);
    }

    @Override
    void tick() {
        refresh();
        Player player = player();
        if (player == null || done) {
            return;
        }
        BlockPatterns.Technique technique = technique();
        if (guide.on() && tickCount % 4 == 0) {
            dust(technique);
        }
        if (!built && complete(technique)) {
            built = true;
            player.sendMessage(ChatColor.GREEN + "Pattern built! " + ChatColor.GRAY + "Now hit RagoBot first.");
            sound(player, Sound.ORB_PICKUP, 1.4f);
        }
        if (bot == null || brain == null) {
            return;
        }
        if (tickCount > technique.wait + LIMIT_TICKS) {
            failed("Too slow: " + technique.name + " again.");
            return;
        }
        if (tickCount % 20 == 0) {
            Bots.chase(bot, player);
        }
        Location at = bot.getLocation();
        if (!bot.isValid() || at.getY() < arena.floorY() - 6) {
            if (built) {
                won(player);
            } else {
                failed("RagoBot fell before your pattern was built.");
            }
            return;
        }
        if (tickCount < technique.wait) {
            return;
        }
        double[] o = arena.offset(at);
        double[] p = arena.offset(player.getLocation());
        Vector speed = bot.getVelocity();
        double[] next = brain.steer(o[0], o[1] - 1, o[2], speed.getX(), speed.getY(), speed.getZ(), bot.isOnGround(), p[0],
                p[2], (x, y, z) -> arena.block(x, 1 + y, z).getType().isSolid());
        bot.setVelocity(new Vector(next[0], next[1], next[2]));
        breakTick(brain.breaking());
        hitTick(player, at);
    }

    /** RagoBot's shears on the block in its way: the cracks, then the block goes. */
    @SuppressWarnings("deprecation") // Block#getTypeId: the id of the block for its sound
    private void breakTick(int[] wanted) {
        Block block = wanted == null ? null : arena.block(wanted[0], 1 + wanted[1], wanted[2]);
        if (block == null || !arena.isPlaced(block) || decor.contains(block)) {
            stopBreaking();
            return;
        }
        if (!block.equals(breaking)) {
            stopBreaking();
            breaking = block;
        }
        breakTicks++;
        BlockCracks.show(online(), crackId, block, BlockCracks.stage(breakTicks, BREAK_TICKS), plugin.getLogger());
        if (breakTicks >= BREAK_TICKS) {
            block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getTypeId());
            arena.restore(block);
            stopBreaking();
            brain.broke();
        }
    }

    private void stopBreaking() {
        if (breaking != null) {
            BlockCracks.show(online(), crackId, breaking, -1, plugin.getLogger());
            crackId = BlockCracks.newId();
        }
        breaking = null;
        breakTicks = 0;
    }

    /** RagoBot's clicks: once it sees the player in its reach, after its reaction; a hit ends the try. */
    private void hitTick(Player player, Location at) {
        Location target = player.getLocation();
        double reach = ComboAi.reach(at.getX(), at.getY() + ComboAi.EYES, at.getZ(), target.getX(), target.getY(),
                target.getZ());
        if (reach > REACH || !bot.hasLineOfSight(player)) {
            inReach = 0;
            return;
        }
        if (++inReach <= REACTION_TICKS) {
            return;
        }
        clicks += CPS / 20.0;
        while (clicks >= 1) {
            clicks--;
            if (random.nextDouble() < ACCURACY) {
                player.playEffect(EntityEffect.HURT);
                sound(player, Sound.HURT_FLESH, 1f);
                fails++;
                failed("RagoBot hit you first. " + technique().name + " again.");
                return;
            }
        }
    }

    /** True if every block of the technique is there. */
    private boolean complete(BlockPatterns.Technique technique) {
        for (int[] b : technique.blocks) {
            if (!arena.block(PatternBot.arenaX(b[0]), 1 + b[1], b[2]).getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    /** Blue dust in each place of the technique still empty. */
    private void dust(BlockPatterns.Technique technique) {
        World world = arena.world();
        for (int[] b : technique.blocks) {
            int x = PatternBot.arenaX(b[0]);
            if (arena.block(x, 1 + b[1], b[2]).getType().isSolid()) {
                continue;
            }
            for (double[] corner : CORNERS) {
                Location at = arena.at(x + corner[0], 1 + b[1] + corner[1], b[2] + corner[2]);
                // a count of 0: one dust exactly there, the offsets give its colour (light blue)
                world.spigot().playEffect(at, Effect.COLOURED_DUST, 0, 0, 0.001f, 0.8f, 1f, 1f, 0, 48);
            }
        }
    }

    private static final double[][] CORNERS = {
        {0.5, 0.5, 0.5}, {0.1, 0.1, 0.1}, {0.9, 0.1, 0.1}, {0.1, 0.1, 0.9}, {0.9, 0.1, 0.9},
        {0.1, 0.9, 0.1}, {0.9, 0.9, 0.1}, {0.1, 0.9, 0.9}, {0.9, 0.9, 0.9},
    };

    /** The player hits RagoBot: no damage, its knockback; once the pattern is built, a success. */
    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (bot == null || !bot.equals(target) || done) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(0.0);
        if (!built) {
            if (!told) {
                told = true;
                attacker.sendMessage(ChatColor.YELLOW + "Build the pattern first, then hit RagoBot.");
            }
            return;
        }
        won(attacker);
    }

    private void won(Player player) {
        done = true;
        successes++;
        long time = Math.max(1, now() - startedAt);
        BlockPatterns.Technique technique = technique();
        player.sendMessage(ChatColor.GREEN + technique.name + " done! " + ChatColor.GRAY + "(" + format(time) + ")");
        sound(player, Sound.LEVEL_UP, 1.2f);
        score(time, true);
        final int roundNow = round;
        later(NEXT_TICKS, () -> {
            if (round != roundNow) {
                return;
            }
            Player now = player();
            if (autoNext.on() && now != null) {
                if (variant == variants.size() - 1) {
                    now.sendMessage(ChatColor.GOLD + "Every technique done! " + ChatColor.GRAY + "Back to the first.");
                }
                nextLevel(now, 1);
            } else {
                again(0L);
            }
        });
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        // the zombie's own hits never go through: RagoBot's are the game's
        return false;
    }

    @Override
    void entityHurt(Entity entity, EntityDamageEvent event) {
        event.setCancelled(true);
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return !done && near(block, 0, 5, 10, -1, 5);
    }

    @Override
    boolean canBreak(Player player, Block block) {
        return arena.isPlaced(block) && !decor.contains(block);
    }

    @Override
    void fell(Player player) {
        fails++;
        failed("You fell. " + technique().name + " again.");
    }

    @Override
    void trainingLines(List<String> lines) {
        BlockPatterns.Technique technique = technique();
        int placed = 0;
        for (int[] b : technique.blocks) {
            if (arena.block(PatternBot.arenaX(b[0]), 1 + b[1], b[2]).getType().isSolid()) {
                placed++;
            }
        }
        lines.add(ChatColor.WHITE + "Technique: " + ChatColor.AQUA + technique.name);
        lines.add(ChatColor.WHITE + "Blocks: " + (built ? ChatColor.GREEN : ChatColor.YELLOW) + placed + ChatColor.GRAY + "/"
                + technique.blocks.length);
        lines.add(ChatColor.WHITE + "Done: " + ChatColor.GREEN + successes + ChatColor.GRAY + " · Missed: " + ChatColor.RED
                + fails);
    }

    /** The ids of the levels, for the tests and the records. */
    static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Variant v : LEVELS) {
            ids.add(v.id);
        }
        return ids;
    }
}
