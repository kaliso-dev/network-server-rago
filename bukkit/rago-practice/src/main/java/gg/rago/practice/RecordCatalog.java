package gg.rago.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

/**
 * Every training with records, in the order of /records and of the wall of the practice records:
 * its mode, its name (that of the lobby's menus), an icon close to the lobby's, and its boards, one
 * per variant (the block edit: one per speed of the bot). The boards of the weeks (the biggest
 * clutches) are not records and stay out. Immutable.
 */
final class RecordCatalog {

    /** One board of records: "clutch.island", the variant it plays, its names in the menus. */
    static final class Board {
        /** "clutch.island", "block-edit.bot.speed2": the board of records.yml. */
        final String id;
        final String modeId;
        /** The training's name: "Block clutch". */
        final String title;
        /** What a time or a count means, and the variant a click plays. */
        final Training.Variant variant;
        /** "Island clutch", "Against the bot, speed 2". */
        final String label;
        /** The challenge of the day played on this board ("block-edit.bot" at speed 2), or null. */
        final String challenge;

        Board(String modeId, String title, Training.Variant variant, String suffix, String label, boolean challenge) {
            this.modeId = modeId;
            this.title = title;
            this.variant = variant;
            this.id = modeId + "." + variant.id + suffix;
            this.label = label;
            this.challenge = challenge ? modeId + "." + variant.id : null;
        }

        /** "4.512 s" or "12 clutches". */
        String format(long value) {
            return Training.format(variant, value);
        }

        /** True for a time: the lowest is the best. */
        boolean timed() {
            return variant.timed;
        }
    }

    /** A training: its mode, its name, its icon, its boards. */
    static final class Entry {
        final String modeId;
        final String title;
        final Material icon;
        final List<Board> boards;

        Entry(String modeId, String title, Material icon, List<Board> boards) {
            this.modeId = modeId;
            this.title = title;
            this.icon = icon;
            this.boards = Collections.unmodifiableList(boards);
        }
    }

    /** Every training, the block clutch first. */
    static final List<Entry> ALL;

    static {
        List<Entry> all = new ArrayList<>();
        all.add(entry("clutch", "Block clutch", Material.LADDER, ClutchTraining.ISLAND));
        all.add(entry("clutch-top-fight", "Block clutch: top fight", Material.WOOL, ClutchTraining.TOP));
        all.add(entry("block-in", "Block in practice", Material.ENDER_STONE, BlockInTraining.DEFENSES));
        all.add(entry("fast-defense", "Fast defense", Material.OBSIDIAN, FastDefenseTraining.DEFENSES));
        all.add(entry("extend-practice", "Extend practice", Material.QUARTZ_BLOCK, ExtendTraining.variants()));
        all.add(entry("block-extend", "Block extend", Material.SANDSTONE, BlockExtendTraining.variants()));
        all.add(entry("ladder-practice", "Ladder practice", Material.LADDER, LadderTraining.variants()));
        all.add(entry("pearl-practice", "Pearl practice", Material.ENDER_PEARL, PearlTraining.variants()));
        all.add(entry("fireball-practice", "Fireball practice", Material.FIREBALL, FireballTraining.VARIANTS));
        all.add(entry("aim-trainer", "Aim trainer", Material.BOW, AimTrainer.TARGETS));
        all.add(entry("bow-training", "Bow training", Material.ARROW, BowTraining.BOTS));
        all.add(entry("combo-bot", "Combo bot", Material.SKULL_ITEM, ComboBot.variants()));
        all.add(entry("kb-displace-bot", "KB displace against a bot", Material.STICK, KbDisplaceBot.STILL));
        String edit = "Block edit";
        List<Board> edits = new ArrayList<>();
        for (int speed = 1; speed <= BlockEditTraining.SPEEDS; speed++) {
            edits.add(new Board("block-edit", edit, BlockEditTraining.BOT, ".speed" + speed, BlockEditTraining.BOT.label + ", speed " + speed,
                    speed == BlockEditTraining.DEFAULT_SPEED));
        }
        all.add(new Entry("block-edit", edit, Material.SHEARS, edits));
        ALL = Collections.unmodifiableList(all);
    }

    private RecordCatalog() {
    }

    private static Entry entry(String modeId, String title, Material icon, Training.Variant... variants) {
        List<Board> boards = new ArrayList<>();
        for (Training.Variant variant : variants) {
            boards.add(new Board(modeId, title, variant, "", variant.label, true));
        }
        return new Entry(modeId, title, icon, boards);
    }

    /** The trainings this server plays (their modes are hosted), in order. */
    static List<Entry> played(List<String> hosted) {
        List<Entry> played = new ArrayList<>();
        for (Entry entry : ALL) {
            if (hosted.contains(entry.modeId)) {
                played.add(entry);
            }
        }
        return played;
    }

    /** Every board of these trainings, in order. */
    static List<Board> boards(List<Entry> entries) {
        List<Board> boards = new ArrayList<>();
        for (Entry entry : entries) {
            boards.addAll(entry.boards);
        }
        return boards;
    }

    /** The training of this mode, or null. */
    static Entry of(String modeId) {
        for (Entry entry : ALL) {
            if (entry.modeId.equals(modeId)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * How each board ranks its scores (a time: lower is better), known from the start: a board read
     * from records.yml is ranked right before anyone plays its training.
     */
    static void define(RecordBook book) {
        for (Entry entry : ALL) {
            for (Board board : entry.boards) {
                book.define(board.id, board.variant.timed);
            }
        }
    }
}
