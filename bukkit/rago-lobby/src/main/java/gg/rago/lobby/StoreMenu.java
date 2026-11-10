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
 * The Shop NPC of the centre of the lobby (and /pack): everything the network sells, on one page,
 * so that a player sees at once what each thing gives. The starter pack first (its price crossed
 * out, what is inside), then PREMIUM+ and its perks, the name colours and the +, the XP boosters,
 * the cosmetics, the gadgets, the events, the ranks of Rago Points, and how to earn Rago Points.
 * What is sold on the web store gives its link; the rest opens its page of the shop. Main thread.
 */
final class StoreMenu {

    static final int ROWS = 6;
    static final int PACK_SLOT = 13;
    /** The row of the things sold, left to right. */
    static final int[] ROW = {19, 20, 21, 22, 23, 24, 25};

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
                ChatColor.GOLD + String.format("%,d", profile.points()) + " Rago Points",
                "Network level: " + book.levels().tag(book.level(player.getUniqueId())),
                "",
                "Spend them in the " + ChatColor.GREEN + "/shop" + ChatColor.GRAY + ": cosmetics,",
                "name colours, the colour of your +,", "XP boosters."), null);

        menu.set(PACK_SLOT, ClickMenu.glow(ClickMenu.item(Material.CHEST, 0, pack.name + (pack.discount() > 0 ? " "
                + ChatColor.RED + ChatColor.BOLD + "-" + pack.discount() + "%" : ""), packLore(pack, profiles.ownsPack(profile)))),
                (p, right) -> {
                    p.closeInventory();
                    tellPack(p);
                });

        RankDef premium = webRank(server);
        int i = 0;
        if (premium != null) {
            boolean owned = profiles.owns(profile, premium);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.LIGHT_PURPLE + "The rank of the network:");
            for (String perk : premium.perks()) {
                lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + perk);
            }
            lore.add("");
            lore.add(owned ? ChatColor.GREEN + "You have it: thank you!" : ChatColor.GOLD + "On the web store " + ChatColor.YELLOW
                    + "(click for the link)");
            menu.set(ROW[i++], ClickMenu.glow(ClickMenu.item(premium.icon(), 0, premium.prefix().trim(), lore)), (p, right) -> {
                if (!owned) {
                    p.closeInventory();
                    tellStore(p, ChatColor.LIGHT_PURPLE + "✦ " + premium.prefix().trim() + ChatColor.GRAY + ": all its perks for good.");
                }
            });
        }
        menu.set(ROW[i++], ClickMenu.item(Material.NAME_TAG, 0, ChatColor.LIGHT_PURPLE + "Name colours & your +", list(
                "Gradient and animated names,",
                "badges after your name, and the",
                "colour of the " + ChatColor.WHITE + "+" + ChatColor.GRAY + " of [PREMIUM+].",
                "",
                "With Rago Points, levels or PREMIUM+.",
                "",
                ChatColor.YELLOW + "Click to browse")), (p, right) -> styles.open(p));
        menu.set(ROW[i++], ClickMenu.item(Material.EXP_BOTTLE, 0, ChatColor.AQUA + "XP boosters", list(
                ChatColor.WHITE + "x2 Network XP" + ChatColor.GRAY + " for 30 minutes",
                "of play: level up twice as fast.",
                "",
                ChatColor.GOLD + String.format("%,d", XpBoosters.PRICE) + " Rago Points" + ChatColor.GRAY + " each,",
                ChatColor.LIGHT_PURPLE + "[PREMIUM+]" + ChatColor.GRAY + ": one free every day.",
                "Yours: " + ChatColor.GREEN + profile.boosters(),
                "",
                ChatColor.YELLOW + "Click to see them")), (p, right) -> boosters.open(p));
        menu.set(ROW[i++], ClickMenu.item(Material.FIREWORK, 0, ChatColor.GREEN + "Cosmetics", list(
                "Projectile trails, kill effects",
                "and victory effects: everyone",
                "sees them in your games.",
                "",
                "With Rago Points, from your level.",
                ChatColor.LIGHT_PURPLE + "✦ The rarest: [PREMIUM+] only.",
                "",
                ChatColor.YELLOW + "Click to browse")), (p, right) -> shop.open(p));
        menu.set(ROW[i++], ClickMenu.item(Material.BLAZE_ROD, 0, ChatColor.LIGHT_PURPLE + "Lobby gadgets", list(
                "Bat blaster, explosive sheep",
                "and disco ball, in your hotbar",
                "in the lobby.",
                "",
                ChatColor.LIGHT_PURPLE + "With [PREMIUM+].",
                "",
                player.hasPermission(LobbyGadgets.PREMIUM_PERMISSION) ? ChatColor.GREEN + "You have them"
                        : ChatColor.YELLOW + "Click for the store")), (p, right) -> {
                            if (!p.hasPermission(LobbyGadgets.PREMIUM_PERMISSION)) {
                                p.closeInventory();
                                tellStore(p, ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GRAY + "The gadgets come with " + ChatColor.LIGHT_PURPLE
                                        + "[PREMIUM+]" + ChatColor.GRAY + ".");
                            }
                        });
        final boolean hosts = player.hasPermission("rago.perk.host");
        menu.set(ROW[i++], ClickMenu.item(Material.GOLD_SWORD, 0, ChatColor.GOLD + "Host events", list(
                "Sumo, Brackets or Last Man",
                "Standing for the whole server:",
                "everyone is told, the winner",
                "is announced and gets XP.",
                "",
                ChatColor.LIGHT_PURPLE + "With [PREMIUM+]: " + ChatColor.WHITE + "/host",
                "",
                hosts ? ChatColor.YELLOW + "Click to host one" : ChatColor.YELLOW + "Click for the store")), (p, right) -> {
                    p.closeInventory();
                    if (hosts) {
                        p.performCommand("host");
                    } else {
                        tellStore(p, ChatColor.GOLD + "Events " + ChatColor.GRAY + "are hosted by " + ChatColor.LIGHT_PURPLE + "[PREMIUM+]"
                                + ChatColor.GRAY + ": /host.");
                    }
                });
        if (!server.ranks().sold().isEmpty() && i < ROW.length) {
            menu.set(ROW[i++], ClickMenu.item(Material.DIAMOND, 0, ChatColor.GOLD + "Ranks for Rago Points", list(
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
        lore.add(ChatColor.GRAY + "Worth " + ChatColor.GOLD + String.format("%,d", pack.pointsWorth(profiles.server().cosmetics()))
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
        player.sendMessage(ChatColor.GOLD + "✪ " + pack.name + (pack.discount() > 0 ? " " + ChatColor.RED + ChatColor.BOLD + "-"
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

    /** The rank of the web store (PREMIUM), or null. */
    static RankDef webRank(ServerSettings server) {
        for (RankDef rank : server.ranks().forSale()) {
            if (!rank.sold()) {
                return rank;
            }
        }
        return null;
    }

    private static List<String> list(String... lines) {
        return new ArrayList<>(Arrays.asList(lines));
    }
}
