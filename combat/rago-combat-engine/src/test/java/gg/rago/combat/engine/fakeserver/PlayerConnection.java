package gg.rago.combat.engine.fakeserver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/** Stands for a player's connection: keeps what it is asked to send, and when. */
public final class PlayerConnection {

    public final List<Packet> sent = new CopyOnWriteArrayList<>();
    public volatile long firstSentNanos;
    public final CountDownLatch firstPacket = new CountDownLatch(1);

    public void sendPacket(Packet packet) {
        if (sent.isEmpty()) {
            firstSentNanos = System.nanoTime();
        }
        sent.add(packet);
        firstPacket.countDown();
    }
}
