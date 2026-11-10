package gg.rago.lobby;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * An NPC body: a player in its skin (Steve without one), holding an item, facing one way. Its name
 * tag is hidden by the lobby scoreboard (team {@link LobbyScoreboard#NPC_TEAM}), its name is
 * shown by the lines above it, and it leaves the tab list two seconds after appearing.
 */
final class FakePlayer extends FakeEntities.Fake {

    /** How long the NPC stays in the tab list: the client needs the entry to create the player. */
    private static final long TAB_LIST_TICKS = 40L;
    /** The metadata of the parts of the skin shown, and all of them (the hat and the jacket too). */
    private static final int SKIN_PARTS = 10;
    private static final byte ALL_PARTS = 0x7F;

    private final UserProfile profile;
    private final ItemStack hand;

    /**
     * @param name a name no real player can have (it contains a colour code): it is the team entry
     *     that hides its name tag
     */
    FakePlayer(org.bukkit.Location at, String name, ItemStack hand, FakeEntities.Clicker clicker) {
        this(at, name, null, hand, clicker);
    }

    /** @param skin its signed skin, or null for Steve */
    FakePlayer(org.bukkit.Location at, String name, Skin skin, ItemStack hand, FakeEntities.Clicker clicker) {
        super(at, 1, clicker);
        this.profile = new UserProfile(steveId(), name, skin == null ? Collections.<TextureProperty>emptyList()
                : Collections.singletonList(new TextureProperty("textures", skin.value(), skin.signature())));
        this.hand = hand;
    }

    /** The profile sent to the clients: its name, and its skin if it has one. */
    UserProfile profile() {
        return profile;
    }

    /**
     * A 1.8 client shows the Steve skin to a player without a skin whose UUID has an even hash (an
     * odd one gets Alex). Version 2, unused by Minecraft accounts, marks it as an NPC.
     */
    static UUID steveId() {
        while (true) {
            UUID random = UUID.randomUUID();
            long most = (random.getMostSignificantBits() & ~0xF000L) | 0x2000L;
            UUID id = new UUID(most, random.getLeastSignificantBits());
            if ((id.hashCode() & 1) == 0) {
                return id;
            }
        }
    }

    @Override
    void spawn(final FakeEntities fakes, final Player viewer) {
        org.bukkit.Location at = at();
        WrapperPlayServerPlayerInfo.PlayerData data =
                new WrapperPlayServerPlayerInfo.PlayerData(null, profile, GameMode.SURVIVAL, 0);
        fakes.send(viewer, new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.ADD_PLAYER, data));
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(SKIN_PARTS, EntityDataTypes.BYTE, ALL_PARTS));
        fakes.send(viewer, new WrapperPlayServerSpawnPlayer(id(0), profile.getUUID(),
                new Location(at.getX(), at.getY(), at.getZ(), at.getYaw(), 0f), metadata));
        fakes.send(viewer, new WrapperPlayServerEntityHeadLook(id(0), at.getYaw()));
        if (hand != null) {
            fakes.send(viewer, new WrapperPlayServerEntityEquipment(id(0), Collections.singletonList(
                    new Equipment(EquipmentSlot.MAIN_HAND, SpigotConversionUtil.fromBukkitItemStack(hand)))));
        }
        fakes.later(TAB_LIST_TICKS, () -> {
            if (viewer.isOnline()) {
                WrapperPlayServerPlayerInfo.PlayerData gone =
                        new WrapperPlayServerPlayerInfo.PlayerData(null, profile, GameMode.SURVIVAL, 0);
                fakes.send(viewer, new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER, gone));
            }
        });
    }
}
