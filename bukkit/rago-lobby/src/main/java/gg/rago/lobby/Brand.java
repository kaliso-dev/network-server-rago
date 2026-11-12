package gg.rago.lobby;

import org.bukkit.ChatColor;

/**
 * The look of Rago Practice, the same everywhere (the README of the module, "Les couleurs du
 * serveur"): yellow and gold for the server itself (the title of the sidebar, the server list, the
 * tab list, the address, its announcements, the rewards); white for the values, grey for the
 * labels, dark grey for the separators; green to play and succeed, red to leave and fail; light
 * blue and pink for the ranks only (VIP, PREMIUM+). The defaults of config.yml follow it.
 */
final class Brand {

    /** The name, in bold yellow. */
    static final String NAME = ChatColor.YELLOW + "" + ChatColor.BOLD + "RAGO PRACTICE";
    /** The slogan: the three ways to play, train, win the duels, climb in Ranked. */
    static final String SLOGAN = ChatColor.GRAY + "Train. Clutch. Climb.";
    /** The address, at the bottom of the sidebar and of the tab list. */
    static final String ADDRESS = ChatColor.YELLOW + "rago.gg";
    /** Before the server's own messages: RAGO in bold yellow, then » in dark grey, the text in white. */
    static final String PREFIX = ChatColor.YELLOW + "" + ChatColor.BOLD + "RAGO " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE;
    /** The colour of the Network XP won, as the Rago Points are gold. */
    static final ChatColor XP = ChatColor.YELLOW;

    private Brand() {
    }
}
