package gg.rago.lobby;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads, on the main thread at enable, every PacketEvents class the lobby's code names.
 *
 * <p>The class loader of a 1.8 plugin defines a class of another plugin without a lock. When the
 * main thread (an NPC spawned for a player) and a Netty thread (a packet of the same player read
 * by a listener) load the same PacketEvents class for the first time at the same moment, one of
 * them fails with "attempted duplicate class definition", and the NPC never appears. Loaded at
 * enable, before any player can join, the classes are only looked up afterwards.
 */
final class PacketWarmup {

    static final String PACKAGE = "com/github/retrooper/packetevents/";
    private static final String OWN = "gg/rago/lobby/";
    private static final Pattern NAME = Pattern.compile(Pattern.quote(PACKAGE) + "[A-Za-z0-9_$/]+");

    private PacketWarmup() {
    }

    /** The PacketEvents classes a compiled class names (types, descriptors, signatures), dotted. */
    static Set<String> named(byte[] classFile) {
        // the names are plain ASCII in the constant pool; a tag byte or ; < ( ends each one
        Matcher matcher = NAME.matcher(new String(classFile, StandardCharsets.ISO_8859_1));
        Set<String> names = new TreeSet<>();
        while (matcher.find()) {
            String name = matcher.group();
            if (!name.endsWith("/")) {
                names.add(name.replace('/', '.'));
            }
        }
        return names;
    }

    /** The PacketEvents classes the lobby's classes in this jar name. */
    static Set<String> named(JarFile jar) throws IOException {
        Set<String> names = new TreeSet<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().startsWith(OWN) && entry.getName().endsWith(".class")) {
                try (InputStream in = jar.getInputStream(entry)) {
                    names.addAll(named(read(in)));
                }
            }
        }
        return names;
    }

    /** Loads them with the plugin's class loader; returns how many were loaded. */
    static int preload(File pluginJar, ClassLoader loader, Logger logger) {
        Set<String> names;
        try (JarFile jar = new JarFile(pluginJar)) {
            names = named(jar);
        } catch (IOException e) {
            logger.log(Level.WARNING, "could not read " + pluginJar + " to load the PacketEvents classes early", e);
            return 0;
        }
        int loaded = 0;
        for (String name : names) {
            try {
                Class.forName(name, false, loader);
                loaded++;
            } catch (ClassNotFoundException | LinkageError e) {
                logger.fine("PacketEvents class " + name + " not loaded early: " + e);
            }
        }
        return loaded;
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
