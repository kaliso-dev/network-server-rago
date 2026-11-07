package gg.rago.combat.engine.fakeserver;

/** Stands for the server's player object, as {@code CraftPlayer.getHandle()} returns it. */
public final class EntityPlayer extends Entity {

    public final PlayerConnection playerConnection = new PlayerConnection();
}
