package com.lin.magazinehome;

import java.util.Calendar;

/** Pure helpers so host-side checks can run without the Android runtime. */
final class Logic {
    static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").trim();
        return s.replaceAll("\\s+", " ");
    }

    static String formatPlaque(String date, String loc) {
        String d = date == null ? "" : date.trim();
        String l = loc == null ? "" : loc.trim();
        if (d.matches("\\d{4}[:\\-]\\d{2}[:\\-]\\d{2}.*")) {
            String y = d.substring(0, 4), m = d.substring(5, 7), day = d.substring(8, 10);
            try { d = Integer.parseInt(y) + "年" + Integer.parseInt(m) + "月" + Integer.parseInt(day) + "日"; }
            catch (Exception ignore) {}
        }
        if (!d.isEmpty() && !l.isEmpty()) return d + "  ·  " + l;
        return !d.isEmpty() ? d : l;
    }

    /** Scheme gate for outbound fetches. Does not resolve the host. */
    static boolean httpUrl(String u) {
        if (u == null) return false;
        String s = u.trim().toLowerCase(java.util.Locale.US);
        int scheme = s.startsWith("https://") ? 8 : s.startsWith("http://") ? 7 : -1;
        if (scheme < 0 || s.length() <= scheme) return false;
        char host0 = s.charAt(scheme);
        return host0 != '/' && host0 != ':' && host0 != '\\';
    }

    static final String[] SOLAR_TERMS = {
            "春分", "清明", "谷雨", "立夏", "小满", "芒种", "夏至", "小暑",
            "大暑", "立秋", "处暑", "白露", "秋分", "寒露", "霜降", "立冬",
            "小雪", "大雪", "冬至", "小寒", "大寒", "立春", "雨水", "惊蛰"};

    static String solarTerm(java.util.Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis(), end = start + 86_400_000L;
        double a = solarLon(start), b = solarLon(end);
        for (int i = 0; i < 24; i++) {
            if (crossed(a, b, i * 15.0)) return SOLAR_TERMS[i];
        }
        return "";
    }

    /** Apparent ecliptic longitude in degrees, 0 = 春分. Approximation, not an almanac. */
    static double solarLon(long epochMs) {
        double n = (epochMs - 946728000000L) / 86400000.0;
        double L = 280.460 + 0.9856474 * n;
        double g = Math.toRadians(357.528 + 0.9856003 * n);
        double lambda = L + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g);
        return ((lambda % 360) + 360) % 360;
    }

    static boolean crossed(double a, double b, double target) {
        if (b >= a) return a <= target && target < b;
        return a <= target || target < b;
    }
}
