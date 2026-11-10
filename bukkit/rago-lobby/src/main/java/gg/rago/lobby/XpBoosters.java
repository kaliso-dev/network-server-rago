package gg.rago.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The XP boosters (/booster): one doubles the player's Network XP for 30 minutes of play, the time
 * running only while they are in a game. A player buys them in the shop with Rago Points; the
 * [PREMIUM+] get one free a day (rago.perk.booster), and the web store gives them with
 * /booster give. One at a time: the next waits until it is over. Main thread only.
 */
final class XpBoosters {

    /** How long a booster lasts: 30 minutes in games. */
    static final long DURATION_MS = 30L * 60 * 1000;
    /** What it multiplies the Network XP by, on top of the rank's boost. */
    static final double MULTIPLIER = 2.0;
    /** Its price in the shop, in Rago Points. */
    static final int PRICE = 2_500;
    /** The permission of the free booster of every day. */
    static final String DAILY_PERMISSION = "rago.perk.booster";
    /** How many boosters a player keeps at most. */
    static final int MAX_STOCK = 99;

    private final RagoLobby lobby;
    private final Profiles profiles;
    private final StatsBook book;
    private final LobbyState state;
    /** The seconds since the last save of each running booster: saved once a minute. */
    private int sinceSave;

    XpBoosters(RagoLobby lobby, Profiles profiles, StatsBook book, LobbyState state) {
        this.lobby = lobby;
        this.profiles = profiles;
        this.book = book;
        this.state = state;
    }

    /** 2 while the player's booster runs, else 1. */
    double multiplier(UUID player) {
        PlayerStats profile = book.get(player);
        return profile != null && profile.boosterLeftMs() > 0 ? MULTIPLIER : 1.0;
    }

