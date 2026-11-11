package gg.rago.kbrecorder;

import gg.rago.kbrecorder.PacketBytes.EntityIdFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S27PacketExplosion;

/**
 * The packets kept in a recording, with their protocol 47 id and name. Ids and names follow the
 * protocol, not Minecraft's class names (the entity status packet is 0x1A although its class is
 * named S19). Everything else is ignored, so a recording stays small even in a busy lobby.
 */
final class PacketCatalog {

    static final class Kind {
        final int id;
        final String name;
        /** Packets about an entity are kept only for the player and the players around. */
        final EntityIdFormat entityId;

        Kind(int id, String name, EntityIdFormat entityId) {
            this.id = id;
            this.name = name;
            this.entityId = entityId;
        }
    }

    private static final Map<Class<?>, Kind> RECEIVED = new HashMap<>();
    private static final Map<Class<?>, Kind> SENT = new HashMap<>();

    static {
        received(S00PacketKeepAlive.class, 0x00, "keep_alive", EntityIdFormat.NONE);
        received(S01PacketJoinGame.class, 0x01, "join_game", EntityIdFormat.NONE);
        received(S06PacketUpdateHealth.class, 0x06, "update_health", EntityIdFormat.NONE);
        received(S07PacketRespawn.class, 0x07, "respawn", EntityIdFormat.NONE);
        received(S08PacketPlayerPosLook.class, 0x08, "player_position_look", EntityIdFormat.NONE);
        received(S0BPacketAnimation.class, 0x0B, "animation", EntityIdFormat.VARINT);
        received(S12PacketEntityVelocity.class, 0x12, "entity_velocity", EntityIdFormat.VARINT);
        received(S14PacketEntity.class, 0x14, "entity", EntityIdFormat.VARINT);
        received(S14PacketEntity.S15PacketEntityRelMove.class, 0x15, "entity_relative_move", EntityIdFormat.VARINT);
        received(S14PacketEntity.S16PacketEntityLook.class, 0x16, "entity_look", EntityIdFormat.VARINT);
        received(S14PacketEntity.S17PacketEntityLookMove.class, 0x17, "entity_look_relative_move", EntityIdFormat.VARINT);
        received(S18PacketEntityTeleport.class, 0x18, "entity_teleport", EntityIdFormat.VARINT);
        received(S19PacketEntityStatus.class, 0x1A, "entity_status", EntityIdFormat.INT);
        received(S1CPacketEntityMetadata.class, 0x1C, "entity_metadata", EntityIdFormat.VARINT);
        received(S27PacketExplosion.class, 0x27, "explosion", EntityIdFormat.NONE);

        sent(C00PacketKeepAlive.class, 0x00, "keep_alive");
        sent(C02PacketUseEntity.class, 0x02, "use_entity");
        sent(C03PacketPlayer.class, 0x03, "player");
        sent(C03PacketPlayer.C04PacketPlayerPosition.class, 0x04, "player_position");
        sent(C03PacketPlayer.C05PacketPlayerLook.class, 0x05, "player_look");
        sent(C03PacketPlayer.C06PacketPlayerPosLook.class, 0x06, "player_position_look");
        sent(C0APacketAnimation.class, 0x0A, "animation");
        sent(C0BPacketEntityAction.class, 0x0B, "entity_action");
    }

    private PacketCatalog() {
    }

    /** The kind of a received packet, or null when it is not recorded. */
    static Kind received(Class<?> packetClass) {
        return RECEIVED.get(packetClass);
    }

    /** The kind of a sent packet, or null when it is not recorded. Sent packets are all kept. */
    static Kind sent(Class<?> packetClass) {
        return SENT.get(packetClass);
    }

    private static void received(Class<?> packetClass, int id, String name, EntityIdFormat entityId) {
        RECEIVED.put(packetClass, new Kind(id, name, entityId));
    }

    private static void sent(Class<?> packetClass, int id, String name) {
        SENT.put(packetClass, new Kind(id, name, EntityIdFormat.NONE));
    }
}
