package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ELO of the ranked modes, as on the Ranked Bedwars servers (config.yml, elo:): everyone starts
 * at 0, and every 100 ELO is a division (Coal, Iron, Gold...). A win gives a fixed amount and a
 * loss takes one, set by the player's division: much to win and little to lose at the bottom, the
 * other way round at the top, so that the divisions spread the players out. The MVP of the game
 * (the most kills) gets a bonus, won or lost. The ELO never goes below 0. Immutable.
 */
final class EloLadder {

    /** One division: from its ELO up to the next one's. */
    static final class Division {
        private final String name;
        private final String colour;
        private final int min;
        private final int win;
        private final int loss;

        Division(String name, String colour, int min, int win, int loss) {
            this.name = name;
            this.colour = colour;
            this.min = min;
            this.win = win;
            this.loss = loss;
        }

        String name() {
            return name;
        }

        String colour() {
            return colour;
        }

        /** The lowest ELO of the division. */
        int min() {
            return min;
        }

        /** The ELO a win gives in this division. */
        int win() {
            return win;
        }

        /** The ELO a loss takes in this division. */
        int loss() {
            return loss;
        }

        /** "§6Gold". */
        String coloured() {
            return colour + name;
        }
    }

    static final EloLadder DEFAULT = new EloLadder(defaultDivisions(), 5);

    private final List<Division> divisions;
    private final int mvpBonus;

    EloLadder(List<Division> divisions, int mvpBonus) {
        if (divisions.isEmpty()) {
            throw new IllegalArgumentException("at least one division");
        }
        List<Division> sorted = new ArrayList<>(divisions);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.min(), b.min()));
        if (sorted.get(0).min() != 0) {
            throw new IllegalArgumentException("the first division starts at 0 ELO");
        }
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).min() == sorted.get(i - 1).min()) {
                throw new IllegalArgumentException("two divisions start at " + sorted.get(i).min() + " ELO");
            }
        }
        if (mvpBonus < 0) {
            throw new IllegalArgumentException("the MVP bonus is 0 or more");
        }
        this.divisions = Collections.unmodifiableList(sorted);
        this.mvpBonus = mvpBonus;
    }

    private static List<Division> defaultDivisions() {
        List<Division> divisions = new ArrayList<>();
        divisions.add(new Division("Coal", "§8", 0, 35, 5));
        divisions.add(new Division("Iron", "§f", 100, 30, 10));
        divisions.add(new Division("Gold", "§6", 200, 30, 15));
        divisions.add(new Division("Diamond", "§b", 300, 25, 15));
        divisions.add(new Division("Emerald", "§2", 400, 25, 20));
        divisions.add(new Division("Sapphire", "§3", 500, 20, 20));
        divisions.add(new Division("Ruby", "§4", 600, 20, 25));
        divisions.add(new Division("Crystal", "§d", 700, 15, 25));
        divisions.add(new Division("Opal", "§9", 800, 15, 30));
        divisions.add(new Division("Amethyst", "§5", 900, 10, 30));
        divisions.add(new Division("Obsidian", "§0", 1000, 10, 35));
        return divisions;
    }

    /** The divisions, the lowest first. */
    List<Division> divisions() {
        return divisions;
    }

    int mvpBonus() {
        return mvpBonus;
    }

    /** The division of this ELO. */
    Division division(int elo) {
        Division found = divisions.get(0);
        for (Division division : divisions) {
            if (elo >= division.min()) {
                found = division;
            }
        }
        return found;
    }

    /** The ELO after a game: won or lost, as the MVP or not. Never below 0. */
    int after(int elo, boolean won, boolean mvp) {
        Division division = division(elo);
        int change = won ? division.win() : -division.loss();
        if (mvp) {
            change += mvpBonus;
        }
        return Math.max(0, elo + change);
    }

    /** "§6Gold 245": the division and the ELO. */
    String format(int elo) {
        Division division = division(elo);
        return division.colour() + division.name() + " " + elo;
    }
}
