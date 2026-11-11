package gg.rago.lobby;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.entity.Player;

/**
 * A step of a podium: the player at this place of what the viewer reads on the leaderboard, in
 * their skin, facing the spawn. Each viewer has their own, since each one chooses what the board
 * shows (the stat, the period, the ranked mode). The body is sent again when the player or their
 * skin changes; nobody at the place leaves the step empty. Its name tag is hidden by the lobby
 * scoreboard (team {@link LobbyScoreboard#NPC_TEAM}): a line above it says who it is.
 */
final class PodiumPlace extends FakeEntities.Fake {

    /** How long the body stays in the tab list: the client needs the entry, with the skin, to create it. */
    private static final long TAB_LIST_TICKS = 40L;
    /** 1.8 player metadata: the parts of the skin shown (hat, jacket, sleeves, trousers, cape). */
    private static final int SKIN_PARTS = 10;
    private static final byte ALL_PARTS = 0x7F;

    interface Holder {
        /** The player at this place for this viewer, or null. */
        StatsBook.Row of(Player viewer);
    }

    /** What a viewer's client has: who, in which skin, under which profile (null: no body). */
    private static final class Shown {
        final UUID player;
        final Skin skin;
        final UserProfile profile;

        Shown(UUID player, Skin skin, UserProfile profile) {
            this.player = player;
            this.skin = skin;
            this.profile = profile;
        }
    }

    private final String name;
    private final Holder holder;
    private final Function<UUID, Skin> skins;
    private final Map<UUID, Shown> sent = new HashMap<>();

    /**
     * @param name the name of the body, a team entry whose name tag is hidden (no real player can
     *     have it: it holds a colour code)
     */
    PodiumPlace(org.bukkit.Location at, String name, Holder holder, Function<UUID, Skin> skins, FakeEntities.Clicker clicker) {
        super(at, 1, clicker);
        this.name = name;
        this.holder = holder;
        this.skins = skins;
    }

    @Override
    void spawn(FakeEntities fakes, Player viewer) {
        StatsBook.Row row = holder.of(viewer);
        UUID player = row == null ? null : row.id();
        show(fakes, viewer, player, player == null ? null : skins.apply(player));
    }

    @Override
    void update(FakeEntities fakes, Player viewer) {
        StatsBook.Row row = holder.of(viewer);
        UUID player = row == null ? null : row.id();
        Skin skin = player == null ? null : skins.apply(player);
        Shown before = sent.get(viewer.getUniqueId());
        if (before != null && Objects.equals(before.player, player) && Objects.equals(before.skin, skin)) {
            return;
        }
        if (before != null && before.profile != null) {
            fakes.send(viewer, new WrapperPlayServerDestroyEntities(id(0)));
        }
        show(fakes, viewer, player, skin);
    }

    @Override
    void forget(UUID viewer) {
        sent.remove(viewer);
    }

    /** Sends the body of this player in this skin (Steve without one), or nothing for nobody. */
    private void show(final FakeEntities fakes, final Player viewer, UUID player, Skin skin) {
        if (player == null) {
            sent.put(viewer.getUniqueId(), new Shown(null, null, null));
            return;
        }
        // A new profile each time: the tab list entry of the last body may still be there.
        List<TextureProperty> textures = skin == null ? Collections.<TextureProperty>emptyList()
                : Collections.singletonList(new TextureProperty("textures", skin.value(), skin.signature()));
        final UserProfile profile = new UserProfile(FakePlayer.steveId(), name, textures);
        sent.put(viewer.getUniqueId(), new Shown(player, skin, profile));
        org.bukkit.Location at = at();
        fakes.send(viewer, new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.ADD_PLAYER,
                new WrapperPlayServerPlayerInfo.PlayerData(null, profile, GameMode.SURVIVAL, 0)));
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(SKIN_PARTS, EntityDataTypes.BYTE, ALL_PARTS));
        fakes.send(viewer, new WrapperPlayServerSpawnPlayer(id(0), profile.getUUID(),
                new Location(at.getX(), at.getY(), at.getZ(), at.getYaw(), 0f), metadata));
        fakes.send(viewer, new WrapperPlayServerEntityHeadLook(id(0), at.getYaw()));
        fakes.later(TAB_LIST_TICKS, () -> {
            if (viewer.isOnline()) {
                fakes.send(viewer, new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER,
                        new WrapperPlayServerPlayerInfo.PlayerData(null, profile, GameMode.SURVIVAL, 0)));
            }
        });
    }
}
