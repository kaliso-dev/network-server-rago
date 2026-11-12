package gg.rago.lobby;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * A line of text above the name of each lobby player, which they choose: their ranked ELO (by
 * default), their level, wins, kills or best streak, the title they wear, a message of their own
 * (PREMIUM), or nothing.
 * Each line is an invisible marker armor stand (no hitbox: it never takes a click) known only to
 * the clients, which follows its player: it is sent with the player, moves when the player moves
 * (the same packets, for its id) and goes with them. Its text changes at most once a second.
 */
final class HeadLines implements Listener {

    /** What a player shows above their name. */
    enum Shown {
        ELO("elo", "Ranked ELO", Material.DIAMOND_SWORD, "Your best division and ELO."),
        LEVEL("niveau", "Network level", Material.EXP_BOTTLE, "Your Network level."),
        WINS("victoires", "Wins", Material.GOLD_INGOT, "Your wins, every mode."),
        KILLS("kills", "Kills", Material.IRON_SWORD, "Your kills, every mode."),
        STREAK("serie", "Best win streak", Material.BLAZE_POWDER, "Your best win streak."),
        TITLE("titre", "Your title", Material.NAME_TAG, "The title you wear (/titles),", "earned with wins."),
        MESSAGE("message", "Your message", Material.SIGN, "A message of yours (PREMIUM):", "/display message <text>"),
        NONE("aucun", "Nothing", Material.BARRIER, "Nothing above your name.");

        private final String id;
        private final String title;
        private final Material icon;
        private final String[] description;

        Shown(String id, String title, Material icon, String... description) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.description = description;
        }

        String id() {
            return id;
        }

        String title() {
            return title;
        }

        Material icon() {
            return icon;
        }

        String[] description() {
            return description.clone();
        }

