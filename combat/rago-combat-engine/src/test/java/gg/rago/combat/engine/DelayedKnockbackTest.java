package gg.rago.combat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.combat.engine.fakeserver.EntityPlayer;
import gg.rago.combat.engine.fakeserver.PacketPlayOutEntityVelocity;
import gg.rago.combat.model.Vec3;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DelayedKnockbackTest {

    private final DelayedKnockback delayed = new DelayedKnockback(Logger.getLogger("DelayedKnockbackTest"));

    @AfterEach
    void stop() {
        delayed.shutdown();
    }

    @Test
    void sendsTheSamePacketLaterAndPutsBackTheMotion() throws Exception {
        EntityPlayer victim = new EntityPlayer();
        // What the server leaves on the victim when the velocity event is cancelled: the knockback.
        victim.motX = 0.4D;
        victim.motY = 0.36D;
        victim.motZ = -0.2D;
        victim.velocityChanged = true;
        Vec3 before = new Vec3(0.0D, -0.0784000015258789D, 0.0D);
        Vec3 velocity = new Vec3(0.4D, 0.36D, -0.2D);

        long start = System.nanoTime();
        assertTrue(delayed.sendLater(victim, 7, before, velocity, 60));

        assertEquals(0.0D, victim.motX);
        assertEquals(-0.0784000015258789D, victim.motY);
        assertEquals(0.0D, victim.motZ);
        assertFalse(victim.velocityChanged);
        assertTrue(victim.playerConnection.firstPacket.await(5, TimeUnit.SECONDS));
        assertTrue(victim.playerConnection.firstSentNanos - start >= TimeUnit.MILLISECONDS.toNanos(60));
        assertEquals(1, victim.playerConnection.sent.size());
        PacketPlayOutEntityVelocity packet = (PacketPlayOutEntityVelocity) victim.playerConnection.sent.get(0);
        assertEquals(7, packet.entityId);
        assertEquals(0.4D, packet.x);
        assertEquals(0.36D, packet.y);
        assertEquals(-0.2D, packet.z);
    }

    @Test
    void refusesWhenTheServerClassesAreNotThere() {
        Object notAServerPlayer = new Object();
        Vec3 zero = new Vec3(0.0D, 0.0D, 0.0D);

        assertFalse(delayed.sendLater(notAServerPlayer, 7, zero, zero, 60));
        assertFalse(delayed.sendLater(new EntityPlayer(), 7, zero, zero, 60));
    }
}
