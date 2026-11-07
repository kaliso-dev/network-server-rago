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

    /** Why the name cannot be used, in French for the player, or null when it is fine. */
    static String problem(String name) {
        if (name == null || name.isEmpty()) {
            return "le nom est vide.";
        }
        if (name.length() > MAX_LENGTH) {
            return "le nom fait plus de " + MAX_LENGTH + " caractères.";
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_';
            if (!allowed) {
                return "le nom ne doit contenir que des minuscules sans accent, des chiffres, - et _ (pas « " + c + " »).";
            }
        }
        if (RESERVED.contains(name)) {
            return "« " + name + " » est un nom réservé par le serveur.";
        }
        return null;
    }
}
