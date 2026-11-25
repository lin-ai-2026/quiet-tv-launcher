package com.lin.magazinehome;

import android.content.Context;
import java.io.File;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Commands from the phone page (one short text line each):
 *   m dx dy | t | lp | s dx dy | k name | x text | cast | next | sleep min | url link
 * Pointer and keys go to the accessibility service; with "remote boost" on, keys and taps go
 * through local ADB instead (true key events, works in every app).
 */
final class Remote {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService adbQ = Executors.newSingleThreadExecutor();

    static boolean accessibilityOn() { return RemoteService.instance != null; }

    static String state(Context c) {
        StringBuilder b = new StringBuilder(512);
        b.append("{\"acc\":").append(accessibilityOn());
        b.append(",\"adb\":").append(Prefs.adbBoost(c));
        b.append(",\"cast\":").append(Cast.pkg(c) != null);
        b.append(",\"sleep\":").append(Sleep.remainingMin(c));
        b.append(",\"wall\":\"").append(esc(Prefs.WALL_SOURCE_NAMES[Math.min(Prefs.idx(c, "wall_src", 0), Prefs.WALL_SOURCE_NAMES.length - 1)])).append("\"");
        b.append(",\"where\":\"").append(esc(Hdmi.where(c))).append("\"");
        b.append(",\"desired\":\"").append(esc(Prefs.desired(c))).append("\"");
        b.append(",\"signal\":").append(Hdmi.hasSignal() == null ? "null" : Hdmi.hasSignal().toString());
        b.append(",\"signalOk\":").append(Hdmi.detectAvailable());
        b.append(",\"wall_src\":").append(Prefs.idx(c, "wall_src", 0));
        b.append(",\"wall_int\":").append(Prefs.idx(c, "wall_int", 2));
        b.append(",\"saver_style\":").append(Prefs.saverStyle(c));
        b.append(",\"saver_dim\":").append(Prefs.idx(c, "saver_dim", 1));
        b.append(",\"saver_news\":").append(Prefs.p(c).getBoolean("saver_news", true));
        b.append(",\"plaque\":").append(Prefs.plaque(c));
        b.append(",\"city_idx\":").append(Prefs.cityIdx(c));
        b.append(",\"city_custom\":").append(Prefs.cityCustom(c));
        b.append(",\"city\":\"").append(esc(Prefs.cityName(c))).append("\"");
        b.append(",\"current\":\"").append(esc(Prefs.phoneCurrent(c))).append("\"");
        b.append(",\"photos\":[");
        java.util.List<Wallpapers.Entry> photos = Wallpapers.phone(c);
        for (int i = 0; i < photos.size(); i++) {
            Wallpapers.Entry e = photos.get(i);
            if (i > 0) b.append(',');
            b.append("{\"id\":\"").append(esc(e.file.getName())).append("\"");
            b.append(",\"cap\":\"").append(esc(e.caption == null ? "" : e.caption)).append("\"}");
        }
        b.append("]}");
        return b.toString();
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') b.append('\\');
            if (ch >= 0x20) b.append(ch);
        }
        return b.toString();
    }

    static int keyCode(String k) {
        switch (k) {
            case "up": return 19; case "down": return 20; case "left": return 21; case "right": return 22;
            case "ok": return 23; case "back": return 4; case "home": return 3; case "menu": return 82;
            case "volup": return 24; case "voldown": return 25; case "mute": return 164;
            case "power": return 223; case "enter": return 66; case "play": return 85; case "del": return 67;
        }
        return 0;
    }

    /** @return short status for the page ("ok", "noacc", ...). */
    static String exec(final Context c, String line) {
        line = line.trim();
        if (line.isEmpty()) return "ok";
        int sp = line.indexOf(' ');
        final String op = sp < 0 ? line : line.substring(0, sp);
        final String arg = sp < 0 ? "" : line.substring(sp + 1);
        final RemoteService s = RemoteService.instance;
        final boolean boost = Prefs.adbBoost(c);
        switch (op) {
            case "m": {
                if (s == null) return "noacc";
                String[] a = arg.split(" ");
                final float dx = Float.parseFloat(a[0]), dy = Float.parseFloat(a[1]);
                main.post(() -> s.moveBy(dx, dy));
                return "ok";
            }
            case "t": case "lp": {
                if (s == null && !boost) return "noacc";
                final boolean longPress = op.equals("lp");
                if (boost && s != null) {
                    final int[] p = s.cursorPoint();
                    adbQ.execute(() -> {
                        if (!LocalAdb.get(c).exec(longPress ? "input swipe " + p[0] + " " + p[1] + " " + p[0] + " " + p[1] + " 700"
                                : "input tap " + p[0] + " " + p[1])) main.post(() -> s.tap(longPress));
                    });
                } else if (s != null) main.post(() -> s.tap(longPress));
                return "ok";
            }
            case "s": {
                if (s == null) return "noacc";
                String[] a = arg.split(" ");
                final float dx = Float.parseFloat(a[0]), dy = Float.parseFloat(a[1]);
                if (boost) {
                    final int[] p = s.cursorPoint();
                    adbQ.execute(() -> LocalAdb.get(c).exec("input swipe " + p[0] + " " + p[1] + " "
                            + Math.round(p[0] + dx * 3) + " " + Math.round(p[1] + dy * 3) + " 180"));
                } else main.post(() -> s.scroll(dx, dy));
                return "ok";
            }
            case "k": return key(c, arg, s, boost);
            case "x": {
                if (s == null) return "noacc";
                final String text = arg;
                main.post(() -> s.typeText(text));
                return "ok";
            }
            case "cast": main.post(() -> Cast.open(c)); return "ok";
            case "next": Bus.post("nextwall", null); return "ok";
            case "sleep": {
                final int min = Integer.parseInt(arg.isEmpty() ? "0" : arg);
                if (min < 0) { adbQ.execute(() -> Sleep.now(c)); return "ok"; }
                Sleep.set(c, min);
                return "ok";
            }
            case "url": {
                final String u = arg;
                main.post(() -> openUrl(c, u));
                return "ok";
            }
            case "home":
                Hdmi.setDesired(c, "home");
                main.post(() -> {
                    Bus.post("go_home", null);
                    Intent i = new Intent(c, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra("open", "home");
                    c.startActivity(i);
                });
                return "ok";
            case "hdmi":
                Hdmi.setDesired(c, "hdmi");
                main.post(() -> Hdmi.switchTo(c, Prefs.hdmiPort(c)));
                return "ok";
            case "night":
                main.post(() -> {
                    Intent i = new Intent(c, ScreenSaverActivity.class).putExtra("night", true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    c.startActivity(i);
                });
                return "ok";
            case "use": {
                File f = Wallpapers.phoneFile(c, arg.trim());
                if (f == null) return "missing";
                Prefs.setPhoneCurrent(c, f.getName());
                Prefs.setIdx(c, "wall_src", 5);
                Bus.post("wall", f.getPath());
                return "ok";
            }
            case "del": {
                if (!Wallpapers.deletePhone(c, arg.trim())) return "missing";
                Bus.post("wall", null);
                return "ok";
            }
            case "set": return setPref(c, arg);
            case "ping": return "ok";
        }
        return "unknown";
    }

    private static String key(final Context c, final String k, final RemoteService s, boolean boost) {
        final int code = keyCode(k);
        if (code == 0) return "unknown";
        if (k.startsWith("vol") || k.equals("mute")) {           // works without any permission
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            int dir = k.equals("volup") ? AudioManager.ADJUST_RAISE : k.equals("voldown") ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE;
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
            return "ok";
        }
        if (boost) {
            adbQ.execute(() -> {
                if (!LocalAdb.get(c).exec("input keyevent " + code) && s != null) main.post(() -> s.key(k));
            });
            return "ok";
        }
        if (s == null) return "noacc";
        if (k.equals("menu") || k.equals("play") || k.equals("del")) return "needadb";
        main.post(() -> s.key(k));
        return "ok";
    }

    static void openUrl(Context c, String u) {
        try {
            String raw = u == null ? "" : u.trim();
            String low = raw.toLowerCase(java.util.Locale.US);
            if (!(low.startsWith("http://") || low.startsWith("https://"))) {
                android.widget.Toast.makeText(c, "只打开 http 或 https 链接", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            Uri uri = Uri.parse(raw);
            Intent i = new Intent(Intent.ACTION_VIEW);
            if (low.matches(".*\\.(mp4|m3u8|mkv|mov|webm|flv)(\\?.*)?$")) i.setDataAndType(uri, "video/*");
            else if (low.matches(".*\\.(mp3|m4a|aac|flac)(\\?.*)?$")) i.setDataAndType(uri, "audio/*");
            else i.setData(uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) {
            android.widget.Toast.makeText(c, "电视上没有能打开这个链接的应用", android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private static void refreshWeather(final Context c) {
        new Thread(() -> {
            Weather.refresh(c);
            Bus.post("weather", null);
        }, "wx").start();
    }

    private static String setPref(final Context c, String arg) {
        arg = arg.trim();
        int sp = arg.indexOf(' ');
        if (sp < 0) return "unknown";
        final String key = arg.substring(0, sp);
        final String val = arg.substring(sp + 1).trim();
        switch (key) {
            case "wall_src": case "wall_int": case "saver_style": case "saver_dim": case "city_idx": {
                int n = Integer.parseInt(val);
                if (key.equals("saver_style") && (n < 0 || n > 3)) n = 0;
                if (key.equals("city_idx")) Prefs.setCityIdx(c, n);
                else Prefs.setIdx(c, key, n);
                if (key.startsWith("wall")) Bus.post("wall", null);
                if (key.equals("city_idx")) refreshWeather(c);
                return "ok";
            }
            case "saver_news": case "plaque": {
                boolean on = val.equals("1") || val.equals("true") || val.equals("开");
                if (key.equals("plaque")) Prefs.setPlaque(c, on);
                else Prefs.p(c).edit().putBoolean("saver_news", on).apply();
                if (key.equals("plaque")) Bus.post("wall", null);
                return "ok";
            }
            case "city_custom": {
                // name|lat|lon
                String[] p = val.split("\\|");
                if (p.length < 3) return "unknown";
                try {
                    Prefs.setCityCustom(c, p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                    refreshWeather(c);
                    return "ok";
                } catch (Exception e) { return "unknown"; }
            }
        }
        return "unknown";
    }
}