        /** The choice of this id ("victoires", or the English "wins"), or null. */
        static Shown of(String id) {
            for (Shown shown : values()) {
                if (shown.id.equalsIgnoreCase(id) || shown.name().equalsIgnoreCase(id)) {
                    return shown;
                }
            }
            switch (id.toLowerCase(java.util.Locale.ROOT)) {
                case "level":
                    return LEVEL;
                case "wins":
                    return WINS;
                case "title":
                    return TITLE;
                case "none":
                    return NONE;
                default:
                    return null;
            }
        }
    }

    /** A custom message: 32 characters at most, without its colours. */
    static final int MAX_MESSAGE = 32;
    /** An entity name in 1.8: 64 characters at most, colours included. */
    private static final int MAX_NAME = 64;
    /** The stand's feet above the player's: its name floats just above the player's name. */
    private static final double HEIGHT = 67 / 32.0;
    /** The stand of the player with entity id n is n + this (far from the server's ids and the other fakes). */
    private static final int OFFSET = 1_700_000_000;

    private static final int FLAGS = 0;
    private static final int CUSTOM_NAME = 2;
    private static final int NAME_VISIBLE = 3;
    private static final int STAND_FLAGS = 10;
    private static final byte INVISIBLE = 0x20;
    private static final byte SMALL_MARKER = 0x01 | 0x08 | 0x10;

    private final World lobby;
    private final Function<Player, String> text;
    /** The entity ids of the players online, read on the Netty threads. */
    private final Set<Integer> players = ConcurrentHashMap.newKeySet();
    /** The players in the lobby world: they see the lines. */
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    /** The line of each lobby player, by their entity id. */
    private final Map<Integer, String> lines = new ConcurrentHashMap<>();
    /** Main thread: the line each player had at the last tick. */
    private final Map<UUID, String> shown = new HashMap<>();
    private PacketListenerCommon listener;

    HeadLines(World lobby, Function<Player, String> text) {
        this.lobby = lobby;
        this.text = text;
    }

    /** Coloured, 32 characters at most without the colours, no obfuscated text: a custom message. */
    static String cleanMessage(String typed) {
        String coloured = LobbySettings.colour(typed.trim()).replaceAll("§[kK]", "");
        StringBuilder out = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < coloured.length() && out.length() < MAX_NAME; i++) {
            char c = coloured.charAt(i);
            if (c == '§' && i + 1 < coloured.length()) {
                if (out.length() + 2 > MAX_NAME) {
                    break;
                }
                out.append(c).append(coloured.charAt(++i));
            } else if (visible < MAX_MESSAGE) {
                out.append(c);
                visible++;
            }
        }
        return out.toString();
    }

    /** "§6Gold 245 §7ELO", "§a1,234 §7wins"...: the line of a choice. */
    static String line(Shown shown, int elo, String eloText, String levelTag, int wins, int kills, int streak, String message) {
        return line(shown, elo, eloText, levelTag, wins, kills, streak, message, null);
    }

    /** The line of a choice; title: the text of the title worn ("§eSumo Gold III"), or null for none. */
    static String line(Shown shown, int elo, String eloText, String levelTag, int wins, int kills, int streak, String message,
            String title) {
        switch (shown) {
            case ELO:
                return elo < 0 ? ChatColor.GRAY + "Unranked" : eloText + ChatColor.GRAY + " ELO";
            case LEVEL:
                return ChatColor.GRAY + "Level " + levelTag;
            case WINS:
                return ChatColor.GREEN + String.format(java.util.Locale.US, "%,d", wins) + ChatColor.GRAY + (wins == 1 ? " win" : " wins");
            case KILLS:
                return ChatColor.RED + String.format(java.util.Locale.US, "%,d", kills) + ChatColor.GRAY + (kills == 1 ? " kill" : " kills");
            case STREAK:
                return ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", streak) + ChatColor.GRAY + " best win streak";
            case TITLE:
                return title == null ? "" : title;
            case MESSAGE:
                return message == null ? "" : message;
            default:
                return "";
        }
    }

    // --- Packets --------------------------------------------------------------------------------

    void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(player.getEntityId());
            if (player.getWorld().equals(lobby)) {
                viewers.add(player.getUniqueId());
            }
        }
        listener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.isCancelled() || event.getUser().getUUID() == null || !viewers.contains(event.getUser().getUUID())) {
                    return;
                }
                PacketWrapper<?> follow = follow(event);
                if (follow != null) {
                    final User user = event.getUser();
                    final PacketWrapper<?> packet = follow;
                    event.getTasksAfterSend().add(() -> user.sendPacketSilently(packet));
                }
            }
        });
    }

    /** What the stand of a player does when the client is told something of the player, or null. */
    private PacketWrapper<?> follow(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer spawn = new WrapperPlayServerSpawnPlayer(event);
            int id = spawn.getEntityId();
            if (!players.contains(id)) {
                return null;
            }
            Vector3d at = spawn.getPosition();
            String line = lines.getOrDefault(id, "");
            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData<>(FLAGS, EntityDataTypes.BYTE, INVISIBLE));
            metadata.add(new EntityData<>(STAND_FLAGS, EntityDataTypes.BYTE, SMALL_MARKER));
            metadata.add(new EntityData<>(CUSTOM_NAME, EntityDataTypes.STRING, line));
            metadata.add(new EntityData<>(NAME_VISIBLE, EntityDataTypes.BYTE, (byte) (line.isEmpty() ? 0 : 1)));
            return new WrapperPlayServerSpawnLivingEntity(OFFSET + id, UUID.randomUUID(), EntityTypes.ARMOR_STAND,
                    new Vector3d(at.getX(), at.getY() + HEIGHT, at.getZ()), 0f, 0f, 0f, new Vector3d(0, 0, 0), metadata);
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove move = new WrapperPlayServerEntityRelativeMove(event);
            return players.contains(move.getEntityId()) ? new WrapperPlayServerEntityRelativeMove(OFFSET + move.getEntityId(),
                    move.getDeltaX(), move.getDeltaY(), move.getDeltaZ(), move.isOnGround()) : null;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation move = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            return players.contains(move.getEntityId()) ? new WrapperPlayServerEntityRelativeMove(OFFSET + move.getEntityId(),
                    move.getDeltaX(), move.getDeltaY(), move.getDeltaZ(), move.isOnGround()) : null;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport teleport = new WrapperPlayServerEntityTeleport(event);
            if (!players.contains(teleport.getEntityId())) {
                return null;
            }
            Vector3d at = teleport.getPosition();
            return new WrapperPlayServerEntityTeleport(OFFSET + teleport.getEntityId(), new Vector3d(at.getX(), at.getY() + HEIGHT,
                    at.getZ()), 0f, 0f, teleport.isOnGround());
        }
        if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(event);
            List<Integer> stands = new ArrayList<>();
            for (int id : destroy.getEntityIds()) {
                if (players.contains(id)) {
                    stands.add(OFFSET + id);
                }
            }
            if (stands.isEmpty()) {
                return null;
            }
            int[] ids = new int[stands.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = stands.get(i);
            }
            return new WrapperPlayServerDestroyEntities(ids);
        }
        return null;
    }

    /** Removes every line from every client, and stops listening. */
    void stop() {
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            listener = null;
        }
        List<Integer> ids = new ArrayList<>();
        for (int player : players) {
            ids.add(OFFSET + player);
        }
        int[] all = new int[ids.size()];
        for (int i = 0; i < all.length; i++) {
            all[i] = ids.get(i);
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (all.length > 0 && viewers.contains(viewer.getUniqueId())) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(all));
            }
        }
        viewers.clear();
        lines.clear();
    }

    // --- Main thread ----------------------------------------------------------------------------

    /** Once a second: the line of each lobby player, sent again where it changed. */
    void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!player.getWorld().equals(lobby)) {
                viewers.remove(id);
                lines.remove(player.getEntityId());
                shown.remove(id);
                continue;
            }
            viewers.add(id);
            String now = player.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY) ? "" : text.apply(player);
            if (now == null) {
                now = "";
            }
            if (now.length() > MAX_NAME) {
                now = now.substring(0, MAX_NAME);
            }
            lines.put(player.getEntityId(), now);
            if (now.equals(shown.put(id, now))) {
                continue;
            }
            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData<>(CUSTOM_NAME, EntityDataTypes.STRING, now));
            metadata.add(new EntityData<>(NAME_VISIBLE, EntityDataTypes.BYTE, (byte) (now.isEmpty() ? 0 : 1)));
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(OFFSET + player.getEntityId(), metadata);
            for (Player viewer : lobby.getPlayers()) {
                // a client without the stand (too far, hidden) leaves it: it gets the line with the player
                if (viewer != player && viewer.canSee(player)) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        players.add(event.getPlayer().getEntityId());
        if (event.getPlayer().getWorld().equals(lobby)) {
            viewers.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer().getEntityId());
        viewers.remove(event.getPlayer().getUniqueId());
        lines.remove(event.getPlayer().getEntityId());
        shown.remove(event.getPlayer().getUniqueId());
    }

    /** Before the player changes world: in the lobby they see the lines, elsewhere neither theirs nor others'. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null || event.getTo().getWorld().equals(event.getFrom().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getTo().getWorld().equals(lobby)) {
            viewers.add(player.getUniqueId());
        } else {
            viewers.remove(player.getUniqueId());
            lines.remove(player.getEntityId());
            shown.remove(player.getUniqueId());
        }
    }
}
