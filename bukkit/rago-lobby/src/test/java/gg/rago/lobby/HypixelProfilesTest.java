package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The answers of Hypixel and Mojang, from a small local server. */
class HypixelProfilesTest {

    private HttpServer server;
    private final AtomicReference<String> sentKey = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        answer("/ok", 200, "{\"success\":true}");
        answer("/missing", 404, "");
        answer("/refused", 403, "{\"success\":false,\"cause\":\"Invalid API key\"}");
        answer("/slow-down", 429, "{\"success\":false,\"cause\":\"Key throttle\"}");
        answer("/broken", 500, "oops");
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void answer(String path, int status, String body) {
        server.createContext(path, exchange -> {
            sentKey.set(exchange.getRequestHeaders().getFirst("API-Key"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private String at(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void readsAnAnswerWithTheKey() throws Exception {
        assertEquals("{\"success\":true}", HypixelProfiles.fetch("Hypixel", at("/ok"), "a-key"));
        assertEquals("a-key", sentKey.get());
        assertNull(HypixelProfiles.fetch("Mojang", at("/missing"), null), "404: no account with this name");
        assertNull(sentKey.get(), "Mojang does not get the key");
    }

    @Test
    void tellsThePlayerWhenTheKeyIsRefused() {
        HypixelProfiles.Failure refused = assertThrows(HypixelProfiles.Failure.class,
                () -> HypixelProfiles.fetch("Hypixel", at("/refused"), "a-key"));
        assertTrue(refused.forPlayer.startsWith("Hypixel refused this server's API key"), refused.forPlayer);
        assertTrue(refused.getMessage().contains("Invalid API key"), "the console reads Hypixel's answer");
        assertTrue(refused.pause, "asking again with the same key does not help");
    }

    @Test
    void tellsThePlayerWhenHypixelIsBusyOrBroken() {
        HypixelProfiles.Failure busy = assertThrows(HypixelProfiles.Failure.class,
                () -> HypixelProfiles.fetch("Hypixel", at("/slow-down"), "a-key"));
        assertEquals("Hypixel gets too many requests right now.", busy.forPlayer);
        assertTrue(busy.pause);
        HypixelProfiles.Failure broken = assertThrows(HypixelProfiles.Failure.class,
                () -> HypixelProfiles.fetch("Hypixel", at("/broken"), "a-key"));
        assertEquals("Hypixel answered with an error (500). Try again in a moment.", broken.forPlayer);
        assertFalse(broken.pause);
    }
}
