package com.mazem3dia.devicereport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * The report as text: the same tree as the .json file, one section per top-level key. Objects indent, lists
 * of short values sit on one line, lists of objects become "- " items, and multi-line values (cpuinfo, logcat)
 * keep their lines.
 */
final class Report {
    private static final int ONE_LINE = 100;

    private Report() {
    }

    static String text(JSONObject root, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append('\n').append(repeat('=', title.length())).append('\n');
        Iterator<String> keys = root.keys();
        int n = 0;
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = root.opt(key);
            String head = ++n + ". " + key.toUpperCase(java.util.Locale.ROOT);
            sb.append('\n').append(head).append('\n').append(repeat('-', head.length())).append('\n');
            if (value instanceof JSONObject) object(sb, (JSONObject) value, 0);
            else value(sb, "value", value, 0);
        }
        return sb.toString();
    }

    static void object(StringBuilder sb, JSONObject obj, int indent) {
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            value(sb, key, obj.opt(key), indent);
        }
    }

    static void value(StringBuilder sb, String key, Object value, int indent) {
        String pad = repeat(' ', indent);
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            if (obj.length() == 0) {
                sb.append(pad).append(key).append(": (none)\n");
                return;
            }
            sb.append(pad).append(key).append(":\n");
            object(sb, obj, indent + 2);
        } else if (value instanceof JSONArray) {
            array(sb, key, (JSONArray) value, indent);
        } else {
            String s = value == null || value == JSONObject.NULL ? "-" : String.valueOf(value);
            if (s.indexOf('\n') < 0) {
                sb.append(pad).append(key).append(": ").append(s).append('\n');
                return;
            }
            sb.append(pad).append(key).append(":\n");
            for (String line : s.split("\n", -1)) {
                if (!line.isEmpty() || sb.charAt(sb.length() - 1) != '\n') {
                    sb.append(pad).append("  ").append(line).append('\n');
                }
            }
        }
    }

    private static void array(StringBuilder sb, String key, JSONArray arr, int indent) {
        String pad = repeat(' ', indent);
        if (arr.length() == 0) {
            sb.append(pad).append(key).append(": (none)\n");
            return;
        }
        if (simple(arr)) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                if (i > 0) line.append(", ");
                line.append(arr.opt(i));
            }
            if (line.length() <= ONE_LINE) {
                sb.append(pad).append(key).append(": ").append(line).append('\n');
                return;
            }
        }
        sb.append(pad).append(key).append(" (").append(arr.length()).append("):\n");
        for (int i = 0; i < arr.length(); i++) {
            Object item = arr.opt(i);
            if (item instanceof JSONObject) {
                // The item's lines at indent + 4, its first line marked "- " at indent + 2.
                StringBuilder sub = new StringBuilder();
                object(sub, (JSONObject) item, indent + 4);
                if (sub.length() == 0) continue;
                sb.append(pad).append("  - ").append(sub, indent + 4, sub.length());
            } else {
                String s = String.valueOf(item);
                sb.append(pad).append("  - ").append(s.replace("\n", "\n" + pad + "    ")).append('\n');
            }
        }
    }

    /** Not String.repeat: that is Java 11, newer than Android 9's library. */
    static String repeat(char c, int n) {
        char[] chars = new char[Math.max(0, n)];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }

    private static boolean simple(JSONArray arr) {
        for (int i = 0; i < arr.length(); i++) {
            Object item = arr.opt(i);
            if (item instanceof JSONObject || item instanceof JSONArray) return false;
            if (String.valueOf(item).indexOf('\n') >= 0) return false;
        }
        return true;
    }

    /** JSONObject.put without the checked exception: only NaN or infinite numbers throw, and none are put. */
    static JSONObject put(JSONObject obj, String key, Object value) {
        try {
            obj.put(key, value == null ? JSONObject.NULL : value);
        } catch (JSONException e) {
            try {
                obj.put(key, String.valueOf(value));
            } catch (JSONException ignored) {
                // Unreachable: a string value never throws.
            }
        }
        return obj;
    }
}
