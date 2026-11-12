package gg.rago.practice;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The records of the trainings. /records [player]: a menu of the trainings, with the player's best
 * on each board and their place; a training opens its boards, each with its top 10 and the
 * player's place among their friends, and a click on a board plays it. And the wall of the practice
 * records, on a point of the lobby: the top 10 of a board after the other, the player's place under
 * it (a left click: the next board, a right click: /records). Main thread.
 */
final class RecordsMenu implements CommandExecutor, Listener {

    /** Lines of the wall: as many as the boards of the lobby. */
    static final int WALL_LINES = 19;
    /** Rows of a top. */
    static final int TOP = 10;
    /** The wall shows the next board after this long. */
    static final long TURN_MS = 8_000L;
    /** A board chosen with a click stays this long before the wall turns again. */
    static final long HOLD_MS = 20_000L;
    /** How long a click on a board keeps it for the next training of its mode. */
    static final long WANTED_MS = 60_000L;
    private static final long CLICK_COOLDOWN_MS = 300L;
    /** The menus: five rows, the summary on top, the buttons at the bottom. */
    private static final int SIZE = 45;
    private static final int TOP_SLOT = 4;
    private static final int BACK_SLOT = 36;
    private static final int CLOSE_SLOT = 40;
    /** Trainings (or boards) in a row of a menu, in its middle. */
    private static final int PER_ROW = 7;
    /** Titles of a chest are 32 characters at most in 1.8. */
    private static final int MAX_TITLE = 32;
    private static final ChatColor[] PLACES = {ChatColor.GOLD, ChatColor.WHITE, ChatColor.RED};

    /** A page of /records: whose records, and the training it shows (null: every training). */
    private static final class Page implements InventoryHolder {
        final UUID player;
        final String name;
        final RecordCatalog.Entry entry;
        final Map<Integer, RecordCatalog.Entry> trainings = new HashMap<>();
        final Map<Integer, RecordCatalog.Board> boards = new HashMap<>();
        Inventory inventory;

        Page(UUID player, String name, RecordCatalog.Entry entry) {
            this.player = player;
            this.name = name;
            this.entry = entry;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** A board clicked to be played, for the next training of its mode. */
    private static final class Wanted {
        final RecordCatalog.Board board;
        final long at;

        Wanted(RecordCatalog.Board board, long at) {
            this.board = board;
            this.at = at;
        }
    }

    /** The board of the wall a player chose with a click (its place among those shown), and when. */
    static final class Pick {
        final int index;
        final long at;

        Pick(int index, long at) {
            this.index = index;
            this.at = at;
        }
    }

    private final RagoPractice plugin;
    /** The trainings this server plays, in order. */
    private final List<RecordCatalog.Entry> entries;
    private final List<RecordCatalog.Board> boards;
    private final Map<UUID, Wanted> wanted = new HashMap<>();
    private final Map<UUID, Pick> picks = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    RecordsMenu(RagoPractice plugin, List<RecordCatalog.Entry> entries) {
        this.plugin = plugin;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.boards = RecordCatalog.boards(entries);
    }

    private RecordBook book() {
        return plugin.records().book();
    }

    private boolean daily(RecordCatalog.Board board) {
        Challenges challenges = plugin.challenges();
        return board.challenge != null && challenges != null && challenges.isToday(board.challenge);
    }

    // --- /records -------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only a player opens the records.");
            return true;
        }
        Player player = (Player) sender;
        if (entries.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "This server plays no training with records.");
            return true;
        }
        if (args.length == 0) {
            open(player, player.getUniqueId(), player.getName());
            return true;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        Map.Entry<UUID, String> other = online != null ? new AbstractMap.SimpleImmutableEntry<>(online.getUniqueId(), online.getName())
                : find(book(), args[0]);
        if (other == null) {
            player.sendMessage(ChatColor.RED + "No records of " + args[0] + " yet.");
            return true;
        }
        open(player, other.getKey(), other.getValue());
        return true;
    }

    /** The player of this name on the boards (any case), with their name as written; or null. */
    static Map.Entry<UUID, String> find(RecordBook book, String name) {
        for (Map<UUID, RecordBook.Entry> board : book.boards().values()) {
            for (RecordBook.Entry entry : board.values()) {
                if (entry.name.equalsIgnoreCase(name)) {
                    return new AbstractMap.SimpleImmutableEntry<>(entry.player, entry.name);
                }
            }
        }
        return null;
    }

