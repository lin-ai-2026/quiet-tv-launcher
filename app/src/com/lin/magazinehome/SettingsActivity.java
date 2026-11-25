package com.lin.magazinehome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

/** D-pad settings: OK / right = next value, left = previous value. */
public class SettingsActivity extends Activity {
    private LinearLayout list;

    interface Opt { String value(); void step(int d); }
    /** A row whose OK key does something other than step(+1). */
    interface Clicky extends Opt { void click(); }

    private ScrollView sv;
    private final java.util.List<TextView> toc = new java.util.ArrayList<>();
    private final java.util.Map<View, Integer> rowGroup = new java.util.HashMap<>();
    private final java.util.List<View> groupHeads = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.migrate53(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        Context c = this;
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setBackgroundColor(0xFF0B0B0A);
        page.setPadding(Ui.dp(c, 80), Ui.dp(c, 56), Ui.dp(c, 80), 0);

        // left: contents column (not focusable; follows the focused row)
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(c, Ui.bold(c), 46, Ui.INK);
        title.setText("设置");
        left.addView(title);
        View rule = new View(c);
        rule.setBackgroundColor(0x40ECE7DD);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Ui.dp(c, 200), Math.max(1, Ui.dp(c, 0.8f)));
        rl.topMargin = Ui.dp(c, 22); rl.bottomMargin = Ui.dp(c, 18);
        left.addView(rule, rl);
        for (String g : GROUPS) {
            TextView t = Ui.text(c, Ui.reg(c), 21, 0xFF5E5A53);
            t.setText(g);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.topMargin = Ui.dp(c, 16);
            left.addView(t, tl);
            toc.add(t);
        }
        page.addView(left, new LinearLayout.LayoutParams(Ui.dp(c, 250), -1));

        sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 80));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(0, -1, 1f));
        setContentView(page);
        build();
    }

    static final String[] GROUPS = {"桌面应用", "壁纸", "新闻", "信号源", "其他"};
    private int curGroup = -1;

    private void markGroup(int g) {
        if (g == curGroup) return;
        curGroup = g;
        for (int i = 0; i < toc.size(); i++) {
            TextView t = toc.get(i);
            t.setTextColor(i == g ? Ui.INK : 0xFF5E5A53);
            t.setTypeface(i == g ? Ui.bold(this) : Ui.reg(this));
        }
    }

    private Opt cycle(final String key, final int def, final String[] names, final Runnable after) {
        return new Opt() {
            public String value() { return names[Math.min(Prefs.idx(SettingsActivity.this, key, def), names.length - 1)]; }
            public void step(int d) {
                int n = names.length, v = (Prefs.idx(SettingsActivity.this, key, def) + d + n) % n;
                Prefs.setIdx(SettingsActivity.this, key, v);
                if (after != null) after.run();
            }
        };
    }

    private Opt toggle(final String key, final boolean def, final Runnable after) {
        return new Opt() {
            public String value() { return Prefs.p(SettingsActivity.this).getBoolean(key, def) ? "开" : "关"; }
            public void step(int d) {
                Prefs.p(SettingsActivity.this).edit().putBoolean(key, !Prefs.p(SettingsActivity.this).getBoolean(key, def)).apply();
                if (after != null) after.run();
            }
        };
    }

    private Opt action(final String value, final Runnable r) {
        return new Opt() { public String value() { return value; } public void step(int d) { if (d > 0) r.run(); } };
    }

    private int groupIdx = -1;
    private void section(String name) {
        groupIdx++;
        TextView t = Ui.text(this, Ui.bold(this), 15, 0xFF8C877E);
        t.setText(name);
        t.setLetterSpacing(0.2f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(this, list.getChildCount() == 0 ? 4 : 34);
        lp.bottomMargin = Ui.dp(this, 6);
        lp.leftMargin = Ui.dp(this, 24);
        list.addView(t, lp);
        groupHeads.add(t);
    }

    private void build() {
        list.removeAllViews();
        values.clear();
        groupIdx = -1;
        section("桌面应用");
        row("添加 / 移除首页应用", new Opt() {
            public String value() { return "已固定 " + Prefs.pins(SettingsActivity.this).size() + " / " + Prefs.MAX_PINS + "  ›"; }
            public void step(int d) { if (d > 0) startActivity(new android.content.Intent(SettingsActivity.this, DrawerActivity.class).putExtra("pick", true)); }
        });
        row("调整顺序", action("首页选中应用后按菜单键  ›", () -> Toast.makeText(this, "在首页选中应用，按菜单键或长按确认键：左移 / 右移 / 移除", Toast.LENGTH_LONG).show()));
        row("首页显示「投屏」", toggle("show_cast", true, null));
        row("首页显示「扫码」", toggle("show_phone", true, null));

        section("壁纸");
        row("壁纸来源", cycle("wall_src", 0, Prefs.WALL_SOURCE_NAMES, this::onSourceChanged));
        row("纯色", cycle("solid_idx", 0, Prefs.SOLID_NAMES, () -> {
            int src = java.util.Arrays.asList(Prefs.WALL_SOURCES).indexOf("solid");
            Prefs.setIdx(this, "wall_src", src); }));
        row("切换间隔", cycle("wall_int", 2, Prefs.WALL_INTERVAL_NAMES, null));
        row("用手机上传壁纸", new Opt() {
            public String value() { int n = Wallpapers.phone(SettingsActivity.this).size(); return (n > 0 ? "已有 " + n + " 张" : "扫码上传") + "  ›"; }
            public void step(int d) { if (d > 0) startActivity(new android.content.Intent(SettingsActivity.this, ConnectActivity.class)); }
        });
        row("显示图片说明", toggle("show_caption", true, null));
        row("缓慢推镜效果", toggle("kenburns", false, null));
        row("立即刷新必应壁纸", new Opt() {
            public String value() { int n = Wallpapers.bingCached(SettingsActivity.this).size(); return n > 0 ? "已缓存 " + n + " 张" : "未下载"; }
            public void step(int d) { if (d > 0) refreshNow(); }
        });
        row("自定义图片链接", new Opt() {
            public String value() { String u = Prefs.wallUrl(SettingsActivity.this); return u.isEmpty() ? "未设置" : u; }
            public void step(int d) { if (d > 0) editUrl(); }
        });
        row("自定义文件夹", action(folderPath(), () -> {}));

        section("新闻");
        row("显示新闻", toggle("news_on", true, null));
        row("新闻来源", cycle("news_src", 0, Prefs.NEWS_SOURCES, null));
        row("切换间隔", cycle("news_int", 1, Prefs.NEWS_INTERVAL_NAMES, null));
        row("天气", toggle("weather_on", true, null));
        row("天气城市", cycle("city_idx", 0, Prefs.CITY_NAMES, null));
        row("农历", toggle("lunar_on", true, null));

        section("信号源");
        row("在首页显示 HDMI 入口", toggle("show_hdmi_on", false, null));
        row("显示哪些接口", cycle("ports_idx", 0, Prefs.PORT_SET_NAMES, null));
        row("桌面额外强制开机进 HDMI（电视自带已开）", toggle("auto_hdmi", false, null));
        row("信号检测", new Opt() {
            public String value() { return Hdmi.detectAvailable() ? "可用" : "信号检测不可用"; }
            public void step(int d) {}
        });
        row("投屏接收", new Opt() {
            public String value() { String l = Cast.label(SettingsActivity.this); return l == null ? "未找到投屏应用" : l + "  ›"; }
            public void step(int d) { if (d > 0) Cast.open(SettingsActivity.this); }
        });

        section("其他");
        row("屏保等待时间", cycle("idle_idx", 1, Prefs.IDLE_NAMES, null));
        row("屏保样式", cycle("saver_style", 0, StandbyView.NAMES, null));
        row("预览屏保", action("›", () -> startActivity(new android.content.Intent(this, ScreenSaverActivity.class))));
        row("屏保亮度", cycle("saver_dim", 1, StandbyView.DIM_NAMES, null));
        row("屏保显示一条新闻", toggle("saver_news", true, null));
        row("定时关机", new Opt() {
            public String value() { long m = Sleep.remainingMin(SettingsActivity.this); return m > 0 ? "约 " + m + " 分钟后" : "关闭"; }
            public void step(int d) {
                long m = Sleep.remainingMin(SettingsActivity.this);
                int cur = 0;
                for (int i = 0; i < Prefs.SLEEP_MINUTES.length; i++) if (m > 0 && Prefs.SLEEP_MINUTES[i] >= m) { cur = i; break; }
                int n = Prefs.SLEEP_MINUTES.length, nx = ((cur + d) % n + n) % n;
                Sleep.set(SettingsActivity.this, Prefs.SLEEP_MINUTES[nx]);
            }
        });
        row("重置手机配对码", action("旧二维码立即失效  ›", () -> { Prefs.newToken(this); Toast.makeText(this, "已重置，请重新扫码", Toast.LENGTH_SHORT).show(); }));
        row("内存", new Opt() {
            public String value() { return memLine() + "  ›"; }
            public void step(int d) { if (d > 0) cleanMemory(); }
        });
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignore) {}
        row("关于", action("极简电视桌面 " + ver + (Hdmi.detectAvailable() ? "" : "  ·  信号检测不可用"), () -> {}));

        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i).isFocusable()) { list.getChildAt(i).requestFocus(); break; }
    }

    private String folderPath() {
        for (File f : Wallpapers.folders(this)) if (f != null) return f.getPath().replace("/storage/emulated/0", "/sdcard");
        return "";
    }

    private String memLine() {
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return String.format(java.util.Locale.CHINA, "可用 %.1f / %.1f GB", mi.availMem / 1073741824f, mi.totalMem / 1073741824f);
    }

    /** Asks the system to drop cached background apps (KILL_BACKGROUND_PROCESSES, no root; never touches foreground/system). */
    private void cleanMemory() {
        final android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final android.app.ActivityManager.MemoryInfo before = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);
        new Thread(() -> {
            for (android.content.pm.ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
                if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_PERSISTENT) != 0 || ai.packageName.equals(getPackageName())) continue;
                try { am.killBackgroundProcesses(ai.packageName); } catch (Throwable ignore) {}
            }
            try { Thread.sleep(800); } catch (InterruptedException ignore) {}
            android.app.ActivityManager.MemoryInfo after = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(after);
            final long freed = Math.max(0, after.availMem - before.availMem) / 1048576;
            runOnUiThread(() -> { Toast.makeText(this, "已清理后台，释放约 " + freed + " MB", Toast.LENGTH_SHORT).show(); refreshValues(); });
        }).start();
    }


    @Override protected void onResume() { super.onResume(); refreshValues(); }

    private final java.util.Map<TextView, Opt> values = new java.util.HashMap<>();

    /** Update value labels in place (no rebuild, so focus and key handling stay intact). */
    private void refreshValues() {
        for (java.util.Map.Entry<TextView, Opt> e : values.entrySet()) e.getKey().setText(e.getValue().value());
    }

    private void row(String label, final Opt o) {
        final int group = groupIdx;
        final LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(Ui.dp(this, 24), Ui.dp(this, 15), Ui.dp(this, 24), Ui.dp(this, 15));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 12));
        bg.setColor(0x00000000);
        r.setBackground(bg);
        final TextView l = Ui.text(this, Ui.reg(this), 20, 0xFFB5AFA5);
        l.setText(label);
        r.addView(l);
        r.addView(Ui.spacer(this));
        final TextView v = Ui.text(this, Ui.sans(), 17, 0xFF8C877E);
        v.setSingleLine(true);
        v.setMaxWidth(Ui.dp(this, 460));
        v.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        v.setText(o.value());
        r.addView(v);
        r.setOnFocusChangeListener((x, has) -> {
            bg.setColor(has ? 0x1FFFFFFF : 0x00000000);
            l.setTextColor(has ? 0xFFFFFFFF : 0xFFB5AFA5);
            v.setTextColor(has ? 0xFFFFFFFF : 0xFF8C877E);
            if (has) {
                markGroup(group);
                if (rowsInGroupIsFirst(r) && group < groupHeads.size()) sv.smoothScrollTo(0, Math.max(0, groupHeads.get(group).getTop() - Ui.dp(this, 6)));
                else sv.post(() -> {                         // keep the focused row fully on screen (both directions)
                    int m = Ui.dp(this, 24), y = sv.getScrollY(), vh = sv.getHeight() - Ui.dp(this, 80);
                    if (r.getTop() < y + m) sv.smoothScrollTo(0, Math.max(0, r.getTop() - m));
                    else if (r.getBottom() > y + vh - m) sv.smoothScrollTo(0, r.getBottom() - vh + m);
                });
            }
        });
        values.put(v, o);
        r.setOnClickListener(x -> { if (o instanceof Clicky) ((Clicky) o).click(); else o.step(1); refreshValues(); });
        r.setOnKeyListener((x, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { o.step(1); refreshValues(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) { o.step(-1); refreshValues(); return true; }
            return false;
        });
        r.setTag(v);
        View prev = list.getChildCount() > 0 ? list.getChildAt(list.getChildCount() - 1) : null;
        r.setTag(R_FIRST, prev == null || !prev.isFocusable());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(this, 2);
        list.addView(r, lp);
    }

    private static final int R_FIRST = 0x7f0f0011;
    private boolean rowsInGroupIsFirst(View r) { return Boolean.TRUE.equals(r.getTag(R_FIRST)); }

    private void onSourceChanged() {
        String s = Prefs.wallSource(this);
        if ("folder".equals(s) && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        if ("url".equals(s) && Prefs.wallUrl(this).isEmpty()) editUrl();
    }

    private void editUrl() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        et.setText(Prefs.wallUrl(this));
        et.setHint("https://…/photo.jpg");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("自定义图片链接").setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    Prefs.p(this).edit().putString("wall_url", et.getText().toString().trim()).putLong("url_at", 0).apply();
                    refreshValues();
                })
                .setNegativeButton("取消", null).show();
    }

    @Override public void onBackPressed() { finish(); }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN
                && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void refreshNow() {
        Toast.makeText(this, "正在下载必应壁纸…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final int n = Wallpapers.refreshBing(this);
            runOnUiThread(() -> {
                Toast.makeText(this, n > 0 ? "已获取 " + n + " 张必应壁纸" : "下载失败，请检查网络", Toast.LENGTH_SHORT).show();
                refreshValues();
            });
        }).start();
    }
}
