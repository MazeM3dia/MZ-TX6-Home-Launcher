package com.mazem3dia.mztx6home;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The Customize side panel: pages of rows over the right of Home, the rest dimmed. Its views exist only while it
 * is open; closing removes them. Rows are built by a Page; a choice row changes with Left/Right (OK steps
 * forward), a link opens another page, an option picks. Back goes up a page, then closes.
 */
final class CustomizePanel {
    /** One screen of the panel. */
    interface Page {
        String title();

        void build(CustomizePanel panel);
    }

    private static final int SLIDE_MS = 150;

    private final Activity activity;
    /** The window's content frame: the panel is added on top of Home. */
    private final ViewGroup host;
    /** Home's content: focus is kept out of it while the panel is open. */
    private final ViewGroup home;
    private Palette palette;
    private boolean animate;
    private View root;
    private ViewGroup list;
    private ScrollView scroll;
    private TextView title;
    /** Focusable rows of the page shown, in order. */
    private final List<View> rows = new ArrayList<>();
    /** Pages from the first to the one shown, with the row focused on each when it was left. */
    private final Deque<Page> pages = new ArrayDeque<>();
    private final Deque<Integer> focus = new ArrayDeque<>();
    private View returnFocus;

    CustomizePanel(Activity activity, ViewGroup host, ViewGroup home) {
        this.activity = activity;
        this.host = host;
        this.home = home;
    }

    boolean isOpen() {
        return root != null;
    }

    void setPalette(Palette palette, boolean animate) {
        this.palette = palette;
        this.animate = animate;
        if (root == null) return;
        root.findViewById(R.id.panel).setBackground(palette.panel());
        rebuild(focusedRow());
    }

    void open(Page page) {
        if (root != null) return;
        returnFocus = activity.getCurrentFocus();
        root = LayoutInflater.from(activity).inflate(R.layout.panel, host, false);
        View panel = root.findViewById(R.id.panel);
        panel.setBackground(palette.panel());
        list = root.findViewById(R.id.panel_list);
        scroll = root.findViewById(R.id.panel_scroll);
        title = root.findViewById(R.id.panel_title);
        host.addView(root);
        home.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        pages.push(page);
        focus.push(0);
        rebuild(0);
        if (animate) {
            panel.setTranslationX(activity.getResources().getDimension(R.dimen.panel_width));
            panel.animate().translationX(0f).setDuration(SLIDE_MS);
        }
    }

    void close() {
        if (root == null) return;
        host.removeView(root);
        root = null;
        list = null;
        rows.clear();
        pages.clear();
        focus.clear();
        home.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (returnFocus != null && returnFocus.isAttachedToWindow()) returnFocus.requestFocus();
        returnFocus = null;
    }

    void push(Page page) {
        if (root == null) return;
        focus.pop();
        focus.push(focusedRow());
        pages.push(page);
        focus.push(0);
        rebuild(0);
    }

    /** Up a page, or close from the first. */
    void back() {
        if (pages.size() <= 1) {
            close();
            return;
        }
        pages.pop();
        focus.pop();
        rebuild(focus.peek());
    }

    /** The page shown, built again with its values as they are now; focus stays on the same row. */
    void refresh() {
        if (root != null) rebuild(focusedRow());
    }

    /** Back closes or goes up; everything else goes to the focused row. */
    boolean dispatch(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) back();
        return true;
    }

    // ----- Rows, for Page.build -----

    void section(String text) {
        TextView header = (TextView) LayoutInflater.from(activity).inflate(R.layout.panel_section, list, false);
        header.setText(text);
        list.addView(header);
    }

    /** A value changed in place: Left and Right step it (-1, +1), OK steps it forward. */
    void choice(String label, Supplier<String> value, IntConsumer step) {
        View row = row(label, null, "‹ " + value.get() + " ›");
        TextView valueView = row.findViewById(R.id.row_value);
        Runnable apply = () -> valueView.setText("‹ " + value.get() + " ›");
        row.setOnClickListener(v -> {
            step.accept(1);
            apply.run();
        });
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                step.accept(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                apply.run();
            }
            return true;
        });
    }

    /** Opens something: another page, or an app. */
    void link(String label, String value, Runnable open) {
        row(label, null, value.isEmpty() ? "›" : value + "  ›").setOnClickListener(v -> open.run());
    }

    void action(String label, Runnable run) {
        row(label, null, "").setOnClickListener(v -> run.run());
    }

    /** One of a list: ✓ when chosen. */
    void option(String label, String detail, boolean checked, Runnable pick) {
        row(label, detail, checked ? "✓" : "").setOnClickListener(v -> pick.run());
    }

    private View row(String label, String detail, String value) {
        View row = LayoutInflater.from(activity).inflate(R.layout.panel_row, list, false);
        ((TextView) row.findViewById(R.id.row_label)).setText(label);
        ((TextView) row.findViewById(R.id.row_value)).setText(value);
        if (detail != null && !detail.isEmpty()) {
            TextView detailView = row.findViewById(R.id.row_detail);
            detailView.setText(detail);
            detailView.setVisibility(View.VISIBLE);
        }
        row.setBackground(palette.row());
        list.addView(row);
        rows.add(row);
        return row;
    }

    private void rebuild(int focusIndex) {
        Page page = pages.peek();
        list.removeAllViews();
        rows.clear();
        title.setText(page.title());
        page.build(this);
        if (rows.isEmpty()) return;
        View target = rows.get(Math.max(0, Math.min(focusIndex, rows.size() - 1)));
        target.requestFocus();
        if (focusIndex == 0) scroll.scrollTo(0, 0);
    }

    private int focusedRow() {
        View focused = activity.getCurrentFocus();
        int index = rows.indexOf(focused);
        return Math.max(index, 0);
    }
}
