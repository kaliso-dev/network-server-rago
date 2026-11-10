package gg.rago.bedwars;

import java.util.Locale;
import org.bukkit.ChatColor;

/**
 * The lines of a final kill and of a bed broken that show the author's own count, every game
 * together: "vaggww78 was qAbel's final #41,000. FINAL KILL!", "BED DESTRUCTION > Red Bed was
 * destroyed by qAbel's bed #1,250!". No line (null) when the count is not kept: a party's
 * private game.
 */
final class KillLines {

    private KillLines() {
    }

    /** A final kill: the victim and the killer as the game colours them, the killer's final kills now. */
    static String finalKill(String victim, String killer, int total) {
        if (total <= 0) {
            return null;
        }
        return victim + ChatColor.GRAY + " was " + killer + ChatColor.GRAY + "'s final " + ChatColor.YELLOW + "#"
                + count(total) + ChatColor.GRAY + ". " + ChatColor.AQUA + ChatColor.BOLD + "FINAL KILL!";
    }

    /** A bed broken: the team's bed and the player as the game colours them, the beds the player broke now. */
    static String bed(String teamBed, String breaker, int total) {
        if (total <= 0) {
            return null;
        }
        return ChatColor.WHITE + "" + ChatColor.BOLD + "BED DESTRUCTION > " + teamBed + ChatColor.GRAY
                + " was destroyed by " + breaker + ChatColor.GRAY + "'s bed " + ChatColor.YELLOW + "#" + count(total)
                + ChatColor.GRAY + "!";
    }

    /** 41000 as "41,000", as Hypixel writes it. */
    static String count(int total) {
        return String.format(Locale.US, "%,d", total);
    }
}
