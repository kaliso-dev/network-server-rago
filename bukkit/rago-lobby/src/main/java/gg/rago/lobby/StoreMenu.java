package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The Shop NPC of the centre of the lobby (and /pack): the store of a player who starts, on one
 * page. The starter pack first (what is inside, its price), then the lowest rank of the web store
 * (VIP) and its perks, the name colours, the XP boosters and the cosmetics that Rago Points buy,
 * the ranks of Rago Points, and how to earn Rago Points. PREMIUM+ is in the /shop, with the ranks.
 * What is sold on the web store gives its link; the rest opens its page of the shop. Main thread.
 */
final class StoreMenu {

    static final int ROWS = 6;
    static final int PACK_SLOT = 13;
    /** The row of the things sold, left to right: VIP, then what Rago Points buy. */
    static final int[] ROW = {19, 21, 23, 25};
    /** The ranks for Rago Points, if any, between how to earn them and the website. */
    static final int RANKS_FOR_POINTS_SLOT = 40;

    private final Profiles profiles;
    private final StatsBook book;
    private final ShopMenu shop;
    private final StyleMenu styles;
    private final XpBoosters boosters;

    StoreMenu(Profiles profiles, StatsBook book, ShopMenu shop, StyleMenu styles, XpBoosters boosters) {
        this.profiles = profiles;
        this.book = book;
        this.shop = shop;
        this.styles = styles;
        this.boosters = boosters;
    }

