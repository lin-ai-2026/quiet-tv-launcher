package com.lin.magazinehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;

/** One small request per hour to Open-Meteo (no key). Location: chosen city, or IP lookup cached for a day. */
final class Weather {
    static final long TTL = 60 * 60_000L;

    static String line(Context c) {
        if (!Prefs.weatherOn(c)) return "";
        return Prefs.p(c).getString("wx_line", "");
    }

    static boolean stale(Context c) {
        SharedPreferences p = Prefs.p(c);
        return System.currentTimeMillis() - p.getLong("wx_at", 0) > TTL
                || p.getInt("wx_city", -1) != Prefs.cityIdx(c)
                || !Prefs.cityName(c).equals(p.getString("wx_city_name", ""));
    }

    /** Blocking; call off the UI thread. Returns true when the line changed. */
    static boolean refresh(Context c) {
        if (!Prefs.weatherOn(c)) return false;
        SharedPreferences p = Prefs.p(c);
        try {
            int ci = Prefs.cityIdx(c);
            double lat, lon; String city;
            double[] custom = Prefs.cityCustom(c) ? Prefs.cityLL(c) : null;
            if (custom != null) { lat = custom[0]; lon = custom[1]; city = Prefs.cityName(c); }
            else if (ci > 0) { lat = Prefs.CITY_LL[ci][0]; lon = Prefs.CITY_LL[ci][1]; city = Prefs.CITY_NAMES[ci]; }
            else {
                if (System.currentTimeMillis() - p.getLong("geo_at", 0) > 24 * 3600_000L || !p.contains("geo_lat")) locate(c);
                lat = Double.longBitsToDouble(p.getLong("geo_lat", Double.doubleToLongBits(39.90)));
                lon = Double.longBitsToDouble(p.getLong("geo_lon", Double.doubleToLongBits(116.40)));
                city = p.getString("geo_city", "");
            }
            String u = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1";
            JSONObject o = new JSONObject(new String(Wallpapers.get(u), "UTF-8"));
            JSONObject cur = o.getJSONObject("current");
            JSONObject d = o.getJSONObject("daily");
            int t = (int) Math.round(cur.getDouble("temperature_2m"));
            int hi = (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
            int lo = (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
            String line = (city.isEmpty() ? "" : city + "  ") + text(cur.getInt("weather_code")) + " " + t + "°   " + lo + "° / " + hi + "°";
            boolean changed = !line.equals(p.getString("wx_line", ""));
            p.edit().putString("wx_line", line).putLong("wx_at", System.currentTimeMillis()).putInt("wx_city", ci).putString("wx_city_name", Prefs.cityName(c)).apply();
            return changed;
        } catch (Throwable e) {
            Log.w(Hdmi.TAG, "weather failed: " + e);
            p.edit().putLong("wx_at", System.currentTimeMillis() - TTL + 10 * 60_000L).apply(); // retry in 10 min
            return false;
        }
    }

    private static void locate(Context c) {
        String[] urls = {"http://ip-api.com/json/?lang=zh-CN&fields=status,city,lat,lon", "https://ipapi.co/json/"};
        for (String u : urls) {
            try {
                JSONObject o = new JSONObject(new String(Wallpapers.get(u), "UTF-8"));
                double lat = o.optDouble("lat", o.optDouble("latitude", Double.NaN));
                double lon = o.optDouble("lon", o.optDouble("longitude", Double.NaN));
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
                String city = o.optString("city", "").replace("市", "");
                Prefs.p(c).edit().putLong("geo_lat", Double.doubleToLongBits(lat)).putLong("geo_lon", Double.doubleToLongBits(lon))
                        .putString("geo_city", city.matches(".*[\\u4e00-\\u9fa5].*") ? city : "").putLong("geo_at", System.currentTimeMillis()).apply();
                return;
            } catch (Throwable e) { Log.w(Hdmi.TAG, "geo " + u + ": " + e); }
        }
    }

    static String text(int code) {
        switch (code) {
            case 0: return "晴";
            case 1: return "晴间多云";
            case 2: return "多云";
            case 3: return "阴";
            case 45: case 48: return "雾";
            case 51: case 53: case 55: return "毛毛雨";
            case 56: case 57: case 66: case 67: return "冻雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 71: return "小雪";
            case 73: return "中雪";
            case 75: return "大雪";
            case 77: return "雪粒";
            case 80: case 81: return "阵雨";
            case 82: return "强阵雨";
            case 85: case 86: return "阵雪";
            case 95: return "雷阵雨";
            case 96: case 99: return "雷雨冰雹";
        }
        return "";
    }
}
