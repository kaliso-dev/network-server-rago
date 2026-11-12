package gg.rago.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The colours of name and the badges (/style, the profile, the shop): how the player looks in the
 * chat, then every colour, those they own to wear (a click), the others with how to get them: the
 * PREMIUM rank, the store, Rago Points or a Network level. The badges (with those of the ranked
 * seasons the player ended) and the colours of the + of [PREMIUM+] have their own pages. Main
 * thread only.
 */
final class StyleMenu {

    /** The wool of each colour character, 0 to f: the colour of a name on an item. */
    private static final int[] WOOL = {15, 11, 13, 9, 14, 10, 1, 8, 7, 11, 5, 3, 14, 2, 4, 0};
    /** The four rows of seven. */
    private static final int LAST = 28;
    /** The badges, at the bottom of the first page. */
    static final int BADGES_SLOT = 52;

    private final Profiles profiles;
    private final StatsBook book;
    /** The Network level whose reward is this style (by key), or 0. */
    private ToIntFunction<String> rewardLevel = key -> 0;
    private ClickMenu.Action back;

    StyleMenu(Profiles profiles, StatsBook book) {
        this.profiles = profiles;
        this.book = book;
    }

    void setRewardLevel(ToIntFunction<String> rewardLevel) {
        this.rewardLevel = rewardLevel;
    }

    /** Where the arrow at the bottom goes (the profile), or null for none. */
    void setBack(ClickMenu.Action back) {
        this.back = back;
    }

    /** The wool data of a colour character ("d" is magenta), white for anything else. */
    static int wool(char colour) {
        int index = Character.digit(colour, 16);
        return index < 0 ? 0 : WOOL[index];
    }

