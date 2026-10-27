package com.mazem3dia.mztx6home;

/**
 * The colour an app's glow takes, picked from a few pixels of its banner or icon: the average of its colourful
 * pixels, so Netflix's white banner with red letters glows red. Plain int maths, tested on the JVM.
 */
final class GlowColor {
    /** No glow: the app is black, white or grey. */
    static final int NONE = 0;
    private static final float MIN_SATURATION = 0.35f;
    private static final int MIN_BRIGHTNESS = 64;
    /** At least this share of the pixels must be colourful, or a stray coloured edge would set the glow. */
    private static final float MIN_SHARE = 0.03f;

    private GlowColor() { }

    static int pick(int[] pixels) {
        double red = 0, green = 0, blue = 0, weight = 0;
        int colourful = 0;
        for (int pixel : pixels) {
            if ((pixel >>> 24) < 128) continue;   // mostly transparent
            int r = (pixel >> 16) & 0xFF, g = (pixel >> 8) & 0xFF, b = pixel & 0xFF;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max < MIN_BRIGHTNESS) continue;
            float saturation = (max - min) / (float) max;
            if (saturation < MIN_SATURATION) continue;
            double w = saturation * saturation;   // the more vivid, the more it counts
            red += r * w;
            green += g * w;
            blue += b * w;
            weight += w;
            colourful++;
        }
        if (colourful == 0 || colourful < pixels.length * MIN_SHARE) return NONE;
        double r = red / weight, g = green / weight, b = blue / weight;
        // Full brightness with the same hue: a glow, not a muddy average.
        double scale = 255 / Math.max(r, Math.max(g, b));
        return 0xFF000000 | channel(r * scale) << 16 | channel(g * scale) << 8 | channel(b * scale);
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }
}
