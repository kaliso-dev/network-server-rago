package gg.rago.bedwars;

import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Top Bed Fight, the owner's mode on his Bed Fight map: the two players face each other on the
 * bridge he built on top, at the build height limit, each with a block of their colour at their
 * back. The rounds of Top Bridge: one life, a kill or a fall off the bridge (under the map's
 * hauteur-vide) gives the other side a point, the first to {@value TopBridgeGame#POINTS_TO_WIN}
 * wins. The mode's kit (the lobby's kit editor; else a wooden sword, a stack of wool and shears)
 * in leather armour of the team's colour, at each round.
 *
 * <p>The bridge is the map's: no platform is built, its blocks cannot be broken (only the blocks
 * placed can), and the rollback of each round makes it whole again. The players start on the
 * spawns of the map, in front of the block at each end, and nobody goes behind that block nor
 * builds there: they stay on the bridge. The beds stay where they are, below, out of the game.
 * Main thread only.
 */
final class TopBedFightGame extends TopBridgeGame {

    /** The kit when the lobby has none for the mode: the materials, with their amount. */
    static final String[] KIT = {"WOOD_SWORD", "WOOL 64", "SHEARS"};
    /**
     * How far behind their spawn, along the bridge, a player may stand (the spawn is half a block
     * in front of the block at their back): a little less than up to that block, so as not to
     * stand on it.
     */
    static final double BEHIND = 0.25;

    TopBedFightGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides, false);
    }

    /** No platform: the map has its bridge. */
    @Override
    boolean hasTopBridge() {
        return false;
    }

    /** Nothing to build for a round: the rollback has made the map's bridge whole again. */
    @Override
    void buildFight() {
    }

    @Override
    String roundsRule() {
        return "Rounds of one life on the top bridge: kill the other player or knock them off it. The first to "
                + pointsToWin() + " points wins. Nobody goes behind the block at their back.";
    }

    /** The beds stay on the map, below the bridge, and nobody comes back with them. */
    @Override
    void removeUnusedBeds() {
        for (Team team : teams) {
            team.bed = false;
        }
    }

    /** Leather armour of the team's colour and the mode's kit, at each round. */
    @Override
    void giveKit(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        gamer.armor = 0;
        wearArmor(player, gamer);
        ItemStack[] kit = lobbyKit(player, gamer);
        if (kit != null) {
            inventory.setContents(kit);
        } else {
            for (ItemStack item : BedFightGame.items(KIT)) {
                inventory.addItem(kitItem(item, gamer));
            }
        }
        player.updateInventory();
    }

    /**
     * Up to the build limit, anywhere on the bridge and around it, the spawns too (a block in the
     * other's way is the game), but not behind the block at either end.
     */
    @Override
    String cannotBuildAt(Block block) {
        if (block.getY() > instance.map().buildLimit()) {
            return "Build height limit reached!";
        }
        double[] ends = ends();
        if (ends != null && behind(ends, block.getX() + 0.5, block.getZ() + 0.5, BEHIND)) {
            return "You can't place blocks behind the bridge!";
        }
        return null;
    }

    /**
     * A player going behind the block at either end stays where they were; one already there (a
     * long knockback, a teleport) goes back to their spawn. Spectators go where they like.
     */
    @Override
    Location keepIn(Player player, Location from, Location to) {
        double[] ends = ends();
        if (ends == null || player.getGameMode() == GameMode.SPECTATOR || !behind(ends, to.getX(), to.getZ(), BEHIND)) {
            return null;
        }
        if (behind(ends, from.getX(), from.getZ(), BEHIND)) {
            Gamer gamer = gamers.get(player.getUniqueId());
            return gamer == null ? null : spawnOf(gamer);
        }
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        return back;
    }

    /** The spawns of the first two teams (x, z, x, z): the ends of the bridge; null without them. */
    private double[] ends() {
        if (teams.size() < 2 || teams.get(0).points == null || teams.get(1).points == null) {
            return null;
        }
        MapPoint a = teams.get(0).points.spawn;
        MapPoint b = teams.get(1).points.spawn;
        return a == null || b == null ? null : new double[] {a.x(), a.z(), b.x(), b.z()};
    }

    /** True if (x, z) is more than margin behind either end, along the line between the two. */
    static boolean behind(double[] ends, double x, double z, double margin) {
        double dx = ends[2] - ends[0];
        double dz = ends[3] - ends[1];
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length == 0) {
            return false;
        }
        double along = ((x - ends[0]) * dx + (z - ends[1]) * dz) / length;
        return along < -margin || along > length + margin;
    }
}
