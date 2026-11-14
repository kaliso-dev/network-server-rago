package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The menu of a stats NPC: the player's head with their totals, then every mode of the catalogue
 * with the player's stats in it (hover a mode to read them), the modes they played first. More
 * modes than a chest holds go on pages, the arrows at the top turn them. Nothing in it can be
 * taken. Main thread only.
 */
final class StatsMenu {

    private static final int HEAD_SLOT = 4;
    private static final int PREVIOUS_SLOT = 0;
    private static final int NEXT_SLOT = 8;
    private static final int FIRST_MODE_SLOT = 9;
    /** The modes of one page: the five rows under the head. */
    static final int PAGE_SIZE = 45;

    private final LobbySettings settings;
    private final StatsBook book;
    private Titles titles = Titles.off();

    StatsMenu(LobbySettings settings, StatsBook book) {
        this.settings = settings;
        this.book = book;
    }

    /** The titles of the modes: each mode tells the title of its wins. */
    void setTitles(Titles titles) {
        this.titles = titles;
    }

    void open(Player player) {
        open(player, player.getUniqueId(), player.getName());
    }

    /** The stats of another player (a player clicked in the lobby), or the viewer's own. */
    void open(Player viewer, UUID player, String name) {
        open(viewer, player, name, 0);
    }

    /** One page of them, from 0. */
    private void open(Player viewer, UUID player, String name, int page) {
        PlayerStats stats = book.get(player);
        List<ModeDisplay> modes = ordered(settings.modes(), id -> played(stats, id));
        int pages = pages(modes.size());
        int shownPage = Math.max(0, Math.min(page, pages - 1));
        List<ModeDisplay> shown = page(modes, shownPage);
        String title = player.equals(viewer.getUniqueId()) ? "Your stats" : "Stats of " + name;
        if (pages > 1) {
            title = title + " (" + (shownPage + 1) + "/" + pages + ")";
        }
        ClickMenu menu = new ClickMenu(1 + (shown.size() + 8) / 9, title);
        long now = System.currentTimeMillis();
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        menu.set(HEAD_SLOT, head(player, name, stats, day, week), null);
        for (int i = 0; i < shown.size(); i++) {
            menu.set(FIRST_MODE_SLOT + i, mode(player, shown.get(i), stats, day, week), null);
        }
        if (shownPage > 0) {
            menu.set(PREVIOUS_SLOT, ClickMenu.item(Material.ARROW, ChatColor.YELLOW + "Previous page",
                    ChatColor.GRAY + "Page " + shownPage + "/" + pages),
                    (clicker, right) -> open(clicker, player, name, shownPage - 1));
        }
        if (shownPage < pages - 1) {
            menu.set(NEXT_SLOT, ClickMenu.item(Material.ARROW, ChatColor.YELLOW + "Next page",
                    ChatColor.GRAY + "Page " + (shownPage + 2) + "/" + pages),
                    (clicker, right) -> open(clicker, player, name, shownPage + 1));
        }
        menu.open(viewer);
    }

    /** True if the player has a game, a kill or a death in the mode. */
    private static boolean played(PlayerStats stats, String modeId) {
        ModeStats m = stats == null ? null : stats.mode(modeId);
        return m != null && (m.games() > 0 || m.kills > 0 || m.deaths > 0);
    }

    /**
     * The modes of the menu: those played first, then the others, each in the order of config.yml;
     * a hidden mode (an event, the kit of other modes) only once played.
     */
    static List<ModeDisplay> ordered(List<ModeDisplay> modes, Predicate<String> played) {
        List<ModeDisplay> first = new ArrayList<>();
        List<ModeDisplay> then = new ArrayList<>();
        for (ModeDisplay display : modes) {
            if (played.test(display.mode().id())) {
                first.add(display);
            } else if (!display.hidden()) {
                then.add(display);
            }
        }
        first.addAll(then);
        return first;
    }

