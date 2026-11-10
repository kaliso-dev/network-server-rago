package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The mode selector, a chest menu. With categories in config.yml, the compass first shows one item
 * per category (a zone of the lobby); clicking one opens that category's modes, with a back
 * button in the bottom row. The modes of one group (groupes: in config.yml, the sizes of one game:
 * 1v1, 2v2...) are one item there, which opens them side by side, as the games of Hypixel. Without categories, every mode is shown at once. Modes are seven per
 * row, framed by an empty row above and below. Clicking a playable mode joins its queue; clicking
 * the mode one waits for leaves it. A mode whose game offers several maps first asks which one:
 * a map, or any map (the queue is shorter). Hovering a mode shows the player's win streak of the
 * day in it, the three teams with the longest streaks of the day (every name of a team of two
 * or more), as on Minemen, and the player's position among all of them. Open menus are refreshed every second, for the queue counts.
 */
final class ModeMenu {

    static final String TITLE = "Game modes";
    private static final int PER_ROW = 7;

    /**
     * Marks our inventories: a click in one of them is a click in the selector. The view is the
     * category menu (CATEGORIES), one category's modes (its id), an NPC's modes (NPC + its
     * point), or every mode (null).
     */
    static final class Holder implements InventoryHolder {
        static final String CATEGORIES = "";
        static final String NPC = "pnj:";
        static final String DUEL = "duel:";
        static final String MAPS = "maps:";
        static final String GROUP = "groupe:";
        /** After a group's id: the NPC its menu was opened from, to go back to. */
        static final String FROM_NPC = "|";
        private Inventory inventory;
        private final String view;

        Holder(String view) {
            this.view = view;
        }