    void open(Player player) {
        NameLooks names = profiles.names();
        ServerSettings server = names.server();
        UUID id = player.getUniqueId();
        PlayerStats profile = profiles.of(player);
        RankDef rank = names.rank(id);
        ClickMenu menu = new ClickMenu(6, "Name colour & badge");
        String level = book.levels().tag(book.level(id)) + " ";
        menu.set(4, ClickMenu.item(Material.NAME_TAG, 0, ChatColor.GREEN + "Your look", listOf(
                "In the chat:",
                level + names.display(id, player.getName()) + names.badgeAfter(id) + rank.chatColour() + ": Hello!",
                "",
                "In the tab list:",
                level + names.styled(id, player.getName()) + names.badgeAfter(id),
                "",
                "Above your head:",
                names.headPrefix(id) + player.getName() + LobbyScoreboard.suffix(names.badgeAfter(id),
                        " " + book.levels().tag(book.level(id))),
                "",
                "Colours of name below: click one",
                "you own to wear it. The badges",
                "and the colour of your + are at",
                "the bottom.")), null);

        NameStyle colour = names.colour(id);
        String plain = rank.nameColour();
        menu.set(ShopMenu.slotOf(0), ClickMenu.item(Material.WOOL, wool(plain.length() > 1 ? plain.charAt(1) : 'f'),
                plain + "Colour of your rank", listOf("Colour of name", "", "Your name: " + plain + player.getName(), "",
                        colour == null ? ChatColor.GREEN + "Worn" : ChatColor.YELLOW + "Click to wear")),
                (p, right) -> {
                    profiles.wear(p, NameStyle.Kind.COLOUR, null);
                    open(p);
                });
        int i = 1;
        for (NameStyle style : server.styles(NameStyle.Kind.COLOUR)) {
            if (i >= LAST) {
                break;
            }
            boolean owned = names.owns(profile, style);
            List<String> lore = listOf("Colour of name" + (style.animated() ? ChatColor.LIGHT_PURPLE + " (animated)"
                    : style.text().length() > 1 ? ChatColor.DARK_GRAY + " (fixed)" : ""), "", "Your name: "
                    + style.apply(player.getName()), "");
            lore.add(how(style));
            lore.add("");
            lore.add(style == colour ? ChatColor.GREEN + "Worn " + ChatColor.GRAY + "(click to take off)"
                    : owned ? ChatColor.YELLOW + "Click to wear" : style.obtain() == NameStyle.Obtain.POINTS
                    ? ChatColor.YELLOW + "Click to buy" : ChatColor.RED + "Locked");
            char main = style.mainColour().charAt(1);
            menu.set(ShopMenu.slotOf(i++), ClickMenu.item(owned ? Material.WOOL : Material.STAINED_GLASS_PANE, wool(main),
                    style.sample(), lore), (p, right) -> pick(p, style, owned));
        }

        NameStyle badge = names.badge(id);
        menu.set(BADGES_SLOT, ClickMenu.item(Material.NAME_TAG, 0, ChatColor.YELLOW + "Badges", listOf(
                "A short word after your name, in",
                "the chat, the tab list and above",
                "your head.",
                "",
                "Yours: " + (badge == null ? ChatColor.DARK_GRAY + "none" : badge.text()),
                "",
                ChatColor.YELLOW + "Click to choose")), (p, right) -> openBadges(p));

        NameStyle plus = names.plus(id);
        menu.set(46, ClickMenu.item(Material.NETHER_STAR, 0, ChatColor.LIGHT_PURPLE + "Colour of your " + names.plusColour(id) + "+",
                listOf("The + of " + Ranks.premium().prefix(names.plusColour(id)).trim() + ChatColor.GRAY + ", in the chat,",
                        "the tab list and above your head.", "",
                        "Yours: " + (plus == null ? ChatColor.WHITE + "White" : plus.sample()),
                        rank.hasPlus() ? "" : ChatColor.LIGHT_PURPLE + "It shows with [PREMIUM+].", "",
                        ChatColor.YELLOW + "Click to choose")), (p, right) -> openPlus(p));
        List<String> store = server.link("boutique");
        menu.set(50, ClickMenu.item(Material.GOLD_INGOT, 0, ChatColor.GOLD + "More colours and badges", listOf(
                "The gradients and badges marked",
                "\"store\" are on the web store.",
                "",
                ChatColor.YELLOW + "Click for the link")), (p, right) -> {
                    p.closeInventory();
                    tellStore(p, store);
                });
        if (back != null) {
            menu.set(48, ClickMenu.back("your profile"), back);
        }
        menu.open(player);
    }

    /**
     * The badges: none, those of the ranked seasons the player ended (the latest first, "§bS2
     * Diamond"), then those of config.yml, owned or not.
     */
    void openBadges(Player player) {
        NameLooks names = profiles.names();
        UUID id = player.getUniqueId();
        PlayerStats profile = profiles.of(player);
        NameStyle badge = names.badge(id);
        String name = names.prefix(id) + names.name(id, player.getName());
        ClickMenu menu = new ClickMenu(6, "Badges");
        EloLadder ladder = book.ladder();
        String example = NameLooks.seasonBadge(names.server().season().number(), ladder.divisions().get(
                Math.min(3, ladder.divisions().size() - 1)).name(), ladder).text();
        menu.set(4, ClickMenu.item(Material.NAME_TAG, 0, ChatColor.GREEN + "Your badge", listOf(
                name + names.badgeAfter(id),
                "",
                "After your name, in the chat,",
                "the tab list and above your head.",
                "",
                "End a ranked season in a division",
                "to get its badge: " + example + ChatColor.GRAY + ".")), null);
        menu.set(ShopMenu.slotOf(0), ClickMenu.item(Material.BARRIER, 0, ChatColor.GRAY + "No badge",
                listOf("Badge", "", badge == null ? ChatColor.GREEN + "Worn" : ChatColor.YELLOW + "Click to take your badge off")),
                (p, right) -> {
                    profiles.wear(p, NameStyle.Kind.BADGE, null);
                    openBadges(p);
                });
        List<NameStyle> badges = names.seasonBadges(profile);
        badges.addAll(profiles.server().styles(NameStyle.Kind.BADGE));
        int i = 1;
        for (NameStyle style : badges) {
            if (i >= LAST) {
                break;
            }
            boolean owned = names.owns(profile, style);
            boolean worn = badge != null && badge.key().equals(style.key());
            List<String> lore = listOf("Badge, after your name", "", name + " " + style.text(), "");
            lore.add(how(style));
            lore.add("");
            lore.add(worn ? ChatColor.GREEN + "Worn " + ChatColor.GRAY + "(click to take off)"
                    : owned ? ChatColor.YELLOW + "Click to wear" : ChatColor.RED + "Locked");
            org.bukkit.inventory.ItemStack item = ClickMenu.item(owned ? Material.NAME_TAG : Material.PAPER, 0, style.text(), lore);
            menu.set(ShopMenu.slotOf(i++), worn ? ClickMenu.glow(item) : item, (p, right) -> pick(p, style, owned));
        }
        menu.set(49, ClickMenu.back("your colours and badges"), (p, right) -> open(p));
        menu.open(player);
    }

