package com.lin.magazinehome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = 0xFF0B0B0A;
    static final int INK = 0xFFECE7DD;
    static final int GREY = 0xFF8C877E;
    static final int DIM = 0xFF55514A;
    static final int RULE = 0xFF26241F;
    static final int ACCENT = 0xFFB89A6A;

    private static Typeface serifBold, serifReg;

    /** System serif. CJK glyphs follow the device font fallback. No font file is bundled. */
    static Typeface bold(Context c) {
        if (serifBold == null) serifBold = Typeface.create("serif", Typeface.BOLD);
        return serifBold;
    }
    static Typeface reg(Context c) {
        if (serifReg == null) serifReg = Typeface.create("serif", Typeface.NORMAL);
        return serifReg;
    }
    static Typeface sans() { return Typeface.create("sans-serif", Typeface.NORMAL); }
    static Typeface sansMedium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, Typeface tf, float sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, sans(), 12.5f, GREY);
        t.setText(s);
        t.setLetterSpacing(0.22f);
        return t;
    }

    static View hRule(Context c) {
        View v = new View(c);
        v.setBackgroundColor(RULE);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f))));
        return v;
    }

    static View vRule(Context c) {
        View v = new View(c);
        v.setBackgroundColor(RULE);
        v.setLayoutParams(new LinearLayout.LayoutParams(Math.max(1, dp(c, 0.7f)), LinearLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    /** 农历 via the platform ICU (API 24+), no tables shipped. */
    static String lunar(java.util.Date d) {
        try {
            android.icu.util.ChineseCalendar cc = new android.icu.util.ChineseCalendar(d);
            int m = cc.get(android.icu.util.ChineseCalendar.MONTH);
            int day = cc.get(android.icu.util.ChineseCalendar.DAY_OF_MONTH);
            boolean leap = cc.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1;
            String[] ms = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};
            String[] n = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
            String ds;
            if (day == 10) ds = "初十"; else if (day == 20) ds = "二十"; else if (day == 30) ds = "三十";
            else if (day < 10) ds = "初" + n[day - 1];
            else if (day < 20) ds = "十" + n[day - 11];
            else ds = "廿" + n[day - 21];
            return "农历" + (leap ? "闰" : "") + ms[m] + "月" + ds;
        } catch (Throwable t) { return ""; }
    }

    static View spacer(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    /** 二十四节气, by an approximate solar longitude. Empty if none today. See {@link Logic}. */
    static String solarTerm(java.util.Date d) { return Logic.solarTerm(d); }
}
