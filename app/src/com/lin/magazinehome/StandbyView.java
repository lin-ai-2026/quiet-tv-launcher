package com.lin.magazinehome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Built-in standby screens: pure black, monochrome only (white / greys).
 * Four styles, all redraw once a minute. The flip card animates 0.45 s on a minute change.
 * Whole picture shifts a few pixels every minute (burn-in).
 */
final class StandbyView extends View {
    static final String[] NAMES = {"数字时钟", "指针时钟", "翻页时钟", "文字时钟"};
    static final float[] DIMS = {1f, 0.7f, 0.45f, 0.3f};
    static final String[] DIM_NAMES = {"标准", "柔和", "暗", "极暗"};

    private final Handler h = new Handler(Looper.getMainLooper());
    private final int style;
    private final float dim;
    private final boolean showNews;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Typeface serif, serifBold, thin, medium;
    private final Random rnd = new Random();
    private float ox, oy;
    private long lastShift;
    private List<News.Item> news;
    private int newsIdx;
    private boolean running;

    StandbyView(Context c, int style, float dim, boolean showNews) {
        super(c);
        this.style = Math.max(0, Math.min(NAMES.length - 1, style));
        this.dim = dim;
        this.showNews = showNews;
        setBackgroundColor(0xFF000000);
        serif = Ui.reg(c); serifBold = Ui.bold(c);
        thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        if (showNews) news = News.load(c);
    }

    static StandbyView fromPrefs(Context c, int styleOverride) {
        return create(c, styleOverride, -1, null);
    }

    static StandbyView create(Context c, int styleOverride, float dimOverride, Boolean newsOverride) {
        int st = styleOverride >= 0 ? styleOverride : Prefs.saverStyle(c);
        if (st < 0 || st > 3) st = 0;
        float dim = dimOverride >= 0 ? dimOverride : DIMS[Math.min(Prefs.idx(c, "saver_dim", 1), DIMS.length - 1)];
        boolean news = newsOverride != null ? newsOverride : Prefs.p(c).getBoolean("saver_news", true);
        return new StandbyView(c, st, dim, news);
    }

    private float dp(float v) { return Ui.dp(getContext(), v); }

