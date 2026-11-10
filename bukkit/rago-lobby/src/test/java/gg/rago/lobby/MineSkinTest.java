package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MineSkinTest {

    private static final String SIGNED = "{\"success\":true,\"skin\":{\"uuid\":\"abc\",\"name\":\"rago-clutch\","
            + "\"texture\":{\"data\":{\"value\":\"dmFsdWU=\",\"signature\":\"c2lnbmF0dXJl\"},"
            + "\"url\":{\"skin\":\"http://textures.minecraft.net/texture/1\"}}},"
            + "\"rateLimit\":{\"next\":{\"absolute\":1,\"relative\":3000}}}";

    @Test
    void theSignedTexturesAreReadFromTheAnswer() {
        Skin skin = MineSkin.skinOf(SIGNED);
        assertNotNull(skin);
        assertEquals("dmFsdWU=", skin.value());
        assertEquals("c2lnbmF0dXJl", skin.signature());
        assertEquals(3000L, MineSkin.waitOf(SIGNED));
        assertNull(MineSkin.skinOf("{\"success\":false,\"errors\":[{\"code\":\"invalid_image\",\"message\":\"Bad image\"}]}"));
        assertNull(MineSkin.skinOf("not json"));
        assertNull(MineSkin.skinOf(""));
    }

    @Test
    void anErrorSaysWhatWentWrong() {
        assertEquals("Bad image (invalid_image)",
                MineSkin.errorOf("{\"success\":false,\"errors\":[{\"code\":\"invalid_image\",\"message\":\"Bad image\"}]}"));
        assertEquals("Too many requests", MineSkin.errorOf("{\"error\":\"Too many requests\"}"));
        assertEquals("<html>oops</html>", MineSkin.errorOf("<html>oops</html>"));
        assertEquals("no answer", MineSkin.errorOf(""));
        assertEquals(0L, MineSkin.waitOf("{}"));
        assertEquals("rago-blocs", MineSkin.shortName("rago-blocs"));
        assertEquals("rago-avery-long-skin", MineSkin.shortName("rago-avery-long-skin-name"));
    }

    @Test
    void thePngGoesUpAsAMultipartFormWithTheKey() throws Exception {
        final AtomicReference<String> seen = new AtomicReference<>();
        final AtomicReference<byte[]> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/generate", exchange -> {
            seen.set(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("User-Agent") + " "
                    + exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(readAll(exchange.getRequestBody()));
            byte[] answer = SIGNED.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();
        try {
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3};
            Skin skin = MineSkin.upload("http://127.0.0.1:" + server.getAddress().getPort(), png, "rago-clutch", "msk_test");
            assertEquals("dmFsdWU=", skin.value());
            String[] parts = seen.get().split(" ", 5);
            assertEquals("POST", parts[0]);
            assertEquals(MineSkin.USER_AGENT, parts[1]);
            assertEquals("Bearer msk_test", parts[2] + " " + parts[3]);
            assertTrue(parts[4].startsWith("multipart/form-data; boundary="), parts[4]);
            String boundary = parts[4].substring("multipart/form-data; boundary=".length());
            String text = new String(body.get(), StandardCharsets.ISO_8859_1);
            assertTrue(text.contains("name=\"variant\"\r\n\r\nclassic\r\n"), text);
            assertTrue(text.contains("name=\"visibility\"\r\n\r\nunlisted\r\n"), text);
            assertTrue(text.contains("name=\"name\"\r\n\r\nrago-clutch\r\n"), text);
            assertTrue(text.contains("name=\"file\"; filename=\"rago-clutch.png\"\r\nContent-Type: image/png\r\n\r\n"), text);
            assertTrue(text.endsWith("\r\n--" + boundary + "--\r\n"), text);
            int start = text.indexOf("image/png\r\n\r\n") + "image/png\r\n\r\n".length();
            assertArrayEquals(png, Arrays.copyOfRange(body.get(), start, start + png.length), "the PNG as it is");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aRefusalCarriesItsStatusAndTheWait() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/generate", exchange -> {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] answer = ("{\"success\":false,\"errors\":[{\"code\":\"rate_limit\",\"message\":\"Slow down\"}],"
                    + "\"rateLimit\":{\"next\":{\"relative\":5000}},\"key\":\"" + header + "\"}").getBytes(StandardCharsets.UTF_8);
            readAll(exchange.getRequestBody());
            exchange.sendResponseHeaders(429, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();
        try {
            MineSkin.Refused refused = assertThrows(MineSkin.Refused.class, () -> MineSkin.upload(
                    "http://127.0.0.1:" + server.getAddress().getPort(), new byte[] {1}, "rago-ffa", ""));
            assertEquals(429, refused.status());
            assertEquals(5000L, refused.waitMs());
            assertTrue(refused.getMessage().contains("Slow down"), refused.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void theSignedSkinsAreKeptWithTheHashOfTheirPng() throws Exception {
        Map<String, NpcSkins.Signed> skins = new TreeMap<>();
        skins.put("clutch", new NpcSkins.Signed(NpcSkins.sha1(new byte[] {1, 2}), new Skin("dmFsdWU=", "c2ln")));
        skins.put("attaque", new NpcSkins.Signed("ab", new Skin("v2", "s2")));
        Map<String, NpcSkins.Signed> back = NpcSkins.read(NpcSkins.yaml(skins));
        assertEquals(skins.keySet(), back.keySet());
        assertEquals("dmFsdWU=", back.get("clutch").skin.value());
        assertEquals("c2ln", back.get("clutch").skin.signature());
        assertEquals(NpcSkins.sha1(new byte[] {1, 2}), back.get("clutch").hash);
        assertEquals(40, back.get("clutch").hash.length());
        assertThrows(java.io.IOException.class, () -> NpcSkins.read("broken: [1\n"));
        assertTrue(NpcSkins.read("x:\n  png: aa\n  value: v\n").isEmpty(), "no signature: not a skin");
        assertTrue(NpcSkins.validId("clutch") && NpcSkins.validId("my-skin2"));
        assertTrue(!NpcSkins.validId("Clutch") && !NpcSkins.validId("../x") && !NpcSkins.validId(""));
    }

    @Test
    void everyNpcOfTheConfigHasItsSkinInTheJar() throws Exception {
        LobbySettings settings = LobbySettings.load(LobbySettingsTest.bundledConfig());
        int skinned = 0;
        for (LobbyNpc npc : settings.npcs()) {
            if (npc.leaderboard()) {
                continue;
            }
            assertNotNull(npc.skin(), npc.point() + " has a skin");
            InputStream png = MineSkinTest.class.getResourceAsStream("/skins/" + npc.skin() + ".png");
            assertNotNull(png, "skins/" + npc.skin() + ".png is in the jar");
            byte[] bytes = readAll(png);
            assertEquals((byte) 0x89, bytes[0]);
            assertEquals(64, ((bytes[16] & 0xFF) << 24 | (bytes[17] & 0xFF) << 16 | (bytes[18] & 0xFF) << 8 | (bytes[19] & 0xFF)), "64 wide");
            assertEquals(64, ((bytes[20] & 0xFF) << 24 | (bytes[21] & 0xFF) << 16 | (bytes[22] & 0xFF) << 8 | (bytes[23] & 0xFF)), "64 high");
            skinned++;
        }
        assertTrue(skinned >= 14, "" + skinned);
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
