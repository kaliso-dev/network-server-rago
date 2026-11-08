package gg.rago.lobby;

/** What a leaderboard ranks (config.yml, classements: ... tableaux:). */
enum BoardStat {

    /** Games won, of the day, of the week or ever: a click changes the period. */
    WINS("victoires", "Wins", true),
    /** Games played, of the day, of the week or ever. */
    GAMES("parties", "Games played", true),
    /** The longest run of wins reached during the day, the week or ever. */
    STREAK("series", "Best win streak", true),
    /** The ELO of a ranked mode: a click changes the mode. */
    ELO("elo", "Ranked Elo", false);

    private final String key;
    private final String title;
    private final boolean periodic;

    BoardStat(String key, String title, boolean periodic) {
        this.key = key;
        this.title = title;
        this.periodic = periodic;
    }

    /** The name in config.yml. */
    String key() {
        return key;
    }

    /** The title above the board, without colour. */
    String title() {
        return title;
    }

    /** True if a click changes its period; false for the ELO, whose clicks change the mode. */
    boolean periodic() {
        return periodic;
    }

    /** The stat of this config.yml name, or null. */
    static BoardStat of(String key) {
        for (BoardStat stat : values()) {
            if (stat.key.equals(key)) {
                return stat;
            }
        }
        return null;
    }
}
