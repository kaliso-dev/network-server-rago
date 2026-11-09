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
 * chat, then every colour and every badge, those they own to wear (a click), the others with how
 * to get them: the PREMIUM rank, the store, or a Network level. Main thread only.
 */
final class StyleMenu {

    /** The wool of each colour character, 0 to f: the colour of a name on an item. */
    private static final int[] WOOL = {15, 11, 13, 9, 14, 10, 1, 8, 7, 11, 5, 3, 14, 2, 4, 0};
    /** Badges start on the fourth row. */
    private static final int BADGES_FROM = 21;
    /** The four rows of seven. */
    private static final int LAST = 28;

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
        String look = book.levels().tag(book.level(id)) + " " + names.display(id, player.getName()) + names.badgeAfter(id);
        menu.set(4, ClickMenu.item(Material.NAME_TAG, 0, ChatColor.GREEN + "Your look", listOf(
                "In the chat:",
                look + rank.chatColour() + ": Hello!",
                "",
                "Above your head:",
                rank.prefix() + names.headColour(id) + player.getName() + LobbyScoreboard.suffix(names.badgeAfter(id),
                        " " + book.levels().tag(book.level(id))),
                "",
                "Colours of name below, badges",
                "under them. Click one you own",
                "to wear it.")), null);

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
            if (i >= BADGES_FROM) {
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
        menu.set(ShopMenu.slotOf(BADGES_FROM), ClickMenu.item(Material.BARRIER, 0, ChatColor.GRAY + "No badge",
                listOf("Badge", "", badge == null ? ChatColor.GREEN + "Worn" : ChatColor.YELLOW + "Click to take your badge off")),
                (p, right) -> {
                    profiles.wear(p, NameStyle.Kind.BADGE, null);
                    open(p);
                });
        i = BADGES_FROM + 1;
        for (NameStyle style : server.styles(NameStyle.Kind.BADGE)) {
            if (i >= LAST) {
                break;
            }
            boolean owned = names.owns(profile, style);
            List<String> lore = listOf("Badge, after your name", "", rank.prefix() + names.name(id, player.getName()) + " "
                    + style.text(), "");
            lore.add(how(style));
            lore.add("");
            lore.add(style == badge ? ChatColor.GREEN + "Worn " + ChatColor.GRAY + "(click to take off)"
                    : owned ? ChatColor.YELLOW + "Click to wear" : ChatColor.RED + "Locked");
            menu.set(ShopMenu.slotOf(i++), ClickMenu.item(owned ? Material.NAME_TAG : Material.PAPER, 0, style.text(), lore),
                    (p, right) -> pick(p, style, owned));
        }

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

    /** Wears the style if the player owns it, else tells them how to get it. */
    private void pick(Player player, NameStyle style, boolean owned) {
        if (owned) {
            profiles.wear(player, style.kind(), style);
            open(player);
            return;
        }
        player.closeInventory();
        switch (style.obtain()) {
            case PREMIUM:
                player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.GRAY + "This comes with the "
                        + ChatColor.LIGHT_PURPLE + "[PREMIUM]" + ChatColor.GRAY + " rank.");
                tellStore(player, profiles.server().link("boutique"));
                break;
            case LEVEL:
                int level = rewardLevel.applyAsInt(style.key());
                player.sendMessage(ChatColor.GRAY + "A reward of the Network levels" + (level > 0 ? ": reach "
                        + book.levels().tag(level) + ChatColor.GRAY + " to get it." : "."));
                break;
            case POINTS:
                String why = profiles.cannotBuy(player, style.price(), 0);
                if (why != null) {
                    player.sendMessage(ChatColor.RED + why);
                    return;
                }
                ClickMenu confirm = new ClickMenu(3, "Buy " + style.name() + "?");
                confirm.set(11, ClickMenu.item(Material.STAINED_CLAY, 5, ChatColor.GREEN + "Buy " + style.sample(), listOf(
                        "Price: " + ChatColor.GOLD + String.format("%,d", style.price()) + " Rago Points", "",
                        ChatColor.YELLOW + "Click to confirm")), (p, right) -> {
                            profiles.buyStyle(p, style);
                            open(p);
                        });
                confirm.set(15, ClickMenu.item(Material.STAINED_CLAY, 14, ChatColor.RED + "Cancel", listOf("Back")),
                        (p, right) -> open(p));
                confirm.open(player);
                break;
            default:
                player.sendMessage(ChatColor.GRAY + "This one is sold on the web store.");
                tellStore(player, profiles.server().link("boutique"));
                break;
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
                return ChatColor.LIGHT_PURPLE + "With the [PREMIUM] rank";
            case LEVEL:
                int level = rewardLevel.applyAsInt(style.key());
                return ChatColor.AQUA + "Reward of Network level" + (level > 0 ? " " + level : "s");
            case POINTS:
                return ChatColor.GOLD + "Price: " + String.format("%,d", style.price()) + " Rago Points";
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
