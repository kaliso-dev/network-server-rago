package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.Matchmaker;
import gg.rago.lobby.api.ModeRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * The mode selector, a chest menu. With categories in config.yml, the compass first shows one item
 * per category, in its middle row; clicking one opens that category's modes, with a back button in
 * the bottom row (the owner's ask of 22/11: the compass as before the Minemen layout). A lobby NPC
 * and /play open a category's modes at once, with nothing above them. The modes of one group (groupes: in config.yml, the sizes of one game:
 * 1v1, 2v2...) are one item there, which opens them side by side, as the games of Hypixel. Without categories, every mode is shown at once. Modes are seven per
 * row, framed by an empty row above and below. Clicking a playable mode joins its queue; clicking
 * the mode one waits for leaves it. A mode whose game offers several maps first asks which one:
 * a map, or any map (the queue is shorter). Hovering a mode shows the player's win streak of the
 * day in it, the three teams with the longest streaks of the day (every name of a team of two
 * or more), as on Minemen, and the player's position among all of them. Open menus are refreshed every second, for the queue counts.
 * The top row of the compass is "Popular now": the modes with players in a game or in the queue
 * right now (the most first, three at most) and the challenge of the day of the trainings, each a
 * click away. The mode marked commencer-ici: (the block clutch) shines with
 * "Start here", in its category, its group and on the category's item. The category of the parties
 * (party: true, plan 3.7) has no modes of its own: it shows the games the viewer's party plays
 * against itself, at its size.
 */
final class ModeMenu {

    static final String TITLE = "Game modes";
    private static final int PER_ROW = 7;

    /**
     * Marks our inventories: a click in one of them is a click in the selector. The view is the
     * category menu (CATEGORIES), one category's modes (its id), an NPC's modes (NPC + its point),
     * a group's modes (GROUP + its id), a mode's maps (MAPS + its id), the modes of /duel (DUEL),
     * or every mode (null).
     */
    static final class Holder implements InventoryHolder {
        static final String CATEGORIES = "";
        static final String NPC = "pnj:";
        static final String DUEL = "duel:";
        static final String MAPS = "maps:";
        static final String GROUP = "groupe:";
        /** After a group's id: the NPC its menu was opened from, to go back to. */
        static final String FROM_NPC = "|";
        /**
         * After a group's id: the category its menu was opened from when it is not the group's own
         * (aussi:, as Red Rover in 3 Players), to go back to.
         */
        static final String FROM_CATEGORY = ">";
        /** In the top row of "Popular now": the challenge of the day, not a mode. */
        static final String CHALLENGE = "*defi";
        private Inventory inventory;
        private final String view;
        /** In the compass: what each slot shows in "Popular now" (a mode id, CHALLENGE), else null. */
        private final String[] popular = new String[27];
        /** In the compass: the category or group each slot opens (its id), else null. */
        private final String[] links = new String[27];

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
    /** The mode of the challenge of the day (a training) and what it asks, or null: none. */
    private String challengeMode;
    private String challengeLabel;
    /** The records of the modes of each plugin that has some (the trainings), in the order they came. */
    private final Map<Plugin, BiFunction<String, UUID, ModeRecord>> records = new LinkedHashMap<>();

    /** Modes in "Popular now", at most. */
    static final int MAX_POPULAR = 3;

    void setDuels(DuelRequests duels) {
        this.duels = duels;
    }

    /**
     * The challenge of the day, from the plugin of the trainings: its mode and what it asks ("Block
     * clutch: 20 blocks"), at the end of "Popular now" while its mode is played; null: none.
     */
    void setChallenge(String modeId, String label) {
        this.challengeMode = modeId;
        this.challengeLabel = label;
    }

    /** The records of the modes of this plugin, under its modes in the place of their Daily Streak. */
    void setRecords(Plugin plugin, BiFunction<String, UUID, ModeRecord> records) {
        this.records.put(plugin, records);
    }

    /** The plugin goes: its modes show their Daily Streak again. */
    void dropRecords(Plugin plugin) {
        records.remove(plugin);
    }

