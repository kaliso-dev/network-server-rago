package gg.rago.lobby.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The record of a mode with records (a training), as the menus of the lobby show it under the mode
 * in the place of its Daily Streak ({@link LobbyService#setModeRecords}): what the record is, its
 * best players and the viewer's best. Immutable.
 */
public final class ModeRecord {

    /** What the record is: "Island clutch", "3 layers, wood side". */
    public final String name;
    /** Its best players, the best first: their name and their score as it reads ("12 clutches", "4.512 s"). */
    public final List<Map.Entry<String, String>> top;
    /** The viewer's best as it reads, or null when they have none. */
    public final String best;
    /** The viewer's place on the record, from 1; 0 when they have no score. */
    public final int place;

    public ModeRecord(String name, List<Map.Entry<String, String>> top, String best, int place) {
        this.name = name;
        this.top = Collections.unmodifiableList(new ArrayList<>(top));
        this.best = best;
        this.place = place;
    }
}
