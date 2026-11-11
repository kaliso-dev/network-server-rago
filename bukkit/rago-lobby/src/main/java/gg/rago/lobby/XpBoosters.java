package gg.rago.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The boosters (/booster), each for 30 minutes of play, the time running only while the player is
 * in a game. The XP boosters bought (in the shop with Rago Points, on the web store, in the starter
 * pack) triple the Network XP; the free one a day of [PREMIUM+] (rago.perk.booster) only doubles
 * it, and PREMIUM's own boost is +50%: what is bought is always the strongest. The coin boosters
 * (web store, starter pack) triple the Rago Points of the games. One XP booster and one coin
 * booster at a time: the next waits until it is over. Main thread only.
 */
final class XpBoosters {

    /** The kinds of booster a player can have waiting. */
    enum Kind {
        /** Bought: x3 Network XP. */
        XP(3.0, "XP booster", "x3 Network XP"),
        /** The free one of PREMIUM: x2 Network XP. */
        DAILY(2.0, "Daily XP booster", "x2 Network XP"),
        /** x3 Rago Points. */
        COINS(3.0, "Points booster", "x3 Rago Points");

        final double rate;
        final String label;
        final String effect;

        Kind(double rate, String label, String effect) {
            this.rate = rate;
            this.label = label;
            this.effect = effect;
        }
    }

    /** How long a booster lasts: 30 minutes in games. */
    static final long DURATION_MS = 30L * 60 * 1000;
    /** What a bought XP booster multiplies the Network XP by, on top of the rank's boost. */
    static final double MULTIPLIER = Kind.XP.rate;
    /** What PREMIUM's free booster of the day multiplies it by: less than a bought one. */
    static final double DAILY_MULTIPLIER = Kind.DAILY.rate;
    /** What a coin booster multiplies the Rago Points of the games by. */
    static final double COIN_MULTIPLIER = Kind.COINS.rate;
    /** The price of an XP booster in the shop, in Rago Points. */
    static final int PRICE = 2_500;
    /** The permission of the free booster of every day. */
    static final String DAILY_PERMISSION = "rago.perk.booster";
    /** How many boosters of a kind a player keeps at most. */
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

    /** What the XP booster running multiplies the XP by: 3 (bought), 2 (PREMIUM's of the day) or 1. */
    double multiplier(UUID player) {
        PlayerStats profile = book.get(player);
        return profile == null ? 1.0 : xpRate(profile);
    }

    static double xpRate(PlayerStats profile) {
        if (profile.boosterLeftMs() <= 0) {
            return 1.0;
        }
        // a booster started before the kinds: the x2 of then
        return profile.boosterRate() > 1.0 ? profile.boosterRate() : DAILY_MULTIPLIER;
    }

    /** What the coin booster running multiplies the Rago Points of the games by: 3 or 1. */
    double coinMultiplier(UUID player) {
        PlayerStats profile = book.get(player);
        return profile != null && profile.coinBoosterLeftMs() > 0 ? COIN_MULTIPLIER : 1.0;
    }

    /** How many of this kind the player has waiting. */
    static int count(PlayerStats profile, Kind kind) {
        switch (kind) {
            case DAILY:
                return profile.dailyBoosters();
            case COINS:
                return profile.coinBoosters();
            default:
                return profile.boosters();
        }
    }

