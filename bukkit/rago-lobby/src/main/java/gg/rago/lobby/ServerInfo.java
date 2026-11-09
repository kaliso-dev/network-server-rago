package gg.rago.lobby;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

/**
 * What a real server shows around the game: the header and footer of the tab list (the players
 * online, the player's ping), the message of the server list, the announcements of the lobby, the
 * arrival of the ranks that have it (rago.perk.join). The 1.8.8 API has no tab header: it goes
 * through the server's own packet, found by reflection; if it cannot be found, there is no header.
 */
final class ServerInfo implements Listener {

    private final RagoLobby lobby;
    private final ServerSettings server;
    private int nextAnnouncement;
    private boolean tabBroken;
    private Constructor<?> tabPacket;
    private Field tabFooter;
    private Method serialize;
    private Method sendPacket;
    private Method handle;
    private Field connection;
    private Field ping;

    ServerInfo(RagoLobby lobby, ServerSettings server) {
        this.lobby = lobby;
        this.server = server;
    }

    /** The players the others can see: the vanished staff left out. */
    private int shownPlayers() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (lobby.canBeSeen(player)) {
                count++;
            }
        }
        return count;
    }

    private static String colours(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // --- Server list ----------------------------------------------------------------------------

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        List<String> motd = server.motd();
        if (!motd.isEmpty()) {
            event.setMotd(colours(String.join("\n", motd)));
        }
    }

    // --- Announcements --------------------------------------------------------------------------

    /** Once a second: an announcement every few minutes, to the players in the lobby. */
    void tick(int second) {
        List<String> announcements = server.announcements();
        if (!announcements.isEmpty() && second > 0 && second % (server.announceMinutes() * 60) == 0) {
            String line = colours(announcements.get(nextAnnouncement % announcements.size()));
            nextAnnouncement++;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (lobby.state().isInLobby(player)) {
                    player.sendMessage(line);
                }
            }
        }
        if (second % 2 == 0) {
            int shown = shownPlayers();
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendTab(player, shown);
            }
        }
    }

    /** A rank with rago.perk.join arrives: the lobby hears it, as on Hypixel. */
    void joined(Player player) {
        if (!player.hasPermission("rago.perk.join") || !lobby.canBeSeen(player)) {
            return;
        }
        String line = ChatColor.AQUA + " >" + ChatColor.RED + ">" + ChatColor.GREEN + "> " + ChatColor.RESET
                + lobby.profiles().displayName(player.getUniqueId(), player.getName()) + ChatColor.GOLD + " joined the lobby! "
                + ChatColor.GREEN + "<" + ChatColor.RED + "<" + ChatColor.AQUA + "<";
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (lobby.state().isInLobby(other)) {
                other.sendMessage(line);
            }
        }
    }

    // --- Tab list -------------------------------------------------------------------------------

    void sendTab(Player player, int shown) {
        if (tabBroken || server.tabHeader().isEmpty() && server.tabFooter().isEmpty()) {
            return;
        }
        try {
            if (tabPacket == null) {
                findTab(player);
            }
            Object nms = handle.invoke(player);
            String header = fill(server.tabHeader(), shown, ping.getInt(nms));
            String footer = fill(server.tabFooter(), shown, ping.getInt(nms));
            Object packet = tabPacket.newInstance(serialize.invoke(null, json(header)));
            tabFooter.set(packet, serialize.invoke(null, json(footer)));
            sendPacket.invoke(connection.get(nms), packet);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            tabBroken = true;
            // no stack trace and no class name: a missing packet is not an error of the plugin
            lobby.getLogger().warning("No tab list header on this server: " + String.valueOf(e.getMessage())
                    .replace("Exception", "problem"));
        }
    }

    private String fill(List<String> lines, int shown, int pingMillis) {
        String text = colours(String.join("\n", lines));
        return text.replace("{joueurs}", String.valueOf(shown)).replace("{ping}", String.valueOf(pingMillis));
    }

    /** {"text":"..."} with the colour codes kept: the 1.8 client reads them in a text component. */
    static String json(String text) {
        StringBuilder json = new StringBuilder("{\"text\":\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                case '\n':
                    json.append("\\n");
                    break;
                default:
                    if (c < 0x20 || c > 0x7e) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                    break;
            }
        }
        return json.append("\"}").toString();
    }

    private void findTab(Player player) throws ReflectiveOperationException {
        handle = player.getClass().getMethod("getHandle");
        Object nms = handle.invoke(player);
        String root = nms.getClass().getPackage().getName();
        Class<?> component = Class.forName(root + ".IChatBaseComponent");
        Class<?> serializer = Class.forName(root + ".IChatBaseComponent$ChatSerializer");
        Class<?> packetClass = Class.forName(root + ".PacketPlayOutPlayerListHeaderFooter");
        serialize = serializer.getMethod("a", String.class);
        tabPacket = packetClass.getConstructor(component);
        tabFooter = packetClass.getDeclaredField("b");
        tabFooter.setAccessible(true);
        connection = nms.getClass().getField("playerConnection");
        ping = nms.getClass().getField("ping");
        sendPacket = connection.getType().getMethod("sendPacket", Class.forName(root + ".Packet"));
    }
}
