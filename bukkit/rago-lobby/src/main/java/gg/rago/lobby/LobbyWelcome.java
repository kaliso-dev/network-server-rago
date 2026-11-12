package gg.rago.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * The welcome of the lobby, so that a player sees at once what they can do here: at each arrival,
 * the name and the slogan of the server, then the ways to play in the chat, one line per category
 * of the compass (its name and its slogan: train alone, duel your friends, play the RBW modes),
 * each clickable to open its modes (/play category) with what it holds on hover; at the first
 * arrival on the server, a title too.
 * accueil: false in config.yml turns it off. Main thread.
 */
final class LobbyWelcome {

    /** A line across the chat. */
    static final String RULE = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "-----------------------------------------------------";
    /** The width of the chat, in pixels of the font. */
    private static final int CHAT_WIDTH = 320;

    private LobbyWelcome() {
    }

    /** Greets the player: the title the first time, the ways to play every time. */
    @SuppressWarnings("deprecation") // Player#sendTitle is marked "API subject to change" in 1.8.8
    static void greet(Player player, LobbySettings settings) {
        List<ModeCategory> categories = settings.categories();
        if (!settings.welcome() || categories.isEmpty()) {
            return;
        }
        if (!player.hasPlayedBefore()) {
            player.sendTitle(settings.title(), subtitle(categories));
        }
        player.sendMessage(RULE);
        player.sendMessage(centred(settings.title()));
        player.sendMessage(centred(Brand.SLOGAN));
        player.sendMessage(ChatColor.GRAY + "Pick how you play. " + ChatColor.YELLOW + "Click one:");
        for (ModeCategory category : categories) {
            // clickable through the vanilla /tellraw, as the duel buttons: names are letters, digits and _
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json(category));
        }
        player.sendMessage(ChatColor.GRAY + "Or the " + ChatColor.WHITE + "compass" + ChatColor.GRAY + " of your hotbar, any time.");
        player.sendMessage(RULE);
    }

    /** "Practice · Duels · RBW Modes": the names of the categories, for the title. */
    static String subtitle(List<ModeCategory> categories) {
        StringBuilder line = new StringBuilder();
        for (ModeCategory category : categories) {
            if (line.length() > 0) {
                line.append(ChatColor.DARK_GRAY).append(" · ");
            }
            line.append(category.name());
        }
        return line.toString();
    }

    /** The line of a category: its name and slogan, which opens its modes, with its description on hover. */
    static String json(ModeCategory category) {
        String text = " " + ChatColor.DARK_GRAY + "» " + category.name() + (category.slogan() == null ? "" : " " + category.slogan());
        StringBuilder hover = new StringBuilder(category.name());
        if (category.slogan() != null) {
            hover.append('\n').append(category.slogan());
        }
        if (!category.description().isEmpty()) {
            hover.append('\n');
            for (String line : category.description()) {
                hover.append('\n').append(line);
            }
        }
        hover.append("\n\n").append(ChatColor.GREEN).append("Click to see the modes");
        return "{\"text\":\"" + NextGame.escape(text) + "\","
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/play " + category.id() + "\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + escapeLines(hover.toString()) + "\"}}";
    }

    /** As {@link NextGame#escape}, keeping the line breaks (\n in the JSON). */
    private static String escapeLines(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (out.length() > 0) {
                out.append("\\n");
            }
            out.append(NextGame.escape(line));
        }
        return out.toString();
    }

    /**
     * The category typed after /play: its id, or its name without colours or spaces, any case
     * ("practice", "rbwmodes", "RBW Modes"); null if none.
     */
    static ModeCategory find(List<ModeCategory> categories, String typed) {
        String wanted = squeeze(typed);
        if (wanted.isEmpty()) {
            return null;
        }
        for (ModeCategory category : categories) {
            if (squeeze(category.id()).equals(wanted)) {
                return category;
            }
        }
        for (ModeCategory category : categories) {
            if (squeeze(ChatColor.stripColor(category.name())).equals(wanted)) {
                return category;
            }
        }
        return null;
    }

    private static String squeeze(String text) {
        return text.replaceAll("[\\s_-]", "").toLowerCase(Locale.ROOT);
    }

    /** The text with spaces before it, so that it shows in the middle of the chat. */
    static String centred(String text) {
        int pad = (CHAT_WIDTH / 2 - width(text) / 2) / 4;
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < pad; i++) {
            line.append(' ');
        }
        return line.append(text).toString();
    }

    /**
     * How wide the text shows in the chat or the server list, in pixels of the 1.8 font: most
     * characters 6, the narrow ones less (a space 4, an i 2...), 1 more in bold.
     */
    static int width(String text) {
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) {
                    bold = false;
                }
                continue;
            }
            width += width(c) + (bold ? 1 : 0);
        }
        return width;
    }

    /** The width of a character of the 1.8 font, the pixel after it included. */
    static int width(char c) {
        switch (c) {
            case '!': case ',': case '.': case ':': case ';': case 'i': case '|':
                return 2;
            case '\'': case '`': case 'l':
                return 3;
            case ' ': case 'I': case 't': case '[': case ']':
                return 4;
            case '"': case '(': case ')': case '*': case '<': case '>': case 'f': case 'k': case '{': case '}':
                return 5;
            case '@': case '~':
                return 7;
            default:
                return 6;
        }
    }

    /** The ids of the categories, for the completion of /play. */
    static List<String> ids(List<ModeCategory> categories) {
        List<String> ids = new ArrayList<>();
        for (ModeCategory category : categories) {
            ids.add(category.id());
        }
        return ids;
    }
}
