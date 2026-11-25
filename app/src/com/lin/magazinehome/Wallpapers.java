package com.lin.magazinehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Environment;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Wallpaper sources: drawn gradients, Bing daily (cn.bing.com), local folders, custom http(s) URL, phone uploads. */
final class Wallpapers {
    static final String TAG = Hdmi.TAG;

    static final class Entry {
        final String asset; final File file; final String caption;
        final boolean gradient; final int g0, g1;
        Entry(String a, File f, String cap) { this(a, f, cap, false, 0, 0); }
        private Entry(String a, File f, String cap, boolean gradient, int g0, int g1) {
            asset = a; file = f; caption = cap; this.gradient = gradient; this.g0 = g0; this.g1 = g1;
        }
        static Entry shade(String cap, int top, int bottom) { return new Entry(null, null, cap, true, top, bottom); }
    }

    /** Original flat gradients drawn for this public tree. Not the excluded photographs. */
    static List<Entry> bundled(Context c) {
        List<Entry> out = new ArrayList<>();
        out.add(Entry.shade("晨霭", 0xFF1A2332, 0xFF8E9AA8));
        out.add(Entry.shade("湖青", 0xFF0E2A2A, 0xFF6E9088));
        out.add(Entry.shade("暮金", 0xFF2A2118, 0xFFC4A574));
        out.add(Entry.shade("松影", 0xFF121814, 0xFF3E5346));
        out.add(Entry.shade("雾白", 0xFF2C3338, 0xFFD9D4CC));
        return out;
    }

    /** Near-black gradients for the “暗色” source. Drawn in memory; no image files. */
    static List<Entry> dark(Context c) {
        List<Entry> out = new ArrayList<>();
        out.add(Entry.shade("墨", 0xFF000000, 0xFF161616));
        out.add(Entry.shade("微光", 0xFF050608, 0xFF1C2430));
        out.add(Entry.shade("沙", 0xFF0C0B09, 0xFF2A241C));
        return out;
    }

    static File[] folders(Context c) {
        File app = c.getExternalFilesDir("wallpapers");
        if (app != null) app.mkdirs();
        File sd = Environment.getExternalStorageDirectory();
        return new File[]{app, new File(sd, "Pictures/Wallpapers"), new File(sd, "Download/Wallpapers"), new File(sd, "Wallpapers")};
    }

    static List<Entry> folder(Context c) {
        List<Entry> out = new ArrayList<>();
        for (File d : folders(c)) {
            if (d == null) continue;
            File[] fs = d.listFiles();
            if (fs == null) continue;
            Arrays.sort(fs);
            for (File f : fs) {
                String n = f.getName().toLowerCase();
                if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")))
                    out.add(new Entry(null, f, ""));
            }
        }
        return out;
    }

    // ---- photos uploaded from the phone page (newest first, at most PHONE_MAX kept)
    static final int PHONE_MAX = 40;
    static File phoneDir(Context c) { File d = new File(c.getFilesDir(), "phone"); d.mkdirs(); return d; }

    static List<Entry> phone(Context c) {
        List<Entry> out = new ArrayList<>();
        File[] fs = phoneDir(c).listFiles((d, n) -> n.endsWith(".jpg"));
        if (fs == null) return out;
        Arrays.sort(fs, (a, b) -> b.getName().compareTo(a.getName()));
        String cur = Prefs.phoneCurrent(c);
        File first = null;
        for (File f : fs) {
            if (f.getName().equals(cur)) first = f;
            else out.add(new Entry(null, f, plaqueOf(f)));
        }
        if (first != null) out.add(0, new Entry(null, first, plaqueOf(first)));
        return out;
    }

    static String plaqueOf(File f) {
        File cap = new File(f.getPath() + ".cap");
        if (!cap.exists()) return "";
        try {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            try (java.io.FileInputStream in = new java.io.FileInputStream(cap)) {
                byte[] b = new byte[256]; int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
            }
            return o.toString("UTF-8").trim();
        } catch (Exception e) { return ""; }
    }

