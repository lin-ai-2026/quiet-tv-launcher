package com.lin.magazinehome;

import android.app.Activity;
import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Locale;

public class DrawerActivity extends Activity {
    static final int COLS = 5;
    private LinearLayout grid;
    private TextView countLabel;
    private int focusIdx = 0;
    private boolean pick;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        Context c = this;
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BG);
        page.setPadding(Ui.dp(c, 80), Ui.dp(c, 52), Ui.dp(c, 80), Ui.dp(c, 36));

        countLabel = Ui.label(c, "");
        pick = getIntent().getBooleanExtra("pick", false);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.BOTTOM);
        TextView title = Ui.text(c, Ui.bold(c), 54, Ui.INK);
        title.setText(pick ? "添加到首页" : "全部应用");
        head.addView(title);
        head.addView(Ui.spacer(c));
        TextView hint = Ui.text(c, Ui.sans(), 13, Ui.DIM);
        hint.setLetterSpacing(0.08f);
        hint.setText(pick ? "" : "菜单键：添加到首页 / 移除");
        head.addView(hint);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = Ui.dp(c, 8);
        hlp.bottomMargin = Ui.dp(c, 30);
        page.addView(head, hlp);

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        sv.addView(grid, new ScrollView.LayoutParams(-1, -2));
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        populate();
    }

    private void populate() {
        grid.removeAllViews();
        List<Apps.App> all = Apps.all(this);
        List<String> pins = Prefs.pins(this);
        
        LinearLayout row = null;
        View first = null;
        int idx = 0;
        for (int i = 0; i < all.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = Ui.dp(this, 16);
                grid.addView(row, lp);
            }
            View tile = tile(all.get(i), pins.contains(all.get(i).pkg), i);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.dp(this, 150), 1f);
            tl.rightMargin = Ui.dp(this, 16);
            row.addView(tile, tl);
            if (i == focusIdx) first = tile;
        }
        if (row != null) for (int k = all.size() % COLS; k > 0 && k < COLS; k++) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (first == null && grid.getChildCount() > 0) first = ((LinearLayout) grid.getChildAt(0)).getChildAt(0);
        if (first != null) first.requestFocus();
    }

    private int pinnedCount(List<Apps.App> all, List<String> pins) {
        int n = 0;
        for (String p : pins) if (Apps.find(all, p) != null) n++;
        return n;
    }

    private View tile(final Apps.App a, boolean pinned, final int index) {
        final Context c = this;
        final LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 14));

        LinearLayout topRow = new LinearLayout(c);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        final ImageView icon = new ImageView(c);
        Drawable d = a.icon(getPackageManager());
        icon.setImageDrawable(d);
        final ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);

        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        topRow.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
        topRow.addView(Ui.spacer(c));
        t.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        final TextView name = Ui.text(c, Ui.reg(c), 22, Ui.GREY);
        name.setText(a.label);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = Ui.dp(c, 12);
        t.addView(name, nlp);

        final TextView tag = Ui.text(c, Ui.sans(), 12, Ui.ACCENT);
        tag.setLetterSpacing(0.15f);
        tag.setText(pinned ? "已在首页" : "");
        tag.setTextColor(Ui.INK);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = Ui.dp(c, 6);
        t.addView(tag, tlp);

        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF1A1A1A);
        bg.setCornerRadius(Ui.dp(c, 18));
        t.setBackground(bg);
        LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(0, Ui.dp(c, 150), 1f);
        t.setLayoutParams(margin);

        t.setOnFocusChangeListener((v, has) -> {
            name.setTextColor(has ? Ui.INK : Ui.GREY);
            bg.setColor(has ? 0xFF2E2E2E : 0xFF1A1A1A);
            t.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(150).start();
            if (has) focusIdx = index;
        });
        t.setOnClickListener(v -> { if (pick) toggle(a); else Apps.launch(c, a); });
        t.setOnLongClickListener(v -> { toggle(a); return true; });
        t.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { toggle(a); return true; }
            return false;
        });
        return t;
    }

    private void toggle(Apps.App a) {
        Boolean r = Prefs.togglePin(this, a.pkg);
        String msg = r == null ? "首页最多放 " + Prefs.MAX_PINS + " 个应用" : (r ? "已置顶到首页：" : "已从首页移除：") + a.label;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        populate();
    }
}
