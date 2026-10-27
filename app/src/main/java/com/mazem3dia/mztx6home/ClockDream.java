package com.mazem3dia.mztx6home;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;

import java.util.Random;

/**
 * Screensaver: the clock and date on black, moved to a random place every minute so no pixel stays lit.
 * Android starts it only when the box sits idle and it is chosen in Settings > Screen saver.
 */
public final class ClockDream extends DreamService {
    private static final long MOVE_MS = 60_000;
    private static final int FADE_MS = 600;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View root;
    private View clock;

    private final Runnable move = new Runnable() {
        @Override
        public void run() {
            // Fade out, jump, fade in: a slide across the screen would ask more of the GPU than needed.
            clock.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
                clock.setX(random.nextInt(Math.max(1, root.getWidth() - clock.getWidth())));
                clock.setY(random.nextInt(Math.max(1, root.getHeight() - clock.getHeight())));
                clock.animate().alpha(1f).setDuration(FADE_MS);
            });
            main.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the box
        setFullscreen(true);
        setContentView(R.layout.dream);
        root = findViewById(R.id.dream_root);
        clock = findViewById(R.id.dream_clock);
        clock.setAlpha(0f);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        // After the first layout, so the clock's size is known when it is placed.
        root.post(move);
    }

    @Override
    public void onDreamingStopped() {
        main.removeCallbacks(move);
        root.removeCallbacks(move);
        super.onDreamingStopped();
    }
}
