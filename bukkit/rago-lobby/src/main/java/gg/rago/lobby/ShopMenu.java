package gg.rago.lobby;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The Rago Shop (the emerald of the hotbar, /shop): the ranks and the cosmetics, bought with Rago
 * Points once the player reaches their Network level. Each page lists what the player owns, wears,
 * can buy, or still has to unlock. A purchase asks for a confirmation. Main thread only.
 */
final class ShopMenu {

    private final Profiles profiles;
    private final StatsBook book;
    private StyleMenu styles;

    ShopMenu(Profiles profiles, StatsBook book) {
        this.profiles = profiles;
        this.book = book;
    }

    void setStyles(StyleMenu styles) {
        this.styles = styles;
    }

    private XpBoosters boosters;

    void setBoosters(XpBoosters boosters) {
        this.boosters = boosters;
    }

    /** Opens the store menu (everything sold, the starter pack first). */
    private java.util.function.Consumer<Player> store;

    void setStore(java.util.function.Consumer<Player> store) {
        this.store = store;
    }

    /** The main page: the player's points, then the ranks and each type of cosmetic. */
    void open(Player player) {
        PlayerStats profile = profiles.of(player);
        ClickMenu menu = new ClickMenu(4, "Rago Shop");
        int level = book.level(player.getUniqueId());
        menu.set(4, ClickMenu.item(Material.EMERALD, ChatColor.GREEN + "Your Rago Points",
                ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", profile.points()) + " Rago Points",
                "",
                "Network level: " + book.levels().tag(level),
                "",
                "Earn points in every game:",
                ChatColor.WHITE + "+" + profiles.server().points().win + ChatColor.GRAY + " a win, "
                        + ChatColor.WHITE + "+" + profiles.server().points().loss + ChatColor.GRAY + " a game played,",
                ChatColor.WHITE + "+" + profiles.server().points().kill + ChatColor.GRAY + " a kill, and more each level."), null);
        List<String> rankLore = new ArrayList<>();
        rankLore.add("The ranks of the network and");
        rankLore.add("what they give.");
        rankLore.add("");
        // one under the other: the highest on top, VIP under PREMIUM+
        List<RankDef> listed = new ArrayList<>(profiles.server().ranks().forSale());
        java.util.Collections.reverse(listed);
        for (RankDef rank : listed) {
            rankLore.add(rank.prefix().trim() + ChatColor.GRAY + (rank.sold() ? " - level " + rank.level()
                    : rank.webPrice() != null ? " - " + rank.webPrice() + " on the web store" : " - web store"));
        }
        rankLore.add("");
        rankLore.add(ChatColor.YELLOW + "Click to browse");
        menu.set(10, ClickMenu.item(Material.GOLD_INGOT, 0, ChatColor.GOLD + "Ranks", rankLore), (p, right) -> openRanks(p));
        int slot = 12;
        for (final Cosmetic.Type type : Cosmetic.Type.values()) {
            List<String> lore = new ArrayList<>();
            for (String line : type.description()) {
                lore.add(line);
            }
            lore.add("");
            int owned = 0;
            List<Cosmetic> all = profiles.server().cosmetics(type);
            for (Cosmetic cosmetic : all) {
                if (profiles.owns(player, cosmetic)) {
                    owned++;
                }
            }
            lore.add("Unlocked: " + ChatColor.GREEN + owned + ChatColor.GRAY + "/" + all.size());
            Cosmetic worn = profiles.equipped(player.getUniqueId(), type);
            lore.add("Equipped: " + (worn == null ? ChatColor.DARK_GRAY + "nothing" : ChatColor.YELLOW + worn.name()));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to browse");
            menu.set(slot, ClickMenu.item(type.icon(), 0, ChatColor.GREEN + type.title(), lore), (p, right) -> openType(p, type));
            slot += 2;
        }
        List<RankDef> giftable = profiles.server().ranks().giftable();
        if (!giftable.isEmpty()) {
            List<String> lore = new ArrayList<>();
            lore.add("Pick a player, then the rank:");
            for (RankDef rank : giftable) {
                lore.add(rank.prefix().trim() + ChatColor.GRAY + " - " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", rank.giftPrice())
                        + " Rago Points");
            }
            lore.add("");
            lore.add("They accept it in a book (YES or");
            lore.add("NO); you pay if they say YES.");
            lore.add("The whole lobby hears it,");
            lore.add("with fireworks!");
            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + "For PREMIUM and above");
            lore.add(ChatColor.YELLOW + "Click to choose a player");
            menu.set(27, ClickMenu.item(Material.FIREWORK_CHARGE, 0, ChatColor.GOLD + "Gift a rank", lore),
                    (p, right) -> openGiftTargets(p));
        }
        if (styles != null) {
            menu.set(29, ClickMenu.item(Material.NAME_TAG, 0, ChatColor.LIGHT_PURPLE + "Name colours & badges", listOf(
                    "Gradient names, and badges after",
                    "your name: §c§lRAGO§7, §6§lBETTER§7, §4§lPVP§7...",
                    "", ChatColor.YELLOW + "Click to browse")), (p, right) -> styles.open(p));
        }
        boolean ready = profiles.dailyReady(player.getUniqueId());
        int next = profiles.server().points().dailyReward(
                Profiles.nextStreak(profile.dailyDay(), profile.dailyStreak(), StatsPeriod.dayKey(System.currentTimeMillis()),
                        StatsPeriod.previousDayKey(System.currentTimeMillis())));
        menu.set(31, ClickMenu.item(ready ? Material.CHEST : Material.ENDER_CHEST, ChatColor.AQUA + "Daily reward",
                ready ? ChatColor.GOLD + "+" + next + " Rago Points" : ChatColor.GRAY + "Claimed today",
                "",
                "A few days in a row give more.",
                "Streak: " + ChatColor.WHITE + profile.dailyStreak() + " days",
                "",
                ready ? ChatColor.YELLOW + "Click to claim" : ChatColor.GRAY
                        + StatsPeriod.DAY.resetText(System.currentTimeMillis()).replace("Resets in", "Again in")),
                (p, right) -> {
                    profiles.claimDaily(p);
                    open(p);
                });
        if (boosters != null) {
            long left = profile.boosterLeftMs();
            menu.set(33, ClickMenu.item(Material.EXP_BOTTLE, Brand.XP + "Boosters",
                    Brand.XP + "x3 Network XP" + ChatColor.GRAY + " for 30 minutes", "of play, for " + ChatColor.GOLD
                            + String.format(java.util.Locale.US, "%,d", XpBoosters.PRICE) + " Rago Points" + ChatColor.GRAY + ".",
                    ChatColor.GOLD + "x3 Rago Points" + ChatColor.GRAY + ": the points boosters.",
                    ChatColor.LIGHT_PURPLE + "[PREMIUM+]" + ChatColor.GRAY + ": a free x2 XP one every day.",
                    "",
                    "Yours: " + ChatColor.GREEN + (profile.boosters() + profile.dailyBoosters()) + " XP, " + profile.coinBoosters()
                            + " points" + (left > 0 ? ChatColor.GRAY + ", one running (" + ChatColor.AQUA + XpBoosters.clock(left)
                            + ChatColor.GRAY + ")" : ""),
                    "",
                    ChatColor.YELLOW + "Click to see them"), (p, right) -> boosters.open(p));
        }
        if (store != null) {
            StarterPack pack = profiles.server().starterPack();
            menu.set(35, ClickMenu.glow(ClickMenu.item(Material.CHEST, 0, pack.name + (pack.discount() > 0 ? " " + ChatColor.RED
                    + ChatColor.BOLD + "-" + pack.discount() + "%" : ""), listOf(
                    "Rago Points, XP boosters and",
                    "cosmetics, once per player.",
                    "Price: " + pack.priceLine(),
                    "",
                    ChatColor.YELLOW + "Click to see it, with VIP"))), (p, right) -> store.accept(p));
        }
        menu.open(player);
    }

    // --- Ranks ----------------------------------------------------------------------------------

    void openRanks(Player player) {
        List<RankDef> sold = profiles.server().ranks().forSale();
        ClickMenu menu = new ClickMenu(Math.min(6, 2 + (sold.size() + 6) / 7), "Shop - Ranks");
        PlayerStats profile = profiles.of(player);
        int level = book.level(player.getUniqueId());
        for (int i = 0; i < sold.size(); i++) {
            final RankDef rank = sold.get(i);
            List<String> lore = new ArrayList<>();
            for (String perk : rank.perks()) {
                lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + perk);
            }
            lore.add("");
            final boolean owned = profiles.owns(profile, rank);
            if (!rank.sold()) {
                lore.add(ChatColor.GOLD + "On the web store" + (rank.webPrice() != null ? ": " + ChatColor.GREEN + ChatColor.BOLD
                        + rank.webPrice() : ""));
                if (rank.giftable()) {
                    lore.add("Gift it to a player: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", rank.giftPrice()) + " Rago Points");
                    lore.add(ChatColor.GRAY + "(PREMIUM and above: /gift <player>)");
                }
                lore.add("");
                lore.add(owned ? ChatColor.GREEN + "You have it" : ChatColor.YELLOW + "Click for the link");
                menu.set(slotOf(i), ClickMenu.item(rank.icon(), 0, rank.prefix().trim(), lore), (p, right) -> {
                    p.closeInventory();
                    List<String> store = profiles.server().link("boutique");
                    for (String line : store == null ? listOf(ChatColor.GRAY + "The store opens soon.") : store) {
                        p.sendMessage(line);
                    }
                });
                continue;
            }
            lore.add("Price: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", rank.price()) + " Rago Points");
            lore.add("Level needed: " + book.levels().tag(rank.level()));
            lore.add("");
            if (owned) {
                lore.add(ChatColor.GREEN + "You have it");
            } else if (level < rank.level()) {
                lore.add(ChatColor.RED + "Unlocks at level " + rank.level());
            } else if (profile.points() < rank.price()) {
                lore.add(ChatColor.RED + "Not enough Rago Points");
            } else {
                lore.add(ChatColor.YELLOW + "Click to buy");
            }
            menu.set(slotOf(i), ClickMenu.item(rank.icon(), 0, rank.prefix().trim(), lore), (p, right) -> {
                if (!owned) {
                    confirm(p, rank.prefix().trim(), rank.price(), () -> profiles.buyRank(p, rank), () -> openRanks(p));
                }
            });
        }
        menu.set(menu.size() - 5, ClickMenu.back("the Rago Shop"), (p, right) -> open(p));
        menu.open(player);
    }

    /** The players online to gift a rank to, a head each; /gift reaches the others. */
    void openGiftTargets(Player player) {
        if (noGift(player)) {
            return;
        }
        List<Player> online = new ArrayList<>();
        for (Player other : player.getServer().getOnlinePlayers()) {
            if (!other.equals(player) && player.canSee(other)) {
                online.add(other);
            }
        }
        online.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        int shown = Math.min(online.size(), GIFT_HEADS);
        ClickMenu menu = new ClickMenu(Math.max(2, Math.min(6, 1 + (shown + 8) / 9)), "Gift a rank to...");
        for (int i = 0; i < shown; i++) {
            final String name = online.get(i).getName();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Click to choose the rank");
            menu.set(i, ClickMenu.head(name, profiles.styledName(online.get(i).getUniqueId(), name), lore),
                    (p, right) -> p.performCommand("gift " + name));
        }
        if (shown == 0) {
            menu.set(4, ClickMenu.item(Material.PAPER, ChatColor.GRAY + "Nobody else is online", "/gift <player> gifts a rank",
                    "to anyone who played here."), null);
        } else if (online.size() > shown) {
            menu.set(menu.size() - 9, ClickMenu.item(Material.PAPER, ChatColor.GRAY + "And " + (online.size() - shown) + " more",
                    "/gift <player> for them."), null);
        }
        menu.set(menu.size() - 5, ClickMenu.back("the Rago Shop"), (p, right) -> open(p));
        menu.open(player);
    }

    /** The heads of a page of players to gift: 5 rows, the last one for the buttons. */
    static final int GIFT_HEADS = 45;

    /** True if the player's rank lets them gift ranks (PREMIUM and above); else they are told so. */
    private static boolean mayGift(Player player) {
        if (player.hasPermission(GiftRequests.PERMISSION)) {
            return true;
        }
        player.closeInventory();
        player.sendMessage(ChatColor.RED + "Gifting a rank is for " + ChatColor.LIGHT_PURPLE + "PREMIUM" + ChatColor.RED
                + " and above.");
        return false;
    }

    /** True (and the player told where to go) when no rank can be gifted with Rago Points. */
    private boolean noGift(Player player) {
        if (!profiles.server().ranks().giftable().isEmpty()) {
            return false;
        }
        player.sendMessage(ChatColor.GRAY + "No rank can be gifted for Rago Points. The ranks of the web store can be gifted there:");
        List<String> store = profiles.server().link("boutique");
        for (String line : store == null ? listOf(ChatColor.GRAY + "The store opens soon.") : store) {
            player.sendMessage(line);
        }
        return true;
    }

    /** The ranks to gift another player (/gift, the shop, or the menu of a player clicked in the lobby). */
    void openGift(Player player, PlayerStats target) {
        if (noGift(player) || !mayGift(player)) {
            return;
        }
        List<RankDef> sold = profiles.server().ranks().giftable();
        ClickMenu menu = new ClickMenu(Math.min(6, 2 + (sold.size() + 6) / 7), "Gift a rank: " + target.name());
        PlayerStats profile = profiles.of(player);
        int level = book.level(player.getUniqueId());
        for (int i = 0; i < sold.size(); i++) {
            final RankDef rank = sold.get(i);
            List<String> lore = new ArrayList<>();
            for (String perk : rank.perks()) {
                lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + perk);
            }
            lore.add("");
            lore.add("Price: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", rank.giftPrice()) + " Rago Points");
            if (rank.level() > 0) {
                lore.add("Your level needed: " + book.levels().tag(rank.level()));
            }
            lore.add("");
            lore.add(target.name() + " accepts it in a book:");
            lore.add("you pay if they say YES.");
            lore.add("The whole lobby hears it,");
            lore.add("with fireworks!");
            lore.add("");
            final boolean owned = profiles.owns(target, rank);
            if (owned) {
                lore.add(ChatColor.GREEN + target.name() + " has it");
            } else if (level < rank.level()) {
                lore.add(ChatColor.RED + "Unlocks at level " + rank.level());
            } else if (profile.points() < rank.giftPrice()) {
                lore.add(ChatColor.RED + "Not enough Rago Points");
            } else {
                lore.add(ChatColor.YELLOW + "Click to offer it to " + target.name());
            }
            menu.set(slotOf(i), ClickMenu.item(rank.icon(), 0, rank.prefix().trim(), lore), (p, right) -> {
                if (!owned) {
                    confirm(p, rank.prefix().trim() + ChatColor.GREEN + " for " + target.name(), rank.giftPrice(),
                            () -> profiles.offerGift(p, target, rank), () -> openGift(p, target));
                }
            });
        }
        menu.set(menu.size() - 5, ClickMenu.item(Material.BARRIER, ChatColor.RED + "Close"), (p, right) -> p.closeInventory());
        menu.open(player);
    }

    // --- Cosmetics ------------------------------------------------------------------------------

    void openType(Player player, final Cosmetic.Type type) {
        List<Cosmetic> all = profiles.server().cosmetics(type);
        ClickMenu menu = new ClickMenu(Math.min(6, 2 + (all.size() + 6) / 7), "Shop - " + type.title());
        PlayerStats profile = profiles.of(player);
        int level = book.level(player.getUniqueId());
        String worn = profile.equipped(type);
        for (int i = 0; i < all.size(); i++) {
            final Cosmetic cosmetic = all.get(i);
            final boolean owned = profiles.owns(player, cosmetic);
            boolean equipped = owned && cosmetic.id().equals(worn);
            List<String> lore = new ArrayList<>();
            lore.add(type.title().substring(0, type.title().length() - 1));
            lore.add("");
            if (cosmetic.premium()) {
                lore.add(ChatColor.LIGHT_PURPLE + "✦ One of the rarest: [PREMIUM+] only");
                lore.add("");
            } else if (!owned) {
                lore.add("Price: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", cosmetic.price()) + " Rago Points");
                lore.add("Level needed: " + book.levels().tag(cosmetic.level()));
                lore.add("");
            }
            if (equipped) {
                lore.add(ChatColor.GREEN + "Equipped " + ChatColor.GRAY + "(click to take off)");
            } else if (owned) {
                lore.add(ChatColor.YELLOW + "Click to equip");
            } else if (cosmetic.premium()) {
                lore.add(ChatColor.RED + "Locked " + ChatColor.GRAY + "(click for the store)");
            } else if (level < cosmetic.level()) {
                lore.add(ChatColor.RED + "Unlocks at level " + cosmetic.level());
            } else if (profile.points() < cosmetic.price()) {
                lore.add(ChatColor.RED + "Not enough Rago Points");
            } else {
                lore.add(ChatColor.YELLOW + "Click to buy");
            }
            String name = (equipped ? ChatColor.GREEN : owned ? ChatColor.YELLOW : ChatColor.WHITE) + cosmetic.name()
                    + (cosmetic.premium() ? " " + ChatColor.LIGHT_PURPLE + "✦" : "");
            menu.set(slotOf(i), ClickMenu.item(cosmetic.icon(), 0, name, lore), (p, right) -> {
                if (owned) {
                    profiles.equip(p, cosmetic);
                    openType(p, type);
                } else if (cosmetic.premium()) {
                    p.closeInventory();
                    profiles.buyCosmetic(p, cosmetic);
                } else {
                    confirm(p, cosmetic.name(), cosmetic.price(), () -> profiles.buyCosmetic(p, cosmetic),
                            () -> openType(p, type));
                }
            });
        }
        menu.set(menu.size() - 5, ClickMenu.back("the Rago Shop"), (p, right) -> open(p));
        menu.open(player);
    }

    /** Asks before spending: green to buy, red to go back. */
    private void confirm(Player player, String what, int price, Runnable buy, Runnable back) {
        String why = profiles.cannotBuy(player, price, 0);
        if (why != null) {
            player.sendMessage(ChatColor.RED + why);
            return;
        }
        ClickMenu menu = new ClickMenu(3, "Confirm the purchase");
        menu.set(11, ClickMenu.item(Material.STAINED_CLAY, 5, ChatColor.GREEN + "Buy " + what, listOf(
                "Price: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", price) + " Rago Points",
                "", ChatColor.YELLOW + "Click to confirm")), (p, right) -> {
                    buy.run();
                    back.run();
                });
        menu.set(15, ClickMenu.item(Material.STAINED_CLAY, 14, ChatColor.RED + "Cancel", listOf("Back to the shop")),
                (p, right) -> back.run());
        menu.open(player);
    }

    private static List<String> listOf(String... lines) {
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            list.add(line);
        }
        return list;
    }

    /** The i-th item of a page: seven per row, from the second row, away from the borders. */
    static int slotOf(int i) {
        return 10 + (i / 7) * 9 + i % 7;
    }
}
