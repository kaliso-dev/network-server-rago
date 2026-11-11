package gg.rago.lobby;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * /nick: a player of PREMIUM (rago.perk.nick) plays under another name. The others see that name
 * above the head, in the tab list and the chat, with the default skin, no rank, no badge, no line
 * above the name and the level 1: a new player. The stats stay theirs, under their real name. The
 * name a client knows a player by is the one of the tab list entry, so that entry is rewritten for
 * everyone but the player. The staff finds the real name with /realname. Main thread, but for the
 * packets.
 */
final class Nicks {

    /** Read on the Netty threads. */
    private final Map<UUID, String> nicks = new ConcurrentHashMap<>();
    private PacketListenerCommon listener;

    /** True for a name a nick can be: 3 to 16 letters, digits or _, as a Minecraft name. */
    static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    void start() {
        listener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.HIGH) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO || nicks.isEmpty()) {
                    return;
                }
                WrapperPlayServerPlayerInfo info = new WrapperPlayServerPlayerInfo(event);
                if (info.getAction() != WrapperPlayServerPlayerInfo.Action.ADD_PLAYER) {
                    return;
                }
                UUID receiver = event.getUser().getUUID();
                boolean changed = false;
                for (WrapperPlayServerPlayerInfo.PlayerData data : info.getPlayerDataList()) {
                    UserProfile profile = data.getUserProfile();
                    String nick = profile == null ? null : nicks.get(profile.getUUID());
                    if (nick != null && !profile.getUUID().equals(receiver)) {
                        // no skin: the client shows the default one of the uuid
                        data.setUserProfile(new UserProfile(profile.getUUID(), nick, Collections.<TextureProperty>emptyList()));
                        changed = true;
                    }
                }
                if (changed) {
                    event.markForReEncode(true);
                }
            }
        });
    }

    void stop() {
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            listener = null;
        }
        for (UUID id : nicks.keySet()) {
            Player player = Bukkit.getPlayer(id);
            nicks.remove(id);
            if (player != null) {
                player.setDisplayName(player.getName());
                resend(player);
            }
        }
    }

    /** The nick of an online player, or null. */
    String nick(UUID player) {
        return nicks.get(player);
    }

    /** The online player playing under this nick (ignoring case), or null. */
    Player byNick(String nick) {
        for (Map.Entry<UUID, String> entry : nicks.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(nick)) {
                return Bukkit.getPlayer(entry.getKey());
            }
        }
        return null;
    }

    /** The player plays under this name (null: their own), and the others' clients learn it. */
    void set(Player player, String nick) {
        if (nick == null) {
            if (nicks.remove(player.getUniqueId()) == null) {
                return;
            }
        } else if (nick.equals(nicks.put(player.getUniqueId(), nick))) {
            return;
        }
        player.setDisplayName(nick == null ? player.getName() : nick);
        resend(player);
    }

    /** At the join, before the others learn of the player: their nick, quietly. */
    void restore(Player player, String nick) {
        if (nick != null) {
            nicks.put(player.getUniqueId(), nick);
            player.setDisplayName(nick);
        }
    }

    void forget(UUID player) {
        nicks.remove(player);
    }

    /** The others who see the player get them again: the tab list entry with the name, then the body. */
    private static void resend(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != player && other.canSee(player)) {
                other.hidePlayer(player);
                other.showPlayer(player);
            }
        }
    }
}
