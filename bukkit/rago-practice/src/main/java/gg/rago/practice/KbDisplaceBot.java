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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * KB displace against a bot, the drill of the {@link KbDisplace} duel, on the same place: the bot
 * holds its notch on the bridge (on the owner's map, its gold block), nothing on its sides. Run along
 * the bridge, turn your head to the side and hit it in the same tick: the knockback of the sprint
 * sends it off sideways. Only a hit from the front counts. After each hit you are back at the
 * start at once, for the momentum of a new run, until the bot falls: the score is the time, and
 * the sidebar counts the tries.
 */
final class KbDisplaceBot extends Training {

    private final KbDisplace.Place place;
    private Zombie bot;
    private long startedAt;
    /** The tick until which the hit bot flies, or -1 when it stands in its notch. */
    private int knockedUntil = -1;
    private int tries;
    private boolean scored;
    private long warnedAt;
    /** How much of a hit's knockback the bot takes, in tenths above half: 1 is 60 %, 5 the whole (a player's). */
    private final Setting botKb;

    static final Variant STILL = new Variant("still", "Bot in the notch", true, "");

    KbDisplaceBot(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, KbDisplace.Place place) {
        super(host, mode, arena, teams, STILL);
        this.place = place;
        this.botKb = setting("Bot KB", 1, 5, DEFAULT_BOT_KB);
    }

    /** The bot takes 80 % of the knockback by default: a zombie flies further than a player who holds. */
    static final int DEFAULT_BOT_KB = 3;

    /** The share of the knockback the bot takes at this setting: 0.6 to 1.0. */
    static double kbShare(int setting) {
        return 0.5 + 0.1 * setting;
    }

    /**
     * The velocity a hit gives the bot, as the 1.8 game gives it to a mob (its motion halved, 0.4
     * away from the attacker and 0.4 up, at most 0.4 up; a sprint adds 0.5 the way the attacker
     * looks and 0.1 up), times its share.
     */
    static double[] knockback(double attackerX, double attackerZ, float attackerYaw, boolean sprint, double x, double z,
            double motionX, double motionY, double motionZ, double share) {
        double dx = attackerX - x;
        double dz = attackerZ - z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) {
            dx = 0.01;
            dz = 0;
            length = 0.01;
        }
        double vx = motionX / 2 - dx / length * 0.4;
        double vy = Math.min(motionY / 2 + 0.4, 0.4);
        double vz = motionZ / 2 - dz / length * 0.4;
        if (sprint) {
            double yaw = Math.toRadians(attackerYaw);
            vx += -Math.sin(yaw) * 0.5;
            vy += 0.1;
            vz += Math.cos(yaw) * 0.5;
        }
        return new double[] {vx * share, vy * share, vz * share};
    }

    @Override
    String describe() {
        return "the bot holds its place on the bridge, nothing on its sides: run, turn your head to the side"
                + " and hit it in the same tick to knock it off sideways. Invisible walls keep you on the bridge; after"
                + " each hit you start again from the start. Bot KB: how far a hit throws it.";
    }

    @Override
    void build() {
        place.build(arena);
    }

    @Override
    Location spawnOf(UUID player) {
        return place.attackerStart(arena);
    }

    @Override
    double fallY() {
        return place.fallY(arena);
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
        bot = Bots.spawn(this, place.defenderSpot(arena), "Bot", Color.BLUE, 0);
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
        if (!bot.isValid() || place.offTheTop(at.getY() - arena.floorY())) {
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
        Location spot = place.defenderSpot(arena);
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
        String refused = KbDisplace.refused(place, attacker, by, at, bot.getLocation());
        if (refused != null) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            if (now - warnedAt > 1500L) {
                warnedAt = now;
                attacker.sendMessage(ChatColor.RED + refused);
            }
            return;
        }
        // the knockback of the game, worked out here to give the bot its share of it (Bot KB)
        event.setCancelled(true);
        Location from = attacker.getLocation();
        Location to = bot.getLocation();
        Vector motion = bot.getVelocity();
        double[] kb = knockback(from.getX(), from.getZ(), from.getYaw(), attacker.isSprinting(), to.getX(), to.getZ(),
                motion.getX(), motion.getY(), motion.getZ(), kbShare(botKb.value));
        bot.setVelocity(new Vector(kb[0], kb[1], kb[2]));
        bot.playEffect(EntityEffect.HURT);
        bot.getWorld().playSound(to, Sound.ZOMBIE_HURT, 1f, 1f);
        knockedUntil = tickCount + KbDisplace.KNOCK_TICKS;
        tries++;
        // back at the start at once, for the momentum of the next run
        final int roundNow = round;
        later(1L, () -> {
            Player player = player();
            if (player != null && round == roundNow && !scored) {
                player.teleport(place.attackerStart(arena));
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
        player.teleport(place.attackerStart(arena));
        player.setFallDistance(0f);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + (scored || bot == null ? "-" : time(now() - startedAt) + " s"));
        lines.add(ChatColor.WHITE + "Tries: " + ChatColor.YELLOW + tries);
    }
}
