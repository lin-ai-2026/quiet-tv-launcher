package com.lin.magazinehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * v5 "magazine cover" layer: full-bleed photo; masthead (serif date + thin clock, hairline rule);
 * cover line (one serif headline) and a quiet icon row at the foot. No boxes around text.
 */
final class HomeLayout {
    final Context c;
    final FrameLayout root;
    ImageView wallA, wallB;
    TextView clock, date, term, sub, weather, news, newsMeta, caption;
    LinearLayout row, bottom, dateRow;
    View ruleTop, ruleBottom;
    boolean poster;

    static final int TILE = 88;
    static final int RADIUS = 20;
    static final int GAP = 18;
    static final int MARGIN = 80;
    static final int FROST = 0x38FFFFFF;
    static final int FROST_FOCUS = 0x6BFFFFFF;

    HomeLayout(Context c) {
        this.c = c;
        root = new FrameLayout(c);
        root.setBackgroundColor(0xFF000000);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        wallA = wallView(); wallB = wallView();
        root.addView(wallA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wallB, new FrameLayout.LayoutParams(-1, -1));
        wallB.setAlpha(0f);

        topShadeV = new View(c);
        topShadeV.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x40000000, 0x00000000}));
        root.addView(topShadeV, new FrameLayout.LayoutParams(-1, dp(250), Gravity.TOP));
        botShadeV = new View(c);
        botShadeV.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xD9000000, 0x8C000000, 0x33000000, 0x00000000}));
        root.addView(botShadeV, new FrameLayout.LayoutParams(-1, dp(430), Gravity.BOTTOM));

        // ---- masthead
        LinearLayout mast = new LinearLayout(c);
        mast.setOrientation(LinearLayout.VERTICAL);
        LinearLayout band = new LinearLayout(c);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.BOTTOM);

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        dateRow = new LinearLayout(c);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.BOTTOM);
        date = shadowText(Ui.bold(c), 46, 0xFFFFFFFF, 6);
        date.setSingleLine(true);
        dateRow.addView(date, new LinearLayout.LayoutParams(-2, -2));
        term = shadowText(Ui.reg(c), 20, 0x99FFFFFF, 3);
        term.setSingleLine(true);
        term.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = dp(22);
        tlp.bottomMargin = dp(6);
        dateRow.addView(term, tlp);
        left.addView(dateRow, new LinearLayout.LayoutParams(-2, -2));
        sub = shadowText(Ui.reg(c), 17, 0xE6FFFFFF, 4);
        sub.setLetterSpacing(0.06f);
        sub.setSingleLine(true);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = dp(12);
        sl.leftMargin = dp(2);
        left.addView(sub, sl);
        band.addView(left, new LinearLayout.LayoutParams(-2, -2));
        band.addView(Ui.spacer(c));

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        clock = shadowText(Typeface.create("sans-serif-thin", Typeface.NORMAL), 64, 0xFFFFFFFF, 6);
        clock.setLetterSpacing(0.01f);
        clock.setSingleLine(true);
        right.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        weather = shadowText(Ui.reg(c), 16, 0xE6FFFFFF, 4);
        weather.setSingleLine(true);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.topMargin = dp(10);
        wl.gravity = Gravity.END;
        right.addView(weather, wl);
        band.addView(right, new LinearLayout.LayoutParams(-2, -2));
        mast.addView(band, new LinearLayout.LayoutParams(-1, -2));

        ruleTop = rule(0x73FFFFFF);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.8f)));
        rl.topMargin = dp(18);
        mast.addView(ruleTop, rl);

        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        mlp.leftMargin = dp(MARGIN); mlp.rightMargin = dp(MARGIN); mlp.topMargin = dp(46);
        root.addView(mast, mlp);

        // ---- foot: cover line + icon row
        bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        blp.bottomMargin = dp(26);
        root.addView(bottom, blp);

        LinearLayout metaRow = new LinearLayout(c);
        metaRow.setGravity(Gravity.BOTTOM);
        newsMeta = shadowText(Ui.sansMedium(), 12.5f, 0xBFFFFFFF, 3);
        newsMeta.setLetterSpacing(0.18f);
        metaRow.addView(newsMeta);
        metaRow.addView(Ui.spacer(c));
        caption = shadowText(Ui.reg(c), 12, 0x8CFFFFFF, 3);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setMaxWidth(dp(420));
        caption.setVisibility(View.INVISIBLE);
        metaRow.addView(caption);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.leftMargin = dp(MARGIN + 2); ml.rightMargin = dp(MARGIN);
        bottom.addView(metaRow, ml);

        news = shadowText(Ui.bold(c), 30, 0xFFFFFFFF, 6);
        news.setMaxLines(2);
        news.setEllipsize(TextUtils.TruncateAt.END);
        news.setLineSpacing(0, 1.18f);
        news.setMaxWidth(dp(860));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.leftMargin = dp(MARGIN); nl.rightMargin = dp(MARGIN); nl.topMargin = dp(12);
        bottom.addView(news, nl);

        ruleBottom = rule(0x40FFFFFF);
        LinearLayout.LayoutParams rb = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.8f)));
        rb.leftMargin = dp(MARGIN); rb.rightMargin = dp(MARGIN); rb.topMargin = dp(26);
        bottom.addView(ruleBottom, rb);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setPadding(dp(MARGIN - 8), dp(22), dp(MARGIN), 0);
        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        hs.addView(row);
        bottom.addView(hs, new LinearLayout.LayoutParams(-1, -2));
    }

    int dp(float v) { return Ui.dp(c, v); }

    private View rule(int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        return v;
    }

    private ImageView wallView() {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        return v;
    }

    private TextView shadowText(Typeface tf, float sp, int color, float shadowDp) {
        TextView t = new TextView(c);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(dp(shadowDp), 0, dp(1.2f), 0x8C000000);
        return t;
    }

    private boolean bShown = false;
    View topShadeV, botShadeV;

    void showSolid(int color) {
        wallA.setImageDrawable(null); wallB.setImageDrawable(null);
        wallA.setAlpha(0f); wallB.setAlpha(0f); bShown = false;
        root.setBackgroundColor(color);
        boolean light = (0.299 * ((color >> 16) & 255) + 0.587 * ((color >> 8) & 255) + 0.114 * (color & 255)) > 150;
        topShadeV.setVisibility(View.GONE); botShadeV.setVisibility(View.GONE);
        setInk(light ? 0xFF1A1A1A : 0xFFFFFFFF, !light);
    }

    void showPhotos() {
        root.setBackgroundColor(0xFF000000);
        wallA.setAlpha(1f);
        topShadeV.setVisibility(View.VISIBLE); botShadeV.setVisibility(View.VISIBLE);
        setInk(0xFFFFFFFF, true);
    }

    private void setInk(int ink, boolean shadow) {
        TextView[] ts = {clock, date, sub, weather, news, newsMeta, caption, term};
        int[] alpha = {0xFF, 0xFF, 0xE6, 0xE6, 0xFF, 0xBF, 0x8C, 0x99};
        for (int i = 0; i < ts.length; i++) {
            ts[i].setTextColor((ink & 0xFFFFFF) | (alpha[i] << 24));
            if (shadow) ts[i].setShadowLayer(dp(i < 2 || i == 4 ? 6 : 3), 0, dp(1.2f), 0x8C000000);
            else ts[i].setShadowLayer(0, 0, 0, 0);
        }
        ruleTop.setBackgroundColor((ink & 0xFFFFFF) | 0x73000000);
        ruleBottom.setBackgroundColor((ink & 0xFFFFFF) | 0x40000000);
        tileInk = ink;
        for (int i = 0; i < row.getChildCount(); i++) tint(row.getChildAt(i));
    }

    void setTerm(String name) {
        if (name == null || name.isEmpty()) { term.setText(""); term.setVisibility(View.GONE); }
        else { term.setText(name); term.setVisibility(View.VISIBLE); }
    }

    void setPoster(boolean on) {
        if (poster == on) return;
        poster = on;
        float a = on ? 0f : 1f;
        long d = 420;
        android.view.animation.DecelerateInterpolator ease = new android.view.animation.DecelerateInterpolator();
        if (bottom != null) {
            bottom.animate().cancel();
            if (!on) { bottom.setVisibility(View.VISIBLE); if (bottom.getAlpha() == 0f) bottom.setAlpha(0f); }
            bottom.animate().alpha(a).setDuration(d).setInterpolator(ease)
                    .withEndAction(() -> { if (poster) bottom.setVisibility(View.INVISIBLE); }).start();
        }
        if (botShadeV != null) botShadeV.animate().alpha(on ? 0.45f : 1f).setDuration(d).start();
    }

    int tileInk = 0xFFFFFFFF;
    private void tint(View v) {
        if (v instanceof TextView && v.getTag(TAG_LABEL) == null && v.getTag(TAG_BOX) == null)
            ((TextView) v).setTextColor(tileInk);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tint(g.getChildAt(i));
        }
    }
    static final int TAG_BOX = 0x7f0f0001;
    static final int TAG_LABEL = 0x7f0f0002;

    Bitmap showWallpaper(Bitmap b, boolean kenBurns, long kbMs) {
        ImageView target = bShown ? wallA : wallB;
        Drawable old = target.getDrawable();
        target.animate().cancel();
        target.setScaleX(1f); target.setScaleY(1f);
        target.setImageBitmap(b);
        DecelerateInterpolator ease = new DecelerateInterpolator(1.2f);
        if (!bShown) {
            wallB.setAlpha(0f);
            wallB.animate().alpha(1f).setDuration(1400).setInterpolator(ease).withLayer().start();
        } else {
            wallA.setAlpha(1f);
            wallB.animate().alpha(0f).setDuration(1400).setInterpolator(ease).withLayer().start();
        }
        bShown = !bShown;
        if (kenBurns) {
            android.animation.ObjectAnimator kb = android.animation.ObjectAnimator.ofPropertyValuesHolder(target,
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f));
            kb.setDuration(Math.min(Math.max(10_000, kbMs), 120_000));
            kb.start();
        }
        if (old instanceof BitmapDrawable) return ((BitmapDrawable) old).getBitmap();
        return null;
    }

    private View baseTile(View content, String label, boolean fill) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setFocusable(true);
        col.setClickable(true);

        final FrameLayout box = new FrameLayout(c);
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(FROST);
        bg.setCornerRadius(dp(RADIUS));
        box.setBackground(bg);
        box.setTag(TAG_BOX, Boolean.TRUE);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(RADIUS));
            }
        });
        box.setClipToOutline(true);
        box.setElevation(dp(2));
        if (fill) box.addView(content, new FrameLayout.LayoutParams(-1, -1));
        else box.addView(content, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        col.addView(box, new LinearLayout.LayoutParams(dp(TILE), dp(TILE)));

        final TextView name = shadowText(Ui.reg(c), 14, 0xFFFFFFFF, 4);
        name.setTag(TAG_LABEL, Boolean.TRUE);
        name.setText(label);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(0f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(TILE + GAP + 20), -2);
        nlp.topMargin = dp(16);
        col.addView(name, nlp);

        box.setAlpha(0.9f);
        col.setOnFocusChangeListener((v, has) -> {
            box.setPivotX(dp(TILE) / 2f);
            box.setPivotY(dp(TILE) * 0.75f);
            box.animate().scaleX(has ? 1.14f : 1f).scaleY(has ? 1.14f : 1f)
                    .translationZ(has ? dp(14) : 0).alpha(has ? 1f : 0.9f)
                    .setDuration(170).setInterpolator(new DecelerateInterpolator(2f)).start();
            bg.setColor(has ? FROST_FOCUS : FROST);
            name.animate().alpha(has ? 1f : 0f).setDuration(150).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE + GAP), -2);
        col.setLayoutParams(lp);
        return col;
    }

    View appTile(Bitmap icon, String label) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(icon);
        return baseTile(iv, label, true);
    }

    View textTile(String symbol, float sp, String label) {
        TextView s = new TextView(c);
        s.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        s.setTextColor(0xFFFFFFFF);
        s.setGravity(Gravity.CENTER);
        s.setIncludeFontPadding(false);
        s.setText(symbol);
        return baseTile(s, label, false);
    }

    /** Faint serif text entries (HDMI 1 · 2 · 3, 投屏, 扫码) that light up on focus. */
    View quiet(String text, float sp) {
        final TextView t = shadowText(Ui.reg(c), sp, 0xFFFFFFFF, 4);
        t.setText(text);
        t.setAlpha(0.42f);
        t.setFocusable(true);
        t.setClickable(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(9), dp(8), dp(9), dp(8));
        t.setOnFocusChangeListener((v, has) -> t.animate().alpha(has ? 1f : 0.42f)
                .scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(160).start());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(TILE / 2f - 17);
        t.setLayoutParams(lp);
        return t;
    }

    View quietDot() {
        TextView t = shadowText(Ui.reg(c), 15, 0x59FFFFFF, 0);
        t.setText("·");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(TILE / 2f - 10);
        t.setLayoutParams(lp);
        return t;
    }

    View gap(int dpw) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dpw), 1));
        return v;
    }
}
