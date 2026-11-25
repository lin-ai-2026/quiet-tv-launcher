package com.lin.magazinehome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final long REFRESH_MS = 20 * 60 * 1000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private HomeLayout ui;
    private List<News.Item> news = new ArrayList<>();
    private int newsIdx = 0;
    static volatile boolean onTop;
    private boolean resumed, homeOnTop;
    private long lastFetch = 0;
    private String focusKey = "";
    private long lastHomeAt;
    private boolean leftForHdmi;
    private boolean poster;
    private final Runnable posterRun = () -> { poster = true; ui.setPoster(true); };

    private final BroadcastReceiver timeRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };
    private final BroadcastReceiver screenRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction()) && Prefs.wantsHdmi(c)) {
                Log.i(Hdmi.TAG, "screen on, desired=hdmi -> HDMI");
                h.postDelayed(() -> Hdmi.switchTo(MainActivity.this, Prefs.hdmiPort(MainActivity.this)), 1200);
            }
        }
    };
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            long iv = Prefs.newsInterval(MainActivity.this);
            if (iv <= 0) return;
            showNews(newsIdx + 1, true); h.postDelayed(this, iv);
        }
    };
    private List<Wallpapers.Entry> walls = new ArrayList<>();
    private int wallIdx = -1;
    private String wallKey = "";
    private boolean wallLoading;
    private final Runnable wallRotate = new Runnable() {
        @Override public void run() { nextWallpaper(false); }
    };
    private final Runnable idle = () -> startActivity(new Intent(this, ScreenSaverActivity.class));

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        Prefs.migrateVisuals(this);
        Prefs.migrate53(this);
        PhoneServer.start(this);
        Hdmi.startWatch(this);
        ui = new HomeLayout(this);
        setContentView(ui.root);
        ui.showPhotos();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED); tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(Intent.ACTION_DATE_CHANGED);
        registerReceiver(timeRx, tf);
        registerReceiver(screenRx, new IntentFilter(Intent.ACTION_SCREEN_ON));
        news = News.load(this);
        showNews(0, false);
        Bus.add(bus);
        handleIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i); setIntent(i); handleIntent(i);
        if (i != null && Intent.ACTION_MAIN.equals(i.getAction()) && i.hasCategory(Intent.CATEGORY_HOME))
            onHomePress();
    }

    private void handleIntent(Intent i) {
        if (i != null && i.hasExtra("wall_src")) {     // adb / preview hook: am start ... --ei wall_src 6
            Prefs.setIdx(this, "wall_src", i.getIntExtra("wall_src", 0)); i.removeExtra("wall_src"); wallKey = "";
        }
        String open = i == null ? null : i.getStringExtra("open");
        if (open == null) { focusKey = ""; return; }
        i.removeExtra("open");
        if ("drawer".equals(open)) startActivity(new Intent(this, DrawerActivity.class));
        else if ("picker".equals(open)) startActivity(new Intent(this, DrawerActivity.class).putExtra("pick", true));
        else if ("settings".equals(open)) startActivity(new Intent(this, SettingsActivity.class));
        else if ("nextwall".equals(open)) nextWallpaper(false);
        else if ("saver".equals(open)) startActivity(new Intent(this, ScreenSaverActivity.class));
        else if ("phone".equals(open)) startActivity(new Intent(this, ConnectActivity.class));
        else if ("cast".equals(open)) Cast.open(this);
        else if (open.startsWith("hdmi")) {
            int port = open.length() > 4 ? Integer.parseInt(open.substring(4)) : Prefs.hdmiPort(this);
            Prefs.setHdmiPort(this, port);
            Hdmi.setDesired(this, "hdmi");
            leftForHdmi = true;
            Hdmi.switchTo(this, port);
        } else if ("night".equals(open)) {
            startActivity(new Intent(this, ScreenSaverActivity.class).putExtra("night", true));
        } else if ("home".equals(open)) {
            Hdmi.setDesired(this, "home");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true; homeOnTop = true; onTop = true;
        if (leftForHdmi) {
            leftForHdmi = false;
            lastHomeAt = android.os.SystemClock.elapsedRealtime();
            Hdmi.setDesired(this, "home");
        }
        Bus.post("where", "home");
        updateClock();
        showWeather();
        rebuildRows();
        h.removeCallbacks(rotate);
        long niv = Prefs.newsInterval(this);
        ui.news.setVisibility(niv > 0 ? View.VISIBLE : View.GONE);
        ui.newsMeta.setVisibility(niv > 0 ? View.VISIBLE : View.GONE);
        if (niv > 0) h.postDelayed(rotate, niv);
        applyCaptionVisibility();
        int nsrc = Prefs.newsSource(this);
        if (nsrc != Prefs.p(this).getInt("news_src_loaded", 0)) { Prefs.p(this).edit().putInt("news_src_loaded", nsrc).apply(); lastFetch = 0; }
        String key = wallKeyNow();
        if (!key.equals(wallKey) || walls.isEmpty() || walls.get(0) == null) { wallKey = key; nextWallpaper(true); }
        else { h.removeCallbacks(wallRotate); h.postDelayed(wallRotate, Prefs.wallInterval(this)); }
        maybeRefreshBing();
        if (System.currentTimeMillis() - lastFetch > REFRESH_MS || news.isEmpty()) fetchNews();
        resetIdle();
        resetPoster();
        h.postDelayed(() -> Hdmi.maybeBootSwitch(this, "home"), 1500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false; onTop = false;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null || pm.isInteractive()) homeOnTop = false;
        if (Prefs.wantsHdmi(this)) leftForHdmi = true;
        h.removeCallbacks(rotate);
        h.removeCallbacks(idle);
        h.removeCallbacks(wallRotate);
        h.removeCallbacks(posterRun);
        if (poster) { poster = false; ui.setPoster(false); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(timeRx); unregisterReceiver(screenRx); } catch (Exception ignore) {}
        Bus.remove(bus);
        h.removeCallbacksAndMessages(null);
    }

    @Override public void onBackPressed() { }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        resetIdle();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_HOME) { onHomePress(); return true; }
            if (poster && isRevealKey(code)) { resetPoster(); return true; }
            resetPoster();
            int port = 0;
            if (code == KeyEvent.KEYCODE_1 || code == KeyEvent.KEYCODE_NUMPAD_1) port = 1;
            else if (code == KeyEvent.KEYCODE_2 || code == KeyEvent.KEYCODE_NUMPAD_2) port = 2;
            else if (code == KeyEvent.KEYCODE_3 || code == KeyEvent.KEYCODE_NUMPAD_3) port = 3;
            if (port > 0) {
                Prefs.setHdmiPort(this, port);
                goHdmi(port);
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private static boolean isRevealKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU;
    }

    private void onHomePress() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastHomeAt > 0 && now - lastHomeAt < 900) {
            lastHomeAt = 0;
            goHdmi(Prefs.hdmiPort(this));
        } else {
            lastHomeAt = now;
            Hdmi.setDesired(this, "home");
            resetPoster();
        }
    }

    private void goHdmi(int port) {
        Hdmi.setDesired(this, "hdmi");
        leftForHdmi = true;
        Hdmi.switchTo(this, port);
    }

    private void resetIdle() {
        h.removeCallbacks(idle);
        long min = Prefs.idleMinutes(this);
        if (resumed && min > 0 && !Prefs.wantsHdmi(this)) h.postDelayed(idle, min * 60_000L);
    }

    private void resetPoster() {
        h.removeCallbacks(posterRun);
        if (poster) { poster = false; ui.setPoster(false); }
        if (resumed && !Prefs.wantsHdmi(this)) h.postDelayed(posterRun, 18_000);
    }

    // ---------------------------------------------------------------- rows

    private void rebuildRows() {
        View cur = getCurrentFocus();
        if (cur != null && cur.getTag() != null) focusKey = String.valueOf(cur.getTag());
        ui.row.removeAllViews();
        View toFocus = null;

        List<Apps.App> all = Apps.all(this);
        for (String pkg : Prefs.pins(this)) {
            final Apps.App a = Apps.find(all, pkg);
            if (a == null) continue;
            View t = ui.appTile(a.tileBitmap(this, Ui.dp(this, HomeLayout.TILE) * 2), Apps.shortName(a));
            t.setTag("app:" + a.pkg);
            t.setOnClickListener(v -> { focusKey = "app:" + a.pkg; Apps.launch(this, a); });
            t.setOnLongClickListener(v -> { tileMenu(a); return true; });
            t.setOnKeyListener((v, code, e) -> {
                if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { tileMenu(a); return true; }
                return false;
            });
            ui.row.addView(t);
            if (focusKey.equals(t.getTag())) toFocus = t;
        }
        if (Prefs.pins(this).size() < Prefs.MAX_PINS) toFocus = add(ui.textTile("+", 32, "添加"), "add",
                v -> startActivity(new Intent(this, DrawerActivity.class).putExtra("pick", true)), toFocus);
        toFocus = add(ui.textTile("⋯", 28, "全部应用"), "all", v -> startActivity(new Intent(this, DrawerActivity.class)), toFocus);
        if (Prefs.showHdmi(this)) {
            String[] ports = Prefs.ports(this);
            for (int k = 0; k < ports.length; k++) {
                final int port = Integer.parseInt(ports[k]);
                if (k > 0) ui.row.addView(ui.quietDot());
                toFocus = add(ui.quiet(k == 0 ? "HDMI " + port : String.valueOf(port), 15), "hdmi:" + port,
                        v -> { Prefs.setHdmiPort(this, port); goHdmi(port); }, toFocus);
            }
        }
        boolean any = Prefs.showHdmi(this);
        String castPkg = Cast.pkg(this);
        if (Prefs.showCast(this) && castPkg != null && !Prefs.pins(this).contains(castPkg)) {
            if (any) ui.row.addView(ui.quietDot());
            toFocus = add(ui.quiet("投屏", 15), "cast", v -> Cast.open(this), toFocus);
            any = true;
        }
        if (Prefs.showPhone(this)) {
            if (any) ui.row.addView(ui.quietDot());
            toFocus = add(ui.quiet("扫码", 15), "phone", v -> startActivity(new Intent(this, ConnectActivity.class)), toFocus);
        }
        toFocus = add(ui.textTile("⚙", 26, "设置"), "settings", v -> startActivity(new Intent(this, SettingsActivity.class)), toFocus);

        if ("solid".equals(Prefs.wallSource(this))) ui.showSolid(Prefs.solidColor(this));
        if (toFocus == null) toFocus = ui.row.getChildAt(0);
        toFocus.requestFocus();
    }


    private View add(View t, String tag, View.OnClickListener l, View toFocus) {
        t.setTag(tag);
        t.setOnClickListener(v -> { focusKey = tag; l.onClick(v); });
        t.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
                startActivity(new Intent(this, SettingsActivity.class)); return true; }
            return false;
        });
        ui.row.addView(t);
        return focusKey.equals(tag) ? t : toFocus;
    }

    // ---------------------------------------------------------------- wallpaper

    private void maybeRefreshBing() {
        if (!"bing".equals(Prefs.wallSource(this))) return;
        new Thread(() -> {
            if (!Wallpapers.bingNeedsRefresh(this)) return;
            int n = Wallpapers.refreshBing(this);
            if (n > 0 && resumed && "bing".equals(Prefs.wallSource(this))) {
                h.post(() -> { wallKey = ""; nextWallpaper(true); });
            }
        }, "bing").start();
    }

    private void nextWallpaper(final boolean reload) {
        h.removeCallbacks(wallRotate);
        if ("solid".equals(Prefs.wallSource(this))) { ui.showSolid(Prefs.solidColor(this)); ui.caption.setText(""); walls.clear(); walls.add(null); return; }
        if (reload) ui.showPhotos();
        if (wallLoading) return;
        wallLoading = true;
        new Thread(() -> {
            if (reload || walls.isEmpty()) {
                List<Wallpapers.Entry> l = Wallpapers.current(this);
                walls = l;
                if (reload) wallIdx = -1;
            }
            if (walls.isEmpty()) { wallLoading = false; return; }
            int i = (wallIdx + 1) % walls.size();
            final Wallpapers.Entry e = walls.get(i);
            final android.graphics.Bitmap b = Wallpapers.load(this, e);
            final int ni = i;
            h.post(() -> {
                wallLoading = false;
                if (b != null) {
                    wallIdx = ni;
                    long iv = Prefs.wallInterval(this);
                    android.graphics.Bitmap old = ui.showWallpaper(b, Prefs.kenBurns(this), iv);
                    if (old != null && old != b) old.recycle();
                    applyCaption(e);
                    Log.i(Hdmi.TAG, "wallpaper " + (ni + 1) + "/" + walls.size() + " src=" + Prefs.wallSource(this));
                }
                if (resumed) h.postDelayed(wallRotate, Prefs.wallInterval(this));
            });
        }, "wall").start();
    }

    private void tileMenu(final Apps.App a) {
        focusKey = "app:" + a.pkg;
        String[] items = {"打开", "左移", "右移", "从首页移除", "设置"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(items, (d, w) -> {
                    if (w == 0) Apps.launch(this, a);
                    else if (w == 1) Prefs.move(this, a.pkg, -1);
                    else if (w == 2) Prefs.move(this, a.pkg, 1);
                    else if (w == 4) { startActivity(new Intent(this, SettingsActivity.class)); return; }
                    else { Prefs.unpin(this, a.pkg); focusKey = "add";
                        Toast.makeText(this, "已移除 " + a.label, Toast.LENGTH_SHORT).show(); }
                    if (w > 0) rebuildRows();
                }).show();
    }

    // ---------------------------------------------------------------- content

    private void updateClock() {
        Date now = new Date();
        ui.clock.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(now));
        ui.date.setText(new SimpleDateFormat("M月d日", Locale.CHINA).format(now));
        ui.setTerm(Ui.solarTerm(now));
        String wk = new SimpleDateFormat("EEEE", Locale.CHINA).format(now);
        String lu = Prefs.lunar(this) ? Ui.lunar(now) : "";
        ui.sub.setText(lu.isEmpty() ? wk : wk + "   ·   " + lu);
    }

    private void showWeather() {
        String w = Weather.line(this);
        ui.weather.setText(w);
        ui.weather.setVisibility(w.isEmpty() ? View.GONE : View.VISIBLE);
        if (Prefs.weatherOn(this) && Weather.stale(this)) new Thread(() -> {
            if (Weather.refresh(this) || true) h.post(() -> {
                String x = Weather.line(this);
                ui.weather.setText(x);
                ui.weather.setVisibility(x.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }, "weather").start();
    }

    private final Bus.L bus = (ev, arg) -> {
        if ("nextwall".equals(ev)) { if (resumed) nextWallpaper(false); }
        else if ("wall".equals(ev)) { wallKey = ""; if (resumed) { wallKey = wallKeyNow(); nextWallpaper(true); } }
        else if ("weather".equals(ev)) { if (resumed) showWeather(); }
    };

    private String wallKeyNow() {
        return Prefs.wallSource(this) + "|" + Prefs.solidColor(this) + "|" + Prefs.wallInterval(this) + "|" + Prefs.wallUrl(this) + "|" + Prefs.kenBurns(this) + "|" + Prefs.phoneCurrent(this) + "|" + Prefs.plaque(this);
    }

    private void applyCaption(Wallpapers.Entry e) {
        boolean phone = "phone".equals(Prefs.wallSource(this));
        String cap = e == null ? "" : (e.caption == null ? "" : e.caption);
        if (phone && Prefs.plaque(this) && !cap.isEmpty()) {
            ui.caption.setText(cap);
            ui.caption.setAlpha(0.55f);
            ui.caption.setVisibility(View.VISIBLE);
        } else if (Prefs.showCaption(this) && !cap.isEmpty()) {
            ui.caption.setText(cap);
            ui.caption.setAlpha(1f);
            ui.caption.setVisibility(View.VISIBLE);
        } else {
            ui.caption.setText("");
            ui.caption.setVisibility(View.INVISIBLE);
        }
    }

    private void applyCaptionVisibility() {
        if (!Prefs.showCaption(this) && !Prefs.plaque(this)) ui.caption.setVisibility(View.INVISIBLE);
    }

    private void fetchNews() {
        lastFetch = System.currentTimeMillis();
        new Thread(() -> {
            final List<News.Item> l = News.fetch(Prefs.newsSource(this));
            Log.i(Hdmi.TAG, "news fetched: " + l.size());
            if (l.isEmpty()) return;
            News.save(this, l);
            h.post(() -> { news = l; showNews(0, true); });
        }, "news").start();
    }

    private void showNews(int idx, boolean animate) {
        if (news.isEmpty()) { ui.news.setText(""); ui.newsMeta.setText(""); return; }
        newsIdx = ((idx % news.size()) + news.size()) % news.size();
        final News.Item it = news.get(newsIdx);
        Runnable apply = () -> {
            ui.news.setText(it.title);
            String t = News.hm(it.time);
            ui.newsMeta.setText(it.source + (t.isEmpty() ? "" : "  ·  " + t));
        };
        if (!animate) { apply.run(); return; }
        ui.news.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            apply.run();
            ui.news.animate().alpha(1f).setDuration(700).start();
        }).start();
        ui.newsMeta.animate().alpha(0f).setDuration(400).withEndAction(() -> ui.newsMeta.animate().alpha(1f).setDuration(700).start()).start();
    }
}
