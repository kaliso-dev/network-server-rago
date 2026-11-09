package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * "My profile" (the head of the hotbar, /profile): the player's level, rank, points and ranked
 * divisions, and the way to their stats, friends, settings, the shop and the daily reward. Main
 * thread only.
 */
final class ProfileMenu {

    private final RagoLobby lobby;
    private final Profiles profiles;
    private final Social social;
    private final StatsService stats;
    private final StatsMenu statsMenu;
    private final ShopMenu shop;
    private StyleMenu styles;
    private LevelMenu levels;

    ProfileMenu(RagoLobby lobby, Profiles profiles, Social social, StatsService stats, StatsMenu statsMenu, ShopMenu shop) {
        this.lobby = lobby;
        this.profiles = profiles;
        this.social = social;
        this.stats = stats;
        this.statsMenu = statsMenu;
        this.shop = shop;
    }

    /** The lines of a player's profile: level, rank, XP, points, the division of each ranked mode. */
    List<String> summary(PlayerStats profile, java.util.UUID id) {
        StatsBook book = stats.book();
        NetworkLevel levels = book.levels();
        int xp = profile == null ? 0 : profile.xp();
        int level = levels.level(xp);
        RankDef rank = profiles.rank(id);
        List<String> lore = new ArrayList<>();
        lore.add("Rank: " + (rank == Ranks.DEFAULT ? ChatColor.GRAY + "Default" : rank.prefix().trim()));
        lore.add("Network level: " + levels.tag(level));
        if (level < NetworkLevel.MAX_LEVEL) {
            lore.add("Progress: " + ChatColor.AQUA + levels.xpIntoLevel(xp) + ChatColor.GRAY + "/" + levels.xpToNext(level)
                    + " XP " + bar(levels.progress(xp)));
            int tier = levels.nextTier(level);
            if (tier > 0) {
                lore.add("Next colour: " + levels.tag(tier));
            }
        }
        lore.add("Rago Points: " + ChatColor.GOLD + String.format("%,d", profile == null ? 0 : profile.points()));
        List<GameMode> ranked = StatsMenu.rankedModes(lobby.settings());
        if (!ranked.isEmpty()) {
            lore.add("");
            lore.add(ChatColor.WHITE + "Ranked divisions");
            for (GameMode mode : ranked) {
                lore.add(ChatColor.GRAY + StatsMenu.shortName(mode) + ": " + book.ladder().format(book.elo(id, mode.id())));
            }
        }
        return lore;
    }

    void setStyles(StyleMenu styles) {
        this.styles = styles;
    }

    void setLevels(LevelMenu levels) {
        this.levels = levels;
    }

    /** What shows above the name: each choice, with the line it gives. */
    void openDisplay(Player player) {
        PlayerStats profile = profiles.of(player);
        HeadLines.Shown now = HeadLines.Shown.of(profile.headLine() == null ? "elo" : profile.headLine());
        ClickMenu menu = new ClickMenu(4, "Above your name");
        int slot = 10;
        for (HeadLines.Shown shown : HeadLines.Shown.values()) {
            List<String> lore = new ArrayList<>();
            for (String line : shown.description()) {
                lore.add(line);
            }
            lore.add("");
            if (shown == HeadLines.Shown.MESSAGE) {
                lore.add(profile.headMessage() == null ? ChatColor.DARK_GRAY + "No message yet" : "Yours: " + ChatColor.RESET
                        + profile.headMessage());
                lore.add("");
                if (!player.hasPermission("rago.perk.message")) {
                    lore.add(ChatColor.LIGHT_PURPLE + "With the [PREMIUM] rank");
                }
            }
            lore.add(shown == now ? ChatColor.GREEN + "Shown" : ChatColor.YELLOW + "Click to show it");
            menu.set(slot++, ClickMenu.item(shown.icon(), 0, (shown == now ? ChatColor.GREEN : ChatColor.YELLOW) + shown.title(),
                    lore), (p, right) -> {
                        profiles.setHeadLine(p, shown);
                        openDisplay(p);
                    });
        }
        menu.set(31, ClickMenu.back("My profile"), (p, right) -> open(p));
        menu.open(player);
    }

    /** "▮▮▮▮▮▯▯▯▯▯". */
    static String bar(float progress) {
        int full = Math.max(0, Math.min(10, Math.round(progress * 10)));
        StringBuilder bar = new StringBuilder(ChatColor.AQUA.toString());
        for (int i = 0; i < 10; i++) {
            if (i == full) {
                bar.append(ChatColor.DARK_GRAY);
            }
            bar.append('▌');
        }
        return bar.toString();
    }

