package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /titles: every title of the modes, with the player's wins in it, the title they have and the
 * next one; a click wears one of theirs, their best (by default) or none. Main thread only.
 */
final class TitlesMenu {

    private static final int INFO_SLOT = 4;
    private static final int BEST_SLOT = 48;
    private static final int BACK_SLOT = 49;
    private static final int NONE_SLOT = 50;
    /** The modes of a title, a line after the other: this many characters at most. */
    private static final int LINE = 30;

    private final LobbySettings settings;
    private final Profiles profiles;
    private ClickMenu.Action back;

    TitlesMenu(LobbySettings settings, Profiles profiles) {
        this.settings = settings;
        this.profiles = profiles;
    }

    /** The "Go back" of the menu (to My profile), or null for none. */
    void setBack(ClickMenu.Action back) {
        this.back = back;
    }

    /** The slot of the n-th title (from 0): four rows of seven, in the middle of the menu. */
    static int slot(int index) {
        return 10 + 9 * (index / 7) + index % 7;
    }

    void open(Player player) {
        Titles titles = profiles.server().titles();
        if (!titles.on()) {
            player.sendMessage(ChatColor.RED + "There are no titles on this server.");
            return;
        }
        PlayerStats profile = profiles.of(player);
        Titles.Title worn = titles.worn(profile);
        String choice = profile.titleChoice();
        ClickMenu menu = new ClickMenu(6, "Titles");
        menu.set(INFO_SLOT, ClickMenu.item(Material.NAME_TAG, 0, ChatColor.GOLD + "Titles", info(titles, worn)), null);
        List<Titles.Family> families = titles.families();
        for (int i = 0; i < families.size() && i < Titles.MAX_FAMILIES; i++) {
            final Titles.Family family = families.get(i);
            int wins = Titles.wins(profile, family);
            final Titles.Title title = titles.of(family, wins);
            boolean wearing = worn != null && worn.family.key.equals(family.key);
            List<String> lore = lore(titles, family, wins, title, modeNames(family));
            lore.add("");
            if (title == null) {
                lore.add(ChatColor.DARK_GRAY + "Win games to earn it");
            } else if (wearing) {
                lore.add(ChatColor.GREEN + (choice == null ? "You wear it: your best" : "You wear it"));
            } else {
                lore.add(Brand.ACCENT + "Click to wear it");
            }
            ItemStack item = ClickMenu.item(icon(family), 0, title == null ? ChatColor.GRAY + family.name : title.text(), lore);
            menu.set(slot(i), wearing ? ClickMenu.glow(item) : item, (p, right) -> {
                profiles.setTitle(p, family.key);
                if (title != null) {
                    open(p);
                }
            });
        }
        menu.set(BEST_SLOT, ClickMenu.item(Material.NETHER_STAR, 0, (choice == null ? ChatColor.GREEN : Brand.ACCENT)
                + "Your best title", choiceLore(choice == null, "Wear your best title, the", "next one as soon as you", "earn it.")),
                (p, right) -> {
                    profiles.setTitle(p, null);
                    open(p);
                });
        if (back != null) {
            menu.set(BACK_SLOT, ClickMenu.back("My profile"), back);
        }
        boolean none = Titles.NONE.equals(choice);
        menu.set(NONE_SLOT, ClickMenu.item(Material.BARRIER, 0, (none ? ChatColor.GREEN : Brand.ACCENT) + "No title",
                choiceLore(none, "Wear no title, in the chat", "or above your name.")), (p, right) -> {
                    profiles.setTitle(p, Titles.NONE);
                    open(p);
                });
        menu.open(player);
    }

    /** What the titles are: the steps, and the one the player wears. */
    static List<String> info(Titles titles, Titles.Title worn) {
        List<String> lore = new ArrayList<>();
        lore.add("Win games to earn the title");
        lore.add("of a mode: its wins climb the");
        lore.add("steps, five levels each (I to V).");
        lore.add("");
        for (Titles.Step step : titles.steps()) {
            lore.add(step.colour + step.name + ChatColor.GRAY + ": " + number(step.wins(1)) + " wins, +" + number(step.every)
                    + " per level");
        }
        lore.add("");
        lore.add("You wear: " + (worn == null ? ChatColor.DARK_GRAY + "no title" : worn.text()));
        lore.add("In the chat, your profile, and");
        lore.add("above your name if you choose");
        lore.add("it (Above your name, /display).");
        return lore;
    }

    /** The wins of a title, the modes it counts, the next level and how far it is. */
    static List<String> lore(Titles titles, Titles.Family family, int wins, Titles.Title title, List<String> modes) {
        List<String> lore = new ArrayList<>();
        lore.add("Wins: " + ChatColor.GREEN + number(wins));
        for (String line : wrap(modes)) {
            lore.add(ChatColor.DARK_GRAY + line);
        }
        lore.add("");
        int next = titles.nextWins(wins);
        if (next < 0) {
            lore.add(ChatColor.LIGHT_PURPLE + "The highest title!");
        } else {
            lore.add("Next: " + titles.rankAt(next) + ChatColor.GRAY + " at " + number(next) + " wins");
            lore.add(ProfileMenu.bar(titles.progress(wins)) + ChatColor.GRAY + " " + number(next - wins) + " more");
        }
        return lore;
    }

    /** The names of the modes of a title, as the lobby's menus write them. */
    private List<String> modeNames(Titles.Family family) {
        List<String> names = new ArrayList<>();
        for (String id : family.modes) {
            GameMode mode = settings.catalog().get(id);
            if (mode != null) {
                names.add(ChatColor.stripColor(mode.name()));
            }
        }
        return names;
    }

    /** The icon of the first mode of the title that the menus show, a name tag else. */
    private Material icon(Titles.Family family) {
        for (ModeDisplay display : settings.modes()) {
            if (family.modes.contains(display.mode().id())) {
                return display.icon();
            }
        }
        return Material.NAME_TAG;
    }

    /** "Bed fight 1v1, Bed fight 2v2," then "Top BedFight 1v1": lines of 30 characters at most. */
    static List<String> wrap(List<String> names) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            String word = names.get(i) + (i < names.size() - 1 ? "," : "");
            if (line.length() > 0 && line.length() + 1 + word.length() > LINE) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() == 0 ? "" : " ").append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static List<String> choiceLore(boolean chosen, String... what) {
        List<String> lore = new ArrayList<>();
        for (String line : what) {
            lore.add(line);
        }
        lore.add("");
        lore.add(chosen ? ChatColor.GREEN + "Chosen" : Brand.ACCENT + "Click to choose");
        return lore;
    }

    private static String number(int value) {
        return String.format(Locale.US, "%,d", value);
    }
}
