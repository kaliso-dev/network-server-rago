package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Color;

/**
 * The teams a map can have, in the order they are listed: their id in bedwars.yml and in the
 * commands, their name, their chat colour, the data value of their wool, clay and glass, and the
 * colour of their leather armour.
 */
enum TeamColor {
    RED("red", "Red", ChatColor.RED, 14, 0xB02E26),
    BLUE("blue", "Blue", ChatColor.BLUE, 11, 0x3C44AA),
    GREEN("green", "Green", ChatColor.GREEN, 5, 0x80C71F),
    YELLOW("yellow", "Yellow", ChatColor.YELLOW, 4, 0xFED83D),
    AQUA("aqua", "Aqua", ChatColor.AQUA, 9, 0x169C9C),
    WHITE("white", "White", ChatColor.WHITE, 0, 0xF9FFFE),
    PINK("pink", "Pink", ChatColor.LIGHT_PURPLE, 6, 0xF38BAA),
    GRAY("gray", "Gray", ChatColor.DARK_GRAY, 7, 0x474F52);

    private final String id;
    private final String displayName;
    private final ChatColor chat;
    private final short woolData;
    private final int leather;

    TeamColor(String id, String displayName, ChatColor chat, int woolData, int leather) {
        this.id = id;
        this.displayName = displayName;
        this.chat = chat;
        this.woolData = (short) woolData;
        this.leather = leather;
    }

    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    ChatColor chat() {
        return chat;
    }

    /** The name in its colour: "§cRed". */
    String coloured() {
        return chat + displayName;
    }

    /** The first letter in bold and colour, as in the sidebar: "§c§lR". */
    String letter() {
        return chat + "" + ChatColor.BOLD + displayName.charAt(0);
    }

    /** The data value of the team's wool, stained clay and stained glass. */
    short woolData() {
        return woolData;
    }

    Color leather() {
        return Color.fromRGB(leather);
    }

    /** The team with this id ("red", "Blue"...), or null. */
    static TeamColor byId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (TeamColor team : values()) {
            if (team.id.equals(key)) {
                return team;
            }
        }
        return null;
    }

    /** "red, blue, green...". */
    static String ids() {
        List<String> ids = new ArrayList<>();
        for (TeamColor team : values()) {
            ids.add(team.id);
        }
        return String.join(", ", ids);
    }
}