    /**
     * The colours of the + of [PREMIUM+]: white (the default), the bright ones won with the Network
     * levels, and black, the rarest, bought with Rago Points.
     */
    void openPlus(Player player) {
        NameLooks names = profiles.names();
        UUID id = player.getUniqueId();
        PlayerStats profile = profiles.of(player);
        NameStyle worn = names.plus(id);
        String name = names.name(id, player.getName());
        ClickMenu menu = new ClickMenu(4, "Colour of your +");
        menu.set(4, ClickMenu.item(Material.NETHER_STAR, 0, ChatColor.LIGHT_PURPLE + "Your +", listOf(
                Ranks.premium().prefix(names.plusColour(id)) + name, "",
                "White by default, bright colours",
                "won with the Network levels, and",
                ChatColor.BLACK + "" + ChatColor.BOLD + "+" + ChatColor.GRAY + " black, the rarest of all.",
                names.rank(id).hasPlus() ? "" : ChatColor.LIGHT_PURPLE + "It shows with [PREMIUM+].")), null);
        menu.set(ShopMenu.slotOf(0), ClickMenu.item(Material.WOOL, 0, ChatColor.WHITE + "+ White", listOf("Colour of +", "",
                Ranks.premium().prefix(RankDef.DEFAULT_PLUS) + name, "", "The colour of every [PREMIUM+]", "",
                worn == null ? ChatColor.GREEN + "Worn" : ChatColor.YELLOW + "Click to wear")), (p, right) -> {
                    profiles.wear(p, NameStyle.Kind.PLUS, null);
                    openPlus(p);
                });
        int i = 1;
        for (NameStyle style : profiles.server().styles(NameStyle.Kind.PLUS)) {
            if (i >= 14) {
                break;
            }
            boolean owned = names.owns(profile, style);
            List<String> lore = listOf("Colour of +", "", Ranks.premium().prefix(style.plusColour()) + name, "", how(style), "");
            lore.add(style == worn ? ChatColor.GREEN + "Worn " + ChatColor.GRAY + "(click to take off)"
                    : owned ? ChatColor.YELLOW + "Click to wear" : style.obtain() == NameStyle.Obtain.POINTS
                    ? ChatColor.YELLOW + "Click to buy" : ChatColor.RED + "Locked");
            menu.set(ShopMenu.slotOf(i++), ClickMenu.item(owned ? Material.WOOL : Material.STAINED_GLASS_PANE,
                    wool(style.text().charAt(0)), style.sample(), lore), (p, right) -> {
                        if (owned) {
                            profiles.wear(p, NameStyle.Kind.PLUS, style);
                            openPlus(p);
                        } else {
                            pick(p, style, false);
                        }
                    });
        }
        menu.set(31, ClickMenu.back("your colours and badges"), (p, right) -> open(p));
        menu.open(player);
    }

