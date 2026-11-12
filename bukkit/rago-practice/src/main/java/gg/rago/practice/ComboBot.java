package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
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
 * Combo bot: sparring against a bot that plays as a player would ({@link ComboAi}), on the arenas
 * of the boxing (or a ring without them). It sprints at you, strafes, clicks at its CPS, hits
 * with a player's knockback, w-taps and jump resets; the level sets how well. The hits hurt no
 * one, only their knockback counts, as in boxing: a combo is the hits in a row on the bot before
 * it hits back (or two seconds without a hit). The score is the longest combo. The bot's own
 * walking is turned off (slowness): the game moves it, tick by tick, with a player's physics.
 */
final class ComboBot extends Training {

    /** A combo ends after this long without a hit. */
    private static final int COMBO_TICKS = 40;
    /** On a map, the bot starts this far in front of the player. */
    static final double BOT_AHEAD = 8;

    private final Random random = new Random();
    private final Setting hitsBack = toggle("Bot hits back", true);
    private Zombie bot;
    private ComboAi ai;
    /** True while the bot's own hit is going through (its zombie hits are not let through). */
    private boolean botHitting;
    private boolean botLanded;
    private int combo;
    private int best;
    private int lastHit;
    private int myHits;
    private int botHits;
    private int botCombo;
    private int botBest;

