package gg.rago.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class BuildListenerTest {

    @Test
    void aBuildWorldGivesItsOwnMode() {
        assertEquals(GameMode.CREATIVE, BuildListener.modeOnEntering(WorldSettings.Mode.CREATIF, false, GameMode.ADVENTURE));
        assertEquals(GameMode.SURVIVAL, BuildListener.modeOnEntering(WorldSettings.Mode.SURVIE, true, GameMode.CREATIVE));
    }

    @Test
    void aSpectatorTeleportedIntoAGameWorldStaysASpectator() {
        assertNull(BuildListener.modeOnEntering(null, false, GameMode.SPECTATOR));
        assertNull(BuildListener.modeOnEntering(null, true, GameMode.SPECTATOR));
    }

    @Test
    void theOtherWorldsKeepWhatTheirPluginGives() {
        assertNull(BuildListener.modeOnEntering(null, false, GameMode.ADVENTURE), "the lobby");
        assertNull(BuildListener.modeOnEntering(null, false, GameMode.SURVIVAL), "a game");
        assertNull(BuildListener.modeOnEntering(null, false, GameMode.CREATIVE), "a creative player not from a build world");
    }

    @Test
    void aBuilderLeavingABuildWorldInCreativeGoesBackToSurvival() {
        assertEquals(GameMode.SURVIVAL, BuildListener.modeOnEntering(null, true, GameMode.CREATIVE));
    }
}