    void open(Player player) {
        PlayerStats profile = profiles.of(player);
        ClickMenu menu = new ClickMenu(5, "My profile");
        menu.set(4, ClickMenu.head(player.getName(), profiles.displayName(player.getUniqueId(), player.getName()),
                summary(profile, player.getUniqueId())), null);
        menu.set(19, ClickMenu.item(Material.PAPER, ChatColor.GREEN + "Stats", "Wins, kills, streaks and ELO", "of every mode.",
                "", ChatColor.YELLOW + "Click to open"), (p, right) -> statsMenu.open(p));
        menu.set(21, ClickMenu.head(player.getName(), ChatColor.GREEN + "Friends", listOf(
                "Friends: " + ChatColor.WHITE + profile.friends().size() + ChatColor.GRAY + "/" + Social.MAX_FRIENDS,
                "", "Add one: /friend add <name>", "", ChatColor.YELLOW + "Click to see them")), (p, right) -> openFriends(p));
        menu.set(23, ClickMenu.item(Material.EMERALD, ChatColor.GREEN + "Rago Shop", "Ranks and cosmetics.", "",
                ChatColor.YELLOW + "Click to open"), (p, right) -> shop.open(p));
        menu.set(25, ClickMenu.item(Material.REDSTONE_COMPARATOR, ChatColor.GREEN + "Settings", "Private messages,",
                "party invites, friend and", "duel requests.", "", ChatColor.YELLOW + "Click to change"),
                (p, right) -> openSettings(p));
        if (styles != null) {
            NameStyle badge = profiles.names().badge(player.getUniqueId());
            menu.set(30, ClickMenu.item(Material.NAME_TAG, 0, ChatColor.LIGHT_PURPLE + "Name colour & badge", listOf(
                    "Your name: " + profiles.names().name(player.getUniqueId(), player.getName()),
                    "Your badge: " + (badge == null ? ChatColor.DARK_GRAY + "none" : badge.text()),
                    "", "Gradients and badges such as", "§c§lRAGO§7, §6§lBETTER§7, §4§lPVP§7 or §c§lYT§7.",
                    "", ChatColor.YELLOW + "Click to change")), (p, right) -> styles.open(p));
        }
        PlayerStats mine = profiles.of(player);
        HeadLines.Shown above = HeadLines.Shown.of(mine.headLine() == null ? "elo" : mine.headLine());
        menu.set(28, ClickMenu.item(Material.SIGN, 0, ChatColor.GREEN + "Above your name", listOf(
                "What the others read above", "your name: your ELO, level,", "wins, kills, best streak, or",
                "a message of yours (PREMIUM).", "", "Now: " + ChatColor.YELLOW + (above == null ? "Ranked ELO" : above.title()), "",
                ChatColor.YELLOW + "Click to change")), (p, right) -> openDisplay(p));
        if (levels != null) {
            menu.set(32, ClickMenu.item(Material.BREWING_STAND_ITEM, 0, ChatColor.AQUA + "Network Level & quests", listOf(
                    "Your level, the rewards of every", "5 levels, the daily and weekly", "quests.", "",
                    ChatColor.YELLOW + "Click to open")), (p, right) -> levels.open(p));
        }
        boolean ready = profiles.dailyReady(player.getUniqueId());
        menu.set(40, ClickMenu.item(ready ? Material.CHEST : Material.ENDER_CHEST, ChatColor.AQUA + "Daily reward",
                ready ? ChatColor.YELLOW + "Click to claim" : ChatColor.GRAY + "Claimed today, come back tomorrow"),
                (p, right) -> {
                    profiles.claimDaily(p);
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

    // --- Friends --------------------------------------------------------------------------------

    void openFriends(Player player) {
        List<PlayerStats> friends = social.friendsOf(player);
        ClickMenu menu = new ClickMenu(6, "Friends (" + friends.size() + ")");
        for (int i = 0; i < friends.size() && i < 45; i++) {
            final PlayerStats friend = friends.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(social.where(player, friend.id()));
            lore.add("Network level: " + stats.book().levels().tag(stats.book().levels().level(friend.xp())));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Right click to remove");
            menu.set(i, ClickMenu.head(friend.name(), profiles.displayName(friend.id(), friend.name()), lore), (p, right) -> {
                if (right) {
                    social.command(p, new String[] {"remove", friend.name()});
                    openFriends(p);
                }
            });
        }
        if (friends.isEmpty()) {
            menu.set(22, ClickMenu.item(Material.BARRIER, ChatColor.RED + "No friends yet", "/friend add <name>"), null);
        }
        menu.set(49, ClickMenu.back("My profile"), (p, right) -> open(p));
        menu.open(player);
    }

    // --- Settings -------------------------------------------------------------------------------

    void openSettings(Player player) {
        final PlayerStats profile = profiles.of(player);
        ClickMenu menu = new ClickMenu(4, "Settings");
        menu.set(10, toggle(Material.PAPER, "Private messages", profile.messages().label(),
                profile.messages() != PlayerStats.MessageSetting.NONE, "Who can /msg you."), (p, right) -> {
                    profile.setMessages(profile.messages().next());
                    saved(p, profile);
                });
        menu.set(12, toggle(Material.CAKE, "Party invites", profile.partyInvites() ? "Everyone" : "Friends only",
                profile.partyInvites(), "Who can invite you to a party."), (p, right) -> {
                    profile.setPartyInvites(!profile.partyInvites());
                    saved(p, profile);
                });
        menu.set(14, toggle(Material.SKULL_ITEM, "Friend requests", profile.friendRequests() ? "On" : "Off",
                profile.friendRequests(), "Whether players can send", "you friend requests."), (p, right) -> {
                    profile.setFriendRequests(!profile.friendRequests());
                    saved(p, profile);
                });
        menu.set(16, toggle(Material.IRON_SWORD, "Duel requests", profile.duelRequests() ? "Everyone" : "Friends only",
                profile.duelRequests(), "Who can challenge you to a duel."), (p, right) -> {
                    profile.setDuelRequests(!profile.duelRequests());
                    saved(p, profile);
                });
        menu.set(31, ClickMenu.back("My profile"), (p, right) -> open(p));
        menu.open(player);
    }

    private void saved(Player player, PlayerStats profile) {
        stats.save(profile);
        player.playSound(player.getLocation(), org.bukkit.Sound.CLICK, 1f, 1f);
        openSettings(player);
    }

    private static org.bukkit.inventory.ItemStack toggle(Material icon, String name, String value, boolean on, String... what) {
        List<String> lore = new ArrayList<>();
        for (String line : what) {
            lore.add(line);
        }
        lore.add("");
        lore.add("Now: " + (on ? ChatColor.GREEN : ChatColor.RED) + value);
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to change");
        return ClickMenu.item(icon, icon == Material.SKULL_ITEM ? 3 : 0, (on ? ChatColor.GREEN : ChatColor.RED) + name, lore);
    }
}
