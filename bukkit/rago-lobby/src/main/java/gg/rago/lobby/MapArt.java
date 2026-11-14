package gg.rago.lobby;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.bukkit.map.MapPalette;

/**
 * An image drawn on the maps of a billboard ({@link FakeBillboard}): brought to columns x rows
 * maps of 128 x 128 pixels, whole and centred (black around it when its shape is not the
 * board's), in the colours a 1.8 client draws on a map, with Floyd-Steinberg dithering so that
 * its gradients stay smooth from a few blocks away. Pure: no server.
 */
final class MapArt {

    /** Pixels on a side of a map. */
    static final int SIZE = 128;
    /** The colours of a 1.8 map: 0 to 3 are transparent, 4 to 143 the 35 colours of blocks, in 4 shades. */
    static final int FIRST = 4;
    static final int COLOURS = 144;
    private static final int[] RED = new int[COLOURS];
    private static final int[] GREEN = new int[COLOURS];
    private static final int[] BLUE = new int[COLOURS];

    static {
        for (int i = FIRST; i < COLOURS; i++) {
            Color colour = colour((byte) i);
            RED[i] = colour.getRed();
            GREEN[i] = colour.getGreen();
            BLUE[i] = colour.getBlue();
        }
    }

    private MapArt() {
    }

    @SuppressWarnings("deprecation")
    private static Color colour(byte index) {
        return MapPalette.getColor(index);
    }

    /**
     * The image on columns x rows maps: tiles[row * columns + column], the top left one first, each
     * the 128 x 128 colours of a map row by row (x + y * 128, as a map packet carries them).
     */
    static byte[][] tiles(BufferedImage image, int columns, int rows) {
        int width = columns * SIZE;
        byte[] all = dither(fit(image, width, rows * SIZE));
        byte[][] tiles = new byte[columns * rows][SIZE * SIZE];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                byte[] tile = tiles[row * columns + column];
                for (int y = 0; y < SIZE; y++) {
                    System.arraycopy(all, (row * SIZE + y) * width + column * SIZE, tile, y * SIZE, SIZE);
                }
            }
        }
        return tiles;
    }

    /**
     * The image scaled to fit in width x height, whole, centred on black (what is transparent in
     * it shows black too).
     */
    static BufferedImage fit(BufferedImage image, int width, int height) {
        double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
        int w = Math.max(1, Math.min(width, (int) Math.round(image.getWidth() * scale)));
        int h = Math.max(1, Math.min(height, (int) Math.round(image.getHeight() * scale)));
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.drawImage(scale(image, w, h), (width - w) / 2, (height - h) / 2, null);
        graphics.dispose();
        return canvas;
    }

    /** Smoothly to this size: halved step by step down to twice it at most, then bicubic. */
    private static BufferedImage scale(BufferedImage image, int width, int height) {
        BufferedImage current = image;
        int w = image.getWidth();
        int h = image.getHeight();
        while (w != width || h != height) {
            w = w > width ? Math.max(width, w / 2) : width;
            h = h > height ? Math.max(height, h / 2) : height;
            BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = next.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(current, 0, 0, w, h, null);
            graphics.dispose();
            current = next;
        }
        return current;
    }

    /**
     * The colours of the maps for each pixel (x + y * width): the nearest map colour, the error
     * carried to the pixels ahead and below (Floyd-Steinberg, each row the other way round).
     */
    static byte[] dither(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        byte[] colours = new byte[width * height];
        // the error carried, 3 channels per pixel, a pixel of margin on each side
        float[] here = new float[(width + 2) * 3];
        float[] below = new float[(width + 2) * 3];
        for (int y = 0; y < height; y++) {
            boolean forward = (y & 1) == 0;
            int ahead = forward ? 3 : -3;
            for (int i = 0; i < width; i++) {
                int x = forward ? i : width - 1 - i;
                int pixel = pixels[y * width + x];
                int k = (x + 1) * 3;
                float r = clamp((pixel >> 16 & 0xFF) + here[k]);
                float g = clamp((pixel >> 8 & 0xFF) + here[k + 1]);
                float b = clamp((pixel & 0xFF) + here[k + 2]);
                int colour = nearest(Math.round(r), Math.round(g), Math.round(b));
                colours[y * width + x] = (byte) colour;
                float er = r - RED[colour];
                float eg = g - GREEN[colour];
                float eb = b - BLUE[colour];
                carry(here, k + ahead, er, eg, eb, 7f / 16);
                carry(below, k - ahead, er, eg, eb, 3f / 16);
                carry(below, k, er, eg, eb, 5f / 16);
                carry(below, k + ahead, er, eg, eb, 1f / 16);
            }
            float[] done = here;
            here = below;
            below = done;
            Arrays.fill(below, 0f);
        }
        return colours;
    }

    private static void carry(float[] errors, int k, float r, float g, float b, float share) {
        errors[k] += r * share;
        errors[k + 1] += g * share;
        errors[k + 2] += b * share;
    }

    private static float clamp(float value) {
        return value < 0 ? 0 : value > 255 ? 255 : value;
    }

    /**
     * The map colour nearest this one, by the weighted distance of Bukkit's MapPalette (times 512,
     * in whole numbers): (2 + mean red / 256) dr² + 4 dg² + (2 + (255 - mean red) / 256) db².
     */
    static int nearest(int r, int g, int b) {
        int best = FIRST;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = FIRST; i < COLOURS; i++) {
            int reds = r + RED[i];
            int dr = r - RED[i];
            int dg = g - GREEN[i];
            int db = b - BLUE[i];
            int distance = (1024 + reds) * dr * dr + 2048 * dg * dg + (1534 - reds) * db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** What a 1.8 client shows for this map colour, as 0xRRGGBB. */
    static int rgb(byte colour) {
        int i = colour & 0xFF;
        return RED[i] << 16 | GREEN[i] << 8 | BLUE[i];
    }
}
