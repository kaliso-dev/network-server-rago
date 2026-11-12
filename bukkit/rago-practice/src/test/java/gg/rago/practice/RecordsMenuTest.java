package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/** /records, the wall of the records in the lobby, and the boards of the week the lobby rewards. */
class RecordsMenuTest {

    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CARL = new UUID(0, 3);
    private static final UUID DAVE = new UUID(0, 4);

    private static RecordCatalog.Board board(String id) {
        for (RecordCatalog.Board board : RecordCatalog.boards(RecordCatalog.ALL)) {
            if (board.id.equals(id)) {
                return board;
            }
        }
        throw new AssertionError("no board " + id);
    }

    private static List<String> plain(List<String> lines) {
        List<String> plain = new ArrayList<>();
        for (String line : lines) {
            plain.add(ChatColor.stripColor(line));
        }
        return plain;
    }

    private static RecordBook book() {
        RecordBook book = new RecordBook();
        RecordCatalog.define(book);
        return book;
    }

    @Test
    void everyTrainingAndItsBoardsOnce() {
        Set<String> modes = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (RecordCatalog.Entry entry : RecordCatalog.ALL) {
            assertTrue(modes.add(entry.modeId), entry.modeId);
            assertFalse(entry.boards.isEmpty(), entry.modeId);
            for (RecordCatalog.Board board : entry.boards) {
                assertTrue(ids.add(board.id), board.id);
                assertEquals(entry.modeId, board.modeId);
                assertEquals(entry.title, board.title);
                assertTrue(board.id.startsWith(entry.modeId + "."), board.id);
                assertFalse(board.id.contains("_"), "records.yml writes the dots as _: " + board.id);
                assertEquals(board.id, Records.fromKey(Records.toKey(board.id)), "the file gives it back");
            }
        }
        assertEquals(14, RecordCatalog.ALL.size());
        assertEquals("clutch", RecordCatalog.ALL.get(0).modeId, "the block clutch first");
        assertEquals(Arrays.asList("block-edit.bot.speed1", "block-edit.bot.speed2", "block-edit.bot.speed3"), idsOf(RecordCatalog.of("block-edit")));
        assertEquals("Against the bot, speed 2", board("block-edit.bot.speed2").label);
        assertEquals("block-edit.bot", board("block-edit.bot.speed2").challenge, "the challenge of the day: at the speed by default");
        assertNull(board("block-edit.bot.speed3").challenge);
        assertEquals("clutch.island", board("clutch.island").challenge);
        assertEquals(Arrays.asList("strafe", "jump", "circle"), variantsOf(RecordCatalog.of("aim-trainer")));
        assertNull(RecordCatalog.of("boxing"), "a duel has no records");

        List<RecordCatalog.Entry> played = RecordCatalog.played(Arrays.asList("boxing", "aim-trainer", "(block-in)", "clutch"));
        assertEquals(2, played.size(), "the closed ones (in brackets) and the duels stay out");
        assertEquals("clutch", played.get(0).modeId, "in the order of the catalog");
        assertEquals("aim-trainer", played.get(1).modeId);
    }

    @Test
    void theFileGivesBackTheBoardsWithSeveralDots() {
        assertEquals("block-edit_bot_speed2", Records.toKey("block-edit.bot.speed2"));
        assertEquals("block-edit.bot.speed2", Records.fromKey("block-edit_bot_speed2"), "before: block-edit.bot_speed2");
        assertEquals("clutch.island-blocks.2026w46", Records.fromKey("clutch_island-blocks_2026w46"));
        assertEquals("clutch.island.kb3.hits1", Records.fromKey("clutch_island_kb3_hits1"), "the old boards fold again");
        assertEquals("clutch.island", Records.fromKey("clutch_island"));
    }

