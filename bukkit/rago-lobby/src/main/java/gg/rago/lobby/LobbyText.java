package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A text of the lobby as config.yml gives it (textes:): lines floating above its point, the same
 * for everyone, as a hologram ({@link FakeHologram}); a click does nothing. The thank-you text of
 * the thank-you room (plan 5.7, point textremerciement) is one. Immutable.
 */
final class LobbyText {

    /** Lines of a text at most. */
    static final int MAX_LINES = 30;
    /** Characters of a line at most, its colour codes included: the name of a 1.8 entity. */
    static final int MAX_LINE = 64;
    /**
     * The last line floats this high above the point (on the floor), the others above it,
     * {@link FakeHologram#LINE} apart: the 20 lines of the thank-you text fill its room of 6 blocks,
     * from half a block above the floor to half a block under the ceiling.
     */
    static final double BOTTOM = 0.45;

    /** Its point (/lobby point), the key of textes:. */
    final String point;
    /** Its lines, top to bottom, coloured; an empty one leaves a gap. */
    final List<String> lines;

    LobbyText(String point, List<String> lines) {
        this.point = point;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /** How high its first line floats above its point. */
    double top() {
        return BOTTOM + (lines.size() - 1) * FakeHologram.LINE;
    }

    /** textes: of config.yml. None without it. */
    static List<LobbyText> read(ConfigurationSection section) {
        List<LobbyText> texts = new ArrayList<>();
        if (section == null) {
            return texts;
        }
        for (String point : section.getKeys(false)) {
            String where = "textes." + point;
            if (!LobbyPoints.validName(point)) {
                throw new IllegalArgumentException(where + ": not a valid point name");
            }
            ConfigurationSection text = section.getConfigurationSection(point);
            if (text == null || !text.isList("lignes")) {
                throw new IllegalArgumentException(where + ": its lines, under lignes:, as in the example");
            }
            List<String> lines = new ArrayList<>();
            for (String line : text.getStringList("lignes")) {
                String coloured = LobbySettings.colour(line);
                if (coloured.length() > MAX_LINE) {
                    throw new IllegalArgumentException(where + ".lignes: \"" + line + "\" is longer than " + MAX_LINE
                            + " characters with its colour codes: cut it in two lines");
                }
                lines.add(coloured);
            }
            // empty lines at the ends would only move the text
            while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            while (!lines.isEmpty() && lines.get(0).trim().isEmpty()) {
                lines.remove(0);
            }
            if (lines.isEmpty() || lines.size() > MAX_LINES) {
                throw new IllegalArgumentException(where + ".lignes: from 1 to " + MAX_LINES + " lines");
            }
            texts.add(new LobbyText(point, lines));
        }
        return texts;
    }
}
