package gg.rago.bedwars;

import gg.rago.game.BedDefense;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Clutch Scenario: a real Ranked Bedwars game (1v1, 1v2, 1v3, 1v4, as Invis Practice and the Bed
 * Break Simulator: the owner's choice, 22/11) on Sky Rise or Archway (the maps of the mode in
 * config.yml), the two teams on opposite islands, where one team has lost its bed already and has
 * to clutch: in 1v1 one of the two drawn at random, from 1v2 the player alone (drawn at random
 * among the players of the game, who come as one side), with one life (the owner's choice, 21/11).
 * The other
 * keeps its bed, under the owner's duo defense already built (end stone, then wool: {@link
 * BedDefense#DUO}). Everyone starts on their island in iron armour with a stone sword, no
 * enchantment, no emerald, nothing else, the forges full of iron and gold (every forge of the map,
 * the empty islands' too: forges-partout in config.yml). The game is half played already, as a
 * real one at 18 minutes: the diamond generators at Tier III, the emerald ones at Tier II, Emerald
 * III next, then the end of the beds. And it looks it: each base has its bridges built, to its
 * diamond island and to mid, and the one it rushed the enemy base by, from mid, in wool of its
 * colour ({@link Bridges}); the bedless team's defense stands where its bed was, with the hole the
 * attacker dug through it from the side of mid. Then it
 * is a game as any other: the shop, the upgrades, the diamonds and emeralds, until one team is
 * left. Main thread only.
 */
final class ClutchScenarioGame extends BedwarsGame {

    /** The defense on the bed of the team that keeps it: the owner's duo one, end stone then wool. */
    static final List<String[]> DEFENSE = BedDefense.DUO;
    /** Iron armour from the start (the tier of {@link Gamer#armor}). */
    static final int ARMOUR_TIER = 2;
    /** The tiers of the diamond and emerald generators at the start: a game half played. */
    static final int DIAMOND_TIER = 3;
    static final int EMERALD_TIER = 2;

    /** The team without its bed. */
    private final Team bedless;

    ClutchScenarioGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, alone(sides, ThreadLocalRandom.current().nextInt(count(sides))));
        // 1v2 to 1v4: the player alone, the first side; 1v1: one of the two at random
        bedless = sides.size() == 1 ? teams.get(0) : teams.get(ThreadLocalRandom.current().nextInt(teams.size()));
    }

    /**
     * The sides of the game: a 1v2, 1v3 or 1v4 comes as one side (the lobby's one team of 3 to 5),
     * split into the player of this index alone, first, and the others together; a 1v1 (two sides)
     * as it is.
     */
    static <T> List<List<T>> alone(List<List<T>> sides, int index) {
        if (sides.size() != 1 || sides.get(0).size() < 2) {
            return sides;
        }
        List<T> others = new ArrayList<>(sides.get(0));
        List<List<T>> split = new ArrayList<>();
        split.add(java.util.Collections.singletonList(others.remove(index)));
        split.add(others);
        return split;
    }

    /** The players of these sides. */
    private static int count(List<? extends List<?>> sides) {
        int count = 0;
        for (List<?> side : sides) {
            count += side.size();
        }
        return Math.max(1, count);
    }

    /** Iron armour from the start, and what they buy over it. */
    @Override
    Gamer newGamer(Player player, Team team) {
        Gamer gamer = super.newGamer(player, team);
        gamer.armor = ARMOUR_TIER;
        return gamer;
    }

    /**
     * The bridges of each base built; the duo defense on each bed, the bed of one team gone and its
     * defense broken through; the forges full; the clock where the diamonds reach Tier III, with
     * the emeralds at Tier II.
     */
    @Override
    void setUp() {
        seconds = settings.diamondIII();
        for (Generator generator : diamonds) {
            generator.upgrade(DIAMOND_TIER, settings.diamond().get(DIAMOND_TIER - 1));
        }
        for (Generator generator : emeralds) {
            generator.upgrade(EMERALD_TIER, settings.emerald().get(EMERALD_TIER - 1));
        }
        buildBridges();
        for (Team team : teams) {
            if (team.points.bed == null) {
                continue;
            }
            Block bed = team.points.bed.blockIn(instance.world());
            if (team == bedless) {
                // the hole of the attacker who broke it, from the side of mid
                double[] mid = mid(team.points.bed);
                Defense.buildBroken(instance, bed, DEFENSE, team.color.woolData(), mid[0] - bed.getX() - 0.5,
                        mid[2] - bed.getZ() - 0.5);
            } else {
                Defense.build(instance, bed, DEFENSE, team.color.woolData());
            }
        }
        destroyBed(bedless);
        // every forge full: the teams' and, with forges-partout, those of the islands nobody plays
        for (Generator forge : forges) {
            forge.fill();
        }
    }

    /**
     * The bridges of each base, to its diamond island and to mid, then the one each team rushed the
     * enemy base by, from mid to the enemy bed (see {@link Bridges}), in wool of its colour, each
     * away from those built before it; placed as by players: they break them, and the copy of the
     * map loses them after the game.
     */
    private void buildBridges() {
        World world = instance.world();
        Bridges.Solid solid = (x, y, z) -> world.getBlockAt(x, y, z).getType().isSolid();
        Set<Long> built = new HashSet<>();
        Bridges.Taken taken = (x, z) -> built.contains(column(x, z));
        for (Team team : teams) {
            if (team.points == null || team.points.spawn == null) {
                continue;
            }
            for (double[][] route : Bridges.routes(point(team.points.spawn), points(instance.map().diamonds()),
                    points(instance.map().emeralds()))) {
                buildBridge(team, route, solid, taken, built);
            }
        }
        for (Team team : teams) {
            for (Team enemy : teams) {
                if (enemy == team || enemy.points == null || enemy.points.spawn == null || enemy.points.bed == null) {
                    continue;
                }
                MapPoint bed = enemy.points.bed;
                double[][] route = Bridges.rush(point(enemy.points.spawn),
                        new double[] {bed.x() + 0.5, bed.y(), bed.z() + 0.5}, points(instance.map().emeralds()));
                if (route != null) {
                    buildBridge(team, route, solid, taken, built);
                }
            }
        }
    }

    /** One bridge of a team, in wool of its colour, its columns added to those built. */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    private void buildBridge(Team team, double[][] route, Bridges.Solid solid, Bridges.Taken taken, Set<Long> built) {
        World world = instance.world();
        List<int[]> blocks = Bridges.plan(solid, route[0], route[1], taken);
        if (blocks.isEmpty()) {
            plugin.getLogger().warning("No room for a bridge of the " + team.color.displayName() + " team on "
                    + instance.map().world() + " from " + route[0][0] + " " + route[0][2] + " to " + route[1][0] + " "
                    + route[1][2]);
            return;
        }
        for (int[] place : blocks) {
            built.add(column(place[0], place[2]));
            Block block = world.getBlockAt(place[0], place[1], place[2]);
            if (instance.isPlaced(block)) {
                continue;
            }
            instance.markPlaced(block);
            block.setTypeIdAndData(Material.WOOL.getId(), (byte) team.color.woolData(), false);
        }
    }

    /** A column of the map, as one number. */
    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Mid, seen from this point: the nearest emerald generator, else the middle of the map. */
    private double[] mid(MapPoint from) {
        double[] here = point(from);
        double[] mid = {0.5, here[1], 0.5};
        double best = Double.MAX_VALUE;
        for (MapPoint emerald : instance.map().emeralds()) {
            double[] there = point(emerald);
            double distance = Math.hypot(there[0] - here[0], there[2] - here[2]);
            if (distance < best) {
                best = distance;
                mid = there;
            }
        }
        return mid;
    }

    private static double[] point(MapPoint point) {
        return new double[] {point.x(), point.y(), point.z()};
    }

    private static List<double[]> points(List<MapPoint> points) {
        List<double[]> list = new ArrayList<>();
        for (MapPoint point : points) {
            list.add(point(point));
        }
        return list;
    }

    /** A game's kit (the armour, the tools bought), with a stone sword for the wooden one. */
    @Override
    void giveKit(Player player, Gamer gamer) {
        super.giveKit(player, gamer);
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.WOOD_SWORD) {
                item.setType(Material.STONE_SWORD);
                inventory.setItem(slot, item);
            }
        }
        player.updateInventory();
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        String head = ChatColor.RED + "" + ChatColor.BOLD + modeName + ChatColor.RED + " on " + ChatColor.WHITE
                + instance.map().world() + ChatColor.RED + ": you are in the " + gamer.team.color.coloured() + ChatColor.RED
                + " team. ";
        if (gamer.team == bedless) {
            player.sendMessage(head + "Your bed is gone already: one life, clutch! The " + other(gamer.team).color.coloured()
                    + ChatColor.RED + " bed is under a defense of end stone and wool.");
            player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "NO BED", ChatColor.WHITE + "One life: clutch!");
        } else {
            boolean one = bedless.members.size() == 1;
            player.sendMessage(head + "You keep your bed, under a defense of end stone and wool; the " + bedless.color.coloured()
                    + ChatColor.RED + " team has lost theirs: " + (one ? "one final kill." : "one final kill each."));
            player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "BED ALIVE",
                    ChatColor.WHITE + (one ? "They have one life" : "They have one life each"));
        }
        player.sendMessage(ChatColor.GRAY + "Iron armour and a stone sword, your forge full of iron and gold, your bridges "
                + "built to mid and to your diamonds, the game half played (Diamond III, Emerald II): then it is a real "
                + "game. /spawn takes you back to the lobby.");
    }

    /** The first other team than this one. */
    private Team other(Team team) {
        for (Team candidate : teams) {
            if (candidate != team) {
                return candidate;
            }
        }
        return team;
    }
}