    @Test
    void aTimeRanksTheShortestFirstFromTheStart() {
        RecordBook book = book();
        String time = "kb-displace-bot.still";
        assertTrue(board(time).timed());
        book.load(time, ALICE, "Alice", 3000);
        book.load(time, BOB, "Bob", 2000);
        assertEquals(BOB, book.ranked(time).get(0).player, "read from the file, ranked before anyone plays");
        book.load("clutch.island", ALICE, "Alice", 3);
        book.load("clutch.island", BOB, "Bob", 9);
        assertEquals(BOB, book.ranked("clutch.island").get(0).player, "a count: the most first");
        assertEquals(2, book.count("clutch.island"));
        assertEquals(0, book.count("clutch-top-fight.top"));
    }

    @Test
    void theFirstPageAndATraining() {
        RecordBook book = book();
        List<RecordCatalog.Board> all = RecordCatalog.boards(RecordCatalog.ALL);
        book.load("clutch.island", ALICE, "Alice", 12);
        book.load("clutch.island", BOB, "Bob", 20);
        book.load("aim-trainer.strafe", ALICE, "Alice", 40);
        for (int i = 0; i < 12; i++) {
            book.load("aim-trainer.jump", new UUID(1, i), "P" + i, 100 - i);
        }
        book.load("aim-trainer.jump", ALICE, "Alice", 50);
        assertArrayEquals(new int[] {3, 1, 2}, RecordsMenu.summary(book, all, ALICE), "3 boards, 1 first place, 2 in a top 10");
        assertArrayEquals(new int[] {0, 0, 0}, RecordsMenu.summary(book, all, DAVE));

        List<String> clutch = plain(RecordsMenu.trainingLore(book, RecordCatalog.of("clutch"), ALICE));
        assertEquals("Island clutch: 12 clutches #2", clutch.get(0));
        List<String> aim = plain(RecordsMenu.trainingLore(book, RecordCatalog.of("aim-trainer"), ALICE));
        assertEquals(Arrays.asList("Strafing target: 40 hits #1", "Strafing and jumping: 50 hits #13", "Circling target: -", "",
                "Click: the top 10 of each"), aim);

        Set<UUID> friends = new HashSet<>(Arrays.asList(BOB, CARL));
        List<String> lore = plain(RecordsMenu.boardLore(book, board("clutch.island"), ALICE, "Alice", true, friends, true));
        assertEquals("✦ The challenge of the day", lore.get(0));
        assertEquals("Your best: 12 clutches #2 of 2", lore.get(1));
        assertEquals("Among your friends: #2 of 2", lore.get(2), "Carl has no score: not counted");
        assertEquals("Top 10", lore.get(4));
        assertEquals("#1 Bob - 20 clutches", lore.get(5));
        assertEquals("#2 Alice - 12 clutches", lore.get(6));
        assertEquals("Click to play it", lore.get(lore.size() - 1));

        List<String> far = plain(RecordsMenu.boardLore(book, board("aim-trainer.jump"), ALICE, "Alice", false,
                Collections.<UUID>emptySet(), false));
        assertEquals("Best: 50 hits #13 of 13", far.get(0), "another player's page");
        assertEquals("Top 10", far.get(2), "no friends line on another's page");
        assertEquals("#10 P9 - 91 hits", far.get(12));
        assertEquals("...", far.get(13));
        assertEquals("#13 Alice - 50 hits", far.get(14), "the player under the top, further down");

        List<String> none = plain(RecordsMenu.boardLore(book, board("aim-trainer.circle"), DAVE, "Dave", true, friends, false));
        assertEquals(Arrays.asList("No record yet", "", "Top 10", "Nobody yet: be the first!", "", "Click to play it"), none);
        assertEquals("#3", ChatColor.stripColor(RecordsMenu.place(3)));
        assertTrue(RecordsMenu.place(1).startsWith(ChatColor.GOLD.toString()));
        assertTrue(RecordsMenu.place(4).startsWith(ChatColor.GRAY.toString()));
    }

