package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * KB displace against a bot, the drill of the {@link KbDisplace} duel: the bot stands on the top
 * of the defense, one block below the bridge, a block in front of it and one behind. Run along
 * the bridge, turn your head to the side and hit it in the same tick: the knockback of the sprint
 * sends it off sideways. Only a hit from the front counts. After each hit you are back at the
 * start at once, for the momentum of a new run, until the bot falls: the score is the time, and
 * the sidebar counts the tries.
 */
final class KbDisplaceBot extends Training {

    private Zombie bot;
    private long startedAt;
    /** The tick until which the hit bot flies, or -1 when it stands in its notch. */
    private int knockedUntil = -1;
    private int tries;
    private boolean scored;
    private long warnedAt;

    KbDisplaceBot(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, new Variant("still", "Bot in the notch", true, ""));
    }

    @Override
    String describe() {
        return "the bot stands one block below the bridge, a block in front and behind: run, turn your head to the side"
                + " and hit it in the same tick to knock it off sideways. Only a hit from the front counts; after each"
                + " hit you start again from the start.";
    }

    @Override
    void build() {
        KbDisplace.bridge(arena);
    }

    @Override
    Location spawnOf(UUID player) {
        return KbDisplace.attackerStart(arena);
    }

    @Override
    double fallY() {
        return arena.floorY() - 6;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        scored = false;
        knockedUntil = -1;
        tries = 0;
        Bots.remove(this, bot);
        bot = null;
    }

    @Override
    void onFight() {
        bot = Bots.spawn(this, KbDisplace.defenderSpot(arena), "Bot", Color.BLUE, 0);
        startedAt = now();
    }

    @Override
    void ended() {
        Bots.remove(this, bot);
        bot = null;
    }

    @Override
    boolean mayTarget(Entity entity, Entity target) {
        // the bot holds its place, it does not hunt
        return false;
    }

    @Override
    void tick() {
        refresh();
        Player player = player();
        if (player == null || bot == null || scored) {
            return;
        }
        Location at = bot.getLocation();
        if (!bot.isValid() || KbDisplace.offTheTop(at.getY() - arena.floorY())) {
            scored = true;
            long took = now() - startedAt;
            player.sendMessage(ChatColor.GREEN + "Displaced! " + ChatColor.WHITE + time(took) + " s" + ChatColor.GRAY
                    + " (" + tries + (tries == 1 ? " try)" : " tries)"));
            score(took);
            Bots.remove(this, bot);
            bot = null;
            again(30L);
            return;
        }
        if (knockedUntil >= 0 && tickCount < knockedUntil) {
            return;
        }
        knockedUntil = -1;
        // in its notch, facing the player
        Location spot = KbDisplace.defenderSpot(arena);
        Vector way = player.getLocation().toVector().subtract(spot.toVector());
        spot.setYaw((float) Math.toDegrees(Math.atan2(-way.getX(), way.getZ())));
        bot.teleport(spot);
        bot.setVelocity(new Vector());
    }

    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (target != bot || knockedUntil >= 0 || scored) {
            event.setCancelled(true);
            return;
        }
        double[] by = arena.offset(attacker.getLocation());
        double[] at = arena.offset(bot.getLocation());
        String refused = KbDisplace.refused(attacker, by, at, bot.getLocation());
        if (refused != null) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            if (now - warnedAt > 1500L) {
                warnedAt = now;
                attacker.sendMessage(ChatColor.RED + refused);
            }
            return;
        }
        event.setDamage(0.0);
        knockedUntil = tickCount + KbDisplace.KNOCK_TICKS;
        tries++;
        // back at the start at once, for the momentum of the next run
        final int roundNow = round;
        later(1L, () -> {
            Player player = player();
            if (player != null && round == roundNow && !scored) {
                player.teleport(KbDisplace.attackerStart(arena));
                player.setVelocity(new Vector());
                player.setFallDistance(0f);
                sound(player, Sound.ORB_PICKUP, 1.6f);
            }
        });
    }

    @Override
    void entityHurt(Entity entity, EntityDamageEvent event) {
        // a fall, the void: the bot never dies of it, it is knocked off
        event.setCancelled(true);
    }

    /** A fall off the bridge: back at the start, the clock still running. */
    @Override
    void fell(Player player) {
        player.sendMessage(ChatColor.RED + "You fell: back at the start.");
        player.teleport(KbDisplace.attackerStart(arena));
        player.setFallDistance(0f);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + (scored || bot == null ? "-" : time(now() - startedAt) + " s"));
        lines.add(ChatColor.WHITE + "Tries: " + ChatColor.YELLOW + tries);
    }
}
