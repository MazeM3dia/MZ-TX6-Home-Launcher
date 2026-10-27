package com.mazem3dia.mztx6home;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * A ScrollView that keeps the focused tile, its name included, clear of both edges. A plain ScrollView scrolls
 * only until the tile touches the edge: at the bottom that is where a soundbar or ornaments in front of the TV
 * cover it, and at the top it sits against the header. Margins in res/values/dimens.xml. Anything that fits on the
 * first screen brings the view back to the top.
 */
public final class FocusScrollView extends ScrollView {
    private final int marginTop;
    private final int marginBottom;

    public FocusScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        marginTop = getResources().getDimensionPixelSize(R.dimen.focus_margin_top);
        marginBottom = getResources().getDimensionPixelSize(R.dimen.focus_margin_bottom);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        // A tile that fits on the first screen scrolls all the way back up: the favourites return to their place at
        // the bottom, with the breathing room above them.
        if (rect.bottom + marginBottom <= getHeight()) return -getScrollY();
        // Otherwise ask for the tile plus its margins: ScrollView then stops scrolling that much earlier.
        return super.computeScrollDeltaToGetChildRectOnScreen(
                new Rect(rect.left, rect.top - marginTop, rect.right, rect.bottom + marginBottom));
    }
}