    /** Grey level 0..255 scaled by the dim setting; always neutral grey (no colour). */
    private int ink(int level, int alpha) {
        int g = Math.round(level * dim);
        return (alpha << 24) | (g << 16) | (g << 8) | g;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            invalidate();
            long now = System.currentTimeMillis();
            long delay;
            if (flipStart > 0 && SystemClock.uptimeMillis() - flipStart < FLIP_MS) delay = 33;
            else delay = 60_000 - now % 60_000;
            h.postDelayed(this, Math.max(16, delay));
        }
    };

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); running = true; h.post(tick); }
    @Override protected void onDetachedFromWindow() { running = false; h.removeCallbacksAndMessages(null); super.onDetachedFromWindow(); }

    private Calendar cal = Calendar.getInstance();
    private int lastMinute = -1;

    @Override protected void onDraw(Canvas c) {
        long now = System.currentTimeMillis();
        cal.setTimeInMillis(now);
        int minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        if (minute != lastMinute) {
            if (lastMinute >= 0 && style == 2) { flipFrom = lastMinute; flipStart = SystemClock.uptimeMillis(); }
            if (lastMinute >= 0 && news != null && !news.isEmpty()) newsIdx = (newsIdx + 1) % news.size();
            lastMinute = minute;
        }
        if (now - lastShift > 60_000) {
            lastShift = now;
            ox = (rnd.nextFloat() * 2 - 1) * dp(14);
            oy = (rnd.nextFloat() * 2 - 1) * dp(10);
        }
        int w = getWidth(), hh = getHeight();
        c.save();
        c.translate(ox, oy);
        switch (style) {
            case 1: analog(c, w, hh); break;
            case 2: flip(c, w, hh, minute); break;
            case 3: words(c, w, hh); break;
            default: digital(c, w, hh); break;
        }
        if (showNews && news != null && !news.isEmpty() && style != 3) {
            p.setTypeface(serif); p.setTextSize(dp(15)); p.setColor(ink(120, 255)); p.setTextAlign(Paint.Align.LEFT);
            p.setLetterSpacing(0.04f);
            String t = news.get(newsIdx % news.size()).title;
            float maxW = w * 0.58f;
            if (p.measureText(t) > maxW) {
                int n = p.breakText(t, true, maxW - p.measureText("…"), null);
                t = t.substring(0, n) + "…";
            }
            c.drawText(t, dp(72), hh - dp(48), p);
            p.setLetterSpacing(0);
        }
        c.restore();
    }

    private String time() { return String.format(Locale.CHINA, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)); }
    private String dateLine() {
        Date d = cal.getTime();
        String s = new SimpleDateFormat("M月d日    EEEE", Locale.CHINA).format(d);
        String lu = Ui.lunar(d);
        String term = Ui.solarTerm(d);
        if (!term.isEmpty()) s += "    ·    " + term;
        return lu.isEmpty() ? s : s + "    ·    " + lu;
    }

    private void text(Canvas c, String s, float x, float y, Typeface tf, float sp, int color, Paint.Align a) {
        p.setTypeface(tf); p.setTextSize(dp(sp)); p.setColor(color); p.setTextAlign(a); p.setLetterSpacing(0);
        c.drawText(s, x, y, p);
    }

    // ① 数字时钟
    private void digital(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h * 0.46f;
        p.setTypeface(thin); p.setTextSize(dp(158)); p.setColor(ink(245, 255)); p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(0.10f);
        c.drawText(time(), cx, cy, p);
        p.setLetterSpacing(0);
        p.setColor(ink(70, 255));
        c.drawRect(cx - dp(28), cy + dp(42), cx + dp(28), cy + dp(43), p);
        text(c, dateLine(), cx, cy + dp(92), serif, 18, ink(140, 255), Paint.Align.CENTER);
    }

    // ② 指针时钟 — no second hand, jumps once a minute
    private void analog(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h * 0.45f, r = Math.min(w, h) * 0.30f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 60; i++) {
            boolean hour = i % 5 == 0;
            double a = Math.toRadians(i * 6);
            float r1 = r - (hour ? dp(16) : dp(6));
            p.setStrokeWidth(hour ? dp(1.8f) : dp(0.9f));
            p.setColor(hour ? ink(190, 255) : ink(70, 255));
            c.drawLine(cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1,
                    cx + (float) Math.sin(a) * r, cy - (float) Math.cos(a) * r, p);
        }
        int min = cal.get(Calendar.MINUTE);
        float hr = cal.get(Calendar.HOUR) + min / 60f;
        hand(c, cx, cy, hr * 30, r * 0.52f, dp(4.2f), ink(230, 255), dp(12));
        hand(c, cx, cy, min * 6, r * 0.80f, dp(2.4f), ink(230, 255), dp(16));
        p.setStyle(Paint.Style.FILL);
        p.setColor(ink(230, 255)); c.drawCircle(cx, cy, dp(5), p);
        p.setColor(0xFF000000); c.drawCircle(cx, cy, dp(1.8f), p);
        text(c, dateLine(), cx, cy + r + dp(78), serif, 17, ink(135, 255), Paint.Align.CENTER);
    }

    private void hand(Canvas c, float cx, float cy, float deg, float len, float width, int color, float tail) {
        double a = Math.toRadians(deg);
        float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); p.setColor(color); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(cx - sx * tail, cy - sy * tail, cx + sx * len, cy + sy * len, p);
    }

    // ③ 翻页时钟 — animates only when the minute changes
    static final long FLIP_MS = 450;
    private long flipStart;
    private int flipFrom = -1;
    private final RectF rf = new RectF();

    private void flip(Canvas c, int w, int h, int minute) {
        float cw = Math.min(w * 0.28f, dp(310)), ch = cw * 1.16f, gap = dp(32);
        float left = w / 2f - cw - gap / 2f, top = h * 0.44f - ch / 2f;
        float prog = flipStart == 0 ? 1f : Math.min(1f, (SystemClock.uptimeMillis() - flipStart) / (float) FLIP_MS);
        int from = flipFrom < 0 ? minute : flipFrom;
        String[] now = {String.format(Locale.CHINA, "%02d", minute / 60), String.format(Locale.CHINA, "%02d", minute % 60)};
        String[] old = {String.format(Locale.CHINA, "%02d", from / 60), String.format(Locale.CHINA, "%02d", from % 60)};
        for (int i = 0; i < 2; i++) {
            float x = left + i * (cw + gap);
            boolean anim = prog < 1f && !now[i].equals(old[i]);
            card(c, x, top, cw, ch, now[i], anim ? old[i] : now[i], anim ? prog : 1f);
        }
        text(c, dateLine(), w / 2f, top + ch + dp(72), serif, 17, ink(135, 255), Paint.Align.CENTER);
    }

    private void card(Canvas c, float x, float y, float cw, float ch, String nu, String old, float prog) {
        float mid = y + ch / 2f;
        half(c, x, y, cw, ch, nu, true, 1f);
        half(c, x, y, cw, ch, prog < 1f ? old : nu, false, 1f);
        if (prog < 1f) {
            if (prog < 0.5f) half(c, x, y, cw, ch, old, true, 1f - prog * 2f);
            else half(c, x, y, cw, ch, nu, false, prog * 2f - 1f);
        }
        p.setStyle(Paint.Style.FILL); p.setColor(0xFF000000);
        c.drawRect(x, mid - dp(1.2f), x + cw, mid + dp(1.2f), p);
    }

    private void half(Canvas c, float x, float y, float cw, float ch, String s, boolean top, float scaleY) {
        float mid = y + ch / 2f;
        c.save();
        if (top) c.clipRect(x, y, x + cw, mid); else c.clipRect(x, mid, x + cw, y + ch);
        if (scaleY < 1f) c.scale(1f, Math.max(0.01f, scaleY), x + cw / 2f, mid);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ink(top ? 22 : 18, 255));
        rf.set(x, y, x + cw, y + ch);
        c.drawRoundRect(rf, dp(16), dp(16), p);
        p.setTypeface(medium); p.setTextSize(ch * 0.68f); p.setTextAlign(Paint.Align.CENTER);
        p.setColor(ink(top ? 225 : 208, 255)); p.setLetterSpacing(0.02f);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, x + cw / 2f, mid - (fm.ascent + fm.descent) / 2f, p);
        p.setLetterSpacing(0);
        c.restore();
    }

    // ④ 文字时钟
    private static final String[] NUM = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
    static String cn(int n) {
        if (n <= 10) return NUM[n];
        if (n < 20) return "十" + NUM[n - 10];
        return NUM[n / 10] + "十" + (n % 10 == 0 ? "" : NUM[n % 10]);
    }
    static String period(int h) {
        if (h < 5) return "凌晨"; if (h < 8) return "早上"; if (h < 11) return "上午";
        if (h < 13) return "中午"; if (h < 18) return "下午"; if (h < 19) return "傍晚"; return "晚上";
    }
    private void words(Canvas c, int w, int h) {
        int hr = cal.get(Calendar.HOUR_OF_DAY), mi = cal.get(Calendar.MINUTE);
        int h12 = hr % 12 == 0 ? 12 : hr % 12;
        String hs = (h12 == 2 ? "两" : cn(h12)) + "点";
        String ms = mi == 0 ? "整" : mi < 10 ? "零" + cn(mi) + "分" : cn(mi) + "分";
        float x = w * 0.18f, y = h * 0.28f;
        p.setLetterSpacing(0.28f);
        text(c, period(hr), x + dp(4), y, serif, 22, ink(140, 255), Paint.Align.LEFT);
        p.setLetterSpacing(0);
        text(c, hs, x, y + dp(136), serifBold, 96, ink(238, 255), Paint.Align.LEFT);
        text(c, ms, x, y + dp(268), serif, 96, ink(168, 255), Paint.Align.LEFT);
        p.setColor(ink(70, 255));
        c.drawRect(x + dp(4), y + dp(324), x + dp(64), y + dp(325), p);
        text(c, dateLine(), x + dp(4), y + dp(372), serif, 16, ink(135, 255), Paint.Align.LEFT);
        if (showNews && news != null && !news.isEmpty()) {
            String t = news.get(newsIdx % news.size()).title;
            text(c, t.length() > 28 ? t.substring(0, 28) + "…" : t, x + dp(4), y + dp(412), serif, 14, ink(110, 255), Paint.Align.LEFT);
        }
    }
}
