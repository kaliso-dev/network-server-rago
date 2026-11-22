package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Parkour Duel, the race of FUN PRACTICE (the owner's choice of 22/11, "dans une map stylée"):
 * each side on its own lane of the same course ({@link ParkourCourse}), red and blue side by side.
 * A fall takes the player back to their last checkpoint; the first to the finish gets the point.
 * No hits, no fall damage, no kit. Its setting: parkour-duel in config.yml.
 */
final class ParkourDuel extends Duel {

    /** Under the lowest block of the course by this much, a player has fallen: back to their checkpoint. */
    static final int FALL_DEPTH = 4;

    private final ParkourCourse course = ParkourCourse.COURSE;
    /** The last checkpoint each player reached in this round (0: the start). */
    private final Map<UUID, Integer> reached = new HashMap<>();
    /** When the fight of this round started (System.currentTimeMillis). */
    private long startedAt;

    ParkourDuel(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    @Override
    String title() {
        return "Parkour Duel";
    }

    @Override
    void intro(Player player) {
        super.intro(player);
        player.sendMessage(ChatColor.GRAY + "Race them to the finish on your lane: a fall takes you back to your last"
                + " checkpoint. No hits.");
    }

    @Override
    boolean colouredBlocks() {
        return false;
    }

    /** The two lanes, red at z -{@value ParkourCourse#LANE_Z}, blue at z +{@value ParkourCourse#LANE_Z}. */
    @Override
    void build() {
        for (int side = 0; side < 2; side++) {
            int laneZ = ParkourCourse.laneZ(side);
            for (ParkourCourse.Piece piece : course.pieces()) {
                int data = piece.data == ParkourCourse.LANE_COLOUR ? WOOL[side] : piece.data;
                arena.build(piece.x, piece.y, piece.z + laneZ, Material.valueOf(piece.material), data);
            }
        }
    }

    @Override
    void roundReset() {
        super.roundReset();
        reached.clear();
    }

    @Override
    void onFight() {
        super.onFight();
        startedAt = System.currentTimeMillis();
    }

    /** The checkpoint this player reached in this round (0: the start). */
    int checkpointOf(UUID player) {
        Integer checkpoint = reached.get(player);
        return checkpoint == null ? 0 : checkpoint;
    }

    /** At the start, or on the last checkpoint they reached, facing the finish. */
    @Override
    Location spawnOf(UUID player) {
        int side = Math.max(0, sideOf(player));
        int laneZ = ParkourCourse.laneZ(side);
        int checkpoint = checkpointOf(player);
        if (checkpoint > 0) {
            double[] middle = course.checkpoints().get(checkpoint).middle();
            return arena.at(middle[0], middle[1], middle[2] + laneZ, -90f);
        }
        double[] start = course.start();
        return arena.at(start[0], start[1], start[2] + across(player) * 2 + laneZ, -90f);
    }

    /** Under the course: the player falls back to their checkpoint (see {@link #fell}). */
    @Override
    double fallY() {
        return arena.floorY() + course.lowest() - FALL_DEPTH;
    }

    /** Above the two lanes, from the side, the whole course in sight. */
    @Override
    Location viewPoint() {
        Location view = arena.at(0.5, 20, -34.5, 0f);
        view.setPitch(30f);
        return view;
    }

    /** A fall is no point: back to the last checkpoint. */
    @Override
    void fell(Player player) {
        player.teleport(spawnOf(player.getUniqueId()));
        player.setFallDistance(0f);
    }

    @Override
    void lethal(Player victim, Player killer) {
        fell(victim);
    }

    /** Nobody hits anybody, and no fall hurts. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return false;
    }

    /** On a checkpoint of their lane: it is theirs; on the finish: the point. */
    @Override
    void moved(Player player, Location from, Location to) {
        if (phase != Phase.PLAYING) {
            return;
        }
        UUID id = player.getUniqueId();
        int side = sideOf(id);
        if (side < 0) {
            return;
        }
        double[] at = arena.offset(to);
        double x = at[0];
        double y = at[1];
        double z = at[2] - ParkourCourse.laneZ(side);
        if (course.finish().holds(x, y, z)) {
            broadcast(CHAT[side] + player.getDisplayName() + ChatColor.GRAY + " finished the course in " + ChatColor.WHITE
                    + time(System.currentTimeMillis() - startedAt) + "s" + ChatColor.GRAY + ".", null);
            point(side);
            return;
        }
        List<ParkourCourse.Pad> checkpoints = course.checkpoints();
        for (int k = checkpoints.size() - 1; k > checkpointOf(id); k--) {
            if (checkpoints.get(k).holds(x, y, z)) {
                reached.put(id, k);
                player.sendMessage(ChatColor.GREEN + "Checkpoint " + k + "/" + course.checkpointCount() + "!");
                player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
                sidebars();
                return;
            }
        }
    }

    /** The checkpoints of each side, under the score: the best of its players. */
    @Override
    void extraLines(UUID viewer, List<String> lines) {
        for (int side = 0; side < 2; side++) {
            int best = 0;
            for (UUID id : sides.get(side)) {
                best = Math.max(best, checkpointOf(id));
            }
            lines.add(cut(CHAT[side] + TEAMS[side] + ChatColor.WHITE + " checkpoint: " + best + "/" + course.checkpointCount()));
        }
        lines.add("");
    }
}
