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
 * KB displace against a bot: the bridge of the KB displace duel, the player at one end and a bot
 * at the other that walks up and hits. No damage, only knockback: knock the bot off the bridge
 * without falling; clutch with your wool. Each bot knocked off adds one to the streak, a fall
 * ends it.
 */
final class KbDisplaceBot extends Training {

    private Zombie bot;
    private int streak;
    private boolean scored;

    KbDisplaceBot(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("normal", "Normal bot", false, "knock-offs"),
                new Variant("fast", "Fast bot", false, "knock-offs"));
    }

    @Override
    String describe() {
        return current().id.equals("fast") ? "the bot has Speed I: knock it off the bridge before it knocks you off."
                : "knock the bot off the bridge with your hits, without falling.";
    }

    @Override
    void build() {
        KbDisplace.bridge(arena);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(-3.5, 1, 0.5, -90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 6;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD), stack(Material.WOOL)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, KbDisplace.HALF + 8, 8, 6);
    }

    @Override
    void roundReset() {
        scored = false;
        Bots.remove(this, bot);
        bot = null;
    }

    @Override
    void onFight() {
        bot = Bots.spawn(this, arena.at(4.5, 1, 0.5, 90f), "Bot", Color.BLUE, variant);
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
        if (player == null || bot == null || scored) {
            return;
        }
        if (tickCount % 20 == 0) {
            Bots.chase(bot, player);
        }
        if (!bot.isValid() || bot.getLocation().getY() < arena.floorY() - 3 || !arena.near(bot.getLocation(), 40)) {
            scored = true;
            streak++;
            player.sendMessage(ChatColor.GREEN + "Knocked off! " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
            sound(player, Sound.ORB_PICKUP, 1.2f);
            score(streak, true);
            Bots.remove(this, bot);
            bot = null;
            again(30L);
        }
    }

    @Override
    boolean mayTarget(Entity entity, Entity target) {
        return target instanceof Player && target.getUniqueId().equals(me);
    }

    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (target != bot) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(0.0);
    }

    @Override
    void entityHurt(Entity entity, EntityDamageEvent event) {
        // the void, a fall: the bot never dies of it, it is knocked off
        event.setCancelled(true);
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (bot == null || damager(event) != bot) {
            return false;
        }
        event.setDamage(0.0);
        return true;
    }

    @Override
    void fell(Player player) {
        String end = streak > 0 ? " The streak of " + streak + " ends." : "";
        streak = 0;
        failed("The bot knocked you off." + end);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
    }
}
