package com.mazem3dia.mztx6home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class GlowColorTest {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int GREY = 0xFF808080;
    private static final int NETFLIX_RED = 0xFFE50914;
    private static final int PRIME_BLUE = 0xFF1A98FF;

    private static int[] fill(int size, int colour) {
        int[] pixels = new int[size];
        Arrays.fill(pixels, colour);
        return pixels;
    }

    @Test
    public void whiteBannerWithRedLettersGlowsRed() {
        int[] pixels = fill(100, WHITE);
        Arrays.fill(pixels, 0, 20, NETFLIX_RED);
        int glow = GlowColor.pick(pixels);
        assertEquals(0xFF, (glow >> 16) & 0xFF);   // red at full brightness
        assertTrue(((glow >> 8) & 0xFF) < 0x20 && (glow & 0xFF) < 0x30);
    }

    @Test
    public void blueBannerGlowsBlue() {
        int glow = GlowColor.pick(fill(100, PRIME_BLUE));
        assertEquals(0xFF, glow & 0xFF);
        assertTrue(((glow >> 16) & 0xFF) < 0x30);
    }

    @Test
    public void blackWhiteAndGreyHaveNoGlow() {
        assertEquals(GlowColor.NONE, GlowColor.pick(fill(100, WHITE)));
        assertEquals(GlowColor.NONE, GlowColor.pick(fill(100, BLACK)));
        assertEquals(GlowColor.NONE, GlowColor.pick(fill(100, GREY)));
    }

    @Test
    public void aFewColouredPixelsAreNotEnough() {
        int[] pixels = fill(100, WHITE);
        pixels[0] = NETFLIX_RED;
        pixels[1] = NETFLIX_RED;
        assertEquals(GlowColor.NONE, GlowColor.pick(pixels));
    }

    @Test
    public void transparentPixelsAreIgnored() {
        int[] pixels = fill(100, 0x00E50914);   // red but fully transparent
        assertEquals(GlowColor.NONE, GlowColor.pick(pixels));
    }

    @Test
    public void darkColoursAreIgnored() {
        assertEquals(GlowColor.NONE, GlowColor.pick(fill(100, 0xFF200000)));
    }
}
