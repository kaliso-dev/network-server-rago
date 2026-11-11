package gg.rago.lobby;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Lines of text floating in the air, each player reading their own: a column of small invisible
 * armor stands whose names are the lines, first line on top. A line left empty shows nothing. The
 * stands keep a hitbox, so that a click on the text reaches {@link FakeEntities.Fake#click}.
 */
final class FakeHologram extends FakeEntities.Fake {

    /** Space between two lines, in blocks. */
    static final double LINE = 0.27;
    /** Height of a small armor stand's name above its feet, in blocks. */
    static final double NAME_HEIGHT = 1.0;

    /** 1.8 entity metadata: flags, custom name, name visible, armor stand flags. */
    private static final int FLAGS = 0;
    private static final int CUSTOM_NAME = 2;
    private static final int NAME_VISIBLE = 3;
    private static final int STAND_FLAGS = 10;
    private static final byte INVISIBLE = 0x20;
    private static final byte SMALL = 0x01;
    private static final byte NO_BASE_PLATE = 0x08;
    /** Names of 1.8 entities are 64 characters at most. */
    private static final int MAX_NAME = 64;

    interface Lines {
        /** The lines this player reads, top to bottom: as many as the hologram has, or fewer. */
        List<String> of(Player viewer);
    }

    private final int lines;
    private final Lines text;
    /** What each player's client shows now. */
    private final Map<UUID, List<String>> sent = new HashMap<>();

    /**
     * @param bottom where the name of the last line floats
     */
    FakeHologram(Location bottom, int lines, Lines text, FakeEntities.Clicker clicker) {
        super(bottom, lines, clicker);
        this.lines = lines;
        this.text = text;
    }

    @Override
    void spawn(FakeEntities fakes, Player viewer) {
        List<String> now = read(viewer);
        Location bottom = at();
        for (int i = 0; i < lines; i++) {
            double y = bottom.getY() - NAME_HEIGHT + (lines - 1 - i) * LINE;
            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData<>(FLAGS, EntityDataTypes.BYTE, INVISIBLE));
            metadata.add(new EntityData<>(STAND_FLAGS, EntityDataTypes.BYTE, (byte) (SMALL | NO_BASE_PLATE)));
            addName(metadata, now.get(i));
            fakes.send(viewer, new WrapperPlayServerSpawnLivingEntity(id(i), UUID.randomUUID(), EntityTypes.ARMOR_STAND,
                    new Vector3d(bottom.getX(), y, bottom.getZ()), 0f, 0f, 0f, new Vector3d(0, 0, 0), metadata));
        }
        sent.put(viewer.getUniqueId(), now);
    }

    @Override
    void update(FakeEntities fakes, Player viewer) {
        List<String> before = sent.get(viewer.getUniqueId());
        List<String> now = read(viewer);
        if (before == null || now.equals(before)) {
            return;
        }
        for (int i = 0; i < lines; i++) {
            if (!now.get(i).equals(before.get(i))) {
                List<EntityData<?>> metadata = new ArrayList<>();
                addName(metadata, now.get(i));
                fakes.send(viewer, new WrapperPlayServerEntityMetadata(id(i), metadata));
            }
        }
        sent.put(viewer.getUniqueId(), now);
    }

    @Override
    void forget(UUID viewer) {
        sent.remove(viewer);
    }

    /** Exactly as many lines as the stands, each one short enough. */
    private List<String> read(Player viewer) {
        List<String> given = text.of(viewer);
        List<String> now = new ArrayList<>(lines);
        for (int i = 0; i < lines; i++) {
            String line = i < given.size() && given.get(i) != null ? given.get(i) : "";
            now.add(line.length() <= MAX_NAME ? line : line.substring(0, MAX_NAME));
        }
        return now;
    }

    private static void addName(List<EntityData<?>> metadata, String line) {
        metadata.add(new EntityData<>(CUSTOM_NAME, EntityDataTypes.STRING, line));
        metadata.add(new EntityData<>(NAME_VISIBLE, EntityDataTypes.BYTE, (byte) (line.isEmpty() ? 0 : 1)));
    }
}
