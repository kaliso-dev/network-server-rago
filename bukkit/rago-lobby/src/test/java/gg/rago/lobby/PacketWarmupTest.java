package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** The PacketEvents classes the lobby names are loaded at enable, before any player. */
class PacketWarmupTest {

    private static byte[] compiled(Class<?> type) throws Exception {
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    @Test
    void theNpcsClassesAreFoundInTheirCode() throws Exception {
        Set<String> names = PacketWarmup.named(compiled(FakePlayer.class));
        // the one two threads defined together
        assertTrue(names.contains("com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes"), names.toString());
        // a nested class, named only by its uses
        assertTrue(names.contains("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo$PlayerData"),
                names.toString());
        for (String name : names) {
            assertTrue(name.startsWith("com.github.retrooper.packetevents."), name);
            assertTrue(!name.contains("/") && !name.contains(";") && !name.endsWith("."), name);
        }
    }

    @Test
    void everyNamedClassLoadsFromTheJar() throws Exception {
        File jar = Files.createTempFile("rago-lobby", ".jar").toFile();
        jar.deleteOnExit();
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            for (Class<?> type : new Class<?>[] {FakePlayer.class, HeadLines.class, FakeHologram.class, Nicks.class}) {
                out.putNextEntry(new JarEntry(type.getName().replace('.', '/') + ".class"));
                out.write(compiled(type));
                out.closeEntry();
            }
            out.putNextEntry(new JarEntry("plugin.yml"));
            out.write("name: test".getBytes("UTF-8"));
            out.closeEntry();
        }
        Set<String> names;
        try (java.util.jar.JarFile file = new java.util.jar.JarFile(jar)) {
            names = PacketWarmup.named(file);
        }
        assertTrue(names.size() > 10, names.toString());
        assertEquals(names.size(), PacketWarmup.preload(jar, getClass().getClassLoader(), Logger.getLogger("test")),
                "each name is a real PacketEvents class");
    }
}
