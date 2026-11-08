package gg.rago.lobby;

import java.util.Arrays;
import java.util.List;

/**
 * The items a mode gives (config.yml, kit: under the mode): 36 slots, the hotbar (0 to 8) then
 * the inventory (9 to 35), from the top row. A player's layout says, for each slot of the kit,
 * where they want its item: {@code layout[slot]}, -1 for "where the kit puts it". The items stay
 * the server's; a player only moves them. Immutable.
 */
final class Kit {

    /** The slots of a player's inventory: the hotbar then the three rows above. */
    static final int SLOTS = 36;

    private final KitItem[] items;

    Kit(List<KitItem> items) {
        if (items.size() > SLOTS) {
            throw new IllegalArgumentException(SLOTS + " slots at most");
        }
        this.items = Arrays.copyOf(items.toArray(new KitItem[0]), SLOTS);
    }

    /** The item of this slot, or null. */
    KitItem item(int slot) {
        return items[slot];
    }

    boolean isEmpty() {
        for (KitItem item : items) {
            if (item != null) {
                return false;
            }
        }
        return true;
    }

    /** The kit's own layout: every item where the kit puts it. */
    static int[] defaultLayout() {
        int[] layout = new int[SLOTS];
        Arrays.fill(layout, -1);
        return layout;
    }

    /** True when every item stays where the kit puts it: nothing to keep for the player. */
    static boolean isDefault(int[] layout) {
        for (int slot = 0; slot < layout.length; slot++) {
            if (layout[slot] >= 0 && layout[slot] != slot) {
                return false;
            }
        }
        return true;
    }

    /**
     * The kit as a player arranged it. An item goes where the layout says when that slot is free,
     * else back to its own slot, else (once every other item is placed) to the first free slot: a
     * layout made for an older kit still gives every item.
     */
    KitItem[] arrange(int[] layout) {
        KitItem[] arranged = new KitItem[SLOTS];
        boolean[] placed = new boolean[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            int target = layout != null && slot < layout.length ? layout[slot] : -1;
            if (items[slot] != null && target >= 0 && target < SLOTS && arranged[target] == null) {
                arranged[target] = items[slot];
                placed[slot] = true;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items[slot] != null && !placed[slot] && arranged[slot] == null) {
                arranged[slot] = items[slot];
                placed[slot] = true;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            int target = items[slot] == null || placed[slot] ? -1 : firstFree(arranged);
            if (target >= 0) {
                arranged[target] = items[slot];
            }
        }
        return arranged;
    }

    /**
     * The layout of an inventory the player arranged in the kit editor: where each item of the kit
     * is. Equal stacks are interchangeable. An item that is not found keeps the kit's slot (-1).
     */
    int[] layoutOf(KitItem[] edited) {
        int[] layout = defaultLayout();
        boolean[] used = new boolean[SLOTS];
        // An item still in its own slot stays there, so that equal stacks keep their places.
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items[slot] != null && slot < edited.length && items[slot].equals(edited[slot])) {
                layout[slot] = slot;
                used[slot] = true;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items[slot] == null || layout[slot] >= 0) {
                continue;
            }
            for (int where = 0; where < SLOTS && where < edited.length; where++) {
                if (!used[where] && items[slot].equals(edited[where])) {
                    layout[slot] = where;
                    used[where] = true;
                    break;
                }
            }
        }
        return layout;
    }

    private static int firstFree(KitItem[] slots) {
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] == null) {
                return slot;
            }
        }
        return -1;
    }
}
