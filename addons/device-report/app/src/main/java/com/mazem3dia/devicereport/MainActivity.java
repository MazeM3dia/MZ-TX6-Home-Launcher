package com.mazem3dia.devicereport;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * One screen: Create report reads the box (Collector) and saves the same report twice, as .txt to read and
 * .json for tools, in Download/. The summary shows below once done.
 */
public final class MainActivity extends Activity {
    private static final int ASK = 1;
    private static final int SECTIONS = 19;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface light, regular, bold;
    private Button create;
    private TextView status;
    private TextView summary;
    private boolean running;
    private int done;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        light = getResources().getFont(R.font.inter_display_light);
        regular = getResources().getFont(R.font.inter_display_regular);
        bold = getResources().getFont(R.font.inter_display_bold);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(64), dp(40), dp(64), dp(32));
        page.setBackgroundColor(0xFF0B0F14);

        page.addView(text(getString(R.string.app_name), light, 44, Color.WHITE));
        TextView about = text(getString(R.string.about), regular, 18, 0xB3FFFFFF);
        about.setPadding(0, dp(4), 0, dp(20));
        page.addView(about);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        create = new Button(this);
        create.setText(R.string.create);
        create.setAllCaps(false);
        create.setTypeface(bold);
        create.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        create.setTextColor(Color.WHITE);
        create.setBackground(buttonBackground());
        create.setPadding(dp(32), dp(12), dp(32), dp(12));
        create.setOnClickListener(v -> start());
        row.addView(create);
        status = text("", regular, 18, 0xCCFFFFFF);
        status.setPadding(dp(24), 0, 0, 0);
        row.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        page.addView(row);

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(true);
        scroll.setPadding(0, dp(20), 0, 0);
        summary = text(getString(R.string.hint), regular, 16, 0xE6FFFFFF);
        summary.setLineSpacing(0, 1.15f);
        scroll.addView(summary);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(page);
        create.requestFocus();
    }

    private void start() {
        if (running) return;
        List<String> ask = new ArrayList<>();
        for (String p : wanted()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) ask.add(p);
        }
        if (ask.isEmpty()) collect();
        else requestPermissions(ask.toArray(new String[0]), ASK);
    }

    /** Optional permissions that fill in more of the report; each refusal only leaves its part out. */
    private static List<String> wanted() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT <= 28) list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 31) list.add(Manifest.permission.BLUETOOTH_CONNECT);
        return list;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code == ASK) collect();
    }

    private void collect() {
        running = true;
        done = 0;
        create.setEnabled(false);
        status.setText(R.string.reading);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Thread worker = new Thread(() -> {
            String result;
            CharSequence shown = null;
            try {
                JSONObject root = new Collector(getApplicationContext(),
                        name -> main.post(() -> status.setText(getString(R.string.reading_section, name,
                                ++done, SECTIONS)))).collect();
                String title = getString(R.string.title, Build.MANUFACTURER + " " + Build.MODEL);
                String txt = Report.text(root, title);
                String json = root.toString(2);
                String base = "device-report-" + Build.MODEL.replaceAll("[^A-Za-z0-9]+", "-") + "-"
                        + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date());
                String txtAt = save(base + ".txt", "text/plain", txt);
                String jsonAt = save(base + ".json", "application/json", json);
                result = getString(R.string.saved, txtAt, Collector.size(txt.length()), jsonAt,
                        Collector.size(json.length()));
                StringBuilder sb = new StringBuilder();
                JSONObject sum = root.optJSONObject("summary");
                if (sum != null) Report.object(sb, sum, 0);
                shown = sb;
            } catch (IOException | JSONException | RuntimeException e) {
                result = getString(R.string.failed, e.toString());
            }
            String message = result;
            CharSequence text = shown;
            main.post(() -> finish(message, text));
        }, "device-report");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private void finish(String message, CharSequence text) {
        running = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        create.setEnabled(true);
        create.setText(R.string.again);
        status.setText(message);
        if (text != null) summary.setText(text);
        create.requestFocus();
    }

    /**
     * Download/ for the owner to copy off: MediaStore on Android 10+, the folder itself on Android 9 with the
     * storage permission. Without it, the app's own folder on shared storage.
     */
    private String save(String name, String mime, String content) throws IOException {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri != null) {
                try (OutputStream out = cr.openOutputStream(uri)) {
                    if (out != null) {
                        out.write(data);
                        return Environment.DIRECTORY_DOWNLOADS + "/" + name;
                    }
                }
            }
        } else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir.isDirectory() || dir.mkdirs()) return write(new File(dir, name), data);
        }
        File own = getExternalFilesDir(null);
        if (own == null) own = getFilesDir();
        return write(new File(own, name), data);
    }

    private static String write(File file, byte[] data) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
        return file.getPath();
    }

    private TextView text(String s, Typeface face, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private StateListDrawable buttonBackground() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, pill(getColor(R.color.accent)));
        s.addState(new int[]{-android.R.attr.state_enabled}, pill(0x14FFFFFF));
        s.addState(new int[0], pill(0x26FFFFFF));
        return s;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(28));
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (!running) super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
