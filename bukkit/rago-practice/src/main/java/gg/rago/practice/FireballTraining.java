package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Fireball practice, with the fireballs of Hypixel: a fireball jump up a cliff 4 blocks high, a
 * fireball jump across 10 blocks of void, or the fireballs of a cannon to send back with a hit, one
 * every two and a half seconds. Each jump made or fireball sent back adds one to the streak.
 */
final class FireballTraining extends Training {

    /** The z of each station: up, across, send back. */
    private static final int[] STATIONS = {-25, 0, 25};
    private static final int CLIFF_START = 2;
    private static final int CLIFF_END = 8;
    private static final int CLIFF_TOP = 4;
    private static final int ACROSS_START = 12;
    private static final int CANNON_X = 26;
    /** Ticks between two fireballs of the cannon. */
    private static final int SHOT_EVERY = 50;
    /** A fireball of this training that is still flying after this long is gone. */
    private static final int FIREBALL_LIFE = 120;

    private final List<Fireball> fireballs = new ArrayList<>();
    private final Set<UUID> sentBack = new HashSet<>();
    private int streak;
    private long lastThrow;
    private int pushedTick;
    private int thrownTick;
    private int nextShot;
    private boolean done;

    FireballTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("up", "Fireball jump up", false, "jumps"),
                new Variant("across", "Fireball jump across", false, "jumps"),
                new Variant("send", "Send them back", false, "fireballs"));
    }

    @Override
    String describe() {
        switch (current().id) {
            case "across":
                return "throw your fireball behind your feet and fly over the void to the green platform.";
            case "send":
                return "hit the cannon's fireballs to send them back before they reach you.";
            default:
                return "throw your fireball at your feet and jump onto the cliff, 4 blocks up.";
        }
    }

    private int station() {
        return STATIONS[variant];
    }

    private boolean sending() {
        return current().id.equals("send");
    }

    @Override
    void build() {
        int up = STATIONS[0];
        Shapes.island(arena, -4, up - 3, 1, up + 3, Material.WOOD, 0, Material.LOG, 0);
        arena.fill(CLIFF_START, 0, up - 3, CLIFF_END, CLIFF_TOP - 1, up + 3, Material.STAINED_CLAY, Shapes.LIGHT_GRAY);
        arena.fill(CLIFF_START, CLIFF_TOP, up - 3, CLIFF_END, CLIFF_TOP, up + 3, Material.STAINED_CLAY, Shapes.LIME);
        int across = STATIONS[1];
        Shapes.island(arena, -4, across - 3, 1, across + 3, Material.WOOD, 0, Material.LOG, 0);
        Shapes.island(arena, ACROSS_START, across - 3, ACROSS_START + 5, across + 3, Material.STAINED_CLAY, Shapes.LIME,
                Material.STAINED_CLAY, Shapes.GREEN);
        int send = STATIONS[2];
        Shapes.island(arena, -3, send - 3, 3, send + 3, Material.WOOD, 0, Material.LOG, 0);
        arena.fill(CANNON_X, -2, send, CANNON_X, 1, send, Material.ENDER_STONE, 0);
        arena.build(CANNON_X, 2, send, Material.DISPENSER, 4);
    }

    @Override
    Location spawnOf(UUID player) {
        return sending() ? arena.at(0.5, 1, station() + 0.5, -90f) : arena.at(-1.5, 1, station() + 0.5, -90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 6;
    }

    @Override
    ItemStack[] kit() {
        return sending() ? new ItemStack[] {new ItemStack(Material.STONE_SWORD)}
                : new ItemStack[] {new ItemStack(Material.FIREBALL, 1)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        clearFireballs();
        pushedTick = -1;
        thrownTick = -1;
        nextShot = 40;
        done = false;
    }

    private void clearFireballs() {
        for (Fireball fireball : fireballs) {
            host.disown(fireball);
            fireball.remove();
        }
        fireballs.clear();
        sentBack.clear();
    }

    @Override
    void ended() {
        clearFireballs();
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return false;
    }

    @Override
    void use(Player player, PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREBALL
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - lastThrow < Fireballs.COOLDOWN_MS || done) {
            return;
        }
        lastThrow = now;
        Fireball fireball = Fireballs.launch(player);
        fireballs.add(fireball);
        thrownTick = tickCount;
    }

    @Override
    void blasted(Player player, Player thrower, double damage) {
        pushedTick = tickCount;
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null) {
            return;
        }
        for (Iterator<Fireball> it = fireballs.iterator(); it.hasNext();) {
            Fireball fireball = it.next();
            if (!fireball.isValid() || fireball.getTicksLived() > FIREBALL_LIFE) {
                host.disown(fireball);
                fireball.remove();
                it.remove();
            }
        }
        if (done) {
            return;
        }
        if (sending()) {
            if (--nextShot <= 0) {
                nextShot = SHOT_EVERY;
                shoot(player);
            }
            return;
        }
        if (pushedTick >= 0 && tickCount - pushedTick > 5 && onGround(player)) {
            double[] at = arena.offset(player.getLocation());
            boolean up = current().id.equals("up");
            boolean landed = up ? at[0] >= CLIFF_START - 0.3 && at[1] >= CLIFF_TOP + 0.9
                    : at[0] >= ACROSS_START - 0.3;
            if (landed) {
                done = true;
                streak++;
                player.sendMessage(ChatColor.GREEN + "Nice jump! " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
                sound(player, Sound.ORB_PICKUP, 1.2f);
                score(streak, true);
                again(20L);
            } else {
                missed(up ? "Not on the cliff: throw the fireball right at your feet as you jump." : "You landed short.");
            }
            return;
        }
        if (thrownTick >= 0 && pushedTick < 0 && tickCount - thrownTick > 40) {
            missed("Your fireball did not push you: throw it at the ground next to your feet.");
        }
    }

    /** The cannon sends a fireball at the player, slow at first, faster and faster. */
    private void shoot(Player player) {
        Location from = arena.at(CANNON_X - 1.0, 2.5, station() + 0.5);
        Fireball fireball = Fireballs.send(from, player.getEyeLocation(), 0.3);
        host.own(fireball, this);
        fireballs.add(fireball);
        sound(player, Sound.GHAST_FIREBALL, 1f);
    }

    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (!(target instanceof Fireball) || !fireballs.contains(target)) {
            event.setCancelled(true);
            return;
        }
        // not cancelled: the fireball flies where the player looks, as in a game
        if (sentBack.add(target.getUniqueId())) {
            streak++;
            attacker.sendMessage(ChatColor.GREEN + "Sent back! " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
            sound(attacker, Sound.ORB_PICKUP, 1.4f);
            score(streak, true);
        }
    }

    @Override
    void ownLanded(Projectile projectile) {
        Player player = player();
        if (player == null || !sending() || sentBack.contains(projectile.getUniqueId())) {
            return;
        }
        if (projectile.getLocation().distance(player.getLocation()) < 4.5) {
            missed("The fireball got you.");
        }
    }

    private void missed(String why) {
        String end = streak > 0 ? " The streak of " + streak + " ends." : "";
        streak = 0;
        failed(why + end);
    }

    @Override
    void fell(Player player) {
        missed("You fell.");
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
    }
}