    /** The record of this mode, from the plugin that plays it; null for a mode without one. */
    ModeRecord recordOf(String modeId, UUID viewer) {
        for (BiFunction<String, UUID, ModeRecord> of : records.values()) {
            ModeRecord record = of.apply(modeId, viewer);
            if (record != null) {
                return record;
            }
        }
        return null;
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
            show(player, (String) null, 2 + rowsFor(settings.modes().size()), TITLE);
        } else {
            show(player, Holder.CATEGORIES, 3, TITLE);
        }
    }

    /**
     * One category's modes, with nothing above them: from the compass, a lobby NPC, /play or the
     * back button of a group. False if unknown.
     */
    boolean openCategory(Player player, String categoryId) {
        ModeCategory category = settings.category(categoryId);
        if (category == null) {
            return false;
        }
        String title = ChatColor.stripColor(category.name());
        show(player, categoryId, 3 + rowsFor(compassEntries(categoryId, player).size()),
                title.length() > 32 ? title.substring(0, 32) : title);
        return true;
    }

    /** The modes of a group, side by side, with the back button to their category. */
    void openGroup(Player player, ModeCategory group) {
        openGroup(player, group, null);
    }

    /** The same, from a lobby NPC's menu (its point; null: from the compass), which the back button reopens. */
    void openGroup(Player player, ModeCategory group, String npcPoint) {
        openGroup(player, group, npcPoint, null);
    }

    /** The same, from a category that shows it but is not its own (aussi:), which the back button reopens. */
    private void openGroup(Player player, ModeCategory group, String npcPoint, String fromCategory) {
        String title = ChatColor.stripColor(group.name());
        List<ModeDisplay> modes = settings.modesOfGroup(group.id());
        String from = npcPoint != null ? Holder.FROM_NPC + npcPoint
                : fromCategory != null && !modes.isEmpty() && !fromCategory.equals(modes.get(0).category())
                        ? Holder.FROM_CATEGORY + fromCategory : "";
        show(player, Holder.GROUP + group.id() + from,
                modes.size() <= 4 ? 4 : 3 + rowsFor(modes.size()), title.length() > 32 ? title.substring(0, 32) : title);
    }

    /** The group of a group view, or null for another view. */
    private ModeCategory groupOf(String view) {
        return view != null && view.startsWith(Holder.GROUP) ? settings.group(groupId(view)) : null;
    }

    /** The id of the group of a group view. */
    private static String groupId(String view) {
        String id = view.substring(Holder.GROUP.length());
        int from = id.indexOf(Holder.FROM_NPC);
        if (from < 0) {
            from = id.indexOf(Holder.FROM_CATEGORY);
        }
        return from < 0 ? id : id.substring(0, from);
    }

