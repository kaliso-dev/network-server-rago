package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The quests NPC of the spawn: a figure in the skin quetes beside the daily reward chest, holding
 * a book and quill, facing the spawn, with lines above its head that tell each player how many of
 * the daily and weekly quests they have done. A click on it or on its lines opens the quests (the
 * page of /quests). It stands on the point "quests" once one is placed (/lobby point quests);
 * else two blocks beside the daily chest (three if that spot is taken), on the side away from
 * the other figures. Without either point, there is none. Main thread only.
 */
final class QuestsNpc {

    static final String POINT = "quests";
    /** Its skin: skins/quetes.png in the folder of the plugin, else the one of the jar. */
    static final String SKIN = "quetes";
    static final int LINES = 3;
    private static final long CLICK_COOLDOWN_MS = 300;
    /** How far beside the chest it stands, the nearest first. */
    private static final int[] GAPS = {2, 3};

    interface Ground {
        /** True if a figure can stand with its feet in this block. */
        boolean standable(int x, int y, int z);
    }

    private final StatsBook book;
    private final ServerSettings server;
    private final Consumer<Player> open;
    private final Map<UUID, Long> lastClick = new HashMap<>();
    /** The signed skin of a name of skin.yml, or null for Steve. */
    private Function<String, Skin> skins = id -> null;

    QuestsNpc(StatsBook book, ServerSettings server, Consumer<Player> open) {
        this.book = book;
        this.server = server;
        this.open = open;
    }

    void setSkins(Function<String, Skin> skins) {
        this.skins = skins;
    }

    /**
     * Places the figure and its lines, after the NPCs (which clear every fake). Returns where it
     * stands, or null when it has nowhere to stand.
     */
    Location place(LobbyPoints points, World world, Location spawn, FakeEntities fakes) {
        Location at = spot(points, world, spawn);
        if (at == null) {
            return null;
        }
        FakeEntities.Clicker click = (player, left) -> click(player);
        fakes.add(new FakePlayer(at, LobbyNpcs.questsName(), skins.apply(SKIN), new ItemStack(Material.BOOK_AND_QUILL), click));
        fakes.add(new FakeHologram(at.clone().add(0, LobbyNpcs.ABOVE_HEAD, 0), LINES, this::lines, click));
        return at;
    }

    /** Its point, else beside the daily chest; facing the spawn. Null without a spot. */
    private static Location spot(LobbyPoints points, World world, Location spawn) {
        SpawnPoint own = points.get(POINT);
        Location at;
        if (own != null) {
            at = own.in(world);
        } else {
            SpawnPoint chest = points.get(DailyChest.POINT);
            if (chest == null) {
                return null;
            }
            Block block = chest.in(world).getBlock();
            List<double[]> others = new ArrayList<>();
            for (Map.Entry<String, SpawnPoint> point : points.all().entrySet()) {
                if (!point.getKey().equals(DailyChest.POINT)) {
                    others.add(new double[] {point.getValue().x(), point.getValue().z()});
                }
            }
            int[] xz = beside(block.getX(), block.getY(), block.getZ(), spawn.getX(), spawn.getZ(), others,
                    (x, y, z) -> standable(world.getBlockAt(x, y, z).getType(), world.getBlockAt(x, y + 1, z).getType(),
                            world.getBlockAt(x, y - 1, z).getType()));
            if (xz == null) {
                return null;
            }
            at = new Location(world, xz[0] + 0.5, block.getY(), xz[1] + 0.5);
        }
        at.setYaw(LobbyNpcs.yawTowards(at.getX(), at.getZ(), spawn.getX(), spawn.getZ()));
        at.setPitch(0f);
        return at;
    }

