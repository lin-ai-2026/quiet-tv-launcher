package com.lin.magazinehome;

import android.content.Context;
import android.util.Log;
import android.util.Xml;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class News {
    static final class Item {
        final String title; final long time; final String source;
        Item(String t, long tm, String s) { title = t; time = tm; source = s; }
    }

    static final int MAX = 24;

    static List<Item> fetch(int preferred) {
        List<Item> l;
        try {
            if (preferred == 1) { l = sina(); if (l.size() >= 5) return l; }
            if (preferred == 2) { l = rss("https://www.ithome.com/rss/", "IT之家"); if (l.size() >= 5) return l; }
        } catch (Throwable t) { Log.w(Hdmi.TAG, "preferred news source failed: " + t); }
        try { l = rss("https://www.chinanews.com.cn/rss/scroll-news.xml", "中新网"); if (l.size() >= 5) return l; }
        catch (Throwable t) { Log.w(Hdmi.TAG, "chinanews failed: " + t); }
        try { l = sina(); if (l.size() >= 5) return l; }
        catch (Throwable t) { Log.w(Hdmi.TAG, "sina failed: " + t); }
        try { l = rss("https://www.ithome.com/rss/", "IT之家"); if (l.size() >= 5) return l; }
        catch (Throwable t) { Log.w(Hdmi.TAG, "ithome failed: " + t); }
        return new ArrayList<>();
    }

    private static byte[] get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) MagazineHome/1.0");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[16384]; int n;
            while ((n = in.read(b)) > 0) { o.write(b, 0, n); if (o.size() > 2_000_000) break; }
            return o.toByteArray();
        } finally { c.disconnect(); }
    }

    static String clean(String s) { return Logic.clean(s); }

    private static List<Item> rss(String url, String src) throws Exception {
        byte[] data = get(url);
        XmlPullParser x = Xml.newPullParser();
        x.setInput(new java.io.ByteArrayInputStream(data), "UTF-8");
        List<Item> out = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        String title = null, date = null; boolean inItem = false;
        for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT && out.size() < MAX; e = x.next()) {
            if (e == XmlPullParser.START_TAG) {
                String n = x.getName();
                if ("item".equals(n)) { inItem = true; title = null; date = null; }
                else if (inItem && "title".equals(n)) title = clean(x.nextText());
                else if (inItem && "pubDate".equals(n)) date = x.nextText();
            } else if (e == XmlPullParser.END_TAG && "item".equals(x.getName())) {
                inItem = false;
                long t = 0;
                try { if (date != null) t = f.parse(date.trim()).getTime(); } catch (Exception ignore) {}
                if (title != null && title.length() >= 6) out.add(new Item(title, t, src));
            }
        }
        return out;
    }

    private static List<Item> sina() throws Exception {
        String s = new String(get("https://feed.mix.sina.com.cn/api/roll/get?pageid=153&lid=2509&num=30"), "UTF-8");
        JSONArray a = new JSONObject(s).getJSONObject("result").getJSONArray("data");
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < a.length() && out.size() < MAX; i++) {
            JSONObject o = a.getJSONObject(i);
            String t = clean(o.optString("title"));
            long tm = o.optLong("ctime", 0) * 1000L;
            if (t.length() >= 6) out.add(new Item(t, tm, "新浪新闻"));
        }
        return out;
    }

    static void save(Context c, List<Item> l) {
        try {
            JSONArray a = new JSONArray();
            for (Item i : l) a.put(new JSONObject().put("t", i.title).put("tm", i.time).put("s", i.source));
            Prefs.p(c).edit().putString("news", a.toString()).putLong("news_at", System.currentTimeMillis()).apply();
        } catch (Exception ignore) {}
    }

    static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.p(c).getString("news", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Item(o.getString("t"), o.optLong("tm"), o.optString("s")));
            }
        } catch (Exception ignore) {}
        return out;
    }

    static String hm(long t) {
        if (t <= 0) return "";
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(t));
    }
}
