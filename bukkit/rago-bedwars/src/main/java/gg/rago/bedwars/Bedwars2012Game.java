package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 2012 Bedwars (the owner's ask of 27/11), as a rush of 2012: two great bases far apart, the
 * emeralds in the middle and the diamonds on an island on each side (the version with them), or
 * the forges of the bases only (the classic version). Each team hides its bed: in the first minute
 * ({@value #HIDE_SECONDS} seconds), each player has the team's bed in the hotbar and puts it on the
 * top of a block, anywhere a block goes (not on a spawn nor a generator, the protection of every
 * game) and not against a shop; the first one put is the team's, the others go. A team that did not
 * hide it gets its bed where the map has it, behind its base. Clay in the place of the wool, the
 * items and the forges of a game, no team upgrades. Everything else is a game of Bed Wars: the
 * shops, the final kills, the bed broken. Main thread only.
 */
final class Bedwars2012Game extends BedwarsGame {

    /** The seconds a team has to hide its bed (the owner: one minute). */
    static final int HIDE_SECONDS = 60;
    /** How near a shop or upgrades NPC no bed goes, in blocks. */
    static final double NPC_ROOM = 3;
    /** The name of the bed to hide: what marks it among the items. */
    static final String BED_NAME = ChatColor.RED + "" + ChatColor.BOLD + "Your bed " + ChatColor.GRAY + "(right click: hide it)";

    /** True for the version with the diamonds of the islands and the emeralds of the middle. */
    private final boolean generators;
    /** The beds of the map, taken away for the hiding, put back for a team that did not hide its own. */
    private final List<BlockState> mapBeds = new ArrayList<>();
    /** The teams that hid their bed. */
    private final Set<TeamColor> hidden = EnumSet.noneOf(TeamColor.class);
    /** True in the first minute, while the beds are hidden. */
    private boolean hiding = true;

    Bedwars2012Game(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, boolean generators) {
        super(plugin, host, instance, modeId, modeName, sides, plugin.catalog().clayForWool());
        this.generators = generators;
    }

    @Override
    boolean upgrades() {
        return false;
    }

    @Override
    boolean midGenerators() {
        return generators;
    }

    @Override
    boolean diamondGenerators() {
        return generators;
    }

    @Override
    int[] times() {
        return timeline(super.times(), generators);
    }

    /** The events of a game, without the tiers of the diamonds and emeralds when the map has none. */
    static int[] timeline(int[] times, boolean generators) {
        int[] result = times.clone();
        if (!generators) {
            for (int i = 0; i < 4; i++) {
                result[i] = -1;
            }
        }
        return result;
    }

    /** Before the players come: the beds of the map go, to be hidden. */
    @Override
    void setUp() {
        super.setUp();
        for (Team team : teams) {
            if (team.points == null || team.points.bed == null) {
                continue;
            }
            for (Block half : Instance.bedOf(team.points.bed.blockIn(instance.world()))) {
                mapBeds.add(half.getState());
            }
        }
        for (BlockState state : mapBeds) {
            state.getBlock().setType(Material.AIR, false);
        }
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        super.welcome(player, gamer);
        player.sendMessage(rules(generators));
    }

    /** The second line of the welcome: what makes the game a rush of 2012. */
    static String rules(boolean generators) {
        return ChatColor.GRAY + "Hide your bed in the first minute: right click the top of a block with it, anywhere"
                + " but a spawn, a generator or a shop. Else it goes behind your base. Clay, no team upgrades"
                + (generators ? "." : ", no diamonds nor emeralds.");
    }

    @Override
    void started() {
        super.started();
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                giveBed(player, gamer);
                player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "HIDE YOUR BED!",
                        ChatColor.WHITE + "" + HIDE_SECONDS + " seconds, anywhere you like");
            }
        }
    }

    /** The kit, and the bed while the team has not hidden it. */
    @Override
    void giveKit(Player player, Gamer gamer) {
        super.giveKit(player, gamer);
        giveBed(player, gamer);
    }

    private void giveBed(Player player, Gamer gamer) {
        if (!hiding || hidden.contains(gamer.team.color) || player.getInventory().contains(Material.BED)) {
            return;
        }
        player.getInventory().addItem(bedItem());
    }

    /** The bed to hide: its name tells what it is for. */
    static ItemStack bedItem() {
        ItemStack bed = new ItemStack(Material.BED);
        ItemMeta meta = bed.getItemMeta();
        meta.setDisplayName(BED_NAME);
        bed.setItemMeta(meta);
        return bed;
    }

    /** True if this item is a bed to hide (and not the Leave bed of the end of a game). */
    static boolean isBedToHide(ItemStack item) {
        return item != null && item.getType() == Material.BED && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && BED_NAME.equals(item.getItemMeta().getDisplayName());
    }

    @Override
    void everySecond() {
        super.everySecond();
        if (!hiding) {
            return;
        }
        int left = HIDE_SECONDS - seconds;
        if (left == 30 || left == 10 || (left > 0 && left <= 3)) {
            for (Gamer gamer : gamers.values()) {
                Player player = Bukkit.getPlayer(gamer.id);
                if (player != null && host.gameOf(gamer.id) == this && !hidden.contains(gamer.team.color)) {
                    player.sendMessage(ChatColor.RED + "Hide your bed: " + ChatColor.WHITE + left + ChatColor.RED
                            + (left == 1 ? " second left!" : " seconds left!"));
                }
            }
        }
        if (seconds >= HIDE_SECONDS) {
            endHiding();
        }
    }

    /**
     * A player puts the team's bed on the top of this block: its foot on the block above, its head
     * the way the player looks. Refused, with a message, where a block cannot go, on what is not
     * free, or next to a shop; else it is the team's bed, and the others go.
     */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to put a bed
    void hideBed(Player player, Block clicked, BlockFace face) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || phase != Phase.PLAYING) {
            return;
        }
        if (!hiding || hidden.contains(gamer.team.color)) {
            takeBeds(player);
            player.sendMessage(ChatColor.RED + "Your team's bed is already in its place.");
            return;
        }
        if (clicked == null || face != BlockFace.UP) {
            player.sendMessage(ChatColor.RED + "Put your bed on the top of a block.");
            return;
        }
        byte direction = direction(player.getLocation().getYaw());
        Block foot = clicked.getRelative(BlockFace.UP);
        Block head = foot.getRelative(towardsHead(direction));
        String refused = refusal(foot, head);
        if (refused != null) {
            player.sendMessage(ChatColor.RED + refused);
            return;
        }
        instance.markPlaced(foot);
        instance.markPlaced(head);
        foot.setTypeIdAndData(Material.BED_BLOCK.getId(), direction, false);
        head.setTypeIdAndData(Material.BED_BLOCK.getId(), (byte) (direction | 8), false);
        gamer.team.hiddenBed = MapPoint.block(foot);
        hidden.add(gamer.team.color);
        for (Gamer mate : gamers.values()) {
            Player other = Bukkit.getPlayer(mate.id);
            if (other == null || mate.team != gamer.team || host.gameOf(mate.id) != this) {
                continue;
            }
            takeBeds(other);
            other.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "BED HIDDEN! " + ChatColor.GRAY + gamer.name
                    + " hid your bed. Only your team knows where.");
            other.playSound(other.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
        }
        if (hidden.size() >= teams.size()) {
            endHiding();
        }
    }

    /** Why a bed cannot go there (its foot and head), or null: it can. */
    private String refusal(Block foot, Block head) {
        for (Block half : new Block[] {foot, head}) {
            if (half.getType() != Material.AIR || !half.getRelative(BlockFace.DOWN).getType().isSolid()) {
                return "Not enough room for your bed here: two free blocks on solid ground.";
            }
            String cannot = cannotBuildAt(half);
            if (cannot != null) {
                return cannot.isEmpty() ? "You can't put your bed here!" : cannot;
            }
            if (nearNpc(half)) {
                return "Not against a shop: put your bed somewhere else.";
            }
        }
        return null;
    }

    /** True if this block is near the shop or the upgrades NPC of an island of the map. */
    private boolean nearNpc(Block block) {
        for (BedwarsMap.Team team : instance.map().teams().values()) {
            for (MapPoint npc : new MapPoint[] {team.shop, team.upgrades}) {
                if (npc != null && npc.flatDistanceSquared(block.getX(), block.getZ()) < NPC_ROOM * NPC_ROOM
                        && Math.abs(block.getY() - npc.y()) < 3) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The 1.8 data of a bed's foot whose head goes the way of this yaw: 0 south, 1 west, 2 north, 3 east. */
    static byte direction(float yaw) {
        return (byte) (((Math.round(yaw / 90f) % 4) + 4) % 4);
    }

    /** The side of the foot where the head of a bed of this direction is. */
    static BlockFace towardsHead(byte direction) {
        return new BlockFace[] {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST}[direction & 3];
    }

    /** The minute is over (or every team hid its bed): the bed of the map for a team that did not. */
    private void endHiding() {
        if (!hiding) {
            return;
        }
        hiding = false;
        for (BlockState state : mapBeds) {
            Team team = null;
            for (Team candidate : teams) {
                if (candidate.points != null && candidate.points.bed != null
                        && sameBed(candidate.points.bed.blockIn(instance.world()), state.getBlock())) {
                    team = candidate;
                }
            }
            if (team != null && !hidden.contains(team.color)) {
                state.update(true, false);
            }
        }
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null || host.gameOf(gamer.id) != this) {
                continue;
            }
            takeBeds(player);
            if (!hidden.contains(gamer.team.color)) {
                player.sendMessage(ChatColor.YELLOW + "Time's up: your bed went behind your base.");
            }
        }
        for (Item item : instance.world().getEntitiesByClass(Item.class)) {
            if (isBedToHide(item.getItemStack())) {
                item.remove();
            }
        }
    }

    /** True if this block is the foot of the bed of the map or its head (the head is next to the foot). */
    private static boolean sameBed(Block foot, Block block) {
        return block.equals(foot) || (Math.abs(block.getX() - foot.getX()) + Math.abs(block.getZ() - foot.getZ()) == 1
                && block.getY() == foot.getY());
    }

    /** The beds to hide go from this player's inventory and cursor. */
    private static void takeBeds(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isBedToHide(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
        if (isBedToHide(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    /** True while the teams hide their bed (the first minute). */
    boolean hiding() {
        return hiding;
    }
}