    static void writePlaque(File f, String cap) {
        if (cap == null || cap.isEmpty()) return;
        try (FileOutputStream o = new FileOutputStream(f.getPath() + ".cap")) {
            o.write(cap.getBytes("UTF-8"));
        } catch (Exception ignore) {}
    }

    static boolean deletePhone(Context c, String name) {
        if (name == null || name.contains("/") || name.contains("..") || !name.endsWith(".jpg")) return false;
        File f = new File(phoneDir(c), name);
        if (!f.exists()) return false;
        f.delete();
        new File(f.getPath() + ".cap").delete();
        thumbFile(c, name).delete();
        if (name.equals(Prefs.phoneCurrent(c))) Prefs.setPhoneCurrent(c, "");
        return true;
    }

    static File phoneFile(Context c, String name) {
        if (name == null || name.contains("/") || name.contains("..") || !name.endsWith(".jpg")) return null;
        File f = new File(phoneDir(c), name);
        return f.exists() ? f : null;
    }

    static File thumbDir(Context c) { File d = new File(c.getFilesDir(), "thumbs"); d.mkdirs(); return d; }
    static File thumbFile(Context c, String name) { return new File(thumbDir(c), name); }

    /** Small JPEG thumbnail for the phone album (on-demand, cached). */
    static byte[] thumb(Context c, String name) {
        File src = phoneFile(c, name);
        if (src == null) return null;
        File t = thumbFile(c, name);
        if (t.exists() && t.lastModified() >= src.lastModified()) {
            try {
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                try (java.io.FileInputStream in = new java.io.FileInputStream(t)) {
                    byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) o.write(b, 0, n);
                }
                return o.toByteArray();
            } catch (Exception ignore) {}
        }
        try {
            BitmapFactory.Options bo = opts(true);
            BitmapFactory.decodeFile(src.getPath(), bo);
            int s = 1;
            while (bo.outWidth / (s * 2) >= 360 && bo.outHeight / (s * 2) >= 200) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = Math.max(s, 1);
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeFile(src.getPath(), d);
            if (b == null) return null;
            if (b.getWidth() > 400) {
                float k = 360f / b.getWidth();
                Bitmap sc = Bitmap.createScaledBitmap(b, 360, Math.max(1, Math.round(b.getHeight() * k)), true);
                if (sc != b) b.recycle();
                b = sc;
            }
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 72, o);
            b.recycle();
            byte[] bytes = o.toByteArray();
            try (FileOutputStream fo = new FileOutputStream(t)) { fo.write(bytes); }
            return bytes;
        } catch (Throwable e) { Log.w(TAG, "thumb", e); return null; }
    }

    static String formatPlaque(String date, String loc) { return Logic.formatPlaque(date, loc); }

    static String plaqueFromExif(File f) {
        try {
            android.media.ExifInterface ei = new android.media.ExifInterface(f.getPath());
            String dt = ei.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL);
            if (dt == null) dt = ei.getAttribute(android.media.ExifInterface.TAG_DATETIME);
            String loc = ei.getAttribute(android.media.ExifInterface.TAG_GPS_AREA_INFORMATION);
            if (loc == null) loc = "";
            return formatPlaque(dt, loc);
        } catch (Exception e) { return ""; }
    }

    /** Validates and stores an uploaded JPEG/PNG; returns the file or null. */
    static File savePhone(Context c, byte[] data) { return savePhone(c, data, null); }

    static File savePhone(Context c, byte[] data, String plaque) {
        try {
            BitmapFactory.Options bo = opts(true);
            BitmapFactory.decodeByteArray(data, 0, data.length, bo);
            if (bo.outWidth < 64 || bo.outHeight < 64) return null;
            File f = new File(phoneDir(c), "p" + System.currentTimeMillis() + ".jpg");
            File tmp = new File(f.getPath() + ".tmp");
            FileOutputStream o = new FileOutputStream(tmp); o.write(data); o.close();
            tmp.renameTo(f);
            String cap = plaque != null && !plaque.isEmpty() ? plaque : plaqueFromExif(f);
            writePlaque(f, cap);
            stripGps(f);
            Prefs.setPhoneCurrent(c, f.getName());
            List<Entry> all = phone(c);
            for (int i = PHONE_MAX; i < all.size(); i++) {
                if (all.get(i).file != null) {
                    all.get(i).file.delete();
                    new File(all.get(i).file.getPath() + ".cap").delete();
                }
            }
            return f;
        } catch (Exception e) { Log.w(TAG, "save phone photo", e); return null; }
    }

    static long dirBytes(File d) {
        long n = 0; File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) n += f.isDirectory() ? dirBytes(f) : f.length();
        return n;
    }

    static File bingDir(Context c) { File d = new File(c.getFilesDir(), "bing"); d.mkdirs(); return d; }

    static List<Entry> bingCached(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.p(c).getString("bing_index", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                File f = new File(bingDir(c), o.getString("f"));
                if (f.exists()) out.add(new Entry(null, f, o.optString("cap")));
            }
        } catch (Exception ignore) {}
        return out;
    }

    /** Blocking: refresh Bing archive (up to 16 images) into the cache. */
    static int refreshBing(Context c) {
        JSONArray index = new JSONArray();
        Set<String> keep = new HashSet<>();
        int got = 0;
        for (int start : new int[]{0, 8}) {
            try {
                String js = new String(get("https://cn.bing.com/HPImageArchive.aspx?format=js&idx=" + start + "&n=8&mkt=zh-CN"), "UTF-8");
                JSONArray imgs = new JSONObject(js).getJSONArray("images");
                for (int i = 0; i < imgs.length(); i++) {
                    JSONObject im = imgs.getJSONObject(i);
                    String base = im.getString("urlbase");
                    String name = base.replaceAll("[^A-Za-z0-9_.-]", "_") + ".jpg";
                    if (keep.contains(name)) continue;
                    File f = new File(bingDir(c), name);
                    if (!f.exists()) {
                        byte[] b = get("https://cn.bing.com" + base + "_1920x1080.jpg");
                        File tmp = new File(f.getPath() + ".tmp");
                        FileOutputStream o = new FileOutputStream(tmp); o.write(b); o.close();
                        tmp.renameTo(f);
                        got++;
                    }
                    keep.add(name);
                    index.put(new JSONObject().put("f", name).put("cap", im.optString("copyright")));
                }
            } catch (Exception e) { Log.w(TAG, "bing idx " + start + " failed: " + e); }
        }
        if (index.length() > 0) {
            Prefs.p(c).edit().putString("bing_index", index.toString()).putLong("bing_at", System.currentTimeMillis()).apply();
            File[] fs = bingDir(c).listFiles();
            if (fs != null) for (File f : fs) if (!keep.contains(f.getName())) f.delete();
        }
        Log.i(TAG, "bing refresh: " + index.length() + " images, " + got + " new");
        return index.length();
    }

    static File urlFile(Context c) { return new File(c.getFilesDir(), "custom_url.jpg"); }

    static boolean refreshUrl(Context c) {
        String u = Prefs.wallUrl(c);
        if (u.isEmpty()) return false;
        try {
            byte[] b = get(u);
            BitmapFactory.Options bo = opts(true); BitmapFactory.decodeByteArray(b, 0, b.length, bo); if (bo.outWidth <= 0) return false;
            FileOutputStream o = new FileOutputStream(urlFile(c)); o.write(b); o.close();
            Prefs.p(c).edit().putLong("url_at", System.currentTimeMillis()).apply();
            return true;
        } catch (Exception e) { Log.w(TAG, "custom url failed: " + e); return false; }
    }

    private static BitmapFactory.Options opts(boolean bounds) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = bounds;
        return o;
    }

    static boolean bingNeedsRefresh(Context c) {
        long age = System.currentTimeMillis() - Prefs.p(c).getLong("bing_at", 0);
        return age > 6 * 3600_000L || bingCached(c).isEmpty();
    }

    /** Entries for the current source; never blocks on the network. Falls back to bundled photos. */
    static List<Entry> current(Context c) {
        String src = Prefs.wallSource(c);
        List<Entry> l = new ArrayList<>();
        if ("bing".equals(src)) {
            l = bingCached(c);
        } else if ("bundled".equals(src)) {
            l = bundled(c);
        } else if ("folder".equals(src)) {
            l = folder(c);
        } else if ("dark".equals(src)) {
            l = dark(c);
        } else if ("phone".equals(src)) {
            l = phone(c);
        } else if ("url".equals(src)) {
            if (urlFile(c).exists()) l.add(new Entry(null, urlFile(c), ""));
        }
        if (l.isEmpty()) l = bundled(c);
        return l;
    }

    /** Decode downscaled to fit 1920x1080 (memory-friendly on the 32-bit chip). */
    static Bitmap load(Context c, Entry e) {
        try {
            if (e == null) return null;
            if (e.gradient) return renderGradient(e.g0, e.g1);
            BitmapFactory.Options o = opts(true);
            decode(c, e, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= 1920 && o.outHeight / (s * 2) >= 1080) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            d.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = decode(c, e, d);
            if (b != null && (b.getWidth() > 2200 || b.getHeight() > 1300)) {
                float k = Math.max(1920f / b.getWidth(), 1080f / b.getHeight());
                Bitmap sc = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
                if (sc != b) b.recycle();
                b = sc;
            }
            return b;
        } catch (Throwable t) { Log.w(TAG, "decode failed", t); return null; }
    }

    private static Bitmap decode(Context c, Entry e, BitmapFactory.Options o) throws Exception {
        if (e.asset != null) { try (InputStream in = c.getAssets().open(e.asset)) { return BitmapFactory.decodeStream(in, null, o); } }
        return BitmapFactory.decodeFile(e.file.getPath(), o);
    }

    /** 1280×720 is enough for a flat gradient and lighter than a decoded 1080p photo. */
    private static Bitmap renderGradient(int c0, int c1) {
        int w = 1280, h = 720;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(b);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h, c0, c1, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        return b;
    }

    /** Best-effort removal of GPS EXIF after a phone upload. Failure keeps the file. */
    private static void stripGps(File f) {
        try {
            android.media.ExifInterface ei = new android.media.ExifInterface(f.getPath());
            String[] tags = {
                    android.media.ExifInterface.TAG_GPS_LATITUDE,
                    android.media.ExifInterface.TAG_GPS_LATITUDE_REF,
                    android.media.ExifInterface.TAG_GPS_LONGITUDE,
                    android.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
                    android.media.ExifInterface.TAG_GPS_ALTITUDE,
                    android.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
                    android.media.ExifInterface.TAG_GPS_TIMESTAMP,
                    android.media.ExifInterface.TAG_GPS_DATESTAMP,
                    android.media.ExifInterface.TAG_GPS_PROCESSING_METHOD,
                    android.media.ExifInterface.TAG_GPS_AREA_INFORMATION
            };
            for (String t : tags) ei.setAttribute(t, null);
            ei.saveAttributes();
        } catch (Throwable t) { Log.w(TAG, "strip gps", t); }
    }

    static byte[] get(String u) throws Exception {
        if (!Logic.httpUrl(u)) throw new Exception("only http(s)");
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) MagazineHome/2.0");
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[32768]; int n;
            while ((n = in.read(b)) > 0) { o.write(b, 0, n); if (o.size() > 15_000_000) break; }
            return o.toByteArray();
        } finally { c.disconnect(); }
    }
}
