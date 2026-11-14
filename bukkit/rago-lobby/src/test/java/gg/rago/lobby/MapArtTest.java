package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** An image on the maps of a billboard. */
class MapArtTest {

    private static BufferedImage filled(int width, int height, Color colour) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(colour);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static int red(int rgb) {
        return rgb >> 16 & 0xFF;
    }

    private static int green(int rgb) {
        return rgb >> 8 & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    /** A colour a map draws stays itself, everywhere: nothing to carry. */
    @Test
    void aColourOfTheMapsStaysItself() {
        for (int colour = MapArt.FIRST; colour < MapArt.COLOURS; colour += 7) {
            int rgb = MapArt.rgb((byte) colour);
            byte[][] tiles = MapArt.tiles(filled(300, 300, new Color(rgb)), 1, 1);
            for (byte pixel : tiles[0]) {
                assertEquals(rgb, MapArt.rgb(pixel), "colour " + colour);
            }
        }
    }

    /** No pixel is transparent (0 to 3): a frame would show its back through it. */
    @Test
    void noPixelIsTransparent() {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 100; y++) {
                image.setRGB(x, y, (x * 255 / 199) << 24 | (y * 2) << 16 | x << 8 | (x + y) % 256);
            }
        }
        for (byte[] tile : MapArt.tiles(image, 2, 1)) {
            for (byte pixel : tile) {
                assertTrue((pixel & 0xFF) >= MapArt.FIRST && (pixel & 0xFF) < MapArt.COLOURS);
            }
        }
    }

    /** The maps row by row from the top left, each 128 x 128 row by row. */
    @Test
    void theMapsGoRowByRowFromTheTopLeft() {
        BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_RGB);
        Color[] quarters = {new Color(MapArt.rgb((byte) 18)), new Color(MapArt.rgb((byte) 70)),
            new Color(MapArt.rgb((byte) 114)), new Color(MapArt.rgb((byte) 34))};
        Graphics2D graphics = image.createGraphics();
        for (int i = 0; i < 4; i++) {
            graphics.setColor(quarters[i]);
            graphics.fillRect(i % 2 * 256, i / 2 * 128, 256, 128);
        }
        graphics.dispose();
        // 4 x 2 maps: the left half of the image on columns 0 and 1, the top half on row 0
        byte[][] tiles = MapArt.tiles(image, 4, 2);
        assertEquals(8, tiles.length);
        for (int tile = 0; tile < 8; tile++) {
            assertEquals(MapArt.SIZE * MapArt.SIZE, tiles[tile].length);
            int quarter = (tile % 4) / 2 + tile / 4 * 2;
            // away from the edges, where the scaling blends the quarters
            assertEquals(quarters[quarter].getRGB() & 0xFFFFFF, MapArt.rgb(tiles[tile][64 + 64 * 128]), "map " + tile);
        }
    }

    /** An image of another shape shows whole, centred, black around it. */
    @Test
    void anImageOfAnotherShapeShowsWholeOnBlack() {
        int white = MapArt.rgb((byte) MapArt.nearest(255, 255, 255));
        int black = MapArt.rgb((byte) MapArt.nearest(0, 0, 0));
        byte[] map = MapArt.tiles(filled(400, 200, Color.WHITE), 1, 1)[0];
        // 128 x 64 in the middle: rows 32 to 95
        for (int y : new int[] {0, 20, 31}) {
            assertEquals(black, MapArt.rgb(map[64 + y * 128]), "above, row " + y);
            assertEquals(black, MapArt.rgb(map[64 + (127 - y) * 128]), "below, row " + (127 - y));
        }
        for (int y : new int[] {33, 64, 94}) {
            for (int x : new int[] {0, 64, 127}) {
                assertEquals(white, MapArt.rgb(map[x + y * 128]), x + " " + y);
            }
        }
    }

    /** A colour between those of the maps: dots of the near ones, of the same colour seen from afar. */
    @Test
    void theDitheringKeepsTheColourSeenFromAfar() {
        Color[] colours = {new Color(128, 128, 128), new Color(200, 30, 40), new Color(30, 30, 30), new Color(90, 160, 220)};
        for (Color colour : colours) {
            byte[] map = MapArt.tiles(filled(128, 128, colour), 1, 1)[0];
            long r = 0;
            long g = 0;
            long b = 0;
            java.util.Set<Integer> used = new java.util.HashSet<>();
            for (byte pixel : map) {
                int rgb = MapArt.rgb(pixel);
                r += red(rgb);
                g += green(rgb);
                b += blue(rgb);
                used.add(pixel & 0xFF);
            }
            int n = map.length;
            assertEquals(colour.getRed(), r / (double) n, 3, colour + " red");
            assertEquals(colour.getGreen(), g / (double) n, 3, colour + " green");
            assertEquals(colour.getBlue(), b / (double) n, 3, colour + " blue");
            assertTrue(used.size() > 1, colour + ": not one flat colour");
        }
    }

    /** The nearest colour, as Bukkit weighs it. */
    @Test
    void theNearestColour() {
        assertEquals(MapArt.rgb((byte) 6), MapArt.rgb((byte) MapArt.nearest(127, 178, 56)), "the grass of the maps itself");
        int black = MapArt.rgb((byte) MapArt.nearest(0, 0, 0));
        assertTrue(red(black) < 30 && green(black) < 30 && blue(black) < 30);
        int red = MapArt.rgb((byte) MapArt.nearest(255, 0, 0));
        assertTrue(red(red) > 150 && green(red) < 60 && blue(red) < 60, Integer.toHexString(red));
    }
}
