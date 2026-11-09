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
import java.util.Random;
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
 * has an island of a Bed Wars map and its shop, a list of items to buy and just the iron, gold
 * and emeralds it takes; the time runs from the opening of the shop to the last item bought.
 * Seven lists, one after the other, each with its records: the first buy, iron armour, the tools,
 * a fireball fight, a bed defense, a rush, and a list drawn at random. A click on an item that is
 * not on the list buys nothing and counts as a misclick. Open: players come and go; nobody can
 * hit anyone, and what is bought is only shown (nothing is placed, thrown, eaten or drunk). Main
 * thread only.
 */
final class FastShopGame extends BedwarsGame {

    /** At most this many players on one island. */
    static final int PER_ISLAND = 2;
    private static final int RANDOM_ITEMS = 5;
    /** After a list, the next one comes this long after. */
    private static final long NEXT_TICKS = 50L;

    /** A list to buy: its id (for the records), its name, the shop ids, one per click. */
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

    /** The lists, in the order they come. The last one is drawn at random each time. */
    static final List<Order> ORDERS = Collections.unmodifiableList(Arrays.asList(
            new Order("first-buy", "First buy", "wool", "wool", "stone_sword", "chainmail"),
            new Order("iron-armour", "Iron armour", "iron_boots", "wool", "wool", "golden_apple", "golden_apple"),
            new Order("tools", "Tools", "pickaxe", "pickaxe", "axe", "shears"),
            new Order("fireball-fight", "Fireball fight", "fireball", "fireball", "fireball", "fireball", "wool", "wool",
                    "golden_apple"),
            new Order("bed-defense", "Bed defense", "end_stone", "end_stone", "plank", "plank", "wool", "wool"),
            new Order("rush", "Rush", "iron_sword", "wool", "wool", "wool", "tnt", "ender_pearl", "speed"),
            new Order("random", "Random list")));

    /** What the random list draws from: each id once at most, but the blocks and what is used up. */
    private static final List<String> RANDOM_POOL = Collections.unmodifiableList(Arrays.asList("wool", "clay", "glass",
            "end_stone", "ladder", "plank", "obsidian", "stone_sword", "iron_sword", "diamond_sword", "stick", "chainmail",
            "iron_boots", "diamond_boots", "shears", "pickaxe", "axe", "arrow", "bow", "speed", "jump", "invisibility",
            "golden_apple", "fireball", "tnt", "ender_pearl", "water", "sponge"));
    private static final List<String> ARMOUR = Arrays.asList("chainmail", "iron_boots", "diamond_boots");
    private static final List<String> REPEATABLE = Arrays.asList("wool", "clay", "glass", "end_stone", "ladder", "plank",
            "obsidian", "arrow", "golden_apple", "fireball", "tnt");

    /** A player's list being bought. */
    private static final class Run {
        final Order order;
        final List<String> items;
        final List<String> left;
        long started;
        long finished;
        int misclicks;

        Run(Order order, List<String> items) {
            this.order = order;
            this.items = items;
            this.left = new ArrayList<>(items);
        }

        boolean done() {
            return left.isEmpty();
        }
    }

    private final Random random = new Random();
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
            spawnNpc(island.points.shop.in(world), island.points.spawn.in(world), ChatColor.AQUA + "ITEM SHOP", null);
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
        List<String> items = order.items.isEmpty() ? randomList(random) : order.items;
        Run run = new Run(order, items);
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
        List<String> names = new ArrayList<>();
        for (String id : items) {
            ShopItem item = catalog.byId(id);
            names.add(item == null ? id : shortName(item));
        }
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + order.name + ChatColor.AQUA + ": " + ChatColor.WHITE
                + String.join(ChatColor.GRAY + ", " + ChatColor.WHITE, names) + ChatColor.AQUA + ". Open the shop: the time starts.");
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
        sidebars();
    }

    /** Five items drawn from the shop: one armour at most, each tool or potion once. */
    static List<String> randomList(Random random) {
        List<String> drawn = new ArrayList<>();
        boolean armour = false;
        while (drawn.size() < RANDOM_ITEMS) {
            String id = RANDOM_POOL.get(random.nextInt(RANDOM_POOL.size()));
            if (ARMOUR.contains(id) ? armour : drawn.contains(id) && !REPEATABLE.contains(id)) {
                continue;
            }
            armour |= ARMOUR.contains(id);
            drawn.add(id);
        }
        return drawn;
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

    /** Only what is on the list can be bought; the last item stops the time. */
    @Override
    boolean buy(Player player, ShopItem item) {
        Gamer gamer = gamers.get(player.getUniqueId());
        Run run = runs.get(player.getUniqueId());
        if (gamer == null || run == null || run.done()) {
            return false;
        }
        ShopItem bought = shown(gamer, item);
        if (!run.left.contains(bought.id())) {
            run.misclicks++;
            player.sendMessage(ChatColor.RED + shortName(bought) + " is not on your list.");
            player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
            return false;
        }
        if (run.started == 0) {
            run.started = System.currentTimeMillis();
        }
        if (!super.buy(player, item)) {
            return false;
        }
        run.left.remove(bought.id());
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
        lines.add(ChatColor.WHITE + run.order.name + ": " + ChatColor.GREEN + (run.items.size() - run.left.size())
                + ChatColor.GRAY + "/" + run.items.size());
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
        // what is left to buy, as many lines as there is room for
        List<String> shown = new ArrayList<>();
        for (String id : run.left) {
            ShopItem item = catalog.byId(id);
            String name = item == null ? id : shortName(item);
            shown.add(ChatColor.RED + "✘ " + ChatColor.WHITE + (name.length() > 30 ? name.substring(0, 30) : name));
        }
        for (int i = 0; i < shown.size() && lines.size() < 12; i++) {
            lines.add(shown.get(i));
        }
        return lines;
    }
}
