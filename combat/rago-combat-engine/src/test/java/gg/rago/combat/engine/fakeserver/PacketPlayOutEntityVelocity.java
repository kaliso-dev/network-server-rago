package gg.rago.combat.engine.fakeserver;

/** Stands for the server's velocity packet, with the constructor the engine uses. */
public final class PacketPlayOutEntityVelocity implements Packet {

    public final int entityId;
    public final double x;
    public final double y;
    public final double z;

    public PacketPlayOutEntityVelocity(int entityId, double x, double y, double z) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
