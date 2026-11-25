package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartyBookTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @Test
    void anInviteStartsThePartyAndLastsAMinute() {
        PartyBook book = new PartyBook();
        assertEquals(PartyBook.Invite.SELF, book.invite(A, A, 0));
        assertNull(book.of(A));
        assertEquals(PartyBook.Invite.SENT, book.invite(A, B, 0));
        assertEquals(Arrays.asList(A), book.of(A).members());
        assertNull(book.together(A), "alone while the invite runs");
        assertEquals(PartyBook.Invite.ALREADY_INVITED, book.invite(A, B, 1000));
        assertEquals(PartyBook.Accept.NO_INVITE, book.accept(C, A, 1000));
        assertEquals(PartyBook.Accept.NO_INVITE, book.accept(B, A, PartyBook.INVITE_MS + 1), "expired");
        assertEquals(PartyBook.Accept.JOINED, book.accept(B, A, PartyBook.INVITE_MS));
        assertEquals(Arrays.asList(A, B), book.together(B).members());
        assertTrue(book.of(A).isLeader(A));
        assertEquals(PartyBook.Invite.NOT_LEADER, book.invite(B, C, 0));
        assertEquals(PartyBook.Invite.ALREADY_MEMBER, book.invite(A, B, 0));
    }

    @Test
    void aPartyOfOneWithoutInviteIsDisbanded() {
        PartyBook book = new PartyBook();
        book.invite(A, B, 0);
        List<PartyBook.Expired> expired = book.expire(PartyBook.INVITE_MS + 1);
        assertEquals(1, expired.size());
        assertEquals(B, expired.get(0).invited);
        assertEquals(A, expired.get(0).leader);
        assertTrue(expired.get(0).last);
        assertTrue(expired.get(0).party.isDisbanded());
        assertNull(book.of(A));
    }

    @Test
    void theNextPlayerLeadsWhenTheLeaderLeaves() {
        PartyBook book = new PartyBook();
        book.invite(A, B, 0);
        book.invite(A, C, 0);
        book.accept(B, A, 1);
        book.accept(C, A, 1);
        PartyBook.Party party = book.leave(A);
        assertFalse(party.isDisbanded());
        assertEquals(B, party.leader());
        assertSame(party, book.of(C));
        assertTrue(book.kick(B, C));
        assertTrue(party.isDisbanded(), "nobody left with B");
        assertNull(book.of(B));
    }

    @Test
    void theLeaderTransfersOrDisbands() {
        PartyBook book = new PartyBook();
        book.invite(A, B, 0);
        book.accept(B, A, 1);
        assertFalse(book.transfer(B, A), "only the leader transfers");
        assertTrue(book.transfer(A, B));
        assertEquals(Arrays.asList(B, A), book.of(A).members());
        assertEquals(PartyBook.Accept.NO_INVITE, book.accept(C, B, 2));
        book.invite(B, C, 2);
        assertEquals(PartyBook.Accept.JOINED, book.accept(C, B, 3));
        assertEquals(PartyBook.Accept.IN_A_PARTY, acceptElsewhere(book));
        assertEquals(Arrays.asList(B, A, C), book.disband(book.of(C)));
        assertNull(book.of(A));
        assertNull(book.of(B));
        assertNull(book.of(C));
    }

    /** C, in B's party, is invited by someone else: they must leave first. */
    private static PartyBook.Accept acceptElsewhere(PartyBook book) {
        UUID d = new UUID(0, 4);
        book.invite(d, C, 3);
        return book.accept(C, d, 4);
    }

    @Test
    void aPartyHoldsSixteenPlayers() {
        // the owner's limit of 22/11: a full map of Bedwars Normal
        assertEquals(16, PartyBook.MAX_SIZE);
        PartyBook book = new PartyBook();
        for (int i = 2; i <= PartyBook.MAX_SIZE; i++) {
            UUID player = new UUID(0, 10 + i);
            assertEquals(PartyBook.Invite.SENT, book.invite(A, player, 0));
            assertEquals(PartyBook.Accept.JOINED, book.accept(player, A, 0));
        }
        assertEquals(PartyBook.MAX_SIZE, book.of(A).size());
        assertEquals(PartyBook.Invite.FULL, book.invite(A, B, 0));
    }

    @Test
    void theJoinButtonRunsTheAccept() {
        String json = Parties.joinButton("RagoUn");
        assertTrue(json.contains("\"value\":\"/party accept RagoUn\""), json);
        assertTrue(json.startsWith("[") && json.endsWith("]"), json);
    }

    /** RagoBot (here C) never leads: a party passes over it, and ends when only it may lead. */
    @Test
    void aPlayerWhoMayNotLeadNeverLeads() {
        PartyBook book = new PartyBook();
        book.setMayLead(player -> !player.equals(C));
        book.invite(A, C, 0);
        book.accept(C, A, 0);
        book.invite(A, B, 0);
        book.accept(B, A, 0);
        assertEquals(Arrays.asList(A, C, B), book.of(A).members());
        assertFalse(book.transfer(A, C), "it may not lead");
        assertTrue(book.of(A).isLeader(A));
        PartyBook.Party party = book.leave(A);
        assertEquals(Arrays.asList(B, C), party.members(), "the next one who may lead leads");
        assertFalse(party.isDisbanded());
        assertTrue(book.of(C).isLeader(B));

        PartyBook alone = new PartyBook();
        alone.setMayLead(player -> !player.equals(C));
        alone.invite(A, C, 0);
        alone.accept(C, A, 0);
        PartyBook.Party left = alone.leave(A);
        assertTrue(left.isDisbanded(), "nobody left who may lead");
        assertNull(alone.of(C));
    }

    /** The owner's ask of 25/11: a member off the server stays in the party for a minute, then is out. */
    @Test
    void aMemberOffTheServerStaysInThePartyForAMinute() {
        PartyBook book = party(A, B, C);
        PartyBook.Party party = book.wentOffline(B, 1000);
        assertSame(book.of(A), party);
        assertTrue(book.isOffline(B));
        assertEquals(Arrays.asList(A, B, C), book.together(A).members(), "still in the party");
        assertTrue(book.offlineTooLong(1000 + PartyBook.OFFLINE_MS).isEmpty(), "a minute, not less");
        assertEquals(Arrays.asList(B), book.offlineTooLong(1000 + PartyBook.OFFLINE_MS + 1));
        book.leave(B);
        assertFalse(book.isOffline(B));
        assertNull(book.of(B), "out after the minute");
        assertEquals(Arrays.asList(A, C), book.of(A).members());
        assertTrue(book.offlineTooLong(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void aMemberBackInTimeIsStillInTheParty() {
        PartyBook book = party(A, B);
        book.wentOffline(B, 0);
        assertSame(book.of(A), book.backOnline(B));
        assertFalse(book.isOffline(B));
        assertTrue(book.offlineTooLong(PartyBook.OFFLINE_MS + 1).isEmpty(), "back: no longer counted");
        assertEquals(Arrays.asList(A, B), book.together(B).members());
        assertNull(book.backOnline(B), "back once");
        assertNull(book.wentOffline(C, 0), "in no party");
        assertFalse(book.isOffline(C));
    }

    /** The leader off the server leads until the minute is over; then the next one on the server leads. */
    @Test
    void theLeaderOffTheServerIsFollowedByOneOnIt() {
        PartyBook book = party(A, B, C);
        book.wentOffline(A, 0);
        book.wentOffline(B, 0);
        assertTrue(book.of(C).isLeader(A), "the leader off the server still leads");
        PartyBook.Party party = book.leave(A);
        assertEquals(Arrays.asList(C, B), party.members(), "the next one on the server leads");
        assertTrue(book.isOffline(B));

        PartyBook all = party(A, B);
        all.wentOffline(A, 0);
        all.wentOffline(B, 0);
        PartyBook.Party left = all.leave(A);
        assertTrue(left.isDisbanded(), "one left, and no invite");
        assertFalse(all.isOffline(B));
        assertNull(all.backOnline(B));
    }

    @Test
    void aMemberOffTheServerIsOutWhenThePartyEndsOrKicksThem() {
        PartyBook book = party(A, B, C);
        book.wentOffline(B, 0);
        assertTrue(book.kick(A, B));
        assertFalse(book.isOffline(B));
        assertNull(book.of(B));
        book.wentOffline(C, 0);
        book.disband(book.of(A));
        assertFalse(book.isOffline(C));
        assertNull(book.backOnline(C));
    }

    /** A party of these players, the first leading. */
    private static PartyBook party(UUID... players) {
        PartyBook book = new PartyBook();
        for (int i = 1; i < players.length; i++) {
            book.invite(players[0], players[i], 0);
            book.accept(players[i], players[0], 0);
        }
        return book;
    }
}