    @Test
    void theirPlaceAmongTheirFriends() {
        RecordBook book = book();
        book.load("clutch.island", ALICE, "Alice", 5);
        book.load("clutch.island", BOB, "Bob", 9);
        book.load("clutch.island", CARL, "Carl", 7);
        book.load("clutch.island", DAVE, "Dave", 1);
        List<RecordBook.Entry> ranked = book.ranked("clutch.island");
        assertArrayEquals(new int[] {2, 3}, RecordsMenu.amongFriends(ranked, ALICE, new HashSet<>(Arrays.asList(CARL, DAVE))));
        assertArrayEquals(new int[] {1, 1}, RecordsMenu.amongFriends(ranked, ALICE, Collections.<UUID>emptySet()));
        assertArrayEquals(new int[] {0, 2}, RecordsMenu.amongFriends(ranked, new UUID(9, 9), new HashSet<>(Arrays.asList(BOB, DAVE))));
    }

    @Test
    void anotherPlayerIsFoundByTheNameOfTheirRecords() {
        RecordBook book = book();
        book.load("clutch.island", ALICE, "Alice", 5);
        Map.Entry<UUID, String> found = RecordsMenu.find(book, "aLiCe");
        assertEquals(ALICE, found.getKey());
        assertEquals("Alice", found.getValue(), "as written");
        assertNull(RecordsMenu.find(book, "Zed"));
    }

    @Test
    void theWallShowsABoardAfterTheOther() {
        RecordBook book = book();
        List<RecordCatalog.Board> boards = RecordCatalog.boards(RecordCatalog.played(Arrays.asList("clutch", "aim-trainer")));
        assertEquals(4, boards.size());
        assertEquals(boards, RecordsMenu.onWall(book, boards), "no record yet: all of them");
        book.load("aim-trainer.jump", ALICE, "Alice", 30);
        book.load("clutch.island", BOB, "Bob", 4);
        List<RecordCatalog.Board> shown = RecordsMenu.onWall(book, boards);
        assertEquals(Arrays.asList("clutch.island", "aim-trainer.jump"), Arrays.asList(shown.get(0).id, shown.get(1).id),
                "only the boards with a record, in order");

        long turn = RecordsMenu.TURN_MS;
        assertEquals(0, RecordsMenu.wallIndex(0, null, 3));
        assertEquals(1, RecordsMenu.wallIndex(turn, null, 3), "everyone the same, the next one every 8 s");
        assertEquals(0, RecordsMenu.wallIndex(3 * turn + 5, null, 3));
        RecordsMenu.Pick pick = new RecordsMenu.Pick(2, 10_000);
        assertEquals(2, RecordsMenu.wallIndex(10_000, pick, 3), "the board clicked");
        assertEquals(2, RecordsMenu.wallIndex(10_000 + RecordsMenu.HOLD_MS - 1, pick, 3), "held while they read it");
        assertEquals(0, RecordsMenu.wallIndex(10_000 + RecordsMenu.HOLD_MS, pick, 3), "then the next ones from it");
        assertEquals(1, RecordsMenu.wallIndex(10_000 + RecordsMenu.HOLD_MS + turn, pick, 3));
        assertEquals(1, RecordsMenu.wallIndex(0, new RecordsMenu.Pick(4, 0), 3), "a board too far comes round");
        assertEquals(0, RecordsMenu.wallIndex(5, null, 0));
    }

