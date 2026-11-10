package gg.rago.bedwars;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Top Fight on a real map: the bed of the first team keeps a ranked defense (end stone, glass,
 * wood, wool), the other beds and the forges go, and both teams start on its top, face to face:
 * the first of each team on the very top (one above the foot of the bed, the other above its
 * head), the second one step down behind, the others one step down beside. Same kit as Top Bridge
 * (the mode's kit of the lobby, full diamond armour with Protection IV and Absorption II). One who
 * is knocked off the defense, down to the island's floor, is dead, as in the void.
 *
 * <p>Rounds of one life to {@value #POINTS_TO_WIN} points, the map and the defense made again for
 * each round; in a party's private game, the practice of {@link TopBridgeGame}: back on top a
 * moment after a death, the defense made whole again when a player comes back. Main thread only.
 */
final class TopFightGame extends TopBridgeGame {

    static final int POINTS_TO_WIN = 3;
    /** The ranked defense, from the bed out: its top is wool, {@code DEFENSE.length} blocks over the bed. */
    static final Material[] DEFENSE = {Material.ENDER_STONE, Material.STAINED_GLASS, Material.WOOD, Material.WOOL};
    /** The dead and those who wait watch from this high over the bed. */
    private static final int WATCH_ABOVE = 8;

    private Defense defense;
    /** The two halves of the bed fought on, as the defense was built round them (empty: none). */
    private List<Block> bed = java.util.Collections.emptyList();

    TopFightGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, boolean practice) {
        super(plugin, host, instance, modeId, modeName, sides, practice);
    }

    @Override
    int pointsToWin() {
        return POINTS_TO_WIN;
    }

    /** No top bridge: the fight is on the defense. */
    @Override
    boolean hasTopBridge() {
        return false;
    }

    @Override
    boolean onTheBridge() {
        return false;
    }

    /** The team whose bed is fought on: the first one, if its island has a bed. */
    private Team fightTeam() {
        return teams.isEmpty() || teams.get(0).points.bed == null ? null : teams.get(0);
    }

    /** Every bed goes but the one fought on; no team has a bed to break or to come back by. */
    @Override
    void removeUnusedBeds() {
        World world = instance.world();
        Team fight = fightTeam();
        for (BedwarsMap.Team team : instance.map().teams().values()) {
            if (team.bed != null && (fight == null || team != fight.points)) {
                for (Block half : Instance.bedOf(team.bed.blockIn(world))) {
                    half.setType(Material.AIR, false);
                }
            }
        }
        for (Team team : teams) {
            team.bed = false;
        }
    }

    /** The defense of the first round: the start builds it with the map. */
    @Override
    void setUp() {
        buildFight();
    }

    /** The ranked defense on the bed, in the colour of its team. */
    @Override
    void buildFight() {
        Team team = fightTeam();
        defense = team == null ? null : Defense.build(instance, team.points.bed.blockIn(instance.world()), DEFENSE,
                team.color.woolData());
        bed = defense == null ? java.util.Collections.<Block>emptyList() : defense.bed();
        if (defense == null) {
            plugin.getLogger().warning("No bed to fight on in " + instance.map().world() + ": the top fight starts on the islands");
        }
    }

    /** Under the top of the bed's own block: down on the island's floor (or lower), knocked off the defense. */
    @Override
    double fallLine() {
        if (bed.isEmpty()) {
            return super.fallLine();
        }
        return Math.max(instance.map().voidY(), bed.get(0).getY() + 0.5);
    }

    @Override
    Location watchPoint() {
        if (bed.isEmpty()) {
            return super.watchPoint();
        }
        Block foot = bed.get(0);
        return new Location(instance.world(), foot.getX() + 0.5, foot.getY() + DEFENSE.length + WATCH_ABOVE, foot.getZ() + 0.5);
    }

    /** On top of the defense; in the practice, the defense made whole again first. */
    @Override
    Location spawnOf(Gamer gamer) {
        if (bed.size() < 2) {
            return super.spawnOf(gamer);
        }
        if (practice) {
            defense.repair();
        }
        Block foot = bed.get(0);
        Block head = bed.get(1);
        int team = teams.indexOf(gamer.team);
        int index = Math.max(0, gamer.team.members.indexOf(gamer.id));
        return at(start(foot.getX(), foot.getY(), foot.getZ(), head.getX() - foot.getX(), head.getZ() - foot.getZ(),
                DEFENSE.length, team != 1, index));
    }

    /**
     * Where a player starts on the defense of a bed whose foot is at (x, y, z) and head one block
     * towards (hx, hz), with this many layers: {x, y of the feet, z, yaw}. The first team on the
     * foot's side, the other on the head's, each looking at the other; the first of a team on the
     * very top, the second one step down behind, the third and fourth one step down beside.
     */
    static double[] start(int x, int y, int z, int hx, int hz, int layers, boolean first, int index) {
        // the half the team stands on, and the way to the other team
        int bx = first ? x : x + hx;
        int bz = first ? z : z + hz;
        int wx = first ? hx : -hx;
        int wz = first ? hz : -hz;
        int ox = 0;
        int oz = 0;
        switch (index % 4) {
            case 1:
                ox = -wx;
                oz = -wz;
                break;
            case 2:
                ox = -wz;
                oz = wx;
                break;
            case 3:
                ox = wz;
                oz = -wx;
                break;
            default:
                break;
        }
        // the top of the defense over a place n blocks from the bed is layers - n over it
        int feet = y + layers + 1 - (Math.abs(ox) + Math.abs(oz));
        return new double[] {bx + ox + 0.5, feet, bz + oz + 0.5, InvisGame.yawTowards(wx, wz)};
    }

    @Override
    String practiceRule() {
        return "A practice of your party: fight on top of the defense, fall off it and you come back on top. No forge, "
                + PRACTICE_MINUTES + " minutes at most.";
    }

    @Override
    String roundsRule() {
        return "Rounds of one life: kill every player of the other team, or knock them off the defense. The first team to "
                + pointsToWin() + " points wins.";
    }
}
