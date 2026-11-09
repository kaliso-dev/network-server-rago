package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
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

/**
 * Combo bot: sparring against a bot that runs at the player and hits back, on the arenas of the
 * boxing (or a ring without them). The hits
 * hurt no one, only their knockback counts, as in boxing: a combo is the hits in a row on the bot
 * before it hits back (or two seconds without a hit). The score is the longest combo. Easy,
 * normal (Speed I) or hard (Speed II).
 */
final class ComboBot extends Training {

    /** A combo ends after this long without a hit. */
    private static final int COMBO_TICKS = 40;
    /** On a map, the bot starts this far in front of the player. */
    static final double BOT_AHEAD = 8;

    private Zombie bot;
    private int combo;
    private int best;
    private int lastHit;

    ComboBot(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("easy", "Easy", false, "hits"),
                new Variant("normal", "Normal", false, "hits"),
                new Variant("hard", "Hard", false, "hits"));
    }

    @Override
    String describe() {
        switch (current().id) {
            case "normal":
                return "the bot is as fast as a player (Speed I): combo it without being hit.";
            case "hard":
                return "the bot is faster than you (Speed II): combo it without being hit.";
            default:
                return "a slow bot: hit it again and again without being hit back.";
        }
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
        Bots.remove(this, bot);
        bot = null;
    }

    @Override
    void onFight() {
        bot = Bots.spawn(this, botStart(), "Combo bot", Color.RED, variant);
        Bots.chase(bot, player());
    }

    @Override
    void ended() {
        Bots.remove(this, bot);
        bot = null;
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || bot == null) {
            return;
        }
        if (tickCount % 20 == 0) {
            Bots.chase(bot, player);
        }
        if (!bot.isValid() || !arena.near(bot.getLocation(), range()) || bot.getLocation().getY() < arena.floorY() - 6) {
            bot.teleport(botStart());
        }
        if (combo > 0 && tickCount - lastHit > COMBO_TICKS) {
            endCombo(player, null);
        }
    }

    @Override
    boolean mayTarget(Entity entity, Entity target) {
        return target instanceof Player && target.getUniqueId().equals(me);
    }

    /** The player hits the bot: no damage, its knockback only. */
    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (target != bot) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(0.0);
        combo++;
        lastHit = tickCount;
        if (combo >= 3) {
            sound(attacker, Sound.NOTE_PLING, 0.5f + Math.min(combo, 15) * 0.1f);
        }
    }

    /** The bot hits the player: no damage either, its knockback, and the combo ends. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (bot == null || damager(event) != bot) {
            return false;
        }
        event.setDamage(0.0);
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
    }
}