    /** The category a group view was opened from when it is not the group's own (aussi:), or null. */
    private static String fromCategory(String view) {
        int from = view == null || !view.startsWith(Holder.GROUP) ? -1 : view.indexOf(Holder.FROM_CATEGORY);
        return from < 0 ? null : view.substring(from + Holder.FROM_CATEGORY.length());
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

    /** Whether a view has the back button: a group's modes and a mode's maps (the compass has its categories). */
    private static boolean hasBack(String view) {
        return view != null && !view.startsWith(Holder.NPC) && !view.startsWith(Holder.DUEL);
    }

    private static int rowsFor(int modes) {
        return Math.max(1, (modes + PER_ROW - 1) / PER_ROW);
    }

    private void show(Player player, String view, int rows, String title) {
        show(player, new Holder(view), rows, title);
    }

    private void show(Player player, Holder holder, int rows, String title) {
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

    /** The slot of the n-th of {@code count} items of "Popular now", centred in its row of the compass (0 to 8). */
    static int popularSlot(int index, int count) {
        return categorySlot(index, count) - 9;
    }

    /**
     * The column (0 to 8) of the n-th of {@code count} in a row of the compass, centred: one slot
     * apart up to five (the owner's ask of 22/11, "plus clair"), side by side beyond.
     */
    static int compassColumn(int index, int count) {
        if (2 * count - 1 <= 9) {
            return (9 - (2 * count - 1)) / 2 + 2 * index;
        }
        return (9 - count) / 2 + index;
    }

    /** The back button: middle of the bottom row. */
    static int backSlot(int size) {
        return size - 5;
    }

    /**
     * The items of a category's menu: its modes and groups, or for the category of the parties
     * those of the viewer's party at its size.
     */
    private List<LobbySettings.Entry> compassEntries(String categoryId, HumanEntity viewer) {
        ModeCategory category = categoryId == null ? null : settings.category(categoryId);
        if (category == null || !category.party()) {
            return settings.entriesIn(categoryId);
        }
        return partyEntries(partySize(viewer));
    }

    /** The players of the viewer's party, 0 out of a party. */
    private int partySize(HumanEntity viewer) {
        return viewer instanceof Player ? queues.partySize((Player) viewer) : 0;
    }

    /**
     * What a party of this size plays against itself (plan 3.7), in the order of the compass: the
     * modes whose game it fills (two players the 1v1s, four the 2v2s...), those whose one team it
     * makes (the 1v2s and Red Rover for three), and the private games whose size follows the party.
     * A group with one such mode is that mode, with several the group. Empty out of a party.
     */
    List<LobbySettings.Entry> partyEntries(int size) {
        List<LobbySettings.Entry> result = new ArrayList<>();
        if (size < 2) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (ModeCategory category : settings.categories()) {
            if (category.party()) {
                continue;
            }
            for (LobbySettings.Entry entry : settings.entriesIn(category.id())) {
                if (entry.group == null) {
                    if (forParty(entry.mode, size) && seen.add(entry.mode.mode().id())) {
                        result.add(entry);
                    }
                    continue;
                }
                List<ModeDisplay> fit = new ArrayList<>();
                for (ModeDisplay display : settings.modesOfGroup(entry.group.id())) {
                    if (forParty(display, size)) {
                        fit.add(display);
                    }
                }
                if (fit.isEmpty() || !seen.add(Holder.GROUP + entry.group.id())) {
                    continue;
                }
                if (fit.size() == 1) {
                    if (seen.add(fit.get(0).mode().id())) {
                        result.add(new LobbySettings.Entry(fit.get(0), null));
                    }
                } else {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * Whether a party of this size plays this mode, played now, against itself. A private game whose
     * size follows the party is shown for a mode of its list that does not show itself: Ranked
     * Bedwars (the private game is never ranked); the private Top Bridge 1v1 is Top Bridge 1v1, shown.
     */
    private boolean forParty(ModeDisplay display, int size) {
        GameMode mode = display.mode();
        if (display.hidden() || !queues.isPlayable(mode.id())) {
            return false;
        }
        List<GameMode> routes = settings.privateRoutes().get(mode.id());
        if (routes != null) {
            for (GameMode target : routes) {
                if (target.playersPerMatch() == size && target.ranked() && queues.isPlayable(target.id())) {
                    return true;
                }
            }
            return false;
        }
        return fitsParty(mode, size);
    }

    /**
     * Whether a party of this size plays this mode against itself: it fills a game of two teams or
     * more (unranked: a private game counts in no ELO), or makes its one team (a 1v2 of three).
     */
    static boolean fitsParty(GameMode mode, int size) {
        if (size < 2) {
            return false;
        }
        return mode.teams() >= 2 ? !mode.ranked() && mode.playersPerMatch() == size : mode.playersPerTeam() == size;
    }

    /** In the category of the parties, with no game to show: how to make a party, or that none fits its size. */
    private static ItemStack partyHint(int size) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (size < 2) {
            meta.setDisplayName(ChatColor.BLUE + "" + ChatColor.BOLD + "You are not in a party");
            lore.add(ChatColor.GRAY + "Invite your friends with");
            lore.add(ChatColor.WHITE + "/party invite <name>" + ChatColor.GRAY + ":");
            lore.add(ChatColor.GRAY + "your party then plays here");
            lore.add(ChatColor.GRAY + "against itself, at its size.");
        } else {
            meta.setDisplayName(ChatColor.BLUE + "" + ChatColor.BOLD + "No game for a party of " + size);
            lore.add(ChatColor.GRAY + "Two players play the 1v1s,");
            lore.add(ChatColor.GRAY + "three the 1v2s, four the 2v2s.");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Clicked in our menu: open a category, go back, or join or leave a mode's queue. */
    void click(Player player, Holder holder, int slot) {
        String view = holder.view();
        if (Holder.CATEGORIES.equals(view)) {
            if (slot >= 0 && slot < holder.popular.length && holder.popular[slot] != null) {
                clickPopular(player, holder.popular[slot]);
                return;
            }
            String link = slot >= 0 && slot < holder.links.length ? holder.links[slot] : null;
            if (link != null && settings.category(link) != null) {
                openCategory(player, link);
            } else if (link != null && settings.group(link) != null) {
                // a group of the top row (Red Rover): its back button comes back to the compass
                openGroup(player, settings.group(link), null, Holder.CATEGORIES);
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
            GameMode maps = mapsMode(view);
            ModeDisplay mapsDisplay = maps == null ? null : settings.display(maps.id());
            if (Holder.CATEGORIES.equals(fromCategory(view))) {
                open(player);
            } else if (fromCategory(view) != null && settings.category(fromCategory(view)) != null) {
                openCategory(player, fromCategory(view));
            } else if (grouped != null && !grouped.isEmpty() && grouped.get(0).category() != null) {
                openCategory(player, grouped.get(0).category());
            } else if (mapsDisplay == null || mapsDisplay.category() == null || !openCategory(player, mapsDisplay.category())) {
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
            List<LobbySettings.Entry> entries = compassEntries(view, player);
            for (int i = 0; i < entries.size(); i++) {
                if (slotOf(i) == slot) {
                    if (entries.get(i).group != null) {
                        openGroup(player, entries.get(i).group, null, view);
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

    /** Clicked in "Popular now": the challenge of the day (its command, in the trainings plugin), or a mode. */
    private void clickPopular(Player player, String id) {
        if (Holder.CHALLENGE.equals(id)) {
            player.closeInventory();
            player.performCommand("challenge play");
            return;
        }
        GameMode mode = settings.catalog().get(id);
        if (mode != null) {
            toggle(player, mode);
        }
    }

    /**
     * Joins the mode's queue, besides the others the player waits in, or leaves it (it alone) if
     * they already wait for it. A mode with several maps first opens their menu.
     */
    void toggle(Player player, GameMode mode) {
        if (queues.isWaiting(player, mode)) {
            queues.leave(player, mode, true);
        } else if (queues.isPlayable(mode.id()) && queues.mapsOf(mode).size() >= 2) {
            openMaps(player, mode);
            return;
        } else {
            queues.join(player, mode);
        }
        if (queues.isWaiting(player, mode)) {
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
        boolean waitingHere = queues.isWaiting(player, mode);
        String waitingFor = waitingHere ? queues.mapOf(player, mode) : null;
        if (waitingHere && (map == null ? waitingFor == null : map.equalsIgnoreCase(waitingFor))) {
            queues.leave(player, mode, true);
        } else {
            queues.join(player, mode, map);
        }
        if (queues.isWaiting(player, mode)) {
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
        List<GameMode> current = viewer instanceof Player ? queues.queuesOf((Player) viewer) : new ArrayList<GameMode>();
        GameMode mapsOf = mapsMode(view);
        if (mapsOf != null) {
            fillMaps(inventory, mapsOf, current.contains(mapsOf), viewer instanceof Player ? queues.mapOf((Player) viewer, mapsOf) : null);
            return;
        }
        if (Holder.CATEGORIES.equals(view)) {
            // the top row (boussole.haut), the other categories under it, "Popular now" at the bottom;
            // without a top row, the compass as before: "Popular now" on top, every category under it
            Holder holder = (Holder) inventory.getHolder();
            java.util.Arrays.fill(holder.links, null);
            List<String> top = settings.compassTop();
            List<ModeCategory> middle = new ArrayList<>();
            for (ModeCategory category : settings.categories()) {
                if (!top.contains(category.id())) {
                    middle.add(category);
                }
            }
            for (int i = 0; i < top.size(); i++) {
                ModeCategory category = settings.category(top.get(i));
                int slot = compassColumn(i, top.size());
                inventory.setItem(slot, category != null ? categoryIcon(category, current, viewer)
                        : icon(settings.group(top.get(i)), current, viewer));
                holder.links[slot] = top.get(i);
            }
            for (int i = 0; i < middle.size(); i++) {
                int slot = top.isEmpty() ? categorySlot(i, middle.size()) : 9 + compassColumn(i, middle.size());
                inventory.setItem(slot, categoryIcon(middle.get(i), current, viewer));
                holder.links[slot] = middle.get(i).id();
            }
            fillPopular(inventory, settings.modes(), current, viewer, top.isEmpty() ? 0 : 2);
            return;
        }
        int last = hasBack(view) ? backSlot(inventory.getSize()) - 4 : inventory.getSize();
        LobbyNpc npc = view != null && view.startsWith(Holder.NPC) ? settings.npc(view.substring(Holder.NPC.length())) : null;
        ModeCategory category = settings.category(view);
        if (category != null || npc != null) {
            List<LobbySettings.Entry> entries = npc != null ? settings.entriesOf(npc.modeIds()) : compassEntries(view, viewer);
            if (category != null) {
                // the games of a party change with its size: nothing stays of the ones before
                for (int slot = 0; slot < last; slot++) {
                    inventory.setItem(slot, null);
                }
                if (entries.isEmpty() && category.party()) {
                    inventory.setItem(slotOf(PER_ROW / 2), partyHint(partySize(viewer)));
                }
            }
            for (int i = 0; i < entries.size() && slotOf(i) < last; i++) {
                LobbySettings.Entry entry = entries.get(i);
                inventory.setItem(slotOf(i), entry.group != null ? icon(entry.group, current, viewer)
                        : icon(entry.mode, current.contains(entry.mode.mode()), viewer));
            }
        } else {
            List<ModeDisplay> modes = modesFor(view);
            for (int i = 0; i < modes.size() && slotIn(view, i, modes.size()) < last; i++) {
                inventory.setItem(slotIn(view, i, modes.size()), icon(modes.get(i), current.contains(modes.get(i).mode()), viewer));
            }
        }
        if (hasBack(view)) {
            ItemStack back = new ItemStack(Material.ARROW);
            ItemMeta meta = back.getItemMeta();
            meta.setDisplayName(ChatColor.GRAY + (category != null ? "Back to the categories" : "Back to the modes"));
            back.setItemMeta(meta);
            inventory.setItem(backSlot(inventory.getSize()), back);
        }
    }

    /**
     * "Popular now", the top row of the compass: the modes with players right now, the most first,
     * then the challenge of the day. What each slot holds goes in the holder, for the click.
     */
    private void fillPopular(Inventory inventory, List<ModeDisplay> modes, List<GameMode> current, HumanEntity viewer, int row) {
        Holder holder = (Holder) inventory.getHolder();
        Map<String, Integer> playing = queues.playing();
        List<String> candidates = new ArrayList<>();
        for (ModeDisplay display : modes) {
            String id = display.mode().id();
            if (!display.hidden() && queues.isPlayable(id) && !settings.privateRoutes().containsKey(id)) {
                candidates.add(id);
            }
        }
        List<String> shown = new ArrayList<>(popular(candidates, id -> playing.getOrDefault(id, 0), queues::waiting, MAX_POPULAR));
        if (challengeMode != null && queues.isPlayable(challengeMode)) {
            shown.add(Holder.CHALLENGE);
        }
        for (int slot = 9 * row; slot < 9 * row + 9; slot++) {
            holder.popular[slot] = null;
            inventory.setItem(slot, null);
        }
        for (int i = 0; i < shown.size(); i++) {
            String id = shown.get(i);
            int slot = 9 * row + popularSlot(i, shown.size());
            holder.popular[slot] = id;
            if (Holder.CHALLENGE.equals(id)) {
                inventory.setItem(slot, challengeIcon());
            } else {
                ModeDisplay display = settings.display(id);
                inventory.setItem(slot, popularIcon(display, playing.getOrDefault(id, 0), current.contains(display.mode()), viewer));
            }
        }
    }

    /**
     * The modes of "Popular now" among these (in the order of the menu): those with at least one
     * player in a game or in the queue, the most players first, then the most waiting (a game
     * soon), then the order of the menu; {@code max} at most.
     */
    static List<String> popular(List<String> modeIds, ToIntFunction<String> playing, ToIntFunction<String> waiting, int max) {
        List<String> busy = new ArrayList<>();
        for (String id : modeIds) {
            if (playing.applyAsInt(id) + waiting.applyAsInt(id) > 0 && !busy.contains(id)) {
                busy.add(id);
            }
        }
        // a stable sort: the order of the menu stays between equals
        busy.sort(Comparator.comparingInt((String id) -> -(playing.applyAsInt(id) + waiting.applyAsInt(id)))
                .thenComparingInt(id -> -waiting.applyAsInt(id)));
        return busy.subList(0, Math.min(Math.max(0, max), busy.size()));
    }

    /** A mode of "Popular now": who plays it, who waits for it, and the click. */
    private ItemStack popularIcon(ModeDisplay display, int playing, boolean waitingHere, HumanEntity viewer) {
        GameMode mode = display.mode();
        ItemStack item = new ItemStack(display.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(mode.name());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + "\u2726 Popular now");
        lore.add(ChatColor.DARK_GRAY + display.format() + (mode.ranked() ? " · ranked" : ""));
        lore.add("");
        lore.add(ChatColor.GRAY + "Playing: " + ChatColor.WHITE + playing);
        if (!Matchmaker.alone(mode)) {
            lore.add(ChatColor.GRAY + "In queue: " + ChatColor.WHITE + queues.waiting(mode.id()));
        }
        clickLines(lore, mode, waitingHere, viewer);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** The challenge of the day in "Popular now": what it asks, a click plays it. */
    private ItemStack challengeIcon() {
        ItemStack item = new ItemStack(Material.WATCH);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Daily challenge");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + (challengeLabel == null ? "" : challengeLabel));
        lore.add("");
        lore.add(ChatColor.GRAY + "The best scores of the day");
        lore.add(ChatColor.GRAY + "win XP: " + Brand.ACCENT + "/challenge");
        lore.add("");
        lore.add(ChatColor.GREEN + "Click to play it");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return ClickMenu.glow(item);
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
        lore.add(waitingHere && waitingFor == null ? Brand.ACCENT + "You are in this queue: click to leave it"
                : ChatColor.GREEN + "Click to join the queue");
        meta.setLore(lore);
        any.setItemMeta(meta);
        inventory.setItem(slotOf(0), any);
        for (int i = 0; i < maps.size() && slotOf(i + 1) < last; i++) {
            String map = maps.get(i);
            boolean chosen = waitingHere && map.equalsIgnoreCase(waitingFor);
            ItemStack item = new ItemStack(Material.EMPTY_MAP);
            meta = item.getItemMeta();
            meta.setDisplayName((chosen ? Brand.ACCENT : ChatColor.AQUA) + map);
            lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + ChatColor.stripColor(mode.name()));
            lore.add("");
            lore.add(ChatColor.GRAY + "Waiting for this map: " + ChatColor.WHITE + queues.waitingFor(mode.id(), map));
            lore.add(chosen ? Brand.ACCENT + "You are in this queue: click to leave it"
                    : ChatColor.GREEN + "Click to play on " + map);
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slotOf(i + 1), item);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        meta = back.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Back to the modes");
        back.setItemMeta(meta);
        inventory.setItem(backSlot(inventory.getSize()), back);
    }

    /** A category in the compass: what it is, its games and queue, and the click to its modes. */
    private ItemStack categoryIcon(ModeCategory category, List<GameMode> current, HumanEntity viewer) {
        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(category.name());
        List<String> lore = new ArrayList<>();
        if (category.slogan() != null) {
            lore.add(category.slogan());
            lore.add("");
        }
        lore.addAll(category.description());
        if (category.party()) {
            int size = partySize(viewer);
            int games = partyEntries(size).size();
            lore.add("");
            lore.add(size < 2 ? ChatColor.GRAY + "You are not in a party"
                    : ChatColor.GRAY + "Your party of " + ChatColor.WHITE + size + ChatColor.GRAY + ": " + ChatColor.WHITE + games
                            + ChatColor.GRAY + (games == 1 ? " game" : " games"));
            lore.add(ChatColor.GREEN + "Click to see the modes");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }
        List<ModeDisplay> modes = settings.modesIn(category.id());
        int waiting = 0;
        boolean here = false;
        for (ModeDisplay display : modes) {
            if (queues.isPlayable(display.mode().id())) {
                waiting += queues.waiting(display.mode().id());
            }
            here |= current.contains(display.mode());
        }
        int games = settings.entriesIn(category.id()).size();
        lore.add("");
        lore.add(ChatColor.GRAY + "" + games + (games == 1 ? " game, " : " games, ") + ChatColor.WHITE + waiting
                + ChatColor.GRAY + " in queue");
        for (ModeDisplay display : modes) {
            if (display.startHere() && !display.hidden()) {
                String group = display.group() == null || settings.group(display.group()) == null ? null
                        : settings.group(display.group()).name();
                lore.add(ChatColor.GOLD + "\u2726 Start here: " + ChatColor.WHITE
                        + ChatColor.stripColor(group != null ? group : display.mode().name()));
                break;
            }
        }
        if (here) {
            lore.add(Brand.ACCENT + "You are in a queue of this category");
        }
        lore.add(ChatColor.GREEN + "Click to see the modes");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** A group in its category: its name, what it is, and each of its modes with its queue. */
    private ItemStack icon(ModeCategory group, List<GameMode> current, HumanEntity viewer) {
        ItemStack item = new ItemStack(group.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(group.name());
        boolean startHere = false;
        for (ModeDisplay display : settings.modesOfGroup(group.id())) {
            startHere |= display.startHere();
        }
        List<String> lore = new ArrayList<>();
        if (startHere) {
            lore.add(ChatColor.GOLD + "\u2726 Start here");
        }
        lore.addAll(group.description());
        lore.add("");
        List<String> waitingIn = new ArrayList<>();
        for (ModeDisplay display : settings.modesOfGroup(group.id())) {
            GameMode mode = display.mode();
            boolean here = current.contains(mode);
            if (here) {
                waitingIn.add(ChatColor.stripColor(mode.name()));
            }
            lore.add((here ? Brand.ACCENT + "\u25b8 " : ChatColor.DARK_GRAY + "\u25b8 ") + ChatColor.WHITE
                    + ChatColor.stripColor(mode.name()) + ChatColor.GRAY + ": "
                    + (queues.isPlayable(mode.id()) ? ChatColor.WHITE + "" + queues.waiting(mode.id()) + ChatColor.GRAY + " in queue"
                            : ChatColor.GRAY + "coming soon"));
        }
        lore.add("");
        if (!waitingIn.isEmpty()) {
            lore.add(Brand.ACCENT + (waitingIn.size() > 1 ? "You are in the queues of " : "You are in the queue of ")
                    + String.join(", ", waitingIn));
        }
        lore.add(ChatColor.GREEN + "Click to choose");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return startHere ? ClickMenu.glow(item) : item;
    }

    private ItemStack icon(ModeDisplay display, boolean waitingHere, HumanEntity viewer) {
        GameMode mode = display.mode();
        ItemStack item = new ItemStack(display.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(mode.name());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + display.format() + (mode.ranked() ? " · ranked" : ""));
        if (display.startHere()) {
            lore.add(ChatColor.GOLD + "\u2726 Start here");
        }
        lore.add("");
        lore.addAll(display.description());
        lore.add("");
        ModeRecord record = recordOf(mode.id(), viewer == null ? null : viewer.getUniqueId());
        lore.addAll(record != null ? recordLines(record) : streakLines(mode, viewer));
        lore.add("");
        if (!queues.isPlayable(mode.id())) {
            lore.add(ChatColor.GRAY + "Coming soon");
        } else {
            lore.add(ChatColor.GRAY + "In queue: " + ChatColor.WHITE + queues.waiting(mode.id()));
            clickLines(lore, mode, waitingHere, viewer);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return display.startHere() ? ClickMenu.glow(item) : item;
    }

    /** What a click on a playable mode does, under its queue: the map one waits for first. */
    private void clickLines(List<String> lore, GameMode mode, boolean waitingHere, HumanEntity viewer) {
        int maps = queues.mapsOf(mode).size();
        String waitingFor = waitingHere && viewer instanceof Player ? queues.mapOf((Player) viewer, mode) : null;
        if (waitingFor != null) {
            lore.add(ChatColor.GRAY + "Your map: " + ChatColor.WHITE + waitingFor);
        }
        // a mode played alone (a training) starts at once; in a training (its Trainings menu), the
        // queue of another is waited in while training
        boolean training = viewer instanceof Player && queues.inGame((Player) viewer);
        boolean alone = Matchmaker.alone(mode);
        lore.add(waitingHere ? Brand.ACCENT + "You are in this queue: click to leave it"
                : maps >= 2 ? ChatColor.GREEN + "Click to choose the map (" + maps + ")"
                : alone ? ChatColor.GREEN + "Click to play"
                : ChatColor.GREEN + "Click to join the queue");
        if (training && !alone && !waitingHere) {
            lore.add(ChatColor.GRAY + "You keep training while you wait.");
        }
    }

    /** Lines of the top of the day under "Daily Streak", and of the top of a training's record. */
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
        lines.add(Brand.ACCENT + "Daily Streak: " + own);
        List<StatsBook.TeamRow> top = book.topStreaks(mode.id(), StatsPeriod.DAY, now);
        for (int i = 0; i < top.size() && i < TOP_STREAKS; i++) {
            lines.add(Brand.ACCENT + " " + (i + 1) + ". " + ChatColor.WHITE + top.get(i).names() + Brand.ACCENT
                    + " - " + top.get(i).value());
        }
        if (viewer != null) {
            lines.add(positionLine(top, viewer.getUniqueId()));
        }
        return lines;
    }

    /**
     * The record of a training in the place of its Daily Streak (plan 3.4), lifetime for now: what
     * it is, "Lifetime record: Island clutch", its top 3, "1. Alice - 12 clutches", and the viewer's
     * best and place: "Your best: 8 clutches (#5)".
     */
    static List<String> recordLines(ModeRecord record) {
        List<String> lines = new ArrayList<>();
        lines.add(Brand.ACCENT + "Lifetime record: " + ChatColor.WHITE + record.name);
        if (record.top.isEmpty()) {
            lines.add(ChatColor.GRAY + " Nobody yet: be the first!");
        }
        for (int i = 0; i < record.top.size() && i < TOP_STREAKS; i++) {
            lines.add(Brand.ACCENT + " " + (i + 1) + ". " + ChatColor.WHITE + record.top.get(i).getKey() + Brand.ACCENT
                    + " - " + record.top.get(i).getValue());
        }
        lines.add(record.best == null ? Brand.ACCENT + "Your best: " + ChatColor.GRAY + "-"
                : Brand.ACCENT + "Your best: " + ChatColor.WHITE + record.best + ChatColor.GRAY + " (#" + record.place + ")");
        return lines;
    }

    /** "Your position: #7 (3)": the first row of the day with this player in its team; "-" if none. */
    static String positionLine(List<StatsBook.TeamRow> rows, java.util.UUID player) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).players().contains(player)) {
                return Brand.ACCENT + "Your position: " + ChatColor.WHITE + "#" + (i + 1) + ChatColor.GRAY + " ("
                        + rows.get(i).value() + ")";
            }
        }
        return Brand.ACCENT + "Your position: " + ChatColor.GRAY + "-";
    }
}
