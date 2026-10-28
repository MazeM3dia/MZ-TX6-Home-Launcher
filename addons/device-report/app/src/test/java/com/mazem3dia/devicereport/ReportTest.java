package com.mazem3dia.devicereport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ReportTest {
    private static String render(String key, Object value) {
        StringBuilder sb = new StringBuilder();
        Report.value(sb, key, value, 0);
        return sb.toString();
    }

    @Test
    public void plainValue() {
        assertEquals("model: TX6\n", render("model", "TX6"));
    }

    @Test
    public void nestedObjectIndents() throws Exception {
        JSONObject o = new JSONObject().put("total", "2 GB");
        assertEquals("ram:\n  total: 2 GB\n", render("ram", o));
    }

    @Test
    public void shortListOnOneLine() {
        assertEquals("abis: armeabi-v7a, armeabi\n", render("abis", new JSONArray().put("armeabi-v7a").put("armeabi")));
    }

    @Test
    public void emptyListAndObject() {
        assertEquals("paired: (none)\n", render("paired", new JSONArray()));
        assertEquals("x: (none)\n", render("x", new JSONObject()));
    }

    @Test
    public void listOfObjectsBecomesItems() throws Exception {
        JSONArray a = new JSONArray().put(new JSONObject().put("component", "a/.Home"));
        assertEquals("home apps (1):\n  - component: a/.Home\n", render("home apps", a));
    }

    @Test
    public void longListOnePerLine() {
        JSONArray a = new JSONArray();
        for (int i = 0; i < 30; i++) a.put("android.hardware.feature" + i);
        String s = render("features", a);
        assertTrue(s.startsWith("features (30):\n  - android.hardware.feature0\n"));
    }

    @Test
    public void multiLineValueKeepsLines() {
        assertEquals("cpuinfo:\n  a\n  b\n", render("cpuinfo", "a\nb\n"));
    }

    @Test
    public void textHasNumberedSections() throws Exception {
        JSONObject root = new JSONObject().put("device", new JSONObject().put("model", "TX6"));
        String s = Report.text(root, "Report");
        assertTrue(s, s.startsWith("Report\n======\n\n1. DEVICE\n---------\nmodel: TX6\n"));
    }

    @Test
    public void sizes() {
        assertEquals("512 B", Collector.size(512));
        assertEquals("1.5 KB", Collector.size(1536));
        assertEquals("1.9 GB", Collector.size(2_000_000_000L));
    }

    @Test
    public void durations() {
        assertEquals("5m", Collector.duration(5 * 60_000));
        assertEquals("2h 3m", Collector.duration(123 * 60_000));
        assertEquals("1d 1h 1m", Collector.duration((1440 + 61) * 60_000L));
    }
}