    /** How many pages so many modes take, 1 at least. */
    static int pages(int modes) {
        return Math.max(1, (modes + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** The modes of one page, from 0. */
    static <T> List<T> page(List<T> modes, int page) {
        int from = Math.min(modes.size(), page * PAGE_SIZE);
        return new ArrayList<>(modes.subList(from, Math.min(modes.size(), from + PAGE_SIZE)));
    }

    @SuppressWarnings("deprecation") // SkullMeta#setOwner(String) is the 1.8.8 way to show a player's head
    private ItemStack head(UUID player, String name, PlayerStats stats, String day, String week) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwner(name);
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + name);
        List<String> lore = new ArrayList<>();
        NetworkLevel levels = book.levels();
        int xp = stats == null ? 0 : stats.xp();
        int level = levels.level(xp);
        lore.add(ChatColor.GRAY + "Network level: " + levels.tag(level));
        if (level < NetworkLevel.MAX_LEVEL) {
            lore.add(ChatColor.GRAY + "XP: " + ChatColor.AQUA + levels.xpIntoLevel(xp) + ChatColor.GRAY + "/"
                    + levels.xpToNext(level) + " to level " + (level + 1));
        }
        int tier = levels.nextTier(level);
        if (tier > 0) {
            lore.add(ChatColor.GRAY + "Next colour: " + levels.tag(tier));
        }
        lore.add(ChatColor.GRAY + "Total XP: " + ChatColor.AQUA + xp);
        lore.add(ChatColor.GRAY + "Rago Points: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", stats == null ? 0 : stats.points()));
        lore.add("");
        lore.add(ChatColor.GRAY + "All modes");
        lore.add("");
        int wins = stats == null ? 0 : stats.wins(StatsPeriod.ALL, null, day, week);
        int losses = stats == null ? 0 : stats.losses();
        lore.add(line("Wins", ChatColor.GREEN, wins));
        lore.add(line("Losses", ChatColor.RED, losses));
        lore.add(line("Games played", ChatColor.WHITE, wins + losses));
        lore.add(ChatColor.GRAY + "WLR (wins / losses): " + ChatColor.GOLD + ratio(wins, losses));
        lore.add(line("Best win streak", Brand.ACCENT, stats == null ? 0 : stats.best(StatsPeriod.ALL, null, day, week)));
        int kills = stats == null ? 0 : stats.kills(StatsPeriod.ALL, null, day, week);
        int deaths = stats == null ? 0 : stats.deaths();
        lore.add(line("Kills", ChatColor.GREEN, kills));
        lore.add(line("Deaths", ChatColor.RED, deaths));
        lore.add(ChatColor.GRAY + "K/D ratio: " + Brand.ACCENT + ratio(kills, deaths));
        int finals = stats == null ? 0 : stats.finalKills();
        int finalDeaths = stats == null ? 0 : stats.finalDeaths();
        lore.add(line("Final kills", ChatColor.GREEN, finals));
        lore.add(line("Final deaths", ChatColor.RED, finalDeaths));
        lore.add(ChatColor.GRAY + "FKDR (final kills / final deaths): " + ChatColor.GOLD + ratio(finals, finalDeaths));
        lore.add("");
        lore.add(line("Wins today", ChatColor.GREEN, stats == null ? 0 : stats.wins(StatsPeriod.DAY, null, day, week)));
        lore.add(line("Wins this week", ChatColor.GREEN, stats == null ? 0 : stats.wins(StatsPeriod.WEEK, null, day, week)));
        lore.add(line("Kills today", ChatColor.GREEN, stats == null ? 0 : stats.kills(StatsPeriod.DAY, null, day, week)));
        lore.add(line("Kills this week", ChatColor.GREEN, stats == null ? 0 : stats.kills(StatsPeriod.WEEK, null, day, week)));
        List<GameMode> ranked = rankedModes(settings);
        if (!ranked.isEmpty()) {
            lore.add("");
            for (GameMode mode : ranked) {
                lore.add(ChatColor.GRAY + "Elo " + shortName(mode) + ": "
                        + book.ladder().format(book.elo(player, mode.id())));
            }
        }
        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack mode(UUID player, ModeDisplay display, PlayerStats stats, String day, String week) {
        GameMode mode = display.mode();
        ItemStack icon = new ItemStack(display.icon());
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + mode.name());
        List<String> lore = new ArrayList<>();
        ModeStats m = stats == null ? null : stats.mode(mode.id());
        if (m == null || m.games() == 0 && m.kills == 0 && m.deaths == 0) {
            lore.add(ChatColor.DARK_GRAY + "No game yet");
        } else {
            lore.add(line("Games played", ChatColor.WHITE, m.games()));
            lore.add(line("Wins", ChatColor.GREEN, m.wins));
            lore.add(line("Losses", ChatColor.RED, m.losses));
            lore.add(ChatColor.GRAY + "WLR: " + ChatColor.GOLD + ratio(m.wins, m.losses));
            lore.add(line("Current win streak", Brand.ACCENT, m.streak));
            lore.add(line("Best win streak", Brand.ACCENT, m.bestStreak));
            lore.add(line("Kills", ChatColor.GREEN, m.kills));
            lore.add(line("Deaths", ChatColor.RED, m.deaths));
            lore.add(ChatColor.GRAY + "K/D ratio: " + Brand.ACCENT + ratio(m.kills, m.deaths));
            if (m.finalKills > 0 || m.finalDeaths > 0 || m.beds > 0) {
                lore.add(line("Final kills", ChatColor.GREEN, m.finalKills));
                lore.add(line("Final deaths", ChatColor.RED, m.finalDeaths));
                lore.add(ChatColor.GRAY + "FKDR: " + ChatColor.GOLD + ratio(m.finalKills, m.finalDeaths));
                lore.add(line("Beds broken", Brand.ACCENT, m.beds));
            }
            lore.add("");
            lore.add(line("Wins today", ChatColor.GREEN, m.wins(StatsPeriod.DAY, day, week)));
            lore.add(line("Wins this week", ChatColor.GREEN, m.wins(StatsPeriod.WEEK, day, week)));
        }
        if (mode.ranked()) {
            lore.add(ChatColor.GRAY + "Elo: " + book.ladder().format(book.elo(player, mode.id())));
        }
        for (Titles.Family family : titles.familiesOf(mode.id())) {
            Titles.Title title = titles.title(stats, family);
            lore.add(ChatColor.GRAY + "Title: " + (title == null ? ChatColor.DARK_GRAY + "none yet" : title.text()));
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static String line(String what, ChatColor colour, int value) {
        return ChatColor.GRAY + what + ": " + colour + value;
    }

    /** 2.5, or the wins alone when there is no loss. */
    static String ratio(int wins, int losses) {
        if (losses == 0) {
            return String.valueOf(wins);
        }
        double ratio = Math.round(100.0 * wins / losses) / 100.0;
        return ratio == Math.floor(ratio) ? String.valueOf((long) ratio) : String.valueOf(ratio);
    }

    static List<GameMode> rankedModes(LobbySettings settings) {
        List<GameMode> ranked = new ArrayList<>();
        for (ModeDisplay display : settings.modes()) {
            if (display.mode().ranked()) {
                ranked.add(display.mode());
            }
        }
        return ranked;
    }

    /** "1v1" for a 1 against 1, else the mode's name without colours. */
    static String shortName(GameMode mode) {
        if (mode.teams() == 2) {
            return mode.playersPerTeam() + "v" + mode.playersPerTeam();
        }
        return ChatColor.stripColor(mode.name());
    }
}