    /** The first page: every training, with the player's best on each board. */
    void open(Player viewer, UUID player, String name) {
        boolean mine = viewer.getUniqueId().equals(player);
        Page page = new Page(player, name, null);
        page.inventory = Bukkit.createInventory(page, SIZE, title(mine ? "Your records" : "Records of " + name));
        RecordBook book = book();
        int[] summary = summary(book, boards, player);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Records: " + ChatColor.WHITE + summary[0] + ChatColor.GRAY + " of " + boards.size() + " boards");
        lore.add(ChatColor.GRAY + "First places: " + ChatColor.GOLD + summary[1]);
        lore.add(ChatColor.GRAY + "In a top 10: " + ChatColor.YELLOW + summary[2]);
        lore.add("");
        lore.add(ChatColor.GRAY + "Click a training: the top 10 of each");
        lore.add(ChatColor.GRAY + "of its boards, and play one of them.");
        page.inventory.setItem(TOP_SLOT, item(Material.NETHER_STAR, ChatColor.AQUA + "" + ChatColor.BOLD
                + (mine ? "Your records" : "Records of " + name), lore));
        for (int i = 0; i < entries.size() && i < PER_ROW * 3; i++) {
            RecordCatalog.Entry entry = entries.get(i);
            int slot = 10 + 9 * (i / PER_ROW) + i % PER_ROW;
            page.trainings.put(slot, entry);
            List<String> lines = trainingLore(book, entry, player);
            boolean daily = false;
            for (RecordCatalog.Board board : entry.boards) {
                daily |= daily(board);
            }
            if (daily) {
                lines.add(0, ChatColor.GOLD + "✦ The challenge of the day");
            }
            ItemStack icon = item(entry.icon, ChatColor.AQUA + entry.title, lines);
            page.inventory.setItem(slot, firsts(book, entry.boards, player) > 0 ? glow(icon) : icon);
        }
        page.inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, ChatColor.RED + "Close", new ArrayList<String>()));
        viewer.openInventory(page.inventory);
    }

    /** A training: its boards, each with its top 10. */
    void openTraining(Player viewer, UUID player, String name, RecordCatalog.Entry entry) {
        boolean mine = viewer.getUniqueId().equals(player);
        Page page = new Page(player, name, entry);
        page.inventory = Bukkit.createInventory(page, SIZE, title(entry.title));
        RecordBook book = book();
        List<String> about = new ArrayList<>();
        about.add(ChatColor.GRAY + (mine ? "Your records" : "Records of " + name));
        about.add(ChatColor.GRAY + "Click a board to play it.");
        page.inventory.setItem(TOP_SLOT, item(entry.icon, ChatColor.AQUA + "" + ChatColor.BOLD + entry.title, about));
        Set<UUID> friends = mine ? plugin.lobby().friends(player) : Collections.<UUID>emptySet();
        int count = entry.boards.size();
        for (int i = 0; i < count && i < PER_ROW * 2; i++) {
            int row = i / PER_ROW;
            int inRow = Math.min(PER_ROW, count - row * PER_ROW);
            int slot = 9 * (2 + row) + (9 - inRow) / 2 + i % PER_ROW;
            RecordCatalog.Board board = entry.boards.get(i);
            page.boards.put(slot, board);
            ItemStack icon = item(entry.icon, ChatColor.YELLOW + board.label, boardLore(book, board, player, name, mine, friends, daily(board)));
            page.inventory.setItem(slot, book.rank(board.id, player) == 1 ? glow(icon) : icon);
        }
        List<String> back = new ArrayList<>();
        back.add(ChatColor.GRAY + "To every training");
        page.inventory.setItem(BACK_SLOT, item(Material.ARROW, ChatColor.GREEN + "Go back", back));
        page.inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, ChatColor.RED + "Close", new ArrayList<String>()));
        viewer.openInventory(page.inventory);
    }

    /** {boards with a score of the player, first places, places in a top 10}. */
    static int[] summary(RecordBook book, List<RecordCatalog.Board> boards, UUID player) {
        int[] summary = new int[3];
        for (RecordCatalog.Board board : boards) {
            int rank = book.rank(board.id, player);
            if (rank > 0) {
                summary[0]++;
                summary[1] += rank == 1 ? 1 : 0;
                summary[2] += rank <= TOP ? 1 : 0;
            }
        }
        return summary;
    }

    private static int firsts(RecordBook book, List<RecordCatalog.Board> boards, UUID player) {
        return summary(book, boards, player)[1];
    }

    /** The lines of a training on the first page: the player's best and place on each board. */
    static List<String> trainingLore(RecordBook book, RecordCatalog.Entry entry, UUID player) {
        List<String> lore = new ArrayList<>();
        for (RecordCatalog.Board board : entry.boards) {
            Long best = book.best(board.id, player);
            lore.add(ChatColor.GRAY + board.label + ": " + (best == null ? ChatColor.DARK_GRAY + "-" : ChatColor.WHITE
                    + board.format(best) + " " + place(book.rank(board.id, player))));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click: the top 10 of each");
        return lore;
    }

    /** "#3" in the colour of the place: gold, silver, bronze, then grey. */
    static String place(int rank) {
        return (rank >= 1 && rank <= PLACES.length ? PLACES[rank - 1] : ChatColor.GRAY) + "#" + rank;
    }

    /**
     * The lines of a board: the player's best and place, their place among their friends (on their
     * own page), the top 10 (and the player under it, further down) and the click that plays it.
     */
    static List<String> boardLore(RecordBook book, RecordCatalog.Board board, UUID player, String name, boolean mine,
            Set<UUID> friends, boolean daily) {
        List<String> lore = new ArrayList<>();
        if (daily) {
            lore.add(ChatColor.GOLD + "✦ The challenge of the day");
        }
        List<RecordBook.Entry> ranked = book.ranked(board.id);
        int rank = 0;
        for (int i = 0; i < ranked.size() && rank == 0; i++) {
            rank = ranked.get(i).player.equals(player) ? i + 1 : 0;
        }
        if (rank == 0) {
            lore.add(ChatColor.GRAY + (mine ? "No record yet" : "No record of " + name + " yet"));
        } else {
            lore.add(ChatColor.GRAY + (mine ? "Your best: " : "Best: ") + ChatColor.WHITE + board.format(ranked.get(rank - 1).value)
                    + " " + place(rank) + ChatColor.GRAY + " of " + ranked.size());
            int[] among = amongFriends(ranked, player, friends);
            if (among[1] > 1) {
                lore.add(ChatColor.GRAY + "Among your friends: " + place(among[0]) + ChatColor.GRAY + " of " + among[1]);
            }
        }
        lore.add("");
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Top 10");
        if (ranked.isEmpty()) {
            lore.add(ChatColor.GRAY + "Nobody yet: be the first!");
        }
        for (int i = 0; i < TOP && i < ranked.size(); i++) {
            lore.add(topLine(board, ranked.get(i), i + 1, player));
        }
        if (rank > TOP) {
            lore.add(ChatColor.DARK_GRAY + "...");
            lore.add(topLine(board, ranked.get(rank - 1), rank, player));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to play it");
        return lore;
    }

    private static String topLine(RecordCatalog.Board board, RecordBook.Entry entry, int rank, UUID player) {
        return place(rank) + " " + (entry.player.equals(player) ? ChatColor.GREEN : ChatColor.WHITE) + entry.name
                + ChatColor.DARK_GRAY + " - " + ChatColor.YELLOW + board.format(entry.value);
    }

    /**
     * The player's place among them and their friends with a score on the board (ranked, the best
     * first): {place, how many}; {0, how many} without a score of theirs.
     */
    static int[] amongFriends(List<RecordBook.Entry> ranked, UUID player, Set<UUID> friends) {
        int place = 0;
        int count = 0;
        for (RecordBook.Entry entry : ranked) {
            boolean me = entry.player.equals(player);
            if (me || friends.contains(entry.player)) {
                count++;
                place = me ? count : place;
            }
        }
        return new int[] {place, count};
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Page)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Page page = (Page) event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < SIZE) {
            // the menu changes after the click is over, not inside it
            Bukkit.getScheduler().runTask(plugin, () -> clicked(player, page, slot));
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Page) {
            event.setCancelled(true);
        }
    }

    private void clicked(Player player, Page page, int slot) {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != page) {
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (page.entry == null) {
            RecordCatalog.Entry entry = page.trainings.get(slot);
            if (entry != null) {
                player.playSound(player.getLocation(), Sound.CLICK, 1f, 1.2f);
                openTraining(player, page.player, page.name, entry);
            }
            return;
        }
        if (slot == BACK_SLOT) {
            player.playSound(player.getLocation(), Sound.CLICK, 1f, 0.9f);
            open(player, page.player, page.name);
            return;
        }
        RecordCatalog.Board board = page.boards.get(slot);
        if (board != null) {
            play(player, board);
        }
    }

    /**
     * A board clicked to be played: in its training, its variant at once; from the lobby or another
     * training, the lobby takes the player there and the training starts on it.
     */
    private void play(Player player, RecordCatalog.Board board) {
        PracticeGames games = plugin.games();
        PracticeGame game = games == null ? null : games.gameOf(player.getUniqueId());
        if (game instanceof Training && game.mode.id().equals(board.modeId)) {
            player.closeInventory();
            ((Training) game).playBoard(player, board);
            return;
        }
        if ((game != null && !(game instanceof Training)) || (game == null && plugin.lobby().isInGame(player))) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Leave your game first (/spawn).");
            return;
        }
        player.closeInventory();
        wanted.put(player.getUniqueId(), new Wanted(board, System.currentTimeMillis()));
        player.performCommand("queue " + board.modeId);
    }

    /**
     * The board the player clicked to play this mode, used once; null when they clicked none (or
     * for another mode, or too long ago).
     */
    String takeBoard(UUID player, String modeId) {
        Wanted asked = wanted.remove(player);
        if (asked == null || !asked.board.modeId.equals(modeId) || System.currentTimeMillis() - asked.at > WANTED_MS) {
            return null;
        }
        return asked.board.id;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        wanted.remove(player);
        picks.remove(player);
        lastClick.remove(player);
    }

    // --- The wall of the lobby ------------------------------------------------------------------

    /** The lines of the wall this player reads now. */
    List<String> wall(Player viewer) {
        RecordBook book = book();
        List<RecordCatalog.Board> shown = onWall(book, boards);
        if (shown.isEmpty()) {
            return Collections.emptyList();
        }
        int index = wallIndex(System.currentTimeMillis(), picks.get(viewer.getUniqueId()), shown.size());
        RecordCatalog.Board board = shown.get(index);
        return wallLines(book, board, viewer.getUniqueId(), index, shown.size(), daily(board));
    }

    /** A click on the wall: a left one shows the next board, a right one opens /records. */
    void wallClick(Player player, boolean left) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return;
        }
        if (!left) {
            if (!entries.isEmpty()) {
                open(player, player.getUniqueId(), player.getName());
            }
            return;
        }
        int count = onWall(book(), boards).size();
        if (count > 1) {
            picks.put(player.getUniqueId(), new Pick(wallIndex(now, picks.get(player.getUniqueId()), count) + 1, now));
            player.playSound(player.getLocation(), Sound.CLICK, 1f, 1.2f);
        }
    }

    /** The boards the wall shows: those with a record, in order; all of them while none has one. */
    static List<RecordCatalog.Board> onWall(RecordBook book, List<RecordCatalog.Board> boards) {
        List<RecordCatalog.Board> shown = new ArrayList<>();
        for (RecordCatalog.Board board : boards) {
            if (book.count(board.id) > 0) {
                shown.add(board);
            }
        }
        return shown.isEmpty() ? boards : shown;
    }

    /**
     * The board of the wall (of count) a player reads: the same for everyone, the next one every
     * {@link #TURN_MS}; after a click, the one chosen for {@link #HOLD_MS}, then the next ones from it.
     */
    static int wallIndex(long now, Pick pick, int count) {
        if (count <= 0) {
            return 0;
        }
        if (pick == null) {
            return (int) Math.floorMod(now / TURN_MS, (long) count);
        }
        long since = Math.max(0, now - pick.at);
        long more = since < HOLD_MS ? 0 : 1 + (since - HOLD_MS) / TURN_MS;
        return (int) Math.floorMod(pick.index + more, (long) count);
    }

    /**
     * The lines of the wall on a board, as the boards of the lobby: the training, the board, the
     * top 10, the player's place, the challenge of the day, the board's place among the others and
     * what the clicks do.
     */
    static List<String> wallLines(RecordBook book, RecordCatalog.Board board, UUID viewer, int index, int count, boolean daily) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + board.title);
        lines.add(ChatColor.GRAY + board.label);
        lines.add("");
        List<RecordBook.Entry> ranked = book.ranked(board.id);
        int rank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            RecordBook.Entry entry = ranked.get(i);
            if (entry.player.equals(viewer)) {
                rank = i + 1;
            }
            if (i < TOP) {
                lines.add(ChatColor.YELLOW + "" + (i + 1) + ". " + ChatColor.WHITE + entry.name + ChatColor.GRAY + " - "
                        + ChatColor.YELLOW + board.format(entry.value));
            }
        }
        if (ranked.isEmpty()) {
            lines.add(ChatColor.GRAY + "Nobody yet: be the first!");
        }
        while (lines.size() < 3 + TOP) {
            lines.add("");
        }
        lines.add("");
        lines.add(rank == 0 ? ChatColor.GRAY + "You: " + ChatColor.DARK_GRAY + "no record yet"
                : ChatColor.GRAY + "You: " + ChatColor.YELLOW + "#" + rank + ChatColor.GRAY + " - "
                        + ChatColor.YELLOW + board.format(ranked.get(rank - 1).value));
        lines.add(daily ? ChatColor.GOLD + "✦ The challenge of the day" : "");
        lines.add(ChatColor.DARK_GRAY + "Practice records " + (index + 1) + "/" + count);
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "» " + ChatColor.GOLD + "Left click: next board");
        lines.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "» " + ChatColor.YELLOW + "Right click: all your records");
        return lines;
    }

    // --- Items ----------------------------------------------------------------------------------

    private static String title(String text) {
        return text.length() <= MAX_TITLE ? text : text.substring(0, MAX_TITLE);
    }

    private static ItemStack item(Material type, String name, List<String> lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    /** The item shines, as an enchanted one, without the enchantment written: a first place. */
    private static ItemStack glow(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
}
