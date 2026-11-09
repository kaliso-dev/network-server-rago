package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The menu of a stats NPC: the player's head with their totals, then every mode of the catalogue
 * with the player's stats in it (hover a mode to read them). Nothing in it can be taken. Main
 * thread only.
 */
final class StatsMenu {

    /** Marks the inventory as this menu; clicks in it are cancelled. */
    static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int HEAD_SLOT = 4;
    private static final int FIRST_MODE_SLOT = 9;

    private final LobbySettings settings;
    private final StatsBook book;

    StatsMenu(LobbySettings settings, StatsBook book) {
        this.settings = settings;
        this.book = book;
    }

    void open(Player player) {
        List<ModeDisplay> modes = settings.modes();
        int slots = Math.min(54, FIRST_MODE_SLOT + ((modes.size() + 8) / 9) * 9);
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, slots, "Your stats");
        holder.inventory = inventory;
        long now = System.currentTimeMillis();
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        PlayerStats stats = book.get(player.getUniqueId());
        inventory.setItem(HEAD_SLOT, head(player, stats, day, week));
        for (int i = 0; i < modes.size() && FIRST_MODE_SLOT + i < slots; i++) {
            inventory.setItem(FIRST_MODE_SLOT + i, mode(player, modes.get(i), stats, day, week));
        }
        player.openInventory(inventory);
    }

    @SuppressWarnings("deprecation") // SkullMeta#setOwner(String) is the 1.8.8 way to show a player's head
    private ItemStack head(Player player, PlayerStats stats, String day, String week) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwner(player.getName());
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + player.getName());
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
        lore.add(ChatColor.GRAY + "Rago Points: " + ChatColor.GOLD + String.format("%,d", stats == null ? 0 : stats.points()));
        lore.add("");
        lore.add(ChatColor.GRAY + "All modes");
        lore.add("");
        int wins = stats == null ? 0 : stats.wins(StatsPeriod.ALL, null, day, week);
        int losses = stats == null ? 0 : stats.losses();
        lore.add(line("Wins", ChatColor.GREEN, wins));
        lore.add(line("Losses", ChatColor.RED, losses));
        lore.add(line("Games played", ChatColor.WHITE, wins + losses));
        lore.add(ChatColor.GRAY + "W/L ratio: " + ChatColor.YELLOW + ratio(wins, losses));
        lore.add(line("Best win streak", ChatColor.YELLOW, stats == null ? 0 : stats.best(StatsPeriod.ALL, null, day, week)));
        int kills = stats == null ? 0 : stats.kills(StatsPeriod.ALL, null, day, week);
        int deaths = stats == null ? 0 : stats.deaths();
        lore.add(line("Kills", ChatColor.GREEN, kills));
        lore.add(line("Deaths", ChatColor.RED, deaths));
        lore.add(ChatColor.GRAY + "K/D ratio: " + ChatColor.YELLOW + ratio(kills, deaths));
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
                        + book.ladder().format(book.elo(player.getUniqueId(), mode.id())));
            }
        }
        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack mode(Player player, ModeDisplay display, PlayerStats stats, String day, String week) {
        GameMode mode = display.mode();
        ItemStack icon = new ItemStack(display.icon());
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + mode.name());
        List<String> lore = new ArrayList<>();
        ModeStats m = stats == null ? null : stats.mode(mode.id());
        if (m == null || m.games() == 0 && m.kills == 0 && m.deaths == 0) {
            lore.add(ChatColor.DARK_GRAY + "No game yet");
        } else {
            lore.add(line("Wins", ChatColor.GREEN, m.wins));
            lore.add(line("Losses", ChatColor.RED, m.losses));
            lore.add(ChatColor.GRAY + "W/L ratio: " + ChatColor.YELLOW + ratio(m.wins, m.losses));
            lore.add(line("Current win streak", ChatColor.YELLOW, m.streak));
            lore.add(line("Best win streak", ChatColor.YELLOW, m.bestStreak));
            lore.add(line("Kills", ChatColor.GREEN, m.kills));
            lore.add(line("Deaths", ChatColor.RED, m.deaths));
            lore.add(ChatColor.GRAY + "K/D ratio: " + ChatColor.YELLOW + ratio(m.kills, m.deaths));
            lore.add("");
            lore.add(line("Wins today", ChatColor.GREEN, m.wins(StatsPeriod.DAY, day, week)));
            lore.add(line("Wins this week", ChatColor.GREEN, m.wins(StatsPeriod.WEEK, day, week)));
        }
        if (mode.ranked()) {
            lore.add(ChatColor.GRAY + "Elo: " + book.ladder().format(book.elo(player.getUniqueId(), mode.id())));
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
