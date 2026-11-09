package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * KB displace as in a real Bed Fight top fight: a bridge one block wide, and the defender on the
 * top of the defense, one block below the bridge, the bridge in front of them and a block behind
 * them, nothing on the sides. The attacker runs along the bridge and, in the tick of the hit,
 * turns their head to one side: the knockback of the sprint sends the defender sideways, off the
 * top. Only a hit from the front of the defender counts (from the bridge, never from a side or
 * from behind). Each hit is one attempt: a displaced defender is a point for the attacker, and
 * the roles swap at each hit (or after {@link #ATTEMPT_SECONDS} seconds without one). The
 * defender stays in their notch, no damage, no blocks. First to the target. The practice against
 * a bot is {@link KbDisplaceBot}.
 */
final class KbDisplace extends Duel {

    /** The attacker starts this far along the bridge from the defender: a run to get the momentum. */
    static final int RUN = 10;
    /** The bridge goes on this far behind the defender. */
    static final int BEHIND = 4;
    /** After a hit, the defender has this long to fall; past it, they held. */
    static final int KNOCK_TICKS = 30;
    /** An attempt without a hit ends after this long. */
    static final int ATTEMPT_SECONDS = 15;

    /** The tick until which the hit defender flies, or -1 before the hit of this attempt. */
    private int knockedUntil = -1;
    private long warnedAt;

    KbDisplace(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    @Override
    String title() {
        return "KB displace";
    }

    @Override
    void intro(Player player) {
        super.intro(player);
        player.sendMessage(ChatColor.GRAY + "The defender stands one block below the bridge, a block in front and behind."
                + " Run, turn your head to the side and hit them in the same tick: they go off sideways."
                + " Only a hit from the front counts. The roles swap at each hit.");
    }

    // --- The place, shared with the bot -----------------------------------------------------------

    /**
     * The bridge along x at z = 0, its blocks at the height of the floor; the defender's notch at
     * x = 0, one block lower; a pad at the attacker's start; under the notch and behind it, the
     * end stone of a defense.
     */
    static void bridge(Arena arena) {
        for (int x = -RUN - 1; x <= BEHIND; x++) {
            if (x != 0) {
                arena.build(x, 0, 0, Material.WOOL, Shapes.WHITE);
            }
        }
        Shapes.floor(arena, -RUN - 4, -1, -RUN - 2, 1, 0, Material.WOOL, Shapes.WHITE);
        arena.build(0, -1, 0, Material.ENDER_STONE);
        for (int x = 1; x <= BEHIND; x++) {
            arena.build(x, -1, 0, Material.ENDER_STONE);
        }
    }

    /** Where the attacker starts: on the bridge, the run in front of them, facing the defender. */
    static Location attackerStart(Arena arena) {
        return arena.at(-RUN + 0.5, 1, 0.5, -90f);
    }

    /** The defender's notch, facing the bridge the attacker comes from. */
    static Location defenderSpot(Arena arena) {
        return arena.at(0.5, 0, 0.5, 90f);
    }

    /** True if the defender, standing at this height above the floor, fell off the top. */
    static boolean offTheTop(double feet) {
        return feet < -0.9;
    }

    /**
     * True if a hit from this offset of the attacker to the defender comes from the front: from the
     * bridge the attacker runs along (smaller x), in its line, not from a side or from behind.
     */
    static boolean fromTheFront(double dx, double dz) {
        return dx <= -0.4 && Math.abs(dz) <= 0.9;
    }

    /** True if a defender who was not hit left their notch (a step, a jump): they are put back. */
    static boolean outOfTheNotch(double x, double y, double z) {
        return Math.abs(x - 0.5) > 0.45 || Math.abs(z - 0.5) > 0.45 || y > 0.6;
    }

    /** The side that attacks in this attempt: the first side first, then each in turn. */
    static int attackerOf(int round) {
        return (round + 1) % 2;
    }

    private int attacker() {
        return attackerOf(round);
    }

    // --- The duel ---------------------------------------------------------------------------------

    @Override
    void build() {
        bridge(arena);
    }

    /** A countdown before the first attempt only: the next ones start at once, the roles swapped. */
    @Override
    int countdownSeconds() {
        return round <= 1 ? 3 : 0;
    }

    @Override
    void roundReset() {
        super.roundReset();
        knockedUntil = -1;
    }

    @Override
    void onFight() {
        for (Player player : online()) {
            boolean attacking = sideOf(player.getUniqueId()) == attacker();
            player.sendMessage(attacking ? ChatColor.YELLOW + "Attack: run and knock them off sideways!"
                    : ChatColor.AQUA + "Defend: hold your place.");
        }
    }

    @Override
    Location spawnOf(UUID player) {
        return sideOf(player) == attacker() ? attackerStart(arena) : defenderSpot(arena);
    }

    @Override
    double fallY() {
        return arena.floorY() - 6;
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD)};
    }

    @Override
    boolean colouredBlocks() {
        return false;
    }

    /** Only the attacker's hit, from the front, once per attempt; no damage, only the knockback. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || phase != Phase.PLAYING || sideOf(attacker.getUniqueId()) != attacker()
                || knockedUntil >= 0) {
            return false;
        }
        double[] by = arena.offset(attacker.getLocation());
        double[] at = arena.offset(victim.getLocation());
        if (!fromTheFront(by[0] - at[0], by[2] - at[2])) {
            long now = System.currentTimeMillis();
            if (now - warnedAt > 1500L) {
                warnedAt = now;
                attacker.sendMessage(ChatColor.RED + "Only a hit from the front counts: from the bridge, in front of them.");
            }
            return false;
        }
        event.setDamage(0.0);
        knockedUntil = tickCount + KNOCK_TICKS;
        return true;
    }

    @Override
    void tick() {
        if (phase != Phase.PLAYING) {
            return;
        }
        List<Player> defenders = online(1 - attacker());
        if (defenders.isEmpty()) {
            return;
        }
        Player defender = defenders.get(0);
        double[] at = arena.offset(defender.getLocation());
        if (offTheTop(at[1])) {
            displaced(defender);
            return;
        }
        if (knockedUntil >= 0) {
            if (tickCount >= knockedUntil) {
                broadcast(CHAT[1 - attacker()] + defender.getDisplayName() + ChatColor.GRAY + " held: the roles swap.", null);
                startRound();
            }
            return;
        }
        if (outOfTheNotch(at[0], at[1], at[2])) {
            Location back = defenderSpot(arena);
            back.setYaw(defender.getLocation().getYaw());
            back.setPitch(defender.getLocation().getPitch());
            defender.teleport(back);
            defender.setFallDistance(0f);
        }
    }

    /** The defender fell off the top: a point for the attacker, a kill in the stats. */
    private void displaced(Player defender) {
        int side = attacker();
        List<Player> attackers = online(side);
        if (!attackers.isEmpty() && counts()) {
            plugin.lobby().recordKill(mode.id(), attackers.get(0).getUniqueId(), defender.getUniqueId());
        }
        broadcast(CHAT[side] + namesOf(side) + ChatColor.GRAY + " displaced " + CHAT[1 - side] + defender.getDisplayName()
                + ChatColor.GRAY + "!", Sound.NOTE_PLING);
        point(side);
    }

    /** A fall of the defender is a displace; a fall of the attacker ends their attempt. */
    @Override
    void fell(Player player) {
        if (phase != Phase.PLAYING) {
            player.teleport(spawnOf(player.getUniqueId()));
            return;
        }
        if (sideOf(player.getUniqueId()) == attacker()) {
            broadcast(CHAT[attacker()] + player.getDisplayName() + ChatColor.GRAY + " fell: the roles swap.", null);
            startRound();
        } else {
            displaced(player);
        }
    }

    @Override
    void lethal(Player victim, Player killer) {
        fell(victim);
    }

    @Override
    void onSecond() {
        if (knockedUntil < 0 && seconds >= ATTEMPT_SECONDS) {
            broadcast(ChatColor.GRAY + "No hit in " + ATTEMPT_SECONDS + " seconds: the roles swap.", null);
            startRound();
        }
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        boolean attacking = sideOf(viewer) == attacker();
        lines.add(attacking ? ChatColor.YELLOW + "Attack: off sideways!" : ChatColor.AQUA + "Defend: hold your place");
        lines.add(ChatColor.WHITE + "Left: " + ChatColor.GREEN + Math.max(0, ATTEMPT_SECONDS - seconds) + " s");
    }
}
