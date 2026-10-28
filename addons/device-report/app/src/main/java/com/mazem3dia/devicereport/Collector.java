package com.mazem3dia.devicereport;

import static com.mazem3dia.devicereport.Report.put;

import android.Manifest;
import android.annotation.SuppressLint;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.database.Cursor;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.ProxyInfo;
import android.net.RouteInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Gathers everything an ordinary app may read about the box into one JSON tree, a section per top-level key.
 * Each section is collected on its own: one that fails records its error and the rest carry on. Runs on a
 * worker thread; the whole pass takes a few seconds.
 */
final class Collector {
    interface Progress {
        void section(String name);
    }

    private static final UUID WIDEVINE = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    private static final UUID CLEARKEY = new UUID(0xE2719D58A985B3C9L, 0x781AB030AF78D30EL);
    private static final int TEXT_LIMIT = 256 * 1024;

    private final Context context;
    private final PackageManager pm;
    private final Progress progress;
    private final Map<String, String> props = new TreeMap<>();
    private final JSONObject root = new JSONObject();
    private String platformSigner;

    Collector(Context context, Progress progress) {
        this.context = context;
        this.pm = context.getPackageManager();
        this.progress = progress;
    }

    JSONObject collect() {
        readProps();
        put(root, "report", report());
        put(root, "summary", new JSONObject()); // filled in last, kept at the top
        section("device", this::device);
        section("system properties", this::properties);
        section("cpu", this::cpu);
        section("memory", this::memory);
        section("storage", this::storage);
        section("display", this::display);
        section("graphics", this::graphics);
        section("features", this::features);
        section("media", this::media);
        section("network", this::network);
        section("bluetooth", this::bluetooth);
        section("input", this::input);
        section("default apps and intents", this::intents);
        section("apps", this::apps);
        section("settings", this::settings);
        section("power", this::power);
        section("security", this::security);
        section("extras (adb grant)", this::extras);
        section("summary", this::summary);
        return root;
    }

    private interface Section {
        JSONObject build() throws Exception;
    }

    private void section(String name, Section section) {
        progress.section(name);
        JSONObject out;
        try {
            out = section.build();
        } catch (Throwable t) {
            out = put(new JSONObject(), "error", t.toString());
        }
        put(root, name, out);
    }

    // ---------------------------------------------------------------- report, summary

    private JSONObject report() {
        JSONObject o = new JSONObject();
        put(o, "created", stamp(System.currentTimeMillis()));
        put(o, "app", "Device Report " + versionName());
        put(o, "timezone", java.util.TimeZone.getDefault().getID());
        put(o, "note", "Read with ordinary app permissions; no root. Sections marked 'error' or 'not allowed' "
                + "were blocked by Android or the firmware.");
        return o;
    }

    private JSONObject summary() {
        JSONObject o = new JSONObject();
        JSONObject device = root.optJSONObject("device");
        JSONObject mem = root.optJSONObject("memory");
        JSONObject gfx = root.optJSONObject("graphics");
        JSONObject feat = root.optJSONObject("features");
        JSONObject intents = root.optJSONObject("default apps and intents");
        JSONObject apps = root.optJSONObject("apps");
        put(o, "device", Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")");
        put(o, "android", Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT
                + ", patch " + Build.VERSION.SECURITY_PATCH);
        put(o, "chip", prop("ro.board.platform") + " / hardware " + Build.HARDWARE);
        put(o, "cpu", Runtime.getRuntime().availableProcessors() + " cores, "
                + String.join(" ", Build.SUPPORTED_ABIS));
        if (mem != null) put(o, "ram", mem.optString("total") + " total, " + mem.optString("available") + " free");
        JSONObject data = root.optJSONObject("storage") == null ? null
                : root.optJSONObject("storage").optJSONObject("data");
        if (data != null) put(o, "storage", data.optString("total") + " total, " + data.optString("free") + " free");
        DisplayMetrics dm = new DisplayMetrics();
        Display display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        display.getRealMetrics(dm);
        put(o, "screen", dm.widthPixels + "x" + dm.heightPixels + " at " + dm.densityDpi + " dpi, "
                + Math.round(display.getRefreshRate()) + " Hz");
        if (gfx != null) put(o, "gpu", gfx.optString("GL renderer") + ", " + gfx.optString("GL version"));
        if (feat != null) put(o, "has", feat.optJSONObject("key features"));
        JSONObject media = root.optJSONObject("media");
        if (media != null && media.optJSONObject("drm") != null) {
            JSONObject wv = media.optJSONObject("drm").optJSONObject("widevine");
            put(o, "widevine", wv == null ? "none" : wv.optString("securityLevel", "?"));
        }
        if (intents != null) {
            put(o, "home apps", names(intents.optJSONArray("home apps"), true));
            put(o, "current home", intents.optString("current home"));
            put(o, "settings apps", names(intents.optJSONArray("settings apps"), false));
        }
        if (apps != null) put(o, "apps", apps.optString("counts"));
        if (device != null) put(o, "build", device.optString("fingerprint"));
        put(o, "extras", root.optJSONObject("extras (adb grant)") == null ? "-"
                : root.optJSONObject("extras (adb grant)").optString("granted"));
        return o;
    }