        String view() {
            return view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final LobbySettings settings;
    private final QueueController queues;
    private final StatsBook book;
    private DuelRequests duels;

    void setDuels(DuelRequests duels) {
        this.duels = duels;
    }

    /** /duel <player>: the 1v1 modes, a click challenges that player. */
    void openDuel(Player player, Player target) {
        show(player, Holder.DUEL + target.getUniqueId(), 2 + rowsFor(duels.duelModes().size()),
                "Duel " + (target.getName().length() > 24 ? target.getName().substring(0, 24) : target.getName()));
    }

    ModeMenu(LobbySettings settings, QueueController queues, StatsBook book) {
        this.settings = settings;
        this.queues = queues;
        this.book = book;
    }

    /** The compass: the categories if there are any, else every mode. */
    void open(Player player) {
        if (settings.categories().isEmpty()) {
            show(player, null, 2 + rowsFor(settings.modes().size()), TITLE);
        } else {
            show(player, Holder.CATEGORIES, 3, TITLE);
        }
    }

    /** One category's modes, from the category menu or later from a lobby NPC. False if unknown. */
    boolean openCategory(Player player, String categoryId) {
        ModeCategory category = settings.category(categoryId);
        if (category == null) {
            return false;
        }
        String title = ChatColor.stripColor(category.name());
        show(player, categoryId, 3 + rowsFor(settings.entriesIn(categoryId).size()),
                title.length() > 32 ? title.substring(0, 32) : title);
        return true;
    }

    /** The modes of a group, side by side, with the back button to their category. */
    void openGroup(Player player, ModeCategory group) {
        openGroup(player, group, null);
    }

    /** The same, from a lobby NPC's menu (its point; null: from the compass), which the back button reopens. */
    void openGroup(Player player, ModeCategory group, String npcPoint) {
        String title = ChatColor.stripColor(group.name());
        int modes = settings.modesOfGroup(group.id()).size();
        show(player, Holder.GROUP + group.id() + (npcPoint == null ? "" : Holder.FROM_NPC + npcPoint),
                modes <= 4 ? 4 : 3 + rowsFor(modes), title.length() > 32 ? title.substring(0, 32) : title);
    }

    /** The group of a group view, or null for another view. */
    private ModeCategory groupOf(String view) {
        return view != null && view.startsWith(Holder.GROUP) ? settings.group(groupId(view)) : null;
    }

    /** The id of the group of a group view. */
    private static String groupId(String view) {
        String id = view.substring(Holder.GROUP.length());
        int from = id.indexOf(Holder.FROM_NPC);
        return from < 0 ? id : id.substring(0, from);
    }

    /** The NPC a group view was opened from, or null. */
    private LobbyNpc npcOf(String view) {
        int from = view == null || !view.startsWith(Holder.GROUP) ? -1 : view.indexOf(Holder.FROM_NPC);
        return from < 0 ? null : settings.npc(view.substring(from + Holder.FROM_NPC.length()));
    }

    /** The slot of the n-th of {@code count} modes of a view: a group's few modes are centred. */
    private static int slotIn(String view, int index, int count) {
        return view != null && view.startsWith(Holder.GROUP) && count <= 4 ? categorySlot(index, count) : slotOf(index);
    }

    /** The modes of a lobby NPC that has several. */
    void openNpc(Player player, LobbyNpc npc) {
        String title = ChatColor.stripColor(npc.name());
        show(player, Holder.NPC + npc.point(), 2 + rowsFor(settings.entriesOf(npc.modeIds()).size()),
                title.length() > 32 ? title.substring(0, 32) : title);
    }

    /** The maps of a mode, to choose from before joining its queue. */
    void openMaps(Player player, GameMode mode) {
        int entries = 1 + queues.mapsOf(mode).size();
        String title = ChatColor.stripColor(mode.name()) + ": map";
        show(player, Holder.MAPS + mode.id(), 3 + rowsFor(entries), title.length() > 32 ? title.substring(0, 32) : title);
    }

    /** The mode of a map view, or null for another view. */
    private GameMode mapsMode(String view) {
        return view != null && view.startsWith(Holder.MAPS) ? settings.catalog().get(view.substring(Holder.MAPS.length())) : null;
    }

    /** The modes a view shows. */
    private List<ModeDisplay> modesFor(String view) {
        if (view != null && view.startsWith(Holder.DUEL)) {
            return duels.duelModes();
        }
        if (view != null && view.startsWith(Holder.NPC)) {
            List<ModeDisplay> result = new ArrayList<>();
            LobbyNpc npc = settings.npc(view.substring(Holder.NPC.length()));
            if (npc != null) {
                for (String id : npc.modeIds()) {
                    result.add(settings.display(id));
                }
            }
            return result;
        }
        if (view != null && view.startsWith(Holder.GROUP)) {
            return settings.modesOfGroup(groupId(view));
        }
        return settings.modesIn(view);
    }

    /** Whether a view has the back button: only a category's modes. */
    private static boolean hasBack(String view) {
        return view != null && !view.startsWith(Holder.NPC) && !view.startsWith(Holder.DUEL);
    }

    private static int rowsFor(int modes) {
        return Math.max(1, (modes + PER_ROW - 1) / PER_ROW);
    }

    private void show(Player player, String view, int rows, String title) {
        Holder holder = new Holder(view);
        Inventory inventory = Bukkit.createInventory(holder, Math.min(6, rows) * 9, title);
        holder.inventory = inventory;
        fill(inventory, player);
        player.openInventory(inventory);
    }

    /** The slot of the n-th mode: rows 1 and after, columns 1 to 7. */
    static int slotOf(int index) {
        return (1 + index / PER_ROW) * 9 + 1 + index % PER_ROW;
    }

    /**
     * The slot of the n-th of {@code count} categories, centred in the middle row: one slot apart
     * up to four, as on Hypixel (the practice on the left, the duels on the right, as in the lobby),
     * side by side beyond.
     */
    static int categorySlot(int index, int count) {
        if (count <= 4) {
            return 9 + (9 - (2 * count - 1)) / 2 + 2 * index;
        }
        return 9 + (9 - count) / 2 + index;
    }

    /** The back button: middle of the bottom row. */
    static int backSlot(int size) {
        return size - 5;
    }

    /** Clicked in our menu: open a category, go back, or join or leave a mode's queue. */
    void click(Player player, Holder holder, int slot) {
        String view = holder.view();
        if (Holder.CATEGORIES.equals(view)) {
            List<ModeCategory> categories = settings.categories();
            for (int i = 0; i < categories.size(); i++) {
                if (categorySlot(i, categories.size()) == slot) {
                    openCategory(player, categories.get(i).id());
                    return;
                }
            }
            return;
        }
        if (hasBack(view) && slot == backSlot(holder.getInventory().getSize())) {
            LobbyNpc from = npcOf(view);
            if (from != null) {
                openNpc(player, from);
                return;
            }
            List<ModeDisplay> grouped = groupOf(view) == null ? null : modesFor(view);
            if (grouped != null && !grouped.isEmpty() && grouped.get(0).category() != null) {
                openCategory(player, grouped.get(0).category());
            } else {
                open(player);
            }
            return;
        }
        GameMode mapsOf = mapsMode(view);
        if (mapsOf != null) {
            clickMap(player, mapsOf, slot);
            return;
        }
        if (settings.category(view) != null) {
            List<LobbySettings.Entry> entries = settings.entriesIn(view);
            for (int i = 0; i < entries.size(); i++) {
                if (slotOf(i) == slot) {
                    if (entries.get(i).group != null) {
                        openGroup(player, entries.get(i).group);
                    } else {
                        toggle(player, entries.get(i).mode.mode());
                    }
                    return;
                }
            }
            return;
        }
        LobbyNpc npc = view != null && view.startsWith(Holder.NPC) ? settings.npc(view.substring(Holder.NPC.length())) : null;
        if (npc != null) {
            List<LobbySettings.Entry> entries = settings.entriesOf(npc.modeIds());
            for (int i = 0; i < entries.size(); i++) {
                if (slotOf(i) == slot) {
                    if (entries.get(i).group != null) {
                        openGroup(player, entries.get(i).group, npc.point());
                    } else {
                        toggle(player, entries.get(i).mode.mode());
                    }
                    return;
                }
            }
            return;
        }
        List<ModeDisplay> modes = modesFor(view);
        GameMode mode = null;
        for (int i = 0; i < modes.size(); i++) {
            if (slotIn(view, i, modes.size()) == slot) {
                mode = modes.get(i).mode();
            }
        }
        if (mode == null) {
            return;
        }
        if (view != null && view.startsWith(Holder.DUEL)) {
            player.closeInventory();
            Player target = Bukkit.getPlayer(java.util.UUID.fromString(view.substring(Holder.DUEL.length())));
            duels.challenge(player, target, mode);
            return;
        }
        toggle(player, mode);
    }

    /**
     * Joins the mode's queue, or leaves it if the player already waits for it. A mode with several
     * maps first opens their menu.
     */
    void toggle(Player player, GameMode mode) {
        GameMode current = queues.queueOf(player);
        if (mode.equals(current)) {
            queues.leave(player, true);
        } else if (queues.isPlayable(mode.id()) && queues.mapsOf(mode).size() >= 2) {
            openMaps(player, mode);
            return;
        } else {
            queues.join(player, mode);
        }
        if (queues.queueOf(player) != null) {
            player.closeInventory();
        }
    }

    /** Clicked in a mode's map menu: any map (the first item) or one of them; the map one waits for leaves the queue. */
    private void clickMap(Player player, GameMode mode, int slot) {
        List<String> maps = queues.mapsOf(mode);
        boolean found = slot == slotOf(0);
        String map = null;
        for (int i = 0; i < maps.size(); i++) {
            if (slotOf(i + 1) == slot) {
                found = true;
                map = maps.get(i);
            }
        }
        if (!found) {
            return;
        }
        boolean waitingHere = mode.equals(queues.queueOf(player));
        String waitingFor = waitingHere ? queues.mapOf(player) : null;
        if (waitingHere && (map == null ? waitingFor == null : map.equalsIgnoreCase(waitingFor))) {
            queues.leave(player, true);
        } else {
            queues.join(player, mode, map);
        }
        if (queues.queueOf(player) != null) {
            player.closeInventory();
        }
    }

    /** Refreshes the counts of every open selector. */
    void refreshOpen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof Holder) {
                fill(top, player);
            }
        }
    }

    private void fill(Inventory inventory, HumanEntity viewer) {
        String view = ((Holder) inventory.getHolder()).view();
        GameMode current = viewer instanceof Player ? queues.queueOf((Player) viewer) : null;
        GameMode mapsOf = mapsMode(view);
        if (mapsOf != null) {
            fillMaps(inventory, mapsOf, mapsOf.equals(current), viewer instanceof Player ? queues.mapOf((Player) viewer) : null);
            return;
        }
        if (Holder.CATEGORIES.equals(view)) {
            List<ModeCategory> categories = settings.categories();
            for (int i = 0; i < categories.size(); i++) {
                inventory.setItem(categorySlot(i, categories.size()), icon(categories.get(i), current));
            }
            return;
        }
        int last = hasBack(view) ? backSlot(inventory.getSize()) - 4 : inventory.getSize();
        LobbyNpc npc = view != null && view.startsWith(Holder.NPC) ? settings.npc(view.substring(Holder.NPC.length())) : null;
        if (settings.category(view) != null || npc != null) {
            List<LobbySettings.Entry> entries = npc != null ? settings.entriesOf(npc.modeIds()) : settings.entriesIn(view);
            for (int i = 0; i < entries.size() && slotOf(i) < last; i++) {
                LobbySettings.Entry entry = entries.get(i);
                inventory.setItem(slotOf(i), entry.group != null ? icon(entry.group, current, viewer)
                        : icon(entry.mode, entry.mode.mode().equals(current), viewer));
            }
        } else {
            List<ModeDisplay> modes = modesFor(view);
            for (int i = 0; i < modes.size() && slotIn(view, i, modes.size()) < last; i++) {
                inventory.setItem(slotIn(view, i, modes.size()), icon(modes.get(i), modes.get(i).mode().equals(current), viewer));
            }
        }
        if (hasBack(view)) {
            ItemStack back = new ItemStack(Material.ARROW);
            ItemMeta meta = back.getItemMeta();
            ModeCategory group = groupOf(view);
            meta.setDisplayName(ChatColor.GRAY + (group != null ? "Back to the modes" : "Back to the categories"));
            back.setItemMeta(meta);
            inventory.setItem(backSlot(inventory.getSize()), back);
        }
    }

    /** The map menu: any map first, then each map, and the back button. */
    private void fillMaps(Inventory inventory, GameMode mode, boolean waitingHere, String waitingFor) {
        List<String> maps = queues.mapsOf(mode);
        int last = backSlot(inventory.getSize()) - 4;
        ItemStack any = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = any.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Random map");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Any of the " + maps.size() + " maps:");
        lore.add(ChatColor.GRAY + "the shortest wait.");
        lore.add("");
        lore.add(ChatColor.GRAY + "In queue: " + ChatColor.WHITE + queues.waiting(mode.id()));
        lore.add(waitingHere && waitingFor == null ? ChatColor.YELLOW + "You are in this queue: click to leave it"
                : ChatColor.GREEN + "Click to join the queue");
        meta.setLore(lore);
        any.setItemMeta(meta);
        inventory.setItem(slotOf(0), any);
        for (int i = 0; i < maps.size() && slotOf(i + 1) < last; i++) {
            String map = maps.get(i);
            boolean chosen = waitingHere && map.equalsIgnoreCase(waitingFor);
            ItemStack item = new ItemStack(Material.EMPTY_MAP);
            meta = item.getItemMeta();
            meta.setDisplayName((chosen ? ChatColor.YELLOW : ChatColor.AQUA) + map);
            lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + ChatColor.stripColor(mode.name()));
            lore.add("");
            lore.add(ChatColor.GRAY + "Waiting for this map: " + ChatColor.WHITE + queues.waitingFor(mode.id(), map));
            lore.add(chosen ? ChatColor.YELLOW + "You are in this queue: click to leave it"
                    : ChatColor.GREEN + "Click to play on " + map);
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slotOf(i + 1), item);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        meta = back.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Back to the categories");
        back.setItemMeta(meta);
        inventory.setItem(backSlot(inventory.getSize()), back);
    }

    private ItemStack icon(ModeCategory category, GameMode current) {
        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(category.name());
        List<String> lore = new ArrayList<>(category.description());
        List<ModeDisplay> modes = settings.modesIn(category.id());
        int waiting = 0;
        boolean here = false;
        for (ModeDisplay display : modes) {
            if (queues.isPlayable(display.mode().id())) {
                waiting += queues.waiting(display.mode().id());
            }
            here |= display.mode().equals(current);
        }
        int games = settings.entriesIn(category.id()).size();
        lore.add("");
        lore.add(ChatColor.GRAY + "" + games + (games == 1 ? " game, " : " games, ") + ChatColor.WHITE + waiting
                + ChatColor.GRAY + " in queue");
        if (here) {
            lore.add(ChatColor.YELLOW + "You are in a queue of this category");
        }
        lore.add(ChatColor.GREEN + "Click to see the modes");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** A group in its category: its name, what it is, and each of its modes with its queue. */
    private ItemStack icon(ModeCategory group, GameMode current, HumanEntity viewer) {
        ItemStack item = new ItemStack(group.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(group.name());
        List<String> lore = new ArrayList<>(group.description());
        lore.add("");
        String waitingIn = null;
        for (ModeDisplay display : settings.modesOfGroup(group.id())) {
            GameMode mode = display.mode();
            boolean here = mode.equals(current);
            if (here) {
                waitingIn = ChatColor.stripColor(mode.name());
            }
            lore.add((here ? ChatColor.YELLOW + "\u25b8 " : ChatColor.DARK_GRAY + "\u25b8 ") + ChatColor.WHITE
                    + ChatColor.stripColor(mode.name()) + ChatColor.GRAY + ": "
                    + (queues.isPlayable(mode.id()) ? ChatColor.WHITE + "" + queues.waiting(mode.id()) + ChatColor.GRAY + " in queue"
                            : ChatColor.GRAY + "coming soon"));
        }
        lore.add("");
        if (waitingIn != null) {
            lore.add(ChatColor.YELLOW + "You are in the queue of " + waitingIn);
        }
        lore.add(ChatColor.GREEN + "Click to choose");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack icon(ModeDisplay display, boolean waitingHere, HumanEntity viewer) {
        GameMode mode = display.mode();
        ItemStack item = new ItemStack(display.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(mode.name());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + mode.format() + (mode.ranked() ? " · ranked" : ""));
        lore.add("");
        lore.addAll(display.description());
        lore.add("");
        lore.addAll(streakLines(mode, viewer));
        lore.add("");
        if (!queues.isPlayable(mode.id())) {
            lore.add(ChatColor.GRAY + "Coming soon");
        } else {
            lore.add(ChatColor.GRAY + "In queue: " + ChatColor.WHITE + queues.waiting(mode.id()));
            int maps = queues.mapsOf(mode).size();
            String waitingFor = waitingHere && viewer instanceof Player ? queues.mapOf((Player) viewer) : null;
            if (waitingFor != null) {
                lore.add(ChatColor.GRAY + "Your map: " + ChatColor.WHITE + waitingFor);
            }
            lore.add(waitingHere ? ChatColor.YELLOW + "You are in this queue: click to leave it"
                    : maps >= 2 ? ChatColor.GREEN + "Click to choose the map (" + maps + ")"
                    : ChatColor.GREEN + "Click to join the queue");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Lines of the top of the day under "Daily Streak". */
    static final int TOP_STREAKS = 3;

    /**
     * As on Minemen: "Daily Streak: 2", the viewer's wins in a row in this mode (0 if they have not
     * won today), then the three longest streaks of the day, "1. Alice, Bob - 24", and the viewer's
     * place among them: "Your position: #7 (3)", their best team of the day.
     */
    List<String> streakLines(GameMode mode, HumanEntity viewer) {
        long now = System.currentTimeMillis();
        PlayerStats stats = viewer == null ? null : book.get(viewer.getUniqueId());
        ModeStats mine = stats == null ? null : stats.mode(mode.id());
        int own = mine != null && StatsPeriod.dayKey(now).equals(mine.day) ? mine.streak : 0;
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.YELLOW + "Daily Streak: " + own);
        List<StatsBook.TeamRow> top = book.topStreaks(mode.id(), StatsPeriod.DAY, now);
        for (int i = 0; i < top.size() && i < TOP_STREAKS; i++) {
            lines.add(ChatColor.YELLOW + " " + (i + 1) + ". " + ChatColor.WHITE + top.get(i).names() + ChatColor.YELLOW
                    + " - " + top.get(i).value());
        }
        if (viewer != null) {
            lines.add(positionLine(top, viewer.getUniqueId()));
        }
        return lines;
    }

    /** "Your position: #7 (3)": the first row of the day with this player in its team; "-" if none. */
    static String positionLine(List<StatsBook.TeamRow> rows, java.util.UUID player) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).players().contains(player)) {
                return ChatColor.YELLOW + "Your position: " + ChatColor.WHITE + "#" + (i + 1) + ChatColor.GRAY + " ("
                        + rows.get(i).value() + ")";
            }
        }
        return ChatColor.YELLOW + "Your position: " + ChatColor.GRAY + "-";
    }
}
