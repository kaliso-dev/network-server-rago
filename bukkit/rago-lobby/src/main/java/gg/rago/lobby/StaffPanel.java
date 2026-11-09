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
 * The staff panel (/staff, or the Staff button of a player's menu): the players online, then for
 * one of them a click to teleport to them or bring them, freeze them, mute, kick, warn or ban them
 * with a reason and a length, lift a mute, and read their history. Every button goes through the
 * staff command it stands for (/mute, /ban...), with its checks: a helper does not ban, nobody
 * sanctions a higher rank. Main thread only.
 */
final class StaffPanel {

    static final int PER_PAGE = 45;

    /** What a sanction of the panel can be for, and for how long. */
    static final String[] MUTE_REASONS = {"Spam", "Insults", "Advertising", "Inappropriate chat"};
    static final String[] MUTE_LENGTHS = {"15m", "1h", "1d", "7d", "perm"};
    static final String[] BAN_REASONS = {"Cheating", "Abusive behaviour", "Ban evasion", "Breaking the rules"};
    static final String[] BAN_LENGTHS = {"1d", "7d", "30d", "perm"};
    static final String[] KICK_REASONS = {"Breaking the rules", "Spam", "AFK", "Inappropriate behaviour"};

    private final RagoLobby lobby;

    StaffPanel(RagoLobby lobby) {
        this.lobby = lobby;
    }

    /** The players online, by name, a page of 45. */
    void open(Player staff, int page) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        int pages = Math.max(1, (players.size() + PER_PAGE - 1) / PER_PAGE);
        final int shown = Math.max(0, Math.min(page, pages - 1));
        ClickMenu menu = new ClickMenu(6, "Staff panel" + (pages > 1 ? " (" + (shown + 1) + "/" + pages + ")" : ""));
        for (int i = 0; i < PER_PAGE && shown * PER_PAGE + i < players.size(); i++) {
            final Player target = players.get(shown * PER_PAGE + i);
            menu.set(i, ClickMenu.head(target.getName(), lobby.profiles().displayName(target.getUniqueId(), target.getName()),
                    info(target)), (p, right) -> openPlayer(p, target.getName()));
        }
        if (shown > 0) {
            menu.set(45, ClickMenu.item(Material.ARROW, ChatColor.GREEN + "Previous page"), (p, right) -> open(p, shown - 1));
        }
        if (shown < pages - 1) {
            menu.set(53, ClickMenu.item(Material.ARROW, ChatColor.GREEN + "Next page"), (p, right) -> open(p, shown + 1));
        }
        menu.set(49, ClickMenu.item(Material.REDSTONE_COMPARATOR, ChatColor.RED + "Staff tools", "/vanish: hide from the players",
                "/sc: the staff chat", "/mutechat, /clearchat", "/history <player>: the sanctions"), null);
        menu.open(staff);
    }

    /** Where a player is, their ping, whether they are frozen or muted. */
    private List<String> info(Player target) {
        List<String> lines = new ArrayList<>();
        lines.add("In: " + ChatColor.WHITE + target.getWorld().getName());
        lines.add("Ping: " + ChatColor.WHITE + LobbyScoreboard.pingOf(target) + " ms");
        if (lobby.staff().frozen(target.getUniqueId())) {
            lines.add(ChatColor.AQUA + "Frozen");
        }
        if (lobby.punishments().mute(target.getUniqueId()) != null) {
            lines.add(ChatColor.RED + "Muted");
        }
        lines.add("");
        lines.add(ChatColor.YELLOW + "Click for the actions");
        return lines;
    }

    /** The actions on one player online. */
    void openPlayer(Player staff, String name) {
        final Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            staff.sendMessage(ChatColor.RED + name + " is no longer online.");
            open(staff, 0);
            return;
        }
        ClickMenu menu = new ClickMenu(5, "Staff: " + name);
        menu.set(4, ClickMenu.head(name, lobby.profiles().displayName(target.getUniqueId(), name), info(target)), null);
        menu.set(19, ClickMenu.item(Material.ENDER_PEARL, ChatColor.GREEN + "Teleport to " + name,
                "Go where they are (vanish first", "to stay unseen: /vanish).", "", ChatColor.YELLOW + "Click to go"),
                (p, right) -> {
                    Player now = Bukkit.getPlayerExact(name);
                    if (now != null) {
                        p.closeInventory();
                        p.teleport(now);
                        p.sendMessage(ChatColor.GREEN + "Teleported to " + name + ".");
                    }
                });
        menu.set(20, ClickMenu.item(Material.EYE_OF_ENDER, ChatColor.GREEN + "Bring " + name + " here",
                "Teleport them to you.", "", ChatColor.YELLOW + "Click to bring"), (p, right) -> {
                    Player now = Bukkit.getPlayerExact(name);
                    if (now != null && lobby.state().isInGame(now)) {
                        p.sendMessage(ChatColor.RED + name + " is in a game: go to them instead.");
                    } else if (now != null) {
                        p.closeInventory();
                        now.teleport(p);
                        p.sendMessage(ChatColor.GREEN + name + " brought to you.");
                    }
                });
        boolean frozen = lobby.staff().frozen(target.getUniqueId());
        menu.set(21, ClickMenu.item(Material.PACKED_ICE, (frozen ? ChatColor.AQUA + "Unfreeze " : ChatColor.AQUA + "Freeze ") + name,
                frozen ? "They can move again." : "They cannot move, hit or use", frozen ? "" : "commands until unfrozen.", "",
                ChatColor.YELLOW + "Click to " + (frozen ? "unfreeze" : "freeze")), (p, right) -> {
                    run(p, "freeze " + name);
                    openPlayer(p, name);
                });
        boolean muted = lobby.punishments().mute(target.getUniqueId()) != null;
        menu.set(23, ClickMenu.item(Material.BOOK_AND_QUILL, ChatColor.GOLD + (muted ? "Unmute " : "Mute ") + name,
                muted ? "Give them the chat back." : "Choose the reason and", muted ? "" : "the length.", "",
                ChatColor.YELLOW + "Click to " + (muted ? "unmute" : "choose")), (p, right) -> {
                    if (muted) {
                        run(p, "unmute " + name);
                        openPlayer(p, name);
                    } else {
                        openSanction(p, name, "mute", 0);
                    }
                });
        menu.set(24, ClickMenu.item(Material.FEATHER, ChatColor.GOLD + "Kick " + name, "Out of the server, with a reason:",
                "they can come back.", "", ChatColor.YELLOW + "Click to choose"), (p, right) -> openKick(p, name));
        menu.set(25, ClickMenu.item(Material.REDSTONE_BLOCK, ChatColor.RED + "Ban " + name, "Choose the reason and the",
                "length (moderators and above).", "", ChatColor.YELLOW + "Click to choose"),
                (p, right) -> openSanction(p, name, "ban", 0));
        menu.set(30, ClickMenu.item(Material.PAPER, ChatColor.YELLOW + "Warn " + name, "A warning in their chat,",
                "kept in their history.", "", ChatColor.YELLOW + "Click to choose"), (p, right) -> openWarn(p, name));
        menu.set(32, ClickMenu.item(Material.BOOK, ChatColor.YELLOW + "History of " + name, "Their bans, mutes, kicks",
                "and warnings, in the chat.", "", ChatColor.YELLOW + "Click to read"), (p, right) -> {
                    p.closeInventory();
                    run(p, "history " + name);
                });
        menu.set(40, ClickMenu.back("the players"), (p, right) -> open(p, 0));
        menu.open(staff);
    }

    /**
     * A mute or a ban: the reasons on the second row (the one chosen lit), the lengths on the fourth;
     * a click on a length gives the sanction.
     */
    void openSanction(Player staff, String name, String kind, int reason) {
        boolean ban = kind.equals("ban");
        String[] reasons = ban ? BAN_REASONS : MUTE_REASONS;
        String[] lengths = ban ? BAN_LENGTHS : MUTE_LENGTHS;
        ClickMenu menu = new ClickMenu(6, (ban ? "Ban " : "Mute ") + name);
        int first = 9 + (9 - reasons.length) / 2;
        for (int i = 0; i < reasons.length; i++) {
            final int chosen = i;
            boolean lit = i == reason;
            menu.set(first + i, ClickMenu.item(lit ? Material.MAP : Material.EMPTY_MAP, 0,
                    (lit ? ChatColor.GREEN : ChatColor.GRAY) + reasons[i], Arrays.asList(lit ? ChatColor.GREEN + "The reason"
                            : ChatColor.YELLOW + "Click for this reason")), (p, right) -> openSanction(p, name, kind, chosen));
        }
        int firstLength = 27 + (9 - lengths.length) / 2;
        for (int i = 0; i < lengths.length; i++) {
            final String length = lengths[i];
            boolean forGood = length.equals("perm");
            menu.set(firstLength + i, ClickMenu.item(forGood ? Material.BEDROCK : Material.WATCH, 0,
                    (forGood ? ChatColor.DARK_RED + "For good" : ChatColor.GOLD + lengthLabel(length)), Arrays.asList(
                            "Reason: " + ChatColor.WHITE + reasons[reason], "", ChatColor.RED + "Click to " + kind + " " + name)),
                    (p, right) -> {
                        p.closeInventory();
                        run(p, kind + " " + name + " " + length + " " + reasons[reason]);
                    });
        }
        menu.set(49, ClickMenu.back(name), (p, right) -> openPlayer(p, name));
        menu.open(staff);
    }

    void openKick(Player staff, String name) {
        ClickMenu menu = new ClickMenu(4, "Kick " + name);
        reasons(menu, name, "kick", KICK_REASONS);
        menu.open(staff);
    }

    void openWarn(Player staff, String name) {
        ClickMenu menu = new ClickMenu(4, "Warn " + name);
        reasons(menu, name, "warn", MUTE_REASONS);
        menu.open(staff);
    }

    private void reasons(ClickMenu menu, String name, String kind, String[] reasons) {
        int first = 9 + (9 - reasons.length) / 2;
        for (int i = 0; i < reasons.length; i++) {
            final String reason = reasons[i];
            menu.set(first + i, ClickMenu.item(Material.PAPER, ChatColor.GOLD + reason, "", ChatColor.YELLOW + "Click to " + kind
                    + " " + name), (p, right) -> {
                        p.closeInventory();
                        run(p, kind + " " + name + " " + reason);
                    });
        }
        menu.set(31, ClickMenu.back(name), (p, right) -> openPlayer(p, name));
    }

    /** "15m": "15 minutes"... */
    static String lengthLabel(String length) {
        if (length.equals("perm")) {
            return "For good";
        }
        int amount = Integer.parseInt(length.substring(0, length.length() - 1));
        char unit = length.charAt(length.length() - 1);
        String word = unit == 'm' ? "minute" : unit == 'h' ? "hour" : "day";
        return amount + " " + word + (amount > 1 ? "s" : "");
    }

    /** Runs a staff command as the staff member, with its checks and its messages. */
    private static void run(Player staff, String command) {
        staff.performCommand(command);
        staff.playSound(staff.getLocation(), Sound.CLICK, 1f, 1f);
    }
}