    ComboBot(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, variants());
    }

    static Variant[] variants() {
        Variant[] variants = new Variant[ComboAi.LEVELS.length];
        for (int i = 0; i < variants.length; i++) {
            variants[i] = new Variant(ComboAi.LEVELS[i].id, ComboAi.LEVELS[i].label, false, "hits");
        }
        return variants;
    }

    /** A fall or a miss gives the next attempt by itself: the bed in the hotbar. */
    @Override
    boolean restartUsed() {
        return false;
    }

    @Override
    String describe() {
        ComboAi.Level level = ComboAi.level(current().id);
        return "a bot that fights as a player: " + (int) level.cps + " CPS, " + level.reach + " blocks of reach"
                + (level.sprints ? ", sprint hits" : "") + (level.wTap > 0 ? ", w-taps" : "")
                + (level.jumpReset > 0 ? ", jump resets" : "") + ". Combo it without being hit back.";
    }

    @Override
    void build() {
        if (arena.map() != null) {
            return;
        }
        Shapes.floor(arena, -11, -11, 11, 11, 0, Material.WOOL, Shapes.WHITE);
        Shapes.walls(arena, -12, -12, 12, 12, 0, 1, Material.STAINED_CLAY, Shapes.GRAY);
        Shapes.floor(arena, -12, -12, 12, 12, -1, Material.STAINED_CLAY, Shapes.GRAY);
        Shapes.walls(arena, -12, -12, 12, 12, 1, 4, Material.BARRIER, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        Location start = arena.mapStart(0);
        return start != null ? start : arena.at(-5.5, 1, 0.5, -90f);
    }

    /**
     * Where the bot starts: on a map, this far in front of the player towards the other start,
     * facing them; else across the ring.
     */
    private Location botStart() {
        Location mine = arena.mapStart(0);
        Location other = arena.mapStart(1);
        if (mine == null || other == null) {
            return arena.at(5.5, 1, 0.5, 90f);
        }
        Location at = mine.clone().add(other.toVector().subtract(mine.toVector()).normalize().multiply(BOT_AHEAD));
        at.setYaw(mine.getYaw() + 180f);
        return at;
    }

    /** How far the bot may go from the middle of the zone before it is put back. */
    private double range() {
        return arena.map() != null ? 70 : 14;
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
        combo = 0;
        lastHit = 0;
        myHits = 0;
        botHits = 0;
        botCombo = 0;
        Bots.remove(this, bot);
        bot = null;
        ai = null;
    }

    @Override
    void onFight() {
        ComboAi.Level level = ComboAi.level(current().id);
        ai = new ComboAi(level, random);
        bot = Bots.spawn(this, botStart(), level.label + " bot", Color.RED, 0);
        // its own walking off: the game moves it
        bot.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false), true);
        bot.getEquipment().setItemInHand(new ItemStack(Material.DIAMOND_SWORD));
        // its target, so that it looks at the player; its own attacks are not let through
        Bots.chase(bot, player());
    }

    @Override
    void ended() {
        Bots.remove(this, bot);
        bot = null;
        ai = null;
    }

    @Override
    void tick() {
        refresh();
        Player player = player();
        if (player == null || bot == null || ai == null) {
            return;
        }
        if (tickCount % 20 == 0) {
            Bots.chase(bot, player);
        }
        Location at = bot.getLocation();
        if (!bot.isValid() || !arena.near(at, range()) || at.getY() < arena.floorY() - 6) {
            bot.teleport(botStart());
            bot.setVelocity(new Vector());
            return;
        }
        Location target = player.getLocation();
        ai.see(target.getX(), target.getY(), target.getZ());
        Vector speed = bot.getVelocity();
        double[] next = ai.steer(at.getX(), at.getZ(), speed.getX(), speed.getY(), speed.getZ(), bot.isOnGround());
        bot.setVelocity(new Vector(next[0], next[1], next[2]));
        if (hitsBack.on()) {
            for (int click = ai.clicks(); click > 0; click--) {
                if (click(player, at, target)) {
                    break;
                }
            }
        }
        if (combo > 0 && tickCount - lastHit > COMBO_TICKS) {
            endCombo(player, null);
        }
    }

    /** One click of the bot: true if it landed. */
    private boolean click(Player player, Location at, Location target) {
        double reach = ComboAi.reach(at.getX(), at.getY() + ComboAi.EYES, at.getZ(), target.getX(), target.getY(), target.getZ());
        if (player.getNoDamageTicks() > player.getMaximumNoDamageTicks() / 2 || !ai.lands(reach)) {
            return false;
        }
        Vector before = player.getVelocity();
        boolean sprint = ai.sprinting();
        botLanded = false;
        botHitting = true;
        try {
            player.damage(0.0, bot);
        } finally {
            botHitting = false;
        }
        if (!botLanded) {
            return false;
        }
        double wx = target.getX() - at.getX();
        double wz = target.getZ() - at.getZ();
        double[] push = ComboAi.knockback(before.getX(), before.getY(), before.getZ(), at.getX(), at.getZ(),
                target.getX(), target.getZ(), sprint, wx, wz);
        player.setVelocity(new Vector(push[0], push[1], push[2]));
        if (sprint) {
            Vector own = bot.getVelocity();
            bot.setVelocity(new Vector(own.getX() * ComboAi.HIT_SLOWDOWN, own.getY(), own.getZ() * ComboAi.HIT_SLOWDOWN));
        }
        ai.hitLanded();
        return true;
    }

    @Override
    boolean mayTarget(Entity entity, Entity target) {
        return target instanceof Player && target.getUniqueId().equals(me);
    }

    /** The player hits the bot: no damage, its knockback only. */
    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (target != bot || ai == null) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(0.0);
        ai.wasHit(bot.isOnGround());
        combo++;
        myHits++;
        botCombo = 0;
        lastHit = tickCount;
        if (combo >= 3) {
            sound(attacker, Sound.NOTE_PLING, 0.5f + Math.min(combo, 15) * 0.1f);
        }
    }

    /** The bot falls, or anything else: it is never hurt. */
    @Override
    void entityHurt(Entity entity, EntityDamageEvent event) {
        event.setCancelled(true);
    }

    /** The bot's own hit: no damage, its knockback (set after it), and the combo ends. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (bot == null || damager(event) != bot || !botHitting) {
            return false;
        }
        event.setDamage(0.0);
        botLanded = true;
        botHits++;
        botCombo++;
        if (botCombo > botBest) {
            botBest = botCombo;
        }
        if (combo > 0) {
            endCombo(victim, "the bot hit you back");
        }
        return true;
    }

    private void endCombo(Player player, String why) {
        int ended = combo;
        combo = 0;
        if (ended >= 2) {
            player.sendMessage(ChatColor.GREEN + "Combo of " + ChatColor.WHITE + ended + ChatColor.GREEN + " hits"
                    + (why == null ? "." : ChatColor.GRAY + " (" + why + ")."));
        }
        if (ended > best) {
            best = ended;
        }
        if (ended > 0) {
            score(ended, true);
        }
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Combo: " + ChatColor.GREEN + combo);
        lines.add(ChatColor.WHITE + "Best today: " + ChatColor.GREEN + best);
        lines.add(ChatColor.WHITE + "Hits: " + ChatColor.GREEN + myHits + ChatColor.GRAY + " - " + ChatColor.RED + botHits);
        if (hitsBack.on()) {
            lines.add(ChatColor.WHITE + "Its best combo: " + ChatColor.RED + botBest);
        }
    }
}
