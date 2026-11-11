package gg.rago.buildtools;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Names of the worlds created with {@code /monde}. A world's name is also its folder name, so it is
 * kept to what every file system accepts the same way: lower case letters, digits, '-' and '_'.
 * Upper case is refused because Windows does not tell "Lobby" from "lobby" while Linux does.
 */
final class WorldName {

    static final int MAX_LENGTH = 32;

    /** Folders the server or its files already use, next to the worlds or inside them. */
    private static final Set<String> RESERVED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "plugins", "logs", "cache", "crash-reports", "region", "data", "playerdata", "stats",
            "con", "prn", "aux", "nul")));

    private WorldName() {
    }

    /** Why the name cannot be used, for the player, or null when it is fine. */
    static String problem(String name) {
        if (name == null || name.isEmpty()) {
            return "the name is empty.";
        }
        if (name.length() > MAX_LENGTH) {
            return "the name is longer than " + MAX_LENGTH + " characters.";
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_';
            if (!allowed) {
                return "the name may only hold lowercase letters without accents, digits, - and _ (not \"" + c + "\").";
            }
        }
        if (RESERVED.contains(name)) {
            return "\"" + name + "\" is a name reserved by the server.";
        }
        return null;
    }
}