    @Test
    void theLinesOfTheWall() {
        RecordBook book = book();
        for (int i = 0; i < 11; i++) {
            book.load("kb-displace-bot.still", new UUID(2, i), "P" + i, 1000 + 100 * i);
        }
        book.load("kb-displace-bot.still", ALICE, "Alice", 5000);
        List<String> lines = plain(RecordsMenu.wallLines(book, board("kb-displace-bot.still"), ALICE, 4, 9, true));
        assertEquals(RecordsMenu.WALL_LINES, lines.size());
        assertEquals("KB displace against a bot", lines.get(0));
        assertEquals("Bot in the notch", lines.get(1));
        assertEquals("", lines.get(2));
        assertEquals("1. P0 - 1.000 s", lines.get(3), "a time: the shortest first");
        assertEquals("10. P9 - 1.900 s", lines.get(12));
        assertEquals("", lines.get(13));
        assertEquals("You: #12 - 5.000 s", lines.get(14));
        assertEquals("✦ The challenge of the day", lines.get(15));
        assertEquals("Practice records 5/9", lines.get(16));
        assertEquals("» Left click: next board", lines.get(17));
        assertEquals("» Right click: all your records", lines.get(18));
        for (String line : RecordsMenu.wallLines(book, board("kb-displace-bot.still"), ALICE, 4, 9, true)) {
            assertTrue(line.length() <= 64, "a name of 1.8: " + line);
        }

        List<String> empty = plain(RecordsMenu.wallLines(book, board("clutch.island"), BOB, 0, 1, false));
        assertEquals(RecordsMenu.WALL_LINES, empty.size());
        assertEquals("Nobody yet: be the first!", empty.get(3));
        assertEquals("", empty.get(4));
        assertEquals("You: no record yet", empty.get(14));
        assertEquals("", empty.get(15));
    }

    @Test
    void theBiggestClutchesOfAWeekOfTheLobby() {
        assertEquals("2026w46", ClutchTraining.week("2026-W46"));
        assertEquals("2026w5", ClutchTraining.week("2026-W05"), "as the boards write it");
        assertEquals(ClutchTraining.week(java.time.LocalDate.of(2027, 1, 1)), ClutchTraining.week("2026-W53"));
        assertNull(ClutchTraining.week("2026w46"));
        assertNull(ClutchTraining.week((String) null));

        RecordBook book = book();
        book.load("clutch.island-blocks.2026w46", ALICE, "Alice", 52);
        book.load("clutch.island-blocks.2026w46", BOB, "Bob", 30);
        book.load("clutch.island-blocks.2026w47", CARL, "Carl", 90);
        List<Map.Entry<UUID, String>> week = ClutchTraining.weekRanking(book, "clutch.island", "2026-W46");
        assertEquals(2, week.size(), "that week only");
        assertEquals(ALICE, week.get(0).getKey(), "the biggest first");
        assertEquals("5.2 blocks", week.get(0).getValue());
        assertEquals("3.0 blocks", week.get(1).getValue());
        assertTrue(ClutchTraining.weekRanking(book, "clutch.island", "2026-W40").isEmpty());
        assertTrue(ClutchTraining.weekRanking(book, "clutch.island", "nope").isEmpty());

        assertEquals(0, book.dropAllBut("clutch.island-blocks.", "clutch.island-blocks.2026w47", "clutch.island-blocks.2026w46"),
                "this week and the last one stay");
        assertEquals(52L, book.best("clutch.island-blocks.2026w46", ALICE));
        book.load("clutch.island-blocks.2026w44", DAVE, "Dave", 10);
        assertEquals(1, book.dropAllBut("clutch.island-blocks.", "clutch.island-blocks.2026w47", "clutch.island-blocks.2026w46"));
        assertNull(book.best("clutch.island-blocks.2026w44", DAVE), "two weeks ago: gone");
        assertEquals(90L, book.best("clutch.island-blocks.2026w47", CARL));
    }

    private static List<String> idsOf(RecordCatalog.Entry entry) {
        List<String> ids = new ArrayList<>();
        for (RecordCatalog.Board board : entry.boards) {
            ids.add(board.id);
        }
        return ids;
    }

    private static List<String> variantsOf(RecordCatalog.Entry entry) {
        List<String> ids = new ArrayList<>();
        for (RecordCatalog.Board board : entry.boards) {
            ids.add(board.variant.id);
        }
        return ids;
    }
}
