package com.mazem3dia.mztx6home;

import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;

/**
 * A colour theme, drawn in code: gradients and rounded shapes cost no image memory. Each call returns new
 * drawables, since a stateful drawable can't be shared between views.
 */
final class Palette {
    private static final int RING = 0xFFFFFFFF;
    /** The ring of a tile being moved. */
    private static final int MOVING = 0xFFFFC107;
    /** Background top, background bottom, tile, focused tile. Names: R.array.palette_names, same order. */
    private static final int[][] COLORS = {
            {0xFF1B2330, 0xFF0D1117, 0xFF263040, 0xFF34425A},   // Midnight
            {0xFF0F3443, 0xFF081C24, 0xFF1C4654, 0xFF26606F},   // Ocean
            {0xFF1E3326, 0xFF0C1610, 0xFF2A4434, 0xFF385A44},   // Forest
            {0xFF2E1F3A, 0xFF140D1A, 0xFF3E2B4D, 0xFF523A66},   // Plum
            {0xFF262626, 0xFF0F0F0F, 0xFF333333, 0xFF474747},   // Graphite
    };

    private final int[] colors;
    private final float radius;
    private final int ring;
    private final int movingRing;

    Palette(Resources res, int index) {
        colors = COLORS[index >= 0 && index < COLORS.length ? index : 0];
        radius = res.getDimension(R.dimen.tile_radius);
        ring = res.getDimensionPixelSize(R.dimen.focus_ring);
        movingRing = res.getDimensionPixelSize(R.dimen.move_ring);
    }

    static int count() { return COLORS.length; }

    Drawable window() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {colors[0], colors[1]});
    }

    /** A tile's card: lighter while focused. */
    Drawable card() {
        StateListDrawable card = new StateListDrawable();
        card.addState(new int[] {android.R.attr.state_focused}, shape(colors[3], 0, 0, radius));
        card.addState(StateSet.WILD_CARD, shape(colors[2], 0, 0, radius));
        return card;
    }

    /** A tile's ring, drawn over the card so a full-size banner can't hide it: amber while moving. */
    Drawable ring() {
        StateListDrawable frame = new StateListDrawable();
        frame.addState(new int[] {android.R.attr.state_activated}, shape(0, movingRing, MOVING, radius));
        frame.addState(new int[] {android.R.attr.state_focused}, shape(0, ring, RING, radius));
        frame.addState(StateSet.WILD_CARD, new ColorDrawable(0));
        return frame;
    }

    /** A pill button at the top: lighter, with a ring, while focused. */
    Drawable button() {
        float pill = 1000f;
        StateListDrawable button = new StateListDrawable();
        button.addState(new int[] {android.R.attr.state_focused}, shape(colors[3], ring, RING, pill));
        button.addState(StateSet.WILD_CARD, shape(colors[2], 0, 0, pill));
        return button;
    }

    private static GradientDrawable shape(int fill, int strokeWidth, int strokeColor, float corner) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        if (strokeWidth > 0) shape.setStroke(strokeWidth, strokeColor);
        shape.setCornerRadius(corner);
        return shape;
    }
}
