package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The parties of the lobby, as on Hypixel: a player invites others (the party starts with the first
 * invite), each invite lasts a minute, the leader kicks, transfers or disbands. Who is in which party
 * only: the messages are {@link Parties}'. No Bukkit here, main thread only.
 */
final class PartyBook {

    static final long INVITE_MS = 60_000;
    /**
     * The players of a party at most: 16, the owner's limit of 22/11 (a full map of Bedwars Normal,
     * 2 x 8 on a map of 2s, 4 x 4 on a map of 4s); 8 before, as on Hypixel without a rank.
     */
    static final int MAX_SIZE = 16;

    /** A party: its leader first, then the others in the order they joined. */
    static final class Party {
        /** The id the party queues with: it does not change with the leader. */
        private final UUID id = UUID.randomUUID();
        private final List<UUID> members = new ArrayList<>();
        /** Invited player, then when the invite expires. */
        private final Map<UUID, Long> invites = new LinkedHashMap<>();
        private boolean disbanded;

        Party(UUID leader) {
            members.add(leader);
        }

        UUID id() {
            return id;
        }

        UUID leader() {
            return members.get(0);
        }

        boolean isLeader(UUID player) {
            return leader().equals(player);
        }

        /** The leader first. */
        List<UUID> members() {
            return Collections.unmodifiableList(new ArrayList<>(members));
        }

        int size() {
            return members.size();
        }

        boolean contains(UUID player) {
            return members.contains(player);
        }

        boolean isInvited(UUID player, long nowMillis) {
            Long expiry = invites.get(player);
            return expiry != null && expiry >= nowMillis;
        }

        boolean isDisbanded() {
            return disbanded;
        }
    }

    enum Invite { SENT, SELF, NOT_LEADER, ALREADY_MEMBER, ALREADY_INVITED, FULL }

    enum Accept { JOINED, NO_INVITE, IN_A_PARTY, FULL }

    /**
     * An invite that ran out: the party (maybe disbanded with it, when nobody else was in), its
     * leader, who was invited, and whether it was the party's last expired invite of this time.
     */
    static final class Expired {
        final Party party;
        final UUID leader;
        final UUID invited;
        boolean last;

        Expired(Party party, UUID leader, UUID invited) {
            this.party = party;
            this.leader = leader;
            this.invited = invited;
        }
    }

    private final Map<UUID, Party> partyOf = new HashMap<>();
    /** Who may lead a party: everyone but RagoBot ({@link RagoBots}). */
    private java.util.function.Predicate<UUID> mayLead = player -> true;

    /** Who may lead a party: the leader who goes passes the party to the next one who may. */
    void setMayLead(java.util.function.Predicate<UUID> mayLead) {
        this.mayLead = mayLead;
    }

    /** The player's party, or null. A party of one is there while its invites run. */
    Party of(UUID player) {
        return partyOf.get(player);
    }

    /** The player's party when someone else is in it, or null. */
    Party together(UUID player) {
        Party party = partyOf.get(player);
        return party == null || party.size() < 2 ? null : party;
    }

    Invite invite(UUID from, UUID to, long nowMillis) {
        if (from.equals(to)) {
            return Invite.SELF;
        }
        Party party = partyOf.get(from);
        if (party != null && !party.isLeader(from)) {
            return Invite.NOT_LEADER;
        }
        if (party != null && party.contains(to)) {
            return Invite.ALREADY_MEMBER;
        }
        if (party != null && party.isInvited(to, nowMillis)) {
            return Invite.ALREADY_INVITED;
        }
        if (party != null && party.size() >= MAX_SIZE) {
            return Invite.FULL;
        }
        if (party == null) {
            party = new Party(from);
            partyOf.put(from, party);
        }
        party.invites.put(to, nowMillis + INVITE_MS);
        return Invite.SENT;
    }

    /** The player accepts the invite of this party's player (its leader, or whoever sent it). */
    Accept accept(UUID player, UUID inviter, long nowMillis) {
        Party party = partyOf.get(inviter);
        if (party == null || !party.isInvited(player, nowMillis)) {
            return Accept.NO_INVITE;
        }
        if (partyOf.containsKey(player)) {
            return Accept.IN_A_PARTY;
        }
        if (party.size() >= MAX_SIZE) {
            return Accept.FULL;
        }
        party.invites.remove(player);
        party.members.add(player);
        partyOf.put(player, party);
        return Accept.JOINED;
    }

    /**
     * The player leaves their party (or is kicked, or left the server). The next player who may
     * lead leads when the leader goes. A party left with one player and no invite, or with nobody
     * who may lead, is disbanded. Returns the party left, or null.
     */
    Party leave(UUID player) {
        Party party = partyOf.remove(player);
        if (party == null) {
            return null;
        }
        party.members.remove(player);
        if (!party.members.isEmpty() && !mayLead.test(party.members.get(0))) {
            for (int i = 1; i < party.members.size(); i++) {
                if (mayLead.test(party.members.get(i))) {
                    party.members.add(0, party.members.remove(i));
                    break;
                }
            }
        }
        if (party.members.isEmpty() || (party.size() == 1 && party.invites.isEmpty())
                || !mayLead.test(party.members.get(0))) {
            disband(party);
        }
        return party;
    }

    /** The leader takes this player out of the party. False if they are not in it. */
    boolean kick(UUID leader, UUID player) {
        Party party = partyOf.get(leader);
        if (party == null || !party.isLeader(leader) || leader.equals(player) || !party.contains(player)) {
            return false;
        }
        leave(player);
        return true;
    }

    /** The leader gives the party to this player. False if they are not in it, or may not lead. */
    boolean transfer(UUID leader, UUID player) {
        Party party = partyOf.get(leader);
        if (party == null || !party.isLeader(leader) || leader.equals(player) || !party.contains(player)
                || !mayLead.test(player)) {
            return false;
        }
        party.members.remove(player);
        party.members.add(0, player);
        return true;
    }

    /** Ends the party: every player is out. Returns them (the leader first). */
    List<UUID> disband(Party party) {
        List<UUID> members = party.members();
        for (UUID member : members) {
            partyOf.remove(member);
        }
        party.members.clear();
        party.invites.clear();
        party.disbanded = true;
        return members;
    }

    /** Removes the invites that ran out; a party of one left without invite is disbanded. */
    List<Expired> expire(long nowMillis) {
        List<Expired> expired = new ArrayList<>();
        Set<Party> parties = new LinkedHashSet<>(partyOf.values());
        for (Party party : parties) {
            Expired last = null;
            for (Iterator<Map.Entry<UUID, Long>> it = party.invites.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, Long> invite = it.next();
                if (invite.getValue() < nowMillis) {
                    it.remove();
                    last = new Expired(party, party.leader(), invite.getKey());
                    expired.add(last);
                }
            }
            if (last != null) {
                last.last = true;
            }
            if (party.size() == 1 && party.invites.isEmpty()) {
                disband(party);
            }
        }
        return expired;
    }
}
