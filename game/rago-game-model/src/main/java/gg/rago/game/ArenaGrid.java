package gg.rago.game;

import java.util.BitSet;

/**
 * The arena slots of a game server: zones on a line in an empty world, far enough apart that
 * players of two games never see each other (the 1.8 tracking range of a player is 48 blocks).
 * A match takes the lowest free slot and gives it back at the end. Not thread-safe (main thread).
 */
public final class ArenaGrid {

    private final int slots;
    private final int spacing;
    private final BitSet used = new BitSet();

    public ArenaGrid(int slots, int spacing) {
        if (slots < 1 || spacing < 100) {
            throw new IllegalArgumentException("at least one slot, 100 blocks apart or more");
        }
        this.slots = slots;
        this.spacing = spacing;
    }

    /** The lowest free slot, now taken, or -1 if every slot is in use. */
    public int acquire() {
        int slot = used.nextClearBit(0);
        if (slot >= slots) {
            return -1;
        }
        used.set(slot);
        return slot;
    }

    /** Takes this slot if it is free: true if it is now taken. */
    public boolean take(int slot) {
        if (slot < 0 || slot >= slots || used.get(slot)) {
            return false;
        }
        used.set(slot);
        return true;
    }

    public void release(int slot) {
        if (slot < 0 || slot >= slots || !used.get(slot)) {
            throw new IllegalArgumentException("slot " + slot + " is not in use");
        }
        used.clear(slot);
    }

    public boolean inUse(int slot) {
        return used.get(slot);
    }

    public int inUse() {
        return used.cardinality();
    }

    public int slots() {
        return slots;
    }

    /** X of the centre of a slot; every slot is centred on Z = 0. */
    public int centreX(int slot) {
        return slot * spacing;
    }
}
