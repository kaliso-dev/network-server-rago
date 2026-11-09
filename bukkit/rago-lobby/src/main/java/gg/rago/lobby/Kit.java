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
        int[] to = placement(layout);
        KitItem[] arranged = new KitItem[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            if (to[slot] >= 0) {
                arranged[to[slot]] = items[slot];
            }
        }
        return arranged;
    }

    /** Where each slot of the kit goes with this layout (-1 for an empty slot), as {@link #arrange} places it. */
    int[] placement(int[] layout) {
        int[] to = defaultLayout();
        boolean[] taken = new boolean[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            int target = layout != null && slot < layout.length ? layout[slot] : -1;
            if (items[slot] != null && target >= 0 && target < SLOTS && !taken[target]) {
                to[slot] = target;
                taken[target] = true;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items[slot] != null && to[slot] < 0 && !taken[slot]) {
                to[slot] = slot;
                taken[slot] = true;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items[slot] != null && to[slot] < 0) {
                for (int free = 0; free < SLOTS; free++) {
                    if (!taken[free]) {
                        to[slot] = free;
                        taken[free] = true;
                        break;
                    }
                }
            }
        }
        return to;
    }

    /** The kit of these stacks, slot for slot (the first 36): a kit that a game makes itself. */
    static Kit of(org.bukkit.inventory.ItemStack[] stacks) {
        List<KitItem> items = new java.util.ArrayList<>();
        for (int slot = 0; slot < SLOTS && slot < stacks.length; slot++) {
            items.add(KitItem.of(stacks[slot]));
        }
        return new Kit(items);
    }

    /** True if every item of the kit was found in the layout: the inventory holds the whole kit. */
    boolean complete(int[] layout) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items[slot] != null && (slot >= layout.length || layout[slot] < 0)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The layout of an inventory the player arranged in the kit editor: where each item of the kit
     * is. Equal stacks are interchangeable. An item that is not found keeps the kit's slot (-1).
     */
    int[] layoutOf(KitItem[] edited) {
        return layoutOf(edited, false);
    }

    /**
     * The same, where a stack may hold fewer items than the kit gave (anyAmount): an inventory
     * arranged in a game, after a few blocks were placed.
     */
    int[] layoutOf(KitItem[] edited, boolean anyAmount) {
        int[] layout = defaultLayout();
        boolean[] used = new boolean[SLOTS];
        // An item still in its own slot stays there, so that equal stacks keep their places.
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items[slot] != null && slot < edited.length && same(items[slot], edited[slot], anyAmount)) {
                layout[slot] = slot;
                used[slot] = true;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items[slot] == null || layout[slot] >= 0) {
                continue;
            }
            for (int where = 0; where < SLOTS && where < edited.length; where++) {
                if (!used[where] && same(items[slot], edited[where], anyAmount)) {
                    layout[slot] = where;
                    used[where] = true;
                    break;
                }
            }
        }
        return layout;
    }

    /**
     * The kit's item, found in the inventory: the same block, in any colour for the blocks a game
     * dyes in its team's colour; the same amount, or no more with anyAmount.
     */
    private static boolean same(KitItem kit, KitItem found, boolean anyAmount) {
        if (found == null || kit.material() != found.material()) {
            return false;
        }
        if (kit.data() != found.data() && !dyed(kit.material())) {
            return false;
        }
        return anyAmount ? found.amount() <= kit.amount() : found.amount() == kit.amount();
    }

    /** The blocks a game gives in its team's colour. */
    static boolean dyed(org.bukkit.Material type) {
        switch (type) {
            case WOOL:
            case STAINED_CLAY:
            case STAINED_GLASS:
            case STAINED_GLASS_PANE:
            case CARPET:
                return true;
            default:
                return false;
        }
    }
}