    static void setCount(PlayerStats profile, Kind kind, int count) {
        int kept = Math.max(0, Math.min(MAX_STOCK, count));
        switch (kind) {
            case DAILY:
                profile.setDailyBoosters(kept);
                break;
            case COINS:
                profile.setCoinBoosters(kept);
                break;
            default:
                profile.setBoosters(kept);
                break;
        }
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
                setCount(profile, Kind.DAILY, profile.dailyBoosters() + 1);
                profiles.changed(profile);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GREEN + "Your free XP booster of the day is"
                        + " here: " + ChatColor.YELLOW + "/booster" + ChatColor.GREEN + " (x2 XP for 30 minutes of play).");
            }
            if (!state.isInGame(player)) {
                continue;
            }
            boolean changed = false;
            long left = profile.boosterLeftMs();
            if (left > 0) {
                left = Math.max(0, left - 1000L);
                profile.setBoosterLeftMs(left);
                changed = true;
                if (left == 0) {
                    profile.setBoosterRate(0);
                    profiles.changed(profile);
                    int more = profile.boosters() + profile.dailyBoosters();
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "Your XP booster is over." + (more > 0
                            ? ChatColor.GRAY + " You have " + more + " more: " + ChatColor.YELLOW + "/booster" : ""));
                }
            }
            long coins = profile.coinBoosterLeftMs();
            if (coins > 0) {
                coins = Math.max(0, coins - 1000L);
                profile.setCoinBoosterLeftMs(coins);
                changed = true;
                if (coins == 0) {
                    profiles.changed(profile);
                    player.sendMessage(ChatColor.GOLD + "Your points booster is over." + (profile.coinBoosters() > 0
                            ? ChatColor.GRAY + " You have " + profile.coinBoosters() + " more: " + ChatColor.YELLOW + "/booster"
                            : ""));
                }
            }
            if (changed && save) {
                profiles.changed(profile);
            }
        }
    }

    /** "12:34": the time a booster has left. */
    static String clock(long ms) {
        long seconds = (ms + 999) / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** /booster on: the strongest XP booster the player has (a bought one before PREMIUM's of the day). */
    void activate(Player player) {
        PlayerStats profile = profiles.of(player);
        activate(player, profile.boosters() > 0 ? Kind.XP : Kind.DAILY);
    }

    /** Starts one of the player's boosters of this kind, if none of its sort runs. */
    void activate(Player player, Kind kind) {
        PlayerStats profile = profiles.of(player);
        boolean coins = kind == Kind.COINS;
        long running = coins ? profile.coinBoosterLeftMs() : profile.boosterLeftMs();
        if (running > 0) {
            player.sendMessage(ChatColor.RED + "A" + (coins ? " coin" : "n XP") + " booster already runs (" + clock(running)
                    + " of play left).");
            return;
        }
        if (count(profile, kind) <= 0) {
            player.sendMessage(ChatColor.RED + "You have no " + kind.label + "." + (kind == Kind.XP
                    ? " Buy one in the shop: " + ChatColor.YELLOW + "/booster" : kind == Kind.COINS
                    ? " Get some on the store: " + ChatColor.YELLOW + "/pack" : ""));
            return;
        }
        setCount(profile, kind, count(profile, kind) - 1);
        if (coins) {
            profile.setCoinBoosterLeftMs(DURATION_MS);
        } else {
            profile.setBoosterLeftMs(DURATION_MS);
            profile.setBoosterRate(kind.rate);
        }
        profiles.changed(profile);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GREEN + kind.label + " on: " + (coins ? ChatColor.GOLD
                : ChatColor.AQUA) + kind.effect + ChatColor.GREEN + " for 30 minutes of play (the time runs in games only).");
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
    }

    /** Buys an XP booster (x3) with Rago Points. */
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
        setCount(profile, Kind.XP, profile.boosters() + 1);
        profiles.changed(profile);
        player.sendMessage(ChatColor.GREEN + "You bought an XP booster (x3 XP) for " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", PRICE)
                + " Rago Points" + ChatColor.GREEN + ". " + ChatColor.YELLOW + "/booster" + ChatColor.GREEN + " to start it.");
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1f, 1.2f);
    }

    /** /booster give: the web store or the staff give boosters of this kind. */
    void give(PlayerStats profile, int count, Kind kind) {
        setCount(profile, kind, count(profile, kind) + count);
        profiles.changed(profile);
        Player online = lobby.getServer().getPlayer(profile.id());
        if (online != null) {
            online.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GREEN + "You got " + count + " " + kind.label
                    + (count > 1 ? "s" : "") + " (" + kind.effect + "): " + ChatColor.YELLOW + "/booster");
            online.playSound(online.getLocation(), Sound.LEVEL_UP, 1f, 1.5f);
        }
    }

    /** The menu: each kind of booster, how many the player has, the ones running, to start or buy one. */
    void open(Player player) {
        PlayerStats profile = profiles.of(player);
        ClickMenu menu = new ClickMenu(4, "Boosters");
        long left = profile.boosterLeftMs();
        long coinsLeft = profile.coinBoosterLeftMs();
        menu.set(4, ClickMenu.item(Material.WATCH, 0, ChatColor.YELLOW + "Running", listOf(
                left > 0 ? ChatColor.AQUA + "x" + rate(xpRate(profile)) + " Network XP" + ChatColor.GRAY + ": " + clock(left) + " left"
                        : ChatColor.GRAY + "No XP booster",
                coinsLeft > 0 ? ChatColor.GOLD + "x" + rate(COIN_MULTIPLIER) + " Rago Points" + ChatColor.GRAY + ": " + clock(coinsLeft)
                        + " left" : ChatColor.GRAY + "No points booster",
                "",
                "The time only runs while you", "are in a game.")), null);
        menu.set(11, ClickMenu.glow(ClickMenu.item(Material.EXP_BOTTLE, 0, ChatColor.AQUA + "" + ChatColor.BOLD + "XP booster x3",
                listOf("Triples your Network XP for", "30 minutes of play.", "",
                        "Yours: " + ChatColor.GREEN + profile.boosters(),
                        "Price: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", PRICE) + " Rago Points",
                        "",
                        profile.boosters() > 0 ? ChatColor.YELLOW + "Left click to start one" : ChatColor.GRAY + "None to start",
                        ChatColor.YELLOW + "Right click to buy one"))), (p, right) -> {
                            if (right) {
                                buy(p);
                                open(p);
                            } else {
                                p.closeInventory();
                                activate(p, Kind.XP);
                            }
                        });
        boolean premium = player.hasPermission(DAILY_PERMISSION);
        menu.set(13, ClickMenu.item(Material.GLASS_BOTTLE, 0, ChatColor.LIGHT_PURPLE + "Daily XP booster x2", listOf(
                "Doubles your Network XP for", "30 minutes of play.",
                ChatColor.LIGHT_PURPLE + "[PREMIUM+]" + ChatColor.GRAY + ": one free every day,", "on top of +50% XP in every game.", "",
                "Yours: " + ChatColor.GREEN + profile.dailyBoosters(),
                "",
                profile.dailyBoosters() > 0 ? ChatColor.YELLOW + "Click to start one" : premium
                        ? ChatColor.GRAY + "The next one comes tomorrow" : ChatColor.YELLOW + "With [PREMIUM+]: /pack")),
                (p, right) -> {
                    p.closeInventory();
                    activate(p, Kind.DAILY);
                });
        menu.set(15, ClickMenu.glow(ClickMenu.item(Material.GOLD_INGOT, 0, ChatColor.GOLD + "" + ChatColor.BOLD + "Points booster x3",
                listOf("Triples the Rago Points of your", "games for 30 minutes of play.", "",
                        "Yours: " + ChatColor.GREEN + profile.coinBoosters(),
                        "On the store, and in the Starter Pack.",
                        "",
                        profile.coinBoosters() > 0 ? ChatColor.YELLOW + "Click to start one" : ChatColor.YELLOW + "Click for the store"))),
                (p, right) -> {
                    p.closeInventory();
                    if (profiles.of(p).coinBoosters() > 0) {
                        activate(p, Kind.COINS);
                    } else {
                        p.performCommand("pack");
                    }
                });
        menu.set(31, ClickMenu.item(Material.BARRIER, ChatColor.RED + "Close"), (p, right) -> p.closeInventory());
        menu.open(player);
    }

    /** "3" or "1.5". */
    static String rate(double rate) {
        return rate == Math.rint(rate) ? String.valueOf((int) rate) : String.valueOf(rate);
    }

    private static List<String> listOf(String... lines) {
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            list.add(line);
        }
        return list;
    }
}