    /**
     * The block beside the chest at (x, y, z) where the figure stands, as {x, z}: across the line
     * from the chest to the spawn, two blocks away (three if two are not free), on the side whose
     * nearest other figure is farther (the right as seen from the spawn when they are as far).
     * Null if no spot is free.
     *
     * @param others the other figures, as {x, z}
     */
    static int[] beside(int x, int y, int z, double spawnX, double spawnZ, Collection<double[]> others, Ground ground) {
        double dx = spawnX - (x + 0.5);
        double dz = spawnZ - (z + 0.5);
        // One step to the right of someone at the spawn looking at the chest: (dz, -dx), on the nearest axis.
        int stepX;
        int stepZ;
        if (Math.abs(dx) >= Math.abs(dz)) {
            stepX = 0;
            stepZ = dx >= 0 ? -1 : 1;
        } else {
            stepX = dz >= 0 ? 1 : -1;
            stepZ = 0;
        }
        double right = clearance(x + 0.5 + stepX * GAPS[0], z + 0.5 + stepZ * GAPS[0], others);
        double left = clearance(x + 0.5 - stepX * GAPS[0], z + 0.5 - stepZ * GAPS[0], others);
        int first = left > right ? -1 : 1;
        for (int gap : GAPS) {
            for (int side : new int[] {first, -first}) {
                int atX = x + side * stepX * gap;
                int atZ = z + side * stepZ * gap;
                if (ground.standable(atX, y, atZ)) {
                    return new int[] {atX, atZ};
                }
            }
        }
        return null;
    }

    /** How far the nearest of the others is from (x, z), across the ground. */
    private static double clearance(double x, double z, Collection<double[]> others) {
        double nearest = Double.MAX_VALUE;
        for (double[] other : others) {
            nearest = Math.min(nearest, Math.hypot(other[0] - x, other[1] - z));
        }
        return nearest;
    }

    /**
     * A figure stands with its feet in a free block (air, or a carpet, a plate, a layer of snow on
     * the floor), its head in the air, on a solid block.
     */
    static boolean standable(Material feet, Material head, Material ground) {
        boolean free = feet == Material.AIR || feet == Material.CARPET || feet == Material.SNOW || feet.name().endsWith("_PLATE");
        return free && head == Material.AIR && ground.isSolid();
    }

    /** The steps of Getting started on this server: "Start 1/5" before the others until all are done. */
    private java.util.function.Supplier<List<GettingStarted.Step>> startSteps = java.util.Collections::emptyList;

    void setStartSteps(java.util.function.Supplier<List<GettingStarted.Step>> startSteps) {
        this.startSteps = startSteps;
    }

    List<String> lines(Player viewer) {
        return lines(server.quests(), book.get(viewer.getUniqueId()), System.currentTimeMillis(), startSteps.get());
    }

    /** "QUESTS", the quests done of each period ("Daily 2/4 · Weekly 0/4"), "Click to see them". */
    static List<String> lines(Quests quests, PlayerStats stats, long now) {
        return lines(quests, stats, now, java.util.Collections.<GettingStarted.Step>emptyList());
    }

    /** The same, with "Start 1/5" first while the player has not done all these steps of Getting started. */
    static List<String> lines(Quests quests, PlayerStats stats, long now, List<GettingStarted.Step> start) {
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        StringBuilder done = new StringBuilder();
        int started = GettingStarted.done(stats, start);
        if (started < start.size()) {
            done.append(ChatColor.GRAY).append("Start ").append(ChatColor.YELLOW).append(started).append('/').append(start.size());
        }
        for (Quests.Period period : Quests.Period.values()) {
            int count = quests.of(period).size();
            if (count == 0) {
                continue;
            }
            int finished = quests.done(stats, period, day, week);
            if (done.length() > 0) {
                done.append(ChatColor.DARK_GRAY).append(" · ");
            }
            done.append(ChatColor.GRAY).append(period == Quests.Period.DAILY ? "Daily " : "Weekly ")
                    .append(finished == count ? ChatColor.GREEN : ChatColor.YELLOW).append(finished).append('/').append(count);
        }
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "QUESTS");
        lines.add(done.length() == 0 ? ChatColor.GRAY + "No quests for now" : done.toString());
        lines.add(ChatColor.YELLOW + "Click to see them");
        return lines;
    }

    /** A click on the figure or its lines: the quests. */
    void click(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return;
        }
        open.accept(player);
    }
}
