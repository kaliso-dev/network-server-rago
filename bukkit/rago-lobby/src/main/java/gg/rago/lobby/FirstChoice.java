package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * A choice of the menu of the first arrival, "What do you want to do?" (premiere-venue: in
 * config.yml): a mode to play at once (its queue; a training starts straight away), or a category
 * of the compass to open. Immutable.
 */
final class FirstChoice {

    /** Choices in the menu, at most: one row. */
    static final int MAX = 7;

    private final String id;
    private final String name;
    private final Material icon;
    private final List<String> description;
    private final String modeId;
    private final String categoryId;

    FirstChoice(String id, String name, Material icon, List<String> description, String modeId, String categoryId) {
        if ((modeId == null) == (categoryId == null)) {
            throw new IllegalArgumentException("a mode or a category, one of them");
        }
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = Collections.unmodifiableList(new ArrayList<>(description));
        this.modeId = modeId;
        this.categoryId = categoryId;
    }

    String id() {
        return id;
    }

    String name() {
        return name;
    }

    Material icon() {
        return icon;
    }

    List<String> description() {
        return description;
    }

    /** The mode it plays, or null for a category. */
    String modeId() {
        return modeId;
    }

    /** The category it opens, or null for a mode. */
    String categoryId() {
        return categoryId;
    }

    /**
     * The plugin's own: train a skill (the block clutch), play Ranked Bedwars (1v1), duel a friend
     * (the Duels), fight now (the FFA). The bundled config.yml says the same.
     */
    static List<FirstChoice> defaults() {
        return Arrays.asList(
                new FirstChoice("entrainer", ChatColor.AQUA + "Train a skill", Material.LADDER, grey("Start with the block clutch:",
                        "a hit throws you off, place", "your wool to save yourself."), "clutch", null),
                new FirstChoice("ranked", ChatColor.GOLD + "Play Ranked Bedwars", Material.BED, grey("1v1 on the RBW maps, your ELO",
                        "from the first game. Train", "while you wait."), "ranked-1v1", null),
                new FirstChoice("duel", ChatColor.GREEN + "Duel a friend", Material.IRON_SWORD, grey("/duel <player> challenges a",
                        "friend; or a duel with anyone:", "sumo, boxing, bridge..."), null, "duels"),
                new FirstChoice("arenes", ChatColor.RED + "Fight now", Material.DIAMOND_SWORD, grey("The FFA: no queue, in at once",
                        "with whoever is there."), "ffa", null));
    }

    private static List<String> grey(String... lines) {
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            list.add(ChatColor.GRAY + line);
        }
        return list;
    }
}
