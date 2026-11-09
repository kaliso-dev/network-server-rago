package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
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
import org.bukkit.util.Vector;

/**
 * KB displace against a bot: the top fight of the KB displace duel, the bot boxed on top of the
 * defense, open on the sides. Climb and knock it off the top with the knockback of your hits, as
 * fast as you can: the score is the time. The bot stands still, or hits back when you are in its
 * reach.
 */
final class KbDisplaceBot extends Training {

    /** After a hit, the bot flies this long before it stands back in its box. */
    private static final int KNOCKED_TICKS = 14;
    /** The bot that hits back: from this far, every this many ticks. */
    private static final double BOT_REACH = 3.0;
    private static final int BOT_HIT_TICKS = 12;

    private Zombie bot;
    private long startedAt;
    private int knockedUntil;
    private int lastBotHit;
    private boolean scored;

    KbDisplaceBot(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("still", "Still bot", true, ""),
                new Variant("fights", "Bot hits back", true, ""));
    }

    private boolean fights() {
        return current().id.equals("fights");
    }

    @Override
    String describe() {
        return "the bot is boxed on top of the defense, open on the sides: climb and knock it off the top"
                + (fights() ? ", it hits back." : ".");
    }

    @Override
    void build() {
        KbDisplace.topFight(arena);
    }

    @Override
    Location spawnOf(UUID player) {
        return KbDisplace.attackerStart(arena);
    }

    private Location botSpot() {
        return arena.at(KbDisplace.TOP_X, KbDisplace.TOP, KbDisplace.TOP_Z, 0f);
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
        return arena.contains(block, 12, 6, KbDisplace.TOP + 4);
    }

    @Override
    void roundReset() {
        scored = false;
        knockedUntil = 0;
        lastBotHit = 0;
        Bots.remove(this, bot);
        bot = null;
        KbDisplace.box(arena, Shapes.BLUE);
    }

    @Override
    void onFight() {
        bot = Bots.spawn(this, botSpot(), "Boxed bot", Color.BLUE, 0);
        startedAt = now();
    }

    @Override
    void ended() {
        Bots.remove(this, bot);
        bot = null;
    }

    @Override
    boolean mayTarget(Entity entity, Entity target) {
        // the bot stays in its box, it does not hunt
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
            player.sendMessage(ChatColor.GREEN + "Off the top! " + ChatColor.WHITE + time(took) + " s");
            score(took);
            Bots.remove(this, bot);
            bot = null;
            again(30L);
            return;
        }
        Location look = player.getLocation();
        if (tickCount >= knockedUntil) {
            // back in its box, facing the player
            Location spot = botSpot();
            Vector way = look.toVector().subtract(spot.toVector());
            spot.setYaw((float) Math.toDegrees(Math.atan2(-way.getX(), way.getZ())));
            bot.teleport(spot);
        }
        if (fights() && tickCount - lastBotHit >= BOT_HIT_TICKS && tickCount >= knockedUntil
                && look.distanceSquared(at) <= BOT_REACH * BOT_REACH) {
            lastBotHit = tickCount;
            Vector push = look.toVector().subtract(at.toVector()).setY(0);
            if (push.lengthSquared() > 0) {
                push.normalize().multiply(0.4);
            }
            player.setVelocity(push.setY(0.36));
            player.playEffect(EntityEffect.HURT);
            sound(player, Sound.HURT_FLESH, 1f);
        }
    }

    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (target != bot) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(0.0);
        knockedUntil = tickCount + KNOCKED_TICKS;
    }

    @Override
    void entityHurt(Entity entity, EntityDamageEvent event) {
        // a fall, the void: the bot never dies of it, it is knocked off
        event.setCancelled(true);
    }

    @Override
    void fell(Player player) {
        failed("You fell.");
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + (scored || bot == null ? "-" : time(now() - startedAt) + " s"));
    }
}
