package gg.rago.bedwars;

import gg.rago.bedwars.ShopItem.Currency;
import gg.rago.bedwars.ShopItem.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Fast Shop: buying in the item shop as fast as possible, with the player's own quick buy (copied
 * from Hypixel in the lobby: the emerald of the kit editor, or /hypixel import name). Each player
 * has an island of a Bed Wars map and its shop, a list of items to buy in order and just the
 * iron, gold and emeralds it takes; the time runs from the opening of the shop to the last item
 * bought. The lists are what is bought in Ranked Bed Wars and in a clutch, one after the other,
 * each with its records. A click on anything but the next item of the list buys nothing and counts
 * as a misclick. Open: players come and go; nobody can hit anyone, and what is bought is only
 * shown (nothing is placed, thrown, eaten or drunk). Main thread only.
 */
final class FastShopGame extends BedwarsGame {

    /** At most this many players on one island. */
    static final int PER_ISLAND = 2;
    /** After a list, the next one comes this long after. */
    private static final long NEXT_TICKS = 20L;

    /** A list to buy: its id (for the records), its name, the shop ids in the order to buy, one per click. */
    static final class Order {
        final String id;
        final String name;
        final List<String> items;

        Order(String id, String name, String... items) {
            this.id = id;
            this.name = name;
            this.items = Collections.unmodifiableList(Arrays.asList(items));
        }
    }

    /** The lists, in the order they come: what is bought in Ranked Bed Wars and in a clutch. */
    static final List<Order> ORDERS = Collections.unmodifiableList(Arrays.asList(
            new Order("rush-start", "Rush start", "wool", "wool", "stone_sword"),
            new Order("ranked-defense", "Ranked bed defense", "end_stone", "glass", "glass", "glass", "glass"),
            new Order("solo-defense", "Solo bed defense", "end_stone", "wool", "wool"),
            new Order("first-rotation", "Iron armour and tools", "iron_boots", "pickaxe", "shears"),
            new Order("fireball-fight", "Fireball fight", "fireball", "fireball", "golden_apple", "wool"),
            new Order("bed-break", "Bed break", "pickaxe", "pickaxe", "shears", "tnt"),
            new Order("clutch-bed", "Clutch: your bed is attacked", "wool", "wool", "fireball", "golden_apple"),
            new Order("clutch-respawn", "Clutch: back from a death", "stone_sword", "wool", "wool", "golden_apple"),
            new Order("emeralds", "Emeralds: the late game", "diamond_boots", "ender_pearl", "golden_apple"),
            new Order("invis-rush", "Invisibility rush", "invisibility", "jump", "wool", "wool", "fireball")));

    /** A player's list being bought, in order. */
    private static final class Run {
        final Order order;
        final List<String> items;
        /** How many items of the list are bought: the next one is items[bought]. */
        int bought;
        long started;
        long finished;
        int misclicks;

        Run(Order order) {
            this.order = order;
            this.items = order.items;
        }

        boolean done() {
            return bought >= items.size();
        }

        String next() {
            return done() ? null : items.get(bought);
        }
    }

    private final FastShopRecords records;
    /** Every island of the map, each with a shop. */
    private final List<Team> islands = new ArrayList<>();
    private final Map<UUID, Run> runs = new HashMap<>();
    /** The list each player is on, in {@link #ORDERS}. */
    private final Map<UUID, Integer> next = new HashMap<>();

    FastShopGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides);
        this.records = plugin.fastShopRecords();
        for (TeamColor color : instance.map().completeTeams()) {
            islands.add(new Team(color, instance.map().teams().get(color)));
        }
    }

    @Override
    boolean joinable() {
        return phase != Phase.ENDED && present().size() < islands.size() * PER_ISLAND;
    }

    @Override
    void join(Player player) {
        UUID id = player.getUniqueId();
        forget(id);
        Gamer gamer = newGamer(player, islandTeam(id));
        gamers.put(id, gamer);
        host.joined(id, this);
        place(player, gamer);
        welcome(player, gamer);
        newRun(player, gamer);
        sidebars();
    }

    /** A team of one on the island with the fewest players. */
    private Team islandTeam(UUID player) {
        Team emptiest = islands.get(0);
        int fewest = Integer.MAX_VALUE;
        for (Team island : islands) {
            int on = 0;
            for (Gamer gamer : present()) {
                if (gamer.team.color == island.color) {
                    on++;
                }
            }
            if (on < fewest) {
                fewest = on;
                emptiest = island;
            }
        }
        Team team = new Team(emptiest.color, emptiest.points);
        team.members.add(player);
        teams.add(team);
        return team;
    }

    private void forget(UUID id) {
        Gamer old = gamers.remove(id);
        if (old != null) {
            teams.remove(old.team);
        }
        runs.remove(id);
    }

    // --- The practice ---------------------------------------------------------------------------

    @Override
    boolean economy() {
        return false;
    }

    /** Everyone in the game hears everyone. */
    @Override
    boolean chatTeams() {
        return false;
    }

    /** The names keep the players' ranks: no teams to show. */
    @Override
    boolean teamNameTags() {
        return false;
    }

    /** The map stays as it is: the beds too. */
    @Override
    void removeUnusedBeds() {
    }

    /** The shop of every island. */
    @Override
    void setUp() {
        World world = instance.world();
        for (Team island : islands) {
            spawnNpc(island.points.shop.in(world), island.points.spawn.in(world), ChatColor.AQUA + "ITEM SHOP", false);
        }
    }

    @Override
    int countdownSeconds() {
        return 0;
    }

    @Override
    int respawnSeconds() {
        return 0;
    }

    @Override
    boolean finalDeath(Gamer gamer) {
        return false;
    }

    @Override
    boolean pvp() {
        return false;
    }

    @Override
    boolean itemsUsable() {
        return false;
    }

    @Override
    Team bedTeam(Block block) {
        return null;
    }

    @Override
    String cannotBuildAt(Block block) {
        return "";
    }

    @Override
    void events() {
    }

    @Override
    void started() {
        for (Gamer gamer : present()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null) {
                newRun(player, gamer);
            }
        }
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Fast Shop" + ChatColor.GREEN + ": buy each list "
                + "as fast as you can. The time runs from the opening of the shop to the last item bought.");
        if (plugin.lobby().hypixelQuickBuy(player) == null) {
            player.sendMessage(ChatColor.YELLOW + "Your shop has the default quick buy: copy yours from Hypixel in the lobby, "
                    + "with the emerald of the kit editor (the book) or " + ChatColor.WHITE + "/hypixel import <name>"
                    + ChatColor.YELLOW + ".");
        } else {
            player.sendMessage(ChatColor.GRAY + "Your quick buy is the one you copied from Hypixel.");
        }
        player.sendMessage(ChatColor.GRAY + "/spawn takes you back to the lobby.");
    }

    /** A void fall: the list starts again, once back on the island. */
    @Override
    void died(Gamer victim, Gamer author, boolean finalKill) {
        final UUID id = victim.id;
        later(2L, () -> {
            Player player = Bukkit.getPlayer(id);
            Gamer gamer = gamers.get(id);
            Run run = runs.get(id);
            if (player != null && gamer != null && present(gamer)) {
                newRun(player, gamer, run == null ? null : run.order);
            }
        });
    }

    @Override
    void left(Gamer gamer) {
        forget(gamer.id);
    }

    @Override
    void checkEnd() {
        if (present().isEmpty()) {
            close();
        }
    }

    /** The next list of this player. */
    private void newRun(Player player, Gamer gamer) {
        int index = next.getOrDefault(gamer.id, 0) % ORDERS.size();
        next.put(gamer.id, index + 1);
        newRun(player, gamer, ORDERS.get(index));
    }

    /** This list (null: the next one) from the start: a new Bed Wars life, and just what the list costs. */
    private void newRun(Player player, Gamer gamer, Order order) {
        if (order == null) {
            newRun(player, gamer);
            return;
        }
        List<String> items = order.items;
        Run run = new Run(order);
        runs.put(gamer.id, run);
        gamer.armor = 0;
        gamer.pickaxe = -1;
        gamer.axe = -1;
        gamer.shears = false;
        player.closeInventory();
        heal(player);
        giveKit(player, gamer);
        PlayerInventory inventory = player.getInventory();
        // the money in the inventory, not in the hotbar where the items bought go
        int slot = 9;
        for (Map.Entry<Currency, Integer> cost : cost(catalog, items).entrySet()) {
            int amount = cost.getValue();
            while (amount > 0 && slot < 36) {
                int stack = Math.min(64, amount);
                inventory.setItem(slot++, new ItemStack(cost.getKey().material(), stack));
                amount -= stack;
            }
        }
        player.updateInventory();
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + order.name + ChatColor.AQUA + ": buy in this order.");
        for (int i = 0; i < items.size(); i++) {
            player.sendMessage(ChatColor.GRAY + " " + (i + 1) + ". " + ChatColor.WHITE + nameOf(items.get(i)));
        }
        player.sendMessage(ChatColor.AQUA + "Open the shop: the time starts.");
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
        sidebars();
    }

    /** What these clicks cost in this shop, the pickaxe and the axe tier after tier. */
    static Map<Currency, Integer> cost(ShopCatalog catalog, List<String> items) {
        Map<Currency, Integer> cost = new EnumMap<>(Currency.class);
        int pickaxe = -1;
        int axe = -1;
        for (String id : items) {
            ShopItem item = catalog.byId(id);
            if (item == null) {
                continue;
            }
            if (item.kind() == Kind.PICKAXE) {
                pickaxe = Math.min(pickaxe + 1, catalog.pickaxes().size() - 1);
                item = catalog.pickaxes().get(pickaxe);
            } else if (item.kind() == Kind.AXE) {
                axe = Math.min(axe + 1, catalog.axes().size() - 1);
                item = catalog.axes().get(axe);
            }
            cost.merge(item.currency(), item.price(), Integer::sum);
        }
        return cost;
    }

    private String nameOf(String id) {
        ShopItem item = catalog.byId(id);
        return item == null ? id : shortName(item);
    }

    private static String shortName(ShopItem item) {
        String name = item.name().replace("Permanent ", "");
        int cut = name.indexOf(" (");
        return cut > 0 && item.kind() == Kind.POTION ? name.substring(0, cut) : name;
    }

    @Override
    void shopOpened(Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run != null && run.started == 0 && !run.done()) {
            run.started = System.currentTimeMillis();
        }
    }

    /** Only the next item of the list can be bought; the last one stops the time. */
    @Override
    boolean buy(Player player, ShopItem item) {
        Gamer gamer = gamers.get(player.getUniqueId());
        Run run = runs.get(player.getUniqueId());
        if (gamer == null || run == null || run.done()) {
            return false;
        }
        ShopItem bought = shown(gamer, item);
        if (!bought.id().equals(run.next())) {
            run.misclicks++;
            player.sendMessage(ChatColor.RED + (run.items.contains(bought.id()) ? "Not yet: " : "Not on your list: ")
                    + ChatColor.WHITE + shortName(bought) + ChatColor.RED + ". Next: " + ChatColor.WHITE + nameOf(run.next())
                    + ChatColor.RED + ".");
            player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
            return false;
        }
        if (run.started == 0) {
            run.started = System.currentTimeMillis();
        }
        if (!super.buy(player, item)) {
            return false;
        }
        run.bought++;
        if (run.done()) {
            finished(player, gamer, run);
        } else {
            sidebars();
        }
        return true;
    }

    private void finished(final Player player, final Gamer gamer, Run run) {
        run.finished = System.currentTimeMillis();
        long millis = run.finished - run.started;
        Long before = records.best(run.order.id, gamer.id);
        boolean best = records.record(run.order.id, gamer.id, gamer.name, millis);
        // out of the click that bought the last item
        later(1L, player::closeInventory);
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + run.order.name + ChatColor.GREEN + " bought in "
                + ChatColor.WHITE + seconds(millis) + " s" + ChatColor.GREEN + (run.misclicks == 0 ? ", no misclick."
                : ", " + run.misclicks + " misclick" + (run.misclicks > 1 ? "s." : ".")));
        if (best) {
            player.sendMessage(ChatColor.GOLD + (before == null ? "Your first time on this list!" : "New personal best! "
                    + ChatColor.GRAY + "(" + seconds(before) + " s before)"));
            player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + seconds(millis) + " s", ChatColor.YELLOW + "Personal best!");
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
        } else {
            player.sendMessage(ChatColor.GRAY + "Your best: " + seconds(before) + " s.");
            player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + seconds(millis) + " s", "");
            player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
        }
        FastShopRecords.Best top = records.top(run.order.id);
        if (top != null && top.player.equals(gamer.id) && best) {
            broadcast(ChatColor.GOLD + gamer.name + ChatColor.YELLOW + " holds the server record of " + run.order.name + ": "
                    + ChatColor.WHITE + seconds(millis) + " s" + ChatColor.YELLOW + "!", null);
        }
        sidebars();
        final UUID id = gamer.id;
        later(NEXT_TICKS, () -> {
            Player online = Bukkit.getPlayer(id);
            Gamer still = gamers.get(id);
            if (online != null && still != null && present(still) && runs.get(id) != null && runs.get(id).done()) {
                newRun(online, still);
            }
        });
    }

    static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.2f", millis / 1000.0);
    }

    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GREEN + "Fast Shop");
        lines.add("");
        Run run = runs.get(viewer.id);
        if (run == null) {
            return lines;
        }
        String title = run.order.name.length() > 24 ? run.order.name.substring(0, 24) : run.order.name;
        lines.add(ChatColor.WHITE + title + ": " + ChatColor.GREEN + run.bought + ChatColor.GRAY + "/" + run.items.size());
        long elapsed = run.started == 0 ? 0 : (run.done() ? run.finished : System.currentTimeMillis()) - run.started;
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + (run.started == 0 ? "-" : seconds(elapsed) + " s"));
        Long best = records.best(run.order.id, viewer.id);
        lines.add(ChatColor.WHITE + "Best: " + ChatColor.GREEN + (best == null ? "-" : seconds(best) + " s"));
        FastShopRecords.Best top = records.top(run.order.id);
        if (top != null) {
            String name = top.name.length() > 14 ? top.name.substring(0, 14) : top.name;
            lines.add(ChatColor.WHITE + "Record: " + ChatColor.GOLD + seconds(top.millis) + " s " + ChatColor.GRAY + name);
        }
        lines.add("");
        // the list in order: what is bought, the next item, then the rest, as many as there is room for
        for (int i = 0; i < run.items.size() && lines.size() < 14; i++) {
            String name = nameOf(run.items.get(i));
            name = name.length() > 24 ? name.substring(0, 24) : name;
            if (i < run.bought) {
                lines.add(ChatColor.GREEN + "✔ " + ChatColor.GRAY + (i + 1) + ". " + name);
            } else if (i == run.bought) {
                lines.add(ChatColor.YELLOW + "➜ " + ChatColor.WHITE + ChatColor.BOLD + (i + 1) + ". " + name);
            } else {
                lines.add(ChatColor.DARK_GRAY + "  " + ChatColor.WHITE + (i + 1) + ". " + name);
            }
        }
        return lines;
    }
}
