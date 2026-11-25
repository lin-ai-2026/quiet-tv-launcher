package com.lin.magazinehome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Prefs {
    /** Popular TV video apps, pinned by default when installed. */
    static final String[] VIDEO_APPS = {
            "com.ktcp.video", "com.ktcp.csvideo", "com.gitvdemo.video", "com.qiyi.video.speaker", "com.youku.taitan.tv",
            "com.cibn.tv", "com.mgtv.tv", "tv.danmaku.bili", "com.xiaodianshi.tv.yst", "com.hunantv.license",
            "com.dangbei.dbmusic", "org.xbmc.kodi", "com.teamsmart.videomanager.tv", "com.google.android.youtube.tv",
            "com.netflix.ninja", "com.tcl.ui_mediaCenter"};
    static final String[][] PORT_SETS = {{"1", "2", "3"}, {"1", "2"}, {"1"}, {"1", "2", "3", "4"}};
    static final String[] PORT_SET_NAMES = {"HDMI 1 · 2 · 3", "HDMI 1 · 2", "仅 HDMI 1", "HDMI 1 · 2 · 3 · 4"};
    static String[] ports(Context c) { return PORT_SETS[Math.min(idx(c, "ports_idx", 0), PORT_SETS.length - 1)]; }
    static boolean showHdmi(Context c) { return p(c).getBoolean("show_hdmi_on", false); }
    static boolean showCaption(Context c) { return p(c).getBoolean("show_caption", false); }
    static boolean newsOn(Context c) { return p(c).getBoolean("news_on", true); }
    static final String[] NEWS_SOURCES = {"中新网", "新浪新闻", "IT之家"};
    static int newsSource(Context c) { return Math.min(idx(c, "news_src", 0), 2); }
    static final int MAX_PINS = 8;

    static SharedPreferences p(Context c) { return c.getSharedPreferences("mag", Context.MODE_PRIVATE); }

    /** One-time: restore Bing photos as the wallpaper (v4 had been left on solid charcoal). */
    static void migrateVisuals(Context c) {
        SharedPreferences sp = p(c);
        if (sp.getInt("visual_rev", 0) >= 5) return;
        sp.edit().putInt("wall_src", 0).putBoolean("show_caption", false).putInt("visual_rev", 5).apply();
    }

    static List<String> pins(Context c) {
        String s = p(c).getString("pins2", null);
        if (s == null) {
            List<String> d = new ArrayList<>();
            for (String v : VIDEO_APPS) if (installed(c, v)) d.add(v);
            d.add("com.tcl.settings");
            return d;
        }
        List<String> out = new ArrayList<>();
        for (String x : s.split(",")) if (!x.isEmpty()) out.add(x);
        return out;
    }

    static void savePins(Context c, List<String> pins) {
        StringBuilder b = new StringBuilder();
        for (String x : pins) { if (b.length() > 0) b.append(','); b.append(x); }
        p(c).edit().putString("pins2", b.toString()).apply();
    }

    /** @return true if now pinned, false if unpinned, null if full */
    static Boolean togglePin(Context c, String pkg) {
        List<String> l = pins(c);
        if (l.remove(pkg)) { savePins(c, l); return false; }
        if (l.size() >= MAX_PINS) return null;
        l.add(pkg); savePins(c, l); return true;
    }

    static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    static void move(Context c, String pkg, int delta) {
        List<String> l = pins(c);
        int i = l.indexOf(pkg), j = i + delta;
        if (i < 0 || j < 0 || j >= l.size()) return;
        l.remove(i); l.add(j, pkg); savePins(c, l);
    }

    static void unpin(Context c, String pkg) {
        List<String> l = pins(c);
        if (l.remove(pkg)) savePins(c, l);
    }

    static int hdmiPort(Context c) { return p(c).getInt("hdmi_port", 1); }
    static void setHdmiPort(Context c, int port) { p(c).edit().putInt("hdmi_port", port).apply(); }
    static boolean autoHdmi(Context c) { return p(c).getBoolean("auto_hdmi", false); }
    static void setAutoHdmi(Context c, boolean v) { p(c).edit().putBoolean("auto_hdmi", v).apply(); }
    static int idleMinutes(Context c) { return idleMinutesSetting(c); }

    // ---- wallpaper / news settings (all persisted)
    static final String[] WALL_SOURCES = {"bing", "bundled", "solid", "folder", "url", "phone", "dark"};
    static final String[] WALL_SOURCE_NAMES = {"必应在线", "内置精选", "纯色", "自定义文件夹", "自定义图片链接", "手机上传", "暗色（省电）"};
    static final int[] SOLID_COLORS = {0xFF000000, 0xFF1C1C1E, 0xFF14213D, 0xFF1F2A24, 0xFFEDE8DF};
    static final String[] SOLID_NAMES = {"纯黑", "深炭灰", "午夜蓝", "墨绿", "暖白"};
    static int solidColor(Context c) { return SOLID_COLORS[Math.min(idx(c, "solid_idx", 0), SOLID_COLORS.length - 1)]; }
    static final long[] WALL_INTERVALS = {30_000L, 60_000L, 300_000L, 900_000L, 1_800_000L, 3_600_000L, 86_400_000L};
    static final String[] WALL_INTERVAL_NAMES = {"30 秒", "1 分钟", "5 分钟", "15 分钟", "30 分钟", "1 小时", "每天"};
    static final long[] NEWS_INTERVALS = {5_000L, 8_000L, 15_000L, 30_000L, 60_000L};
    static final String[] NEWS_INTERVAL_NAMES = {"5 秒", "8 秒", "15 秒", "30 秒", "1 分钟"};
    static final int[] IDLE_MINUTES = {2, 5, 10, 15, 30, 0};
    static final String[] IDLE_NAMES = {"2 分钟", "5 分钟", "10 分钟", "15 分钟", "30 分钟", "关闭"};

    static int idx(Context c, String key, int def) { return p(c).getInt(key, def); }
    static void setIdx(Context c, String key, int v) { p(c).edit().putInt(key, v).apply(); }
    static String wallSource(Context c) { return WALL_SOURCES[Math.min(idx(c, "wall_src", 0), WALL_SOURCES.length - 1)]; }
    static long wallInterval(Context c) { return WALL_INTERVALS[Math.min(idx(c, "wall_int", 2), WALL_INTERVALS.length - 1)]; }
    static long newsInterval(Context c) { if (!newsOn(c)) return 0; return NEWS_INTERVALS[Math.min(idx(c, "news_int", 1), NEWS_INTERVALS.length - 1)]; }
    static boolean kenBurns(Context c) { return p(c).getBoolean("kenburns", false); }
    static String wallUrl(Context c) { return p(c).getString("wall_url", ""); }
    // ---- v5: weather, phone remote, sleep timer, quick cast
    static boolean weatherOn(Context c) { return p(c).getBoolean("weather_on", true); }
    static final String[] CITY_NAMES = {"自动定位", "北京", "上海", "广州", "深圳", "成都", "杭州", "武汉", "西安", "南京", "重庆", "天津", "长沙", "郑州", "苏州", "厦门", "青岛", "昆明", "沈阳", "哈尔滨", "韶关", "阳江", "云浮"};
    static final double[][] CITY_LL = {null, {39.90, 116.40}, {31.23, 121.47}, {23.13, 113.26}, {22.54, 114.06}, {30.66, 104.07},
            {30.27, 120.15}, {30.59, 114.31}, {34.34, 108.94}, {32.06, 118.80}, {29.56, 106.55}, {39.13, 117.20}, {28.23, 112.94},
            {34.75, 113.62}, {31.30, 120.62}, {24.48, 118.09}, {36.07, 120.38}, {25.04, 102.71}, {41.80, 123.43}, {45.80, 126.53}, {24.81, 113.60},
            {21.86, 111.98}, {22.93, 112.04}};
    static int cityIdx(Context c) { return Math.min(idx(c, "city_idx", 0), CITY_NAMES.length - 1); }
    static boolean cityCustom(Context c) { return p(c).getBoolean("city_custom", false); }
    static String cityName(Context c) {
        if (cityCustom(c)) {
            String n = p(c).getString("city_custom_name", "");
            if (!n.isEmpty()) return n;
        }
        return CITY_NAMES[cityIdx(c)];
    }
    static double[] cityLL(Context c) {
        if (cityCustom(c) && p(c).contains("city_custom_lat"))
            return new double[]{Double.longBitsToDouble(p(c).getLong("city_custom_lat", 0)),
                    Double.longBitsToDouble(p(c).getLong("city_custom_lon", 0))};
        int i = cityIdx(c);
        return i > 0 ? CITY_LL[i] : null;
    }
    static void setCityIdx(Context c, int i) {
        p(c).edit().putBoolean("city_custom", false).putInt("city_idx", Math.max(0, Math.min(i, CITY_NAMES.length - 1))).apply();
    }
    static void setCityCustom(Context c, String name, double lat, double lon) {
        p(c).edit().putBoolean("city_custom", true).putString("city_custom_name", name)
                .putLong("city_custom_lat", Double.doubleToLongBits(lat))
                .putLong("city_custom_lon", Double.doubleToLongBits(lon)).apply();
    }

    static final int[] SLEEP_MINUTES = {0, 30, 60, 90, 120, 180};
    static final String[] SLEEP_NAMES = {"关闭", "30 分钟后", "1 小时后", "1.5 小时后", "2 小时后", "3 小时后"};

    static boolean remoteOn(Context c) { return p(c).getBoolean("remote_on", true); }
    static boolean adbBoost(Context c) { return p(c).getBoolean("adb_boost", false); }
    static boolean showCast(Context c) { return p(c).getBoolean("show_cast", true); }
    static boolean showPhone(Context c) { return p(c).getBoolean("show_phone", true); }
    static boolean lunar(Context c) { return p(c).getBoolean("lunar_on", true); }

    /** Pairing key embedded in the QR link; anyone on the LAN without it is refused. */
    static String token(Context c) {
        String t = p(c).getString("token", null);
        if (t == null) t = newToken(c);
        return t;
    }
    static String newToken(Context c) {
        String t = Pairing.newCode(new java.security.SecureRandom());
        p(c).edit().putString("token", t).apply();
        return t;
    }

    static int idleMinutesSetting(Context c) { return IDLE_MINUTES[Math.min(idx(c, "idle_idx", 1), IDLE_MINUTES.length - 1)]; }

    /** v5.3: drop deleted saver styles (4+), keep 0–3. */
    static void migrate53(Context c) {
        SharedPreferences sp = p(c);
        if (sp.getInt("rev53", 0) >= 1) return;
        int st = sp.getInt("saver_style", 0);
        if (st < 0 || st > 3) st = 0;
        sp.edit().putInt("saver_style", st).putInt("rev53", 1).apply();
    }

    static int saverStyle(Context c) {
        int st = idx(c, "saver_style", 0);
        return (st < 0 || st > 3) ? 0 : st;
    }

    static boolean plaque(Context c) { return p(c).getBoolean("plaque", false); }
    static void setPlaque(Context c, boolean v) { p(c).edit().putBoolean("plaque", v).apply(); }

    static String desired(Context c) { return p(c).getString("desired", "home"); }
    static void setDesired(Context c, String v) { p(c).edit().putString("desired", "hdmi".equals(v) ? "hdmi" : "home").apply(); }
    static boolean wantsHdmi(Context c) { return "hdmi".equals(desired(c)); }

    static String phoneCurrent(Context c) { return p(c).getString("phone_current", ""); }
    static void setPhoneCurrent(Context c, String name) { p(c).edit().putString("phone_current", name == null ? "" : name).apply(); }
}