    private static JSONArray names(JSONArray list, boolean priority) {
        JSONArray out = new JSONArray();
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r == null) continue;
            String s = r.optString("component");
            if (priority) s += " (priority " + r.optInt("priority") + ")";
            if (r.has("tv entry")) s += r.optBoolean("tv entry") ? " [TV]" : " [phone]";
            out.put(s);
        }
        return out;
    }

    // ---------------------------------------------------------------- device

    private JSONObject device() {
        JSONObject o = new JSONObject();
        put(o, "manufacturer", Build.MANUFACTURER);
        put(o, "brand", Build.BRAND);
        put(o, "model", Build.MODEL);
        put(o, "device", Build.DEVICE);
        put(o, "product", Build.PRODUCT);
        put(o, "board", Build.BOARD);
        put(o, "hardware", Build.HARDWARE);
        put(o, "platform", prop("ro.board.platform"));
        put(o, "bootloader", Build.BOOTLOADER);
        put(o, "android version", Build.VERSION.RELEASE);
        put(o, "api level", Build.VERSION.SDK_INT);
        put(o, "security patch", Build.VERSION.SECURITY_PATCH);
        put(o, "build id", Build.ID);
        put(o, "display id", Build.DISPLAY);
        put(o, "incremental", Build.VERSION.INCREMENTAL);
        put(o, "build type", Build.TYPE);
        put(o, "build tags", Build.TAGS);
        put(o, "build time", stamp(Build.TIME));
        put(o, "build user@host", Build.USER + "@" + Build.HOST);
        put(o, "fingerprint", Build.FINGERPRINT);
        put(o, "radio", Build.getRadioVersion());
        put(o, "supported abis", new JSONArray(java.util.Arrays.asList(Build.SUPPORTED_ABIS)));
        put(o, "32-bit abis", new JSONArray(java.util.Arrays.asList(Build.SUPPORTED_32_BIT_ABIS)));
        put(o, "64-bit abis", new JSONArray(java.util.Arrays.asList(Build.SUPPORTED_64_BIT_ABIS)));
        put(o, "kernel", trim(read("/proc/version")));
        put(o, "java vm", System.getProperty("java.vm.version"));
        put(o, "treble", prop("ro.treble.enabled"));
        put(o, "system as root", prop("ro.build.system_root_image"));
        put(o, "first api level", prop("ro.product.first_api_level"));
        put(o, "vendor fingerprint", prop("ro.vendor.build.fingerprint"));
        put(o, "locale", Locale.getDefault().toLanguageTag());
        put(o, "ui mode", uiMode(context.getResources().getConfiguration()));
        return o;
    }

    private JSONObject properties() {
        JSONObject o = new JSONObject();
        if (props.isEmpty()) return put(o, "error", "getprop gave nothing");
        for (Map.Entry<String, String> e : props.entrySet()) put(o, e.getKey(), e.getValue());
        return o;
    }

    // ---------------------------------------------------------------- cpu, memory, storage

    private JSONObject cpu() {
        JSONObject o = new JSONObject();
        int cores = Runtime.getRuntime().availableProcessors();
        put(o, "cores", cores);
        put(o, "load average", trim(read("/proc/loadavg")));
        JSONArray list = new JSONArray();
        for (int i = 0; i < 64; i++) {
            String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
            if (!new File("/sys/devices/system/cpu/cpu" + i).exists()) break;
            JSONObject c = new JSONObject();
            put(c, "cpu", i);
            put(c, "online", trim(read("/sys/devices/system/cpu/cpu" + i + "/online")));
            put(c, "min MHz", mhz(read(base + "cpuinfo_min_freq")));
            put(c, "max MHz", mhz(read(base + "cpuinfo_max_freq")));
            put(c, "current MHz", mhz(read(base + "scaling_cur_freq")));
            put(c, "governor", trim(read(base + "scaling_governor")));
            put(c, "available frequencies", trim(read(base + "scaling_available_frequencies")));
            list.put(c);
        }
        put(o, "per core", list);
        JSONArray thermal = new JSONArray();
        File[] zones = new File("/sys/class/thermal").listFiles();
        if (zones != null) {
            java.util.Arrays.sort(zones);
            for (File z : zones) {
                if (!z.getName().startsWith("thermal_zone")) continue;
                String temp = trim(read(z + "/temp"));
                JSONObject t = new JSONObject();
                put(t, "zone", z.getName());
                put(t, "type", trim(read(z + "/type")));
                put(t, "temp", celsius(temp));
                thermal.put(t);
            }
        }
        put(o, "thermal", thermal);
        put(o, "cpuinfo", read("/proc/cpuinfo"));
        return o;
    }

    private JSONObject memory() {
        JSONObject o = new JSONObject();
        ActivityManager am = context.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        put(o, "total", size(mi.totalMem));
        put(o, "available", size(mi.availMem));
        put(o, "low memory threshold", size(mi.threshold));
        put(o, "low memory now", mi.lowMemory);
        put(o, "low-RAM device", am.isLowRamDevice());
        put(o, "app heap limit", am.getMemoryClass() + " MB");
        put(o, "app large heap limit", am.getLargeMemoryClass() + " MB");
        put(o, "meminfo", read("/proc/meminfo"));
        String zram = trim(read("/sys/block/zram0/disksize"));
        if (zram != null) put(o, "zram size", size(Long.parseLong(zram)));
        put(o, "swaps", read("/proc/swaps"));
        return o;
    }

    private JSONObject storage() {
        JSONObject o = new JSONObject();
        put(o, "data", statfs(Environment.getDataDirectory()));
        put(o, "system", statfs(Environment.getRootDirectory()));
        put(o, "cache", statfs(Environment.getDownloadCacheDirectory()));
        put(o, "shared storage", statfs(Environment.getExternalStorageDirectory()));
        put(o, "shared storage emulated", Environment.isExternalStorageEmulated());
        put(o, "shared storage removable", Environment.isExternalStorageRemovable());
        JSONArray vols = new JSONArray();
        StorageManager sm = context.getSystemService(StorageManager.class);
        for (StorageVolume v : sm.getStorageVolumes()) {
            JSONObject j = new JSONObject();
            put(j, "name", v.getDescription(context));
            put(j, "uuid", v.getUuid());
            put(j, "state", v.getState());
            put(j, "primary", v.isPrimary());
            put(j, "removable", v.isRemovable());
            put(j, "emulated", v.isEmulated());
            File dir = Build.VERSION.SDK_INT >= 30 ? v.getDirectory() : volumePath(v);
            if (dir != null) {
                put(j, "path", dir.getPath());
                put(j, "space", statfs(dir));
            }
            vols.put(j);
        }
        put(o, "volumes", vols);
        put(o, "partitions", read("/proc/partitions"));
        put(o, "mounts", read("/proc/mounts"));
        return o;
    }

    /** StorageVolume.getPath() is hidden before Android 11; reflection is the only way on the TX6. */
    private static File volumePath(StorageVolume v) {
        try {
            Object path = StorageVolume.class.getMethod("getPath").invoke(v);
            return path == null ? null : new File(path.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject statfs(File dir) {
        JSONObject o = new JSONObject();
        try {
            StatFs s = new StatFs(dir.getPath());
            put(o, "path", dir.getPath());
            put(o, "total", size(s.getTotalBytes()));
            put(o, "free", size(s.getAvailableBytes()));
        } catch (Exception e) {
            put(o, "error", e.toString());
        }
        return o;
    }

    // ---------------------------------------------------------------- display, graphics

    private JSONObject display() {
        JSONObject o = new JSONObject();
        JSONArray list = new JSONArray();
        DisplayManager dmgr = context.getSystemService(DisplayManager.class);
        for (Display d : dmgr.getDisplays()) {
            JSONObject j = new JSONObject();
            DisplayMetrics real = new DisplayMetrics();
            DisplayMetrics app = new DisplayMetrics();
            d.getRealMetrics(real);
            d.getMetrics(app);
            put(j, "id", d.getDisplayId());
            put(j, "name", d.getName());
            put(j, "real size", real.widthPixels + "x" + real.heightPixels);
            put(j, "app size", app.widthPixels + "x" + app.heightPixels);
            put(j, "size in dp", Math.round(real.widthPixels / real.density) + "x"
                    + Math.round(real.heightPixels / real.density));
            put(j, "density dpi", real.densityDpi);
            put(j, "density", real.density);
            put(j, "scaled density", real.scaledDensity);
            put(j, "x/y dpi", real.xdpi + " / " + real.ydpi);
            put(j, "refresh rate", d.getRefreshRate());
            put(j, "state", d.getState());
            put(j, "hdr", d.isHdr());
            put(j, "wide colour gamut", d.isWideColorGamut());
            Display.HdrCapabilities hdr = d.getHdrCapabilities();
            if (hdr != null) {
                JSONArray types = new JSONArray();
                for (int t : hdr.getSupportedHdrTypes()) types.put(hdrType(t));
                put(j, "hdr types", types);
                put(j, "max luminance", hdr.getDesiredMaxLuminance());
            }
            Display.Mode mode = d.getMode();
            put(j, "mode", mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight() + " @ " + mode.getRefreshRate());
            JSONArray modes = new JSONArray();
            for (Display.Mode m : d.getSupportedModes()) {
                modes.put(m.getModeId() + ": " + m.getPhysicalWidth() + "x" + m.getPhysicalHeight()
                        + " @ " + m.getRefreshRate());
            }
            put(j, "supported modes", modes);
            list.put(j);
        }
        put(o, "displays", list);
        Configuration c = context.getResources().getConfiguration();
        put(o, "smallest width dp", c.smallestScreenWidthDp);
        put(o, "font scale", c.fontScale);
        put(o, "orientation", c.orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait");
        put(o, "touchscreen", c.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH ? "none" : "yes");
        put(o, "navigation", c.navigation == Configuration.NAVIGATION_DPAD ? "d-pad" : String.valueOf(c.navigation));
        put(o, "keyboard", c.keyboard);
        put(o, "ui mode", uiMode(c));
        return o;
    }

    private JSONObject graphics() {
        JSONObject o = new JSONObject();
        ActivityManager am = context.getSystemService(ActivityManager.class);
        ConfigurationInfo ci = am.getDeviceConfigurationInfo();
        put(o, "GL ES (system)", ci.getGlEsVersion());
        EGLDisplay d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] v = new int[2];
        if (!EGL14.eglInitialize(d, v, 0, v, 1)) return put(o, "error", "EGL failed to start");
        put(o, "EGL version", EGL14.eglQueryString(d, EGL14.EGL_VERSION));
        put(o, "EGL vendor", EGL14.eglQueryString(d, EGL14.EGL_VENDOR));
        put(o, "EGL client APIs", EGL14.eglQueryString(d, EGL14.EGL_CLIENT_APIS));
        int[] attr = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE};
        EGLConfig[] cfg = new EGLConfig[1];
        int[] n = new int[1];
        if (EGL14.eglChooseConfig(d, attr, 0, cfg, 0, 1, n, 0) && n[0] > 0) {
            EGLContext ctx = EGL14.eglCreateContext(d, cfg[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            EGLSurface s = EGL14.eglCreatePbufferSurface(d, cfg[0],
                    new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (EGL14.eglMakeCurrent(d, s, s, ctx)) {
                put(o, "GL vendor", GLES20.glGetString(GLES20.GL_VENDOR));
                put(o, "GL renderer", GLES20.glGetString(GLES20.GL_RENDERER));
                put(o, "GL version", GLES20.glGetString(GLES20.GL_VERSION));
                put(o, "GLSL version", GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION));
                int[] max = new int[1];
                GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0);
                put(o, "max texture size", max[0]);
                put(o, "GL extensions", sorted(GLES20.glGetString(GLES20.GL_EXTENSIONS)));
                EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            }
            EGL14.eglDestroySurface(d, s);
            EGL14.eglDestroyContext(d, ctx);
        }
        put(o, "EGL extensions", sorted(EGL14.eglQueryString(d, EGL14.EGL_EXTENSIONS)));
        put(o, "vulkan", feature("android.hardware.vulkan.version"));
        put(o, "vulkan level", feature("android.hardware.vulkan.level"));
        return o;
    }

    // ---------------------------------------------------------------- features, media

    private JSONObject features() {
        JSONObject o = new JSONObject();
        String[][] keys = {
                {"bluetooth", PackageManager.FEATURE_BLUETOOTH}, {"bluetooth LE", PackageManager.FEATURE_BLUETOOTH_LE},
                {"wifi", PackageManager.FEATURE_WIFI}, {"wifi direct", PackageManager.FEATURE_WIFI_DIRECT},
                {"ethernet", "android.hardware.ethernet"}, {"usb host", PackageManager.FEATURE_USB_HOST},
                {"usb accessory", PackageManager.FEATURE_USB_ACCESSORY},
                {"hdmi cec", "android.hardware.hdmi.cec"}, {"leanback (TV)", PackageManager.FEATURE_LEANBACK},
                {"live tv", PackageManager.FEATURE_LIVE_TV}, {"touchscreen", PackageManager.FEATURE_TOUCHSCREEN},
                {"microphone", PackageManager.FEATURE_MICROPHONE}, {"camera", PackageManager.FEATURE_CAMERA_ANY},
                {"location", PackageManager.FEATURE_LOCATION}, {"gps", PackageManager.FEATURE_LOCATION_GPS},
                {"nfc", PackageManager.FEATURE_NFC}, {"telephony", PackageManager.FEATURE_TELEPHONY},
                {"webview", PackageManager.FEATURE_WEBVIEW}, {"picture in picture", PackageManager.FEATURE_PICTURE_IN_PICTURE},
                {"printing", PackageManager.FEATURE_PRINTING}, {"app widgets", PackageManager.FEATURE_APP_WIDGETS},
                {"home screen", PackageManager.FEATURE_HOME_SCREEN}, {"device admin", PackageManager.FEATURE_DEVICE_ADMIN},
                {"input methods", PackageManager.FEATURE_INPUT_METHODS}, {"gamepad", PackageManager.FEATURE_GAMEPAD},
                {"audio output", PackageManager.FEATURE_AUDIO_OUTPUT}, {"opengl es aep", PackageManager.FEATURE_OPENGLES_EXTENSION_PACK},
                {"vulkan", "android.hardware.vulkan.version"}, {"freeform windows", PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT},
                {"verified boot", PackageManager.FEATURE_VERIFIED_BOOT}};
        JSONObject key = new JSONObject();
        for (String[] k : keys) put(key, k[0], pm.hasSystemFeature(k[1]) ? "yes" : "no");
        put(o, "key features", key);
        JSONArray all = new JSONArray();
        TreeSet<String> names = new TreeSet<>();
        for (FeatureInfo f : pm.getSystemAvailableFeatures()) {
            if (f.name != null) names.add(f.version > 0 ? f.name + " (v" + f.version + ")" : f.name);
        }
        for (String s : names) all.put(s);
        put(o, "all system features", all);
        put(o, "shared libraries", new JSONArray(sortedList(pm.getSystemSharedLibraryNames())));
        return o;
    }

    private JSONObject media() {
        JSONObject o = new JSONObject();
        JSONArray video = new JSONArray();
        JSONArray audio = new JSONArray();
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            for (String type : info.getSupportedTypes()) {
                JSONObject c = new JSONObject();
                put(c, "codec", info.getName());
                put(c, "type", type);
                put(c, "role", info.isEncoder() ? "encoder" : "decoder");
                put(c, "hardware", hardware(info));
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
                    MediaCodecInfo.VideoCapabilities vc = caps.getVideoCapabilities();
                    MediaCodecInfo.AudioCapabilities ac = caps.getAudioCapabilities();
                    if (vc != null) {
                        put(c, "max size", vc.getSupportedWidths().getUpper() + "x" + vc.getSupportedHeights().getUpper());
                        put(c, "frame rates", vc.getSupportedFrameRates().toString());
                        put(c, "bitrate", vc.getBitrateRange().toString());
                        put(c, "4K 60fps", vc.areSizeAndRateSupported(3840, 2160, 60));
                        put(c, "4K 30fps", vc.areSizeAndRateSupported(3840, 2160, 30));
                    }
                    if (ac != null) {
                        put(c, "channels", ac.getMaxInputChannelCount());
                        // Not getSupportedSampleRates(): on Android 9 it throws for codecs with a continuous range.
                        put(c, "sample rates", java.util.Arrays.toString(ac.getSupportedSampleRateRanges()));
                    }
                    JSONArray profiles = new JSONArray();
                    if (caps.profileLevels != null) for (MediaCodecInfo.CodecProfileLevel pl : caps.profileLevels) profiles.put(pl.profile + "/" + pl.level);
                    if (profiles.length() > 0) put(c, "profile/level", profiles);
                    put(c, "tunneled playback", caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback));
                    put(c, "secure playback", caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback));
                } catch (Exception e) {
                    put(c, "error", e.toString());
                }
                (type.startsWith("video/") ? video : audio).put(c);
            }
        }
        put(o, "video codecs", video);
        put(o, "audio codecs", audio);
        JSONObject drm = new JSONObject();
        put(drm, "widevine", drm(WIDEVINE));
        put(drm, "clearkey", drm(CLEARKEY));
        put(o, "drm", drm);
        AudioManager am = context.getSystemService(AudioManager.class);
        JSONArray outs = new JSONArray();
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            JSONObject a = new JSONObject();
            put(a, "name", String.valueOf(d.getProductName()));
            put(a, "type", audioType(d.getType()));
            put(a, "encodings", java.util.Arrays.toString(d.getEncodings()));
            put(a, "sample rates", java.util.Arrays.toString(d.getSampleRates()));
            put(a, "channel counts", java.util.Arrays.toString(d.getChannelCounts()));
            outs.put(a);
        }
        put(o, "audio outputs", outs);
        put(o, "output sample rate", am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
        put(o, "output frames per buffer", am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
        put(o, "music volume", am.getStreamVolume(AudioManager.STREAM_MUSIC) + " / "
                + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        put(o, "asound cards", read("/proc/asound/cards"));
        return o;
    }

    private static String hardware(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= 29) return info.isHardwareAccelerated() ? "yes" : "no";
        String n = info.getName().toLowerCase(Locale.ROOT);
        return n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw.") ? "no (guess)" : "yes (guess)";
    }

    private static Object drm(UUID uuid) {
        if (!MediaDrm.isCryptoSchemeSupported(uuid)) return "not supported";
        JSONObject o = new JSONObject();
        MediaDrm drm = null;
        try {
            drm = new MediaDrm(uuid);
            for (String key : new String[]{"vendor", "version", "description", "algorithms", "securityLevel",
                    "systemId", "hdcpLevel", "maxHdcpLevel", "usageReportingSupport", "maxNumberOfSessions",
                    "oemCryptoApiVersion"}) {
                try {
                    put(o, key, drm.getPropertyString(key));
                } catch (Exception ignored) {
                    // Not every plugin knows every property.
                }
            }
            if (Build.VERSION.SDK_INT >= 28) {
                put(o, "connected HDCP", drm.getConnectedHdcpLevel());
                put(o, "max HDCP", drm.getMaxHdcpLevel());
                put(o, "max security level", MediaDrm.getMaxSecurityLevel());
            }
        } catch (Exception e) {
            put(o, "error", e.toString());
        } finally {
            if (drm != null) {
                if (Build.VERSION.SDK_INT >= 28) drm.close();
                else drm.release();
            }
        }
        return o;
    }

    // ---------------------------------------------------------------- network, bluetooth, input

    @SuppressLint("HardwareIds") // the box's own addresses are what the report is for
    private JSONObject network() throws Exception {
        JSONObject o = new JSONObject();
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        Network active = cm.getActiveNetwork();
        JSONArray nets = new JSONArray();
        for (Network n : cm.getAllNetworks()) {
            JSONObject j = new JSONObject();
            put(j, "active", n.equals(active));
            NetworkInfo ni = cm.getNetworkInfo(n);
            if (ni != null) put(j, "type", ni.getTypeName() + (ni.getSubtypeName().isEmpty() ? "" : " " + ni.getSubtypeName()));
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null) {
                put(j, "internet validated", nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                put(j, "metered", !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
                put(j, "down/up kbps", nc.getLinkDownstreamBandwidthKbps() + " / " + nc.getLinkUpstreamBandwidthKbps());
                put(j, "capabilities", nc.toString());
            }
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) {
                put(j, "interface", lp.getInterfaceName());
                JSONArray addr = new JSONArray();
                for (LinkAddress a : lp.getLinkAddresses()) addr.put(a.toString());
                put(j, "addresses", addr);
                JSONArray dns = new JSONArray();
                for (InetAddress a : lp.getDnsServers()) dns.put(a.getHostAddress());
                put(j, "dns", dns);
                JSONArray routes = new JSONArray();
                for (RouteInfo r : lp.getRoutes()) routes.put(r.toString());
                put(j, "routes", routes);
                put(j, "domains", lp.getDomains());
                ProxyInfo proxy = lp.getHttpProxy();
                put(j, "proxy", proxy == null ? "none" : proxy.toString());
                if (Build.VERSION.SDK_INT >= 28) put(j, "private dns", lp.isPrivateDnsActive());
            }
            nets.put(j);
        }
        put(o, "networks", nets);
        JSONArray ifaces = new JSONArray();
        Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface ni : all == null ? new ArrayList<NetworkInterface>() : Collections.list(all)) {
            JSONObject j = new JSONObject();
            put(j, "name", ni.getName());
            put(j, "up", ni.isUp());
            put(j, "mtu", ni.getMTU());
            byte[] mac = ni.getHardwareAddress();
            put(j, "mac", mac == null ? "hidden" : hex(mac, ":"));
            JSONArray addr = new JSONArray();
            Enumeration<InetAddress> in = ni.getInetAddresses();
            if (in != null) for (InetAddress a : Collections.list(in)) addr.put(a.getHostAddress());
            put(j, "addresses", addr);
            ifaces.put(j);
        }
        put(o, "interfaces", ifaces);
        WifiManager wm = context.getApplicationContext().getSystemService(WifiManager.class);
        if (wm != null) {
            JSONObject w = new JSONObject();
            put(w, "enabled", wm.isWifiEnabled());
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null) {
                put(w, "ssid", wi.getSSID());
                put(w, "bssid", wi.getBSSID());
                put(w, "rssi dBm", wi.getRssi());
                put(w, "link speed Mbps", wi.getLinkSpeed());
                put(w, "frequency MHz", wi.getFrequency());
                put(w, "ip", ipv4(wi.getIpAddress()));
                put(w, "mac", wi.getMacAddress());
            }
            put(w, "5 GHz supported", wm.is5GHzBandSupported());
            put(w, "wifi direct supported", wm.isP2pSupported());
            put(w, "note", "The network name shows only when location access was allowed.");
            put(o, "wifi", w);
        }
        put(o, "listening ports", listening());
        put(o, "global http proxy", Settings.Global.getString(context.getContentResolver(), "http_proxy"));
        return o;
    }

    /** TCP ports in LISTEN state, from /proc/net/tcp and tcp6 (open on Android 9, closed from 10). */
    private static JSONArray listening() {
        TreeSet<Integer> ports = new TreeSet<>();
        for (String f : new String[]{"/proc/net/tcp", "/proc/net/tcp6"}) {
            String s = read(f);
            if (s == null) continue;
            for (String line : s.split("\n")) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 4 || !"0A".equals(p[3])) continue;
                int colon = p[1].lastIndexOf(':');
                try {
                    ports.add(Integer.parseInt(p[1].substring(colon + 1), 16));
                } catch (NumberFormatException ignored) {
                    // Header line.
                }
            }
        }
        JSONArray out = new JSONArray();
        for (int port : ports) out.put(port == 5555 ? "5555 (network adb)" : String.valueOf(port));
        return out;
    }

    @SuppressLint("HardwareIds")
    private JSONObject bluetooth() {
        JSONObject o = new JSONObject();
        BluetoothManager bm = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter a = bm == null ? null : bm.getAdapter();
        put(o, "feature declared", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        if (a == null) return put(o, "adapter", "none: this box has no Bluetooth");
        put(o, "adapter", "present");
        if (Build.VERSION.SDK_INT >= 31
                && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return put(o, "details", "not allowed (Nearby devices permission refused)");
        }
        try {
            put(o, "enabled", a.isEnabled());
            put(o, "state", btState(a.getState()));
            put(o, "name", a.getName());
            put(o, "address", a.getAddress());
            put(o, "LE supported", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE));
            put(o, "LE 2M PHY", a.isLe2MPhySupported());
            put(o, "LE extended advertising", a.isLeExtendedAdvertisingSupported());
            JSONArray bonded = new JSONArray();
            for (BluetoothDevice d : a.getBondedDevices()) {
                JSONObject j = new JSONObject();
                put(j, "name", d.getName());
                put(j, "address", d.getAddress());
                put(j, "type", d.getType());
                put(j, "class", d.getBluetoothClass() == null ? "-" : Integer.toHexString(d.getBluetoothClass().getDeviceClass()));
                bonded.put(j);
            }
            put(o, "paired devices", bonded);
        } catch (SecurityException e) {
            put(o, "details", "not allowed: " + e.getMessage());
        }
        return o;
    }

    private JSONObject input() {
        JSONObject o = new JSONObject();
        InputManager im = context.getSystemService(InputManager.class);
        int[] probe = {KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_GUIDE,
                KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_SEARCH,
                KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_CHANNEL_UP,
                KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_POWER,
                KeyEvent.KEYCODE_MOVE_HOME};
        JSONArray list = new JSONArray();
        for (int id : im.getInputDeviceIds()) {
            InputDevice d = im.getInputDevice(id);
            if (d == null) continue;
            JSONObject j = new JSONObject();
            put(j, "name", d.getName());
            put(j, "id", id);
            put(j, "vendor:product", String.format(Locale.ROOT, "%04x:%04x", d.getVendorId(), d.getProductId()));
            put(j, "virtual", d.isVirtual());
            if (Build.VERSION.SDK_INT >= 29) put(j, "external", d.isExternal());
            put(j, "sources", sources(d.getSources()));
            put(j, "keyboard", d.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC ? "alphabetic"
                    : d.getKeyboardType() == InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC ? "keys" : "none");
            put(j, "descriptor", d.getDescriptor());
            boolean[] has = d.hasKeys(probe);
            JSONArray keys = new JSONArray();
            for (int i = 0; i < probe.length; i++) {
                if (has[i]) keys.put(KeyEvent.keyCodeToString(probe[i]).replace("KEYCODE_", ""));
            }
            put(j, "special keys", keys);
            list.put(j);
        }
        put(o, "devices", list);
        return o;
    }

    // ---------------------------------------------------------------- intents and defaults

    private JSONObject intents() {
        JSONObject o = new JSONObject();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        put(o, "home apps", resolve(home, true));
        put(o, "current home", current(home));
        Intent settings = new Intent(Settings.ACTION_SETTINGS);
        JSONArray settingsApps = resolve(settings, false);
        for (int i = 0; i < settingsApps.length(); i++) {
            JSONObject r = settingsApps.optJSONObject(i);
            put(r, "tv entry", pm.getLeanbackLaunchIntentForPackage(r.optString("package")) != null);
        }
        put(o, "settings apps", settingsApps);
        put(o, "settings opens", current(settings));
        Object[][] list = {
                {"home settings", new Intent(Settings.ACTION_HOME_SETTINGS)},
                {"browser", new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)},
                {"video player", new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///x.mp4"), "video/mp4")},
                {"audio player", new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///x.mp3"), "audio/mpeg")},
                {"image viewer", new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///x.jpg"), "image/jpeg")},
                {"apk installer", new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://x/y.apk"),
                        "application/vnd.android.package-archive")},
                {"file picker", new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)},
                {"document picker (SAF)", new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)},
                {"play store", new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.example"))},
                {"voice input", new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)},
                {"web search", new Intent(Intent.ACTION_WEB_SEARCH)},
                {"assistant", new Intent(Intent.ACTION_ASSIST)},
                {"wifi settings", new Intent(Settings.ACTION_WIFI_SETTINGS)},
                {"bluetooth settings", new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)},
                {"display settings", new Intent(Settings.ACTION_DISPLAY_SETTINGS)},
                {"sound settings", new Intent(Settings.ACTION_SOUND_SETTINGS)},
                {"date settings", new Intent(Settings.ACTION_DATE_SETTINGS)},
                {"apps settings", new Intent(Settings.ACTION_APPLICATION_SETTINGS)},
                {"app info", new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:android"))},
                {"keyboard settings", new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)},
                {"screensaver settings", new Intent(Settings.ACTION_DREAM_SETTINGS)},
                {"developer options", new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)},
                {"about device", new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)},
                {"accessibility settings", new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)},
                {"unknown sources", new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)},
                {"all apps (TV)", new Intent("android.intent.action.ALL_APPS")}};
        JSONObject others = new JSONObject();
        for (Object[] row : list) {
            JSONObject j = new JSONObject();
            put(j, "opens", current((Intent) row[1]));
            put(j, "handlers", resolve((Intent) row[1], false));
            put(others, (String) row[0], j);
        }
        put(o, "other intents", others);
        put(o, "tts engines", services(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)));
        ContentResolver cr = context.getContentResolver();
        JSONObject dreams = new JSONObject();
        put(dreams, "installed", services(new Intent(DreamService.SERVICE_INTERFACE)));
        put(dreams, "chosen", Settings.Secure.getString(cr, "screensaver_components"));
        put(dreams, "enabled", Settings.Secure.getString(cr, "screensaver_enabled"));
        put(dreams, "start after (ms)", Settings.System.getString(cr, Settings.System.SCREEN_OFF_TIMEOUT));
        put(dreams, "sleep after (ms)", Settings.Secure.getString(cr, "sleep_timeout"));
        put(o, "screensavers", dreams);
        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        JSONObject ime = new JSONObject();
        JSONArray installed = new JSONArray();
        for (InputMethodInfo i : imm.getInputMethodList()) installed.put(i.getId());
        put(ime, "installed", installed);
        JSONArray enabled = new JSONArray();
        for (InputMethodInfo i : imm.getEnabledInputMethodList()) enabled.put(i.getId());
        put(ime, "enabled", enabled);
        put(ime, "default", Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD));
        put(o, "keyboards", ime);
        AccessibilityManager acc = context.getSystemService(AccessibilityManager.class);
        JSONObject a = new JSONObject();
        JSONArray accAll = new JSONArray();
        for (AccessibilityServiceInfo s : acc.getInstalledAccessibilityServiceList()) accAll.put(s.getId());
        put(a, "installed", accAll);
        JSONArray accOn = new JSONArray();
        for (AccessibilityServiceInfo s : acc.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            accOn.put(s.getId());
        }
        put(a, "on", accOn);
        put(o, "accessibility services", a);
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        JSONArray admins = new JSONArray();
        List<ComponentName> active = dpm == null ? null : dpm.getActiveAdmins();
        if (active != null) for (ComponentName c : active) admins.put(c.flattenToShortString());
        put(o, "device admins", admins);
        put(o, "notification listeners", Settings.Secure.getString(cr, "enabled_notification_listeners"));
        return o;
    }

    private JSONArray resolve(Intent intent, boolean withPriority) {
        JSONArray out = new JSONArray();
        List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo r : list) {
            JSONObject j = new JSONObject();
            ActivityInfo ai = r.activityInfo;
            put(j, "component", ai.packageName + "/" + ai.name.replace(ai.packageName, ""));
            put(j, "package", ai.packageName);
            put(j, "label", String.valueOf(r.loadLabel(pm)));
            put(j, "priority", r.priority);
            if (withPriority) {
                put(j, "preferred order", r.preferredOrder);
                put(j, "default category", r.isDefault);
                put(j, "enabled", ai.isEnabled());
                put(j, "system app", (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            }
            out.put(j);
        }
        return out;
    }

    private String current(Intent intent) {
        ResolveInfo r = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (r == null || r.activityInfo == null) return "nothing";
        ActivityInfo ai = r.activityInfo;
        if ("android".equals(ai.packageName) || ai.name.endsWith("ResolverActivity")) return "no default (asks)";
        return ai.packageName + "/" + ai.name.replace(ai.packageName, "");
    }

    private JSONArray services(Intent intent) {
        JSONArray out = new JSONArray();
        for (ResolveInfo r : pm.queryIntentServices(intent, PackageManager.MATCH_ALL)) {
            ServiceInfo si = r.serviceInfo;
            out.put(si.packageName + "/" + si.name.replace(si.packageName, "") + " (" + r.loadLabel(pm) + ")");
        }
        return out;
    }

    // ---------------------------------------------------------------- apps

    @SuppressWarnings("deprecation")
    private JSONObject apps() {
        JSONObject o = new JSONObject();
        platformSigner = signer("android");
        List<ApplicationInfo> all = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS
                | PackageManager.MATCH_UNINSTALLED_PACKAGES);
        Collections.sort(all, (a, b) -> a.packageName.compareTo(b.packageName));
        int system = 0, user = 0, disabled = 0, tv = 0;
        JSONArray list = new JSONArray();
        int sigFlag = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        int flags = PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS | PackageManager.MATCH_DISABLED_COMPONENTS
                | sigFlag;
        for (ApplicationInfo ai : all) {
            JSONObject j = new JSONObject();
            boolean sys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean on = ai.enabled;
            if (sys) system++;
            else user++;
            if (!on) disabled++;
            put(j, "package", ai.packageName);
            put(j, "label", String.valueOf(ai.loadLabel(pm)));
            PackageInfo pi;
            try {
                // One package per call: all of them at once overflows the binder on a box with many apps.
                pi = pm.getPackageInfo(ai.packageName, flags);
            } catch (Exception e) {
                put(j, "error", e.toString());
                list.put(j);
                continue;
            }
            put(j, "version", pi.versionName + " (" + (Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode) + ")");
            put(j, "kind", sys ? ((ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ? "system (updated)" : "system") : "user");
            put(j, "state", on ? "enabled" : "disabled");
            put(j, "enabled setting", enabledSetting(ai.packageName));
            put(j, "installed (not just data)", (ai.flags & ApplicationInfo.FLAG_INSTALLED) != 0);
            boolean tvEntry = pm.getLeanbackLaunchIntentForPackage(ai.packageName) != null;
            boolean phoneEntry = pm.getLaunchIntentForPackage(ai.packageName) != null;
            if (tvEntry) tv++;
            put(j, "entry", tvEntry && phoneEntry ? "TV + phone" : tvEntry ? "TV" : phoneEntry ? "phone" : "none");
            put(j, "uid", ai.uid);
            put(j, "min/target sdk", ai.minSdkVersion + " / " + ai.targetSdkVersion);
            put(j, "installer", installer(ai.packageName));
            put(j, "first installed", stamp(pi.firstInstallTime));
            put(j, "updated", stamp(pi.lastUpdateTime));
            put(j, "apk", ai.sourceDir);
            put(j, "apk size", size(new File(ai.sourceDir).length()));
            put(j, "data dir", ai.dataDir);
            put(j, "debuggable", (ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
            put(j, "persistent", (ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0);
            String sig = signer(pi);
            put(j, "signer sha256", sig);
            put(j, "platform signed", sig != null && sig.equals(platformSigner));
            JSONArray perms = new JSONArray();
            if (pi.requestedPermissions != null) {
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    boolean granted = pi.requestedPermissionsFlags != null
                            && (pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                    perms.put(pi.requestedPermissions[i].replace("android.permission.", "")
                            + (granted ? " [granted]" : " [not granted]"));
                }
            }
            put(j, "permissions", perms);
            JSONArray defined = new JSONArray();
            if (pi.permissions != null) {
                for (android.content.pm.PermissionInfo p : pi.permissions) defined.put(p.name);
            }
            put(j, "defines permissions", defined);
            put(j, "activities", components(pi.activities));
            put(j, "services", components(pi.services));
            put(j, "receivers", components(pi.receivers));
            put(j, "providers", components(pi.providers));
            list.put(j);
        }
        put(o, "counts", all.size() + " total: " + system + " system, " + user + " user, " + disabled
                + " disabled, " + tv + " with a TV entry");
        put(o, "list", list);
        return o;
    }

    private String enabledSetting(String pkg) {
        try {
            switch (pm.getApplicationEnabledSetting(pkg)) {
                case PackageManager.COMPONENT_ENABLED_STATE_ENABLED: return "enabled";
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED: return "disabled";
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER: return "disabled by user";
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED: return "disabled until used";
                default: return "default";
            }
        } catch (Exception e) {
            return "-";
        }
    }

    @SuppressWarnings("deprecation")
    private String installer(String pkg) {
        try {
            String s = pm.getInstallerPackageName(pkg);
            return s == null ? "-" : s;
        } catch (Exception e) {
            return "-";
        }
    }

    private static JSONArray components(ComponentInfo[] items) {
        JSONArray out = new JSONArray();
        if (items == null) return out;
        for (ComponentInfo c : items) {
            String name = c.name.startsWith(c.packageName) ? c.name.substring(c.packageName.length()) : c.name;
            out.put(name + (c.exported ? " [exported]" : "") + (c.isEnabled() ? "" : " [disabled]"));
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    private String signer(String pkg) {
        try {
            int flag = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            return signer(pm.getPackageInfo(pkg, flag));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static String signer(PackageInfo pi) {
        Signature[] sigs;
        if (Build.VERSION.SDK_INT >= 28) {
            sigs = pi.signingInfo == null ? null : pi.signingInfo.getApkContentsSigners();
        } else {
            sigs = pi.signatures;
        }
        if (sigs == null || sigs.length == 0) return null;
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray()), ":");
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- settings, power, security

    private JSONObject settings() {
        JSONObject o = new JSONObject();
        put(o, "global", table(Settings.Global.CONTENT_URI, Settings.Global.class));
        put(o, "system", table(Settings.System.CONTENT_URI, Settings.System.class));
        put(o, "secure", table(Settings.Secure.CONTENT_URI, Settings.Secure.class));
        LocaleList locales = context.getResources().getConfiguration().getLocales();
        put(o, "locales", locales.toLanguageTags());
        put(o, "timezone", java.util.TimeZone.getDefault().getID());
        put(o, "24-hour clock", DateFormat.is24HourFormat(context));
        return o;
    }

    /** The whole table where Android lets an app read it; otherwise each public key one by one. */
    private JSONObject table(Uri uri, Class<?> keys) {
        JSONObject o = new JSONObject();
        ContentResolver cr = context.getContentResolver();
        TreeMap<String, String> rows = new TreeMap<>();
        try (Cursor c = cr.query(uri, new String[]{"name", "value"}, null, null, null)) {
            if (c != null) while (c.moveToNext()) rows.put(c.getString(0), c.getString(1));
        } catch (Exception e) {
            put(o, "full table", "not allowed: " + e.getMessage());
        }
        if (rows.isEmpty()) {
            for (Field f : keys.getFields()) {
                if (f.getType() != String.class || !Modifier.isStatic(f.getModifiers())) continue;
                try {
                    String key = (String) f.get(null);
                    if (key == null || key.contains("/") || key.contains(".")) continue;
                    String value = keys == Settings.Global.class ? Settings.Global.getString(cr, key)
                            : keys == Settings.System.class ? Settings.System.getString(cr, key)
                            : Settings.Secure.getString(cr, key);
                    if (value != null) rows.put(key, value);
                } catch (Exception ignored) {
                    // A key the firmware refuses: left out.
                }
            }
        }
        for (Map.Entry<String, String> e : rows.entrySet()) put(o, e.getKey(), e.getValue());
        return o;
    }

    private JSONObject power() {
        JSONObject o = new JSONObject();
        put(o, "up for", duration(SystemClock.elapsedRealtime()));
        put(o, "awake for", duration(SystemClock.uptimeMillis()));
        put(o, "booted at", stamp(System.currentTimeMillis() - SystemClock.elapsedRealtime()));
        put(o, "boot count", Settings.Global.getString(context.getContentResolver(), "boot_count"));
        PowerManager p = context.getSystemService(PowerManager.class);
        put(o, "screen on", p.isInteractive());
        put(o, "power saver", p.isPowerSaveMode());
        put(o, "doze now", p.isDeviceIdleMode());
        put(o, "this app ignores battery limits", p.isIgnoringBatteryOptimizations(context.getPackageName()));
        Intent bat = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat != null) {
            JSONObject b = new JSONObject();
            put(b, "present", bat.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false));
            put(b, "level", bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) + " / " + bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1));
            int plug = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            put(b, "plugged", plug == 0 ? "no" : plug == BatteryManager.BATTERY_PLUGGED_AC ? "AC" : String.valueOf(plug));
            put(b, "technology", bat.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
            put(b, "temperature", bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0 + " °C");
            put(o, "battery / power", b);
        }
        ActivityManager am = context.getSystemService(ActivityManager.class);
        JSONArray procs = new JSONArray();
        List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
        if (running != null) for (ActivityManager.RunningAppProcessInfo r : running) procs.put(r.processName + " (pid " + r.pid + ")");
        put(o, "running processes visible to this app", procs);
        return o;
    }

    private JSONObject security() throws Exception {
        JSONObject o = new JSONObject();
        put(o, "selinux", trim(run(4, "getenforce")));
        put(o, "debuggable", prop("ro.debuggable"));
        put(o, "secure", prop("ro.secure"));
        put(o, "adb secure", prop("ro.adb.secure"));
        put(o, "build tags", Build.TAGS);
        put(o, "verified boot", prop("ro.boot.verifiedbootstate"));
        put(o, "flash locked", prop("ro.boot.flash.locked"));
        put(o, "encryption", prop("ro.crypto.state") + " " + prop("ro.crypto.type"));
        put(o, "usb adb", Settings.Global.getString(context.getContentResolver(), Settings.Global.ADB_ENABLED));
        put(o, "network adb port", prop("service.adb.tcp.port") + " / persist " + prop("persist.adb.tcp.port"));
        JSONArray su = new JSONArray();
        for (String path : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/vendor/bin/su",
                "/system/app/Superuser.apk", "/data/adb/magisk"}) {
            if (new File(path).exists()) su.put(path);
        }
        put(o, "su / root files", su);
        boolean magisk = false;
        for (String pkg : new String[]{"com.topjohnwu.magisk", "eu.chainfire.supersu", "me.phh.superuser"}) {
            try {
                pm.getPackageInfo(pkg, 0);
                magisk = true;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Not installed.
            }
        }
        put(o, "root manager app", magisk);
        KeyStore ks = KeyStore.getInstance("AndroidCAStore");
        ks.load(null, null);
        JSONArray system = new JSONArray();
        JSONArray user = new JSONArray();
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        TreeMap<String, String> certs = new TreeMap<>();
        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {
            String alias = e.nextElement();
            if (!(ks.getCertificate(alias) instanceof X509Certificate)) continue;
            X509Certificate c = (X509Certificate) ks.getCertificate(alias);
            String name = c.getSubjectX500Principal().getName();
            certs.put(alias, cn(name) + " (until " + day.format(c.getNotAfter()) + ")");
        }
        for (Map.Entry<String, String> e : certs.entrySet()) {
            (e.getKey().startsWith("user:") ? user : system).put(e.getValue());
        }
        put(o, "ca certificates (system)", system);
        put(o, "ca certificates (user-added)", user);
        return o;
    }

    // ---------------------------------------------------------------- extras

    private JSONObject extras() {
        JSONObject o = new JSONObject();
        boolean logs = granted(Manifest.permission.READ_LOGS);
        boolean dump = granted(Manifest.permission.DUMP);
        boolean usage = usageAllowed();
        put(o, "granted", (logs ? "logcat " : "") + (dump ? "dumpsys " : "") + (usage ? "usage " : "")
                + (logs || dump || usage ? "" : "none"));
        String pkg = context.getPackageName();
        put(o, "how to grant", "adb shell pm grant " + pkg + " android.permission.READ_LOGS\n"
                + "adb shell pm grant " + pkg + " android.permission.DUMP\n"
                + "adb shell appops set " + pkg + " GET_USAGE_STATS allow");
        put(o, "dmesg", "not allowed for apps on Android 9+ (needs root)");
        put(o, "kernel cmdline", orNot(read("/proc/cmdline")));
        if (logs) {
            put(o, "logcat crashes", run(20, "logcat", "-d", "-b", "crash"));
            put(o, "logcat (last 10000 lines)", run(30, "logcat", "-d", "-b", "main,system,events", "-v", "threadtime",
                    "-t", "10000"));
        }
        if (dump) {
            JSONObject d = new JSONObject();
            for (String s : new String[]{"display", "hdmi_control", "audio", "input", "meminfo", "cpuinfo", "battery",
                    "power", "connectivity", "wifi", "bluetooth_manager", "window displays", "activity recents",
                    "package preferred-activities", "SurfaceFlinger --list"}) {
                List<String> cmd = new ArrayList<>();
                cmd.add("dumpsys");
                Collections.addAll(cmd, s.split(" "));
                put(d, s, run(20, cmd.toArray(new String[0])));
            }
            put(o, "dumpsys", d);
        }
        if (usage) {
            UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - TimeUnit.DAYS.toMillis(7), now);
            Collections.sort(stats, (a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));
            JSONArray list = new JSONArray();
            TreeSet<String> seen = new TreeSet<>();
            for (UsageStats s : stats) {
                if (s.getTotalTimeInForeground() == 0 || !seen.add(s.getPackageName())) continue;
                list.put(s.getPackageName() + ": " + duration(s.getTotalTimeInForeground())
                        + ", last " + stamp(s.getLastTimeUsed()));
            }
            put(o, "app usage, last 7 days", list);
        }
        return o;
    }

    private boolean granted(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private boolean usageAllowed() {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(),
                context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    // ---------------------------------------------------------------- helpers

    private void readProps() {
        String out = run(10, "getprop");
        if (out == null) return;
        for (String line : out.split("\n")) {
            int split = line.indexOf("]: [");
            if (line.startsWith("[") && split > 0 && line.endsWith("]")) {
                props.put(line.substring(1, split), line.substring(split + 4, line.length() - 1));
            }
        }
    }

    private String prop(String key) {
        String v = props.get(key);
        return v == null || v.isEmpty() ? "-" : v;
    }

    private String feature(String name) {
        for (FeatureInfo f : pm.getSystemAvailableFeatures()) {
            if (name.equals(f.name)) return f.version == 0 ? "yes" : "yes (" + Integer.toHexString(f.version) + ")";
        }
        return "no";
    }

    private String versionName() {
        try {
            return pm.getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** Runs a command and returns its output (stdout and stderr), cut to 256 KB; null if it can't start. */
    static String run(int seconds, String... cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = readAll(p.getInputStream());
            p.waitFor(seconds, TimeUnit.SECONDS);
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            if (p != null) p.destroy();
        }
    }

    static String read(String path) {
        try (InputStream in = new FileInputStream(path)) {
            return readAll(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) {
                if (sb.length() < TEXT_LIMIT) sb.append(buf, 0, Math.min(n, TEXT_LIMIT - sb.length()));
            }
        }
        if (sb.length() >= TEXT_LIMIT) sb.append("\n[cut at 256 KB]");
        return sb.toString();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String orNot(String s) {
        if (s == null || s.trim().isEmpty() || s.contains("Permission denied") || s.contains("not permitted")) {
            return "not allowed";
        }
        return s;
    }

    private static String mhz(String khz) {
        try {
            return String.valueOf(Long.parseLong(khz.trim()) / 1000);
        } catch (Exception e) {
            return "-";
        }
    }

    private static String celsius(String raw) {
        try {
            long t = Long.parseLong(raw);
            return (t > 1000 ? t / 1000.0 : t) + " °C";
        } catch (Exception e) {
            return "-";
        }
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        do {
            v /= 1024;
            i++;
        } while (v >= 1024 && i < units.length - 1);
        return String.format(Locale.ROOT, v >= 100 ? "%.0f %s" : "%.1f %s", v, units[i]);
    }

    static String duration(long ms) {
        long m = ms / 60000;
        return m >= 1440 ? (m / 1440) + "d " + (m % 1440 / 60) + "h " + (m % 60) + "m"
                : m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    private static String stamp(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(ms));
    }

    static String hex(byte[] bytes, String sep) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(String.format(Locale.ROOT, "%02X", b));
        }
        return sb.toString();
    }

    private static String ipv4(int ip) {
        return (ip & 0xff) + "." + (ip >> 8 & 0xff) + "." + (ip >> 16 & 0xff) + "." + (ip >> 24 & 0xff);
    }

    private static String cn(String dn) {
        for (String part : dn.split(",")) {
            if (part.startsWith("CN=")) return part.substring(3);
        }
        return dn;
    }

    private static JSONArray sorted(String spaced) {
        JSONArray out = new JSONArray();
        if (spaced == null) return out;
        for (String s : new TreeSet<>(java.util.Arrays.asList(spaced.trim().split("\\s+")))) out.put(s);
        return out;
    }

    private static List<String> sortedList(String[] items) {
        List<String> out = new ArrayList<>();
        if (items != null) Collections.addAll(out, items);
        Collections.sort(out);
        return out;
    }

    private static String uiMode(Configuration c) {
        switch (c.uiMode & Configuration.UI_MODE_TYPE_MASK) {
            case Configuration.UI_MODE_TYPE_TELEVISION: return "television";
            case Configuration.UI_MODE_TYPE_NORMAL: return "normal (phone/tablet)";
            case Configuration.UI_MODE_TYPE_DESK: return "desk";
            case Configuration.UI_MODE_TYPE_APPLIANCE: return "appliance";
            default: return String.valueOf(c.uiMode & Configuration.UI_MODE_TYPE_MASK);
        }
    }

    private static String hdrType(int t) {
        switch (t) {
            case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION: return "Dolby Vision";
            case Display.HdrCapabilities.HDR_TYPE_HDR10: return "HDR10";
            case Display.HdrCapabilities.HDR_TYPE_HLG: return "HLG";
            default: return "type " + t;
        }
    }

    private static String audioType(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC: return "HDMI ARC";
            case AudioDeviceInfo.TYPE_LINE_ANALOG: return "analogue line";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL: return "digital line (S/PDIF)";
            case AudioDeviceInfo.TYPE_AUX_LINE: return "AV / aux";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "built-in speaker";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth A2DP";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB device";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB headset";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "wired headphones";
            case AudioDeviceInfo.TYPE_TELEPHONY: return "telephony";
            default: return "type " + t;
        }
    }

    private static String btState(int s) {
        switch (s) {
            case BluetoothAdapter.STATE_ON: return "on";
            case BluetoothAdapter.STATE_OFF: return "off";
            case BluetoothAdapter.STATE_TURNING_ON: return "turning on";
            case BluetoothAdapter.STATE_TURNING_OFF: return "turning off";
            default: return String.valueOf(s);
        }
    }

    private static JSONArray sources(int s) {
        JSONArray out = new JSONArray();
        int[][] map = {{InputDevice.SOURCE_KEYBOARD}, {InputDevice.SOURCE_DPAD}, {InputDevice.SOURCE_GAMEPAD},
                {InputDevice.SOURCE_MOUSE}, {InputDevice.SOURCE_TOUCHSCREEN}, {InputDevice.SOURCE_TOUCHPAD},
                {InputDevice.SOURCE_JOYSTICK}, {InputDevice.SOURCE_STYLUS}, {InputDevice.SOURCE_TRACKBALL},
                {InputDevice.SOURCE_HDMI}, {InputDevice.SOURCE_ROTARY_ENCODER}};
        String[] names = {"keyboard", "dpad", "gamepad", "mouse", "touchscreen", "touchpad", "joystick", "stylus",
                "trackball", "hdmi (CEC)", "rotary"};
        for (int i = 0; i < map.length; i++) {
            if ((s & map[i][0]) == map[i][0]) out.put(names[i]);
        }
        return out;
    }
}