    /** Wears the style if the player owns it, else tells them how to get it. */
    private void pick(Player player, NameStyle style, boolean owned) {
        if (owned) {
            profiles.wear(player, style.kind(), style);
            reopen(player, style);
            return;
        }
        player.closeInventory();
        switch (style.obtain()) {
            case PREMIUM:
                player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GRAY + "This comes with the "
                        + ChatColor.LIGHT_PURPLE + "[PREMIUM+]" + ChatColor.GRAY + " rank.");
                tellStore(player, profiles.server().link("boutique"));
                break;
            case LEVEL:
                int level = rewardLevel.applyAsInt(style.key());
                player.sendMessage(ChatColor.GRAY + "A reward of the Network levels" + (level > 0 ? ": reach "
                        + book.levels().tag(level) + ChatColor.GRAY + " to get it." : "."));
                break;
            case WEEK:
                player.sendMessage(style.text() + ChatColor.GRAY + " The top of each leaderboard of the week wears it"
                        + " until the next results, on Monday: the walls of the lobby show this week's.");
                break;
            case SEASON:
                player.sendMessage(ChatColor.GRAY + "End a ranked season in a division to get its badge (/season).");
                break;
            case POINTS:
                String why = profiles.cannotBuy(player, style.price(), 0);
                if (why != null) {
                    player.sendMessage(ChatColor.RED + why);
                    return;
                }
                ClickMenu confirm = new ClickMenu(3, "Buy " + style.name() + "?");
                confirm.set(11, ClickMenu.item(Material.STAINED_CLAY, 5, ChatColor.GREEN + "Buy " + style.sample(), listOf(
                        "Price: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", style.price()) + " Rago Points", "",
                        ChatColor.YELLOW + "Click to confirm")), (p, right) -> {
                            profiles.buyStyle(p, style);
                            reopen(p, style);
                        });
                confirm.set(15, ClickMenu.item(Material.STAINED_CLAY, 14, ChatColor.RED + "Cancel", listOf("Back")),
                        (p, right) -> reopen(p, style));
                confirm.open(player);
                break;
            default:
                player.sendMessage(ChatColor.GRAY + "This one is sold on the web store.");
                tellStore(player, profiles.server().link("boutique"));
                break;
        }
    }

    /** Back to the page of this style: the badges and the + have their own. */
    private void reopen(Player player, NameStyle style) {
        if (style.kind() == NameStyle.Kind.PLUS) {
            openPlus(player);
        } else if (style.kind() == NameStyle.Kind.BADGE) {
            openBadges(player);
        } else {
            open(player);
        }
    }

    private static void tellStore(Player player, List<String> store) {
        if (store == null || store.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "The store opens soon.");
            return;
        }
        for (String line : store) {
            player.sendMessage(line);
        }
    }

    /** How a style is obtained, for its lore. */
    private String how(NameStyle style) {
        switch (style.obtain()) {
            case PREMIUM:
                return ChatColor.LIGHT_PURPLE + "With the [PREMIUM+] rank";
            case LEVEL:
                int level = rewardLevel.applyAsInt(style.key());
                return ChatColor.AQUA + "Reward of Network level" + (level > 0 ? " " + level : "s");
            case POINTS:
                return ChatColor.GOLD + "Price: " + String.format(java.util.Locale.US, "%,d", style.price()) + " Rago Points";
            case WEEK:
                return ChatColor.GOLD + "Top of a leaderboard of the week";
            case SEASON:
                return ChatColor.AQUA + "Your division in season " + NameLooks.seasonOf(style.id());
            default:
                return ChatColor.GOLD + "On the store";
        }
    }

    private static List<String> listOf(String... lines) {
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            list.add(line);
        }
        return list;
    }
}