    void open(Player player) {
        ServerSettings server = profiles.server();
        StarterPack pack = server.starterPack();
        PlayerStats profile = profiles.of(player);
        ClickMenu menu = new ClickMenu(ROWS, "Rago Store");
        for (int slot = 0; slot < ROWS * 9; slot++) {
            if (slot < 9 || slot >= (ROWS - 1) * 9 || slot % 9 == 0 || slot % 9 == 8) {
                menu.set(slot, ClickMenu.pane(), null);
            }
        }
        menu.set(4, ClickMenu.item(Material.EMERALD, ChatColor.GREEN + "Your Rago Points",
                ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", profile.points()) + " Rago Points",
                "Network level: " + book.levels().tag(book.level(player.getUniqueId())),
                "",
                "Spend them in the " + ChatColor.GREEN + "/shop" + ChatColor.GRAY + ": cosmetics,",
                "name colours and XP boosters."), null);

        menu.set(PACK_SLOT, ClickMenu.glow(ClickMenu.item(Material.CHEST, 0, pack.name + (pack.discount() > 0 ? " "
                + ChatColor.RED + ChatColor.BOLD + "-" + pack.discount() + "%" : ""), packLore(pack, profiles.ownsPack(profile)))),
                (p, right) -> {
                    p.closeInventory();
                    tellPack(p);
                });

        int i = 0;
        List<RankDef> web = webRanks(server);
        // The lowest rank only (VIP): PREMIUM+ is in the /shop, with the ranks. The rest of the row
        // is what Rago Points buy.
        final RankDef vip = web.isEmpty() ? null : web.get(0);
        if (vip != null) {
            boolean owned = profiles.owns(profile, vip);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GREEN + "To start supporting the network:");
            for (String perk : vip.perks()) {
                lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + perk);
            }
            lore.add("");
            if (vip.webPrice() != null) {
                lore.add(ChatColor.GRAY + "Price: " + ChatColor.GREEN + ChatColor.BOLD + vip.webPrice());
            }
            lore.add(owned ? ChatColor.GREEN + "You have it: thank you!" : ChatColor.GOLD + "On the web store " + ChatColor.YELLOW
                    + "(click for the link)");
            menu.set(ROW[i++], ClickMenu.item(vip.icon(), 0, vip.prefix().trim(), lore), (p, right) -> {
                if (!owned) {
                    p.closeInventory();
                    tellStore(p, vip.nameColour() + "✦ " + vip.prefix().trim() + ChatColor.GRAY + ": all its perks for good.");
                }
            });
        }
        List<String> colours = list(
                "Gradient and animated names",
                "and badges after your name.",
                "",
                "With Rago Points or your level.");
        if (vip != null && vip.style() != null) {
            colours.add(vip.prefix().trim() + ChatColor.GRAY + ": a colour of its own.");
        }
        colours.add("");
        colours.add(ChatColor.YELLOW + "Click to browse");
        menu.set(ROW[i++], ClickMenu.item(Material.NAME_TAG, 0, ChatColor.LIGHT_PURPLE + "Name colours", colours),
                (p, right) -> styles.open(p));
        menu.set(ROW[i++], ClickMenu.item(Material.EXP_BOTTLE, 0, Brand.XP + "Boosters", list(
                Brand.XP + "x3 Network XP" + ChatColor.GRAY + " for 30 minutes",
                "of play: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", XpBoosters.PRICE) + " Rago Points" + ChatColor.GRAY + " each.",
                ChatColor.GOLD + "x3 Rago Points" + ChatColor.GRAY + ": the points boosters,",
                "in the starter pack.",
                "Yours: " + ChatColor.GREEN + (profile.boosters() + profile.dailyBoosters()) + " XP, " + profile.coinBoosters() + " points",
                "",
                ChatColor.YELLOW + "Click to see them")), (p, right) -> boosters.open(p));
        List<String> cosmetics = list(
                "Projectile trails, kill effects",
                "and victory effects: everyone",
                "sees them in your games.",
                "",
                "With Rago Points, from your level.");
        if (!pack.cosmetics.isEmpty()) {
            cosmetics.add(ChatColor.GOLD + "The starter pack: " + pack.cosmetics.size() + " of them.");
        }
        cosmetics.add("");
        cosmetics.add(ChatColor.YELLOW + "Click to browse");
        menu.set(ROW[i++], ClickMenu.item(Material.FIREWORK, 0, ChatColor.GREEN + "Cosmetics", cosmetics), (p, right) -> shop.open(p));
        if (!server.ranks().sold().isEmpty()) {
            menu.set(RANKS_FOR_POINTS_SLOT, ClickMenu.item(Material.DIAMOND, 0, ChatColor.GOLD + "Ranks for Rago Points", list(
                    "Ranks you unlock with your level",
                    "and buy with Rago Points.",
                    "",
                    ChatColor.YELLOW + "Click to browse")), (p, right) -> shop.openRanks(p));
        }

        menu.set(39, ClickMenu.item(Material.GOLD_INGOT, 0, ChatColor.GOLD + "Earn Rago Points", list(
                ChatColor.WHITE + "+" + server.points().win + ChatColor.GRAY + " a win, " + ChatColor.WHITE + "+" + server.points().loss
                        + ChatColor.GRAY + " a game,",
                ChatColor.WHITE + "+" + server.points().kill + ChatColor.GRAY + " a kill, more at each level,",
                "the daily reward (" + ChatColor.WHITE + "/daily" + ChatColor.GRAY + ") and",
                "the quests (" + ChatColor.WHITE + "/quests" + ChatColor.GRAY + ").",
                "",
                ChatColor.YELLOW + "Click for the shop")), (p, right) -> shop.open(p));
        menu.set(41, ClickMenu.item(Material.EYE_OF_ENDER, 0, ChatColor.AQUA + "" + ChatColor.BOLD + "Rago Optimizer", list(
                "News, downloads and the store",
                "of the network on the website.",
                "",
                ChatColor.YELLOW + "Click for the link")), (p, right) -> {
                    p.closeInventory();
                    tellStore(p, ChatColor.AQUA + "" + ChatColor.BOLD + "Rago Optimizer" + ChatColor.GRAY + ": news, downloads and the store.");
                });
        menu.set(49, ClickMenu.item(Material.BARRIER, ChatColor.RED + "Close"), (p, right) -> p.closeInventory());
        menu.open(player);
        player.playSound(player.getLocation(), Sound.CHEST_OPEN, 0.6f, 1.2f);
    }

    /** The lore of the starter pack: the saving, what is inside, the price, once per player. */
    List<String> packLore(StarterPack pack, boolean owned) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Everything to start, once per player:");
        lore.add("");
        lore.addAll(pack.contents(profiles.server().cosmetics()));
        if (!pack.cosmetics.isEmpty()) {
            lore.add(ChatColor.GRAY + "  (the cosmetics without their level)");
        }
        lore.add("");
        lore.add(ChatColor.GRAY + "Worth " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", pack.pointsWorth(profiles.server().cosmetics()))
                + " Rago Points" + ChatColor.GRAY + " in the shop.");
        lore.add(ChatColor.GRAY + "Price: " + pack.priceLine());
        lore.add("");
        lore.add(owned ? ChatColor.GREEN + "You have it: thank you!" : ChatColor.YELLOW + "Click for the store");
        return lore;
    }

    /** /pack, or a click on the pack: what is inside and the link, in the chat. */
    void tellPack(Player player) {
        StarterPack pack = profiles.server().starterPack();
        if (profiles.ownsPack(profiles.of(player))) {
            player.sendMessage(ChatColor.GREEN + "You have the " + pack.name + ChatColor.GREEN + " already: thank you!");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "\u2726 " + pack.name + (pack.discount() > 0 ? " " + ChatColor.RED + ChatColor.BOLD + "-"
                + pack.discount() + "%" : "") + ChatColor.GRAY + ": " + pack.priceLine());
        for (String line : pack.contents(profiles.server().cosmetics())) {
            player.sendMessage("  " + line);
        }
        button(player, pack);
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1f, 1.2f);
    }

    /** A line about what is sold, then the store's link to click. */
    void tellStore(Player player, String line) {
        player.sendMessage(line);
        List<String> store = profiles.server().link("boutique");
        if (store != null) {
            for (String l : store) {
                player.sendMessage(l);
            }
        }
        button(player, profiles.server().starterPack());
    }

    private static void button(Player player, StarterPack pack) {
        // clickable through the vanilla /tellraw, as the duel buttons: names are letters, digits and _
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + pack.button());
    }

    /** The ranks of the web store, the lowest first (VIP, then PREMIUM+), as the shop shows them. */
    static List<RankDef> webRanks(ServerSettings server) {
        List<RankDef> ranks = new ArrayList<>();
        for (RankDef rank : server.ranks().forSale()) {
            if (!rank.sold()) {
                ranks.add(rank);
            }
        }
        return ranks;
    }

    private static List<String> list(String... lines) {
        return new ArrayList<>(Arrays.asList(lines));
    }
}