    /**
     * Once a second: the boosters of the players in a game run down a second, and the [PREMIUM+]
     * get their booster of the day.
     */
    void tick() {
        boolean save = ++sinceSave >= 60;
        if (save) {
            sinceSave = 0;
        }
        String today = StatsPeriod.dayKey(System.currentTimeMillis());
        for (Player player : lobby.getServer().getOnlinePlayers()) {
            PlayerStats profile = book.get(player.getUniqueId());
            if (profile == null) {
                continue;
            }
            if (player.hasPermission(DAILY_PERMISSION) && !today.equals(profile.boosterDay())) {
                profile.setBoosterDay(today);
                profile.setBoosters(Math.min(MAX_STOCK, profile.boosters() + 1));
                profiles.changed(profile);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GREEN + "Your free XP booster of the day is"
                        + " here: " + ChatColor.YELLOW + "/booster" + ChatColor.GREEN + " (x2 XP for 30 minutes of play).");
            }
            long left = profile.boosterLeftMs();
            if (left <= 0 || !state.isInGame(player)) {
                continue;
            }
            left = Math.max(0, left - 1000L);
            profile.setBoosterLeftMs(left);
            if (left == 0) {
                profiles.changed(profile);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Your XP booster is over." + (profile.boosters() > 0
                        ? ChatColor.GRAY + " You have " + profile.boosters() + " more: " + ChatColor.YELLOW + "/booster" : ""));
            } else if (save) {
                profiles.changed(profile);
            }
        }
    }

    /** "12:34": the time a booster has left. */
    static String clock(long ms) {
        long seconds = (ms + 999) / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Starts one of the player's boosters, if none runs. */
    void activate(Player player) {
        PlayerStats profile = profiles.of(player);
        if (profile.boosterLeftMs() > 0) {
            player.sendMessage(ChatColor.RED + "A booster already runs (" + clock(profile.boosterLeftMs())
                    + " of play left).");
            return;
        }
        if (profile.boosters() <= 0) {
            player.sendMessage(ChatColor.RED + "You have no XP booster. Buy one in the shop: " + ChatColor.YELLOW + "/booster");
            return;
        }
        profile.setBoosters(profile.boosters() - 1);
        profile.setBoosterLeftMs(DURATION_MS);
        profiles.changed(profile);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GREEN + "XP booster on: " + ChatColor.AQUA + "x2 Network XP"
                + ChatColor.GREEN + " for 30 minutes of play (the time runs in games only).");
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
    }

    /** Buys a booster with Rago Points. */
    void buy(Player player) {
        PlayerStats profile = profiles.of(player);
        String why = profiles.cannotBuy(player, PRICE, 0);
        if (why != null) {
            player.sendMessage(ChatColor.RED + why);
            return;
        }
        if (profile.boosters() >= MAX_STOCK) {
            player.sendMessage(ChatColor.RED + "You have enough boosters for now.");
            return;
        }
        profile.addPoints(-PRICE);
        profile.setBoosters(profile.boosters() + 1);
        profiles.changed(profile);
        player.sendMessage(ChatColor.GREEN + "You bought an XP booster for " + ChatColor.GOLD + String.format("%,d", PRICE)
                + " Rago Points" + ChatColor.GREEN + ". " + ChatColor.YELLOW + "/booster" + ChatColor.GREEN + " to start it.");
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1f, 1.2f);
    }

    /** /booster give: the web store or the staff give boosters. */
    void give(PlayerStats profile, int count) {
        profile.setBoosters(Math.min(MAX_STOCK, profile.boosters() + count));
        profiles.changed(profile);
        Player online = lobby.getServer().getPlayer(profile.id());
        if (online != null) {
            online.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GREEN + "You got " + count + " XP booster"
                    + (count > 1 ? "s" : "") + ": " + ChatColor.YELLOW + "/booster");
            online.playSound(online.getLocation(), Sound.LEVEL_UP, 1f, 1.5f);
        }
    }

    /** The menu: the boosters the player has, the one running, to start one or buy one. */
    void open(Player player) {
        PlayerStats profile = profiles.of(player);
        ClickMenu menu = new ClickMenu(3, "XP boosters");
        long left = profile.boosterLeftMs();
        List<String> lore = new ArrayList<>();
        lore.add("Doubles your Network XP for");
        lore.add("30 minutes of play: the time");
        lore.add("only runs while you are in a game.");
        lore.add("");
        lore.add("Yours: " + ChatColor.GREEN + profile.boosters());
        lore.add(left > 0 ? "Running: " + ChatColor.AQUA + clock(left) + ChatColor.GRAY + " of play left" : "None running");
        lore.add("");
        lore.add(left > 0 ? ChatColor.GRAY + "One at a time" : profile.boosters() > 0 ? ChatColor.YELLOW + "Click to start one"
                : ChatColor.RED + "Buy one on the right");
        menu.set(11, ClickMenu.item(Material.EXP_BOTTLE, 0, ChatColor.AQUA + "Start an XP booster", lore), (p, right) -> {
            p.closeInventory();
            activate(p);
        });
        menu.set(13, ClickMenu.item(Material.NETHER_STAR, 0, ChatColor.LIGHT_PURPLE + "Free with [PREMIUM+]", listOf(
                "One XP booster every day,", "on top of +50% XP in every game.", "",
                player.hasPermission(DAILY_PERMISSION) ? ChatColor.GREEN + "Yours: it comes once a day"
                        : ChatColor.YELLOW + "rago-optimizer.com")), null);
        menu.set(15, ClickMenu.item(Material.GOLD_INGOT, 0, ChatColor.GOLD + "Buy an XP booster", listOf(
                "Price: " + ChatColor.GOLD + String.format("%,d", PRICE) + " Rago Points", "",
                "Your points: " + ChatColor.GOLD + String.format("%,d", profile.points()), "",
                ChatColor.YELLOW + "Click to buy")), (p, right) -> {
                    buy(p);
                    open(p);
                });
        menu.open(player);
    }

    private static List<String> listOf(String... lines) {
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            list.add(line);
        }
        return list;
    }
}
