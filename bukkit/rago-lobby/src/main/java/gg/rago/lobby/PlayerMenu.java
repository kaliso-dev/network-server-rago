package gg.rago.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * A right click on a player in the lobby opens their menu, as on Hypixel: their head with their
 * rank, level and ranked ELO, then a duel, their stats, a party invite, a friend request and a
 * rank to gift them. Each button goes through the usual command, with its checks (a player who
 * refuses duels, a full party...). Main thread only.
 */
final class PlayerMenu implements Listener {

    static final int HEAD = 4;
    static final int DUEL = 19;
    static final int STATS = 21;
    static final int PARTY = 23;
    static final int FRIEND = 25;
    static final int GIFT = 31;

    private final RagoLobby lobby;
    private final StatsMenu stats;

    PlayerMenu(RagoLobby lobby, StatsMenu stats) {
        this.lobby = lobby;
        this.stats = stats;
    }

    /** After the lobby's guard, which cancels the click: a click on a player still opens their menu. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }
        Player player = event.getPlayer();
        Player target = (Player) event.getRightClicked();
        if (!lobby.state().isInLobby(player) || !lobby.state().isInLobby(target) || !lobby.canBeSeen(target)
                || !target.isOnline()) {
            return;
        }
        event.setCancelled(true);
        open(player, target);
    }

    void open(Player player, Player target) {
        UUID id = target.getUniqueId();
        String name = target.getName();
        PlayerStats profile = lobby.profiles().of(target);
        ClickMenu menu = new ClickMenu(4, name);
        menu.set(HEAD, ClickMenu.head(name, lobby.profiles().displayName(id, name), lobby.profileMenu().summary(profile, id)), null);
        menu.set(DUEL, ClickMenu.item(Material.IRON_SWORD, ChatColor.GREEN + "Duel", "Challenge " + name + " to a duel:",
                "pick the mode.", "", ChatColor.YELLOW + "Click to choose"), (p, right) -> command(p, "duel " + name));
        menu.set(STATS, ClickMenu.item(Material.PAPER, ChatColor.GREEN + "Stats", "Wins, kills, streaks and ELO",
                "of " + name + " in every mode.", "", ChatColor.YELLOW + "Click to open"), (p, right) -> stats.open(p, id, name));
        menu.set(PARTY, ClickMenu.item(Material.CAKE, ChatColor.GREEN + "Invite to your party", "Play together:",
                "the party joins the queues as one.", "", ChatColor.YELLOW + "Click to invite"),
                (p, right) -> command(p, "party invite " + name));
        boolean friends = lobby.profiles().of(player).friends().contains(id);
        menu.set(FRIEND, ClickMenu.head(name, ChatColor.GREEN + (friends ? "Friends" : "Add as a friend"), lines(friends
                ? new String[] {"You are already friends."} : new String[] {"Send a friend request", "to " + name + ".", "",
                    ChatColor.YELLOW + "Click to send"})), (p, right) -> {
                        if (!friends) {
                            command(p, "friend add " + name);
                        }
                    });
        menu.set(GIFT, ClickMenu.item(Material.GOLD_INGOT, ChatColor.GOLD + "Gift a rank", "Give " + name + " a rank",
                "with your Rago Points.", "", ChatColor.YELLOW + "Click to choose"), (p, right) -> lobby.shop().openGift(p, profile));
        menu.open(player);
    }

    private static void command(Player player, String command) {
        player.closeInventory();
        player.performCommand(command);
    }

    private static List<String> lines(String[] lines) {
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            list.add(line);
        }
        return list;
    }
}
