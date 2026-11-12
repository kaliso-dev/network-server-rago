package gg.rago.lobby;

import org.bukkit.ChatColor;

/**
 * The look of Rago Practice, the same everywhere (the README of the module, "Les couleurs du
 * serveur"): red, white and grey, the colours of the logo. Light red for the server itself (the
 * title of the sidebar, the server list, the tab list, the address, its announcements and the
 * commands they name); white for the values, grey for the labels, dark grey for the separators;
 * green to play and succeed; the rewards keep their colours (Network XP yellow, Rago Points gold);
 * light blue and pink for the ranks only (VIP, PREMIUM+). The defaults of config.yml follow it.
 */
final class Brand {

    /** The name, in bold red. */
    static final String NAME = ChatColor.RED + "" + ChatColor.BOLD + "RAGO PRACTICE";
    /** The slogan: the three ways to play, train, win the duels, climb in Ranked. */
    static final String SLOGAN = ChatColor.GRAY + "Train. Clutch. Climb.";
    /** The address, at the bottom of the sidebar and of the tab list. */
    static final String ADDRESS = ChatColor.RED + "rago.gg";
    /** What the server's messages point to (a command, a link): red too. */
    static final ChatColor ACCENT = ChatColor.RED;
    /** Before the server's own messages: RAGO in bold red, then » in dark grey, the text in white. */
    static final String PREFIX = ChatColor.RED + "" + ChatColor.BOLD + "RAGO " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE;
    /** The colour of the Network XP won, a reward, as the Rago Points are gold. */
    static final ChatColor XP = ChatColor.YELLOW;

    private Brand() {
    }
}
