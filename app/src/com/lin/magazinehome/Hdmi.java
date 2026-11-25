package com.lin.magazinehome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import java.util.List;

final class Hdmi {
    static final String TAG = "MagHome";
    static final long BOOT_WINDOW_MS = 90 * 1000L;
    static final long DEBOUNCE_MS = 1500L;

    private static Context app;
    private static TvInputManager tim;
    private static TvInputManager.TvInputCallback cb;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static volatile Boolean available;          // null = not tried, false = cannot read
    private static volatile Integer lastRaw;            // last TvInputManager state
    private static volatile Boolean lastHas;            // debounced: has useful signal
    private static volatile boolean watching;
    static volatile String detectNote = "";

    static String inputIdFor(Context c, int port) {
        try {
            TvInputManager m = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            if (m != null) {
                List<TvInputInfo> list = m.getTvInputList();
                String fallback = null;
                for (TvInputInfo i : list) {
                    Log.i(TAG, "input " + i.getId() + " type=" + i.getType() + " label=" + i.loadLabel(c));
                    if (i.getType() != TvInputInfo.TYPE_HDMI) continue;
                    if (i.getId().contains("Hdmi" + port + "InputService")) return i.getId();
                    if (fallback == null) fallback = i.getId();
                }
                if (port == 1 && fallback != null) return fallback;
            }
        } catch (Throwable t) {
            Log.w(TAG, "tv input list failed", t);
        }
        // Amlogic/droidlogic convention: HDMI1..4 -> hardware device 5..8
        return "com.droidlogic.tvinput/.services.Hdmi" + port + "InputService/HW" + (4 + port);
    }

    static boolean switchTo(Context c, int port) {
        String id = inputIdFor(c, port);
        Uri uri = TvContract.buildChannelUriForPassthroughInput(id);
        Log.i(TAG, "switch HDMI" + port + " -> " + uri);
        // TCL LiveTV ignores the passthrough URI and reopens the last saved source.
        // It does honour the int extra "targetSourceId" = EnTCLInputSource ordinal
        // (verified on this TV: HDMI1=8, HDMI2=9, HDMI3=10, HDMI4=11).
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("targetSourceId", 7 + port);
        i.setClassName("com.tcl.tv", "com.tcl.tv.TVActivity");
        try { c.startActivity(i); return true; }
        catch (Throwable t) { Log.w(TAG, "explicit TVActivity failed", t); }
        try {
            Intent b = new Intent("com.tcl.receiver.KUKONG_SWITCH_SOURCE").setPackage("com.tcl.tv").putExtra("targetSourceId", 7 + port);
            c.sendBroadcast(b);
            return true;
        } catch (Throwable t) { Log.w(TAG, "kukong broadcast failed", t); }
        return false;
    }

    static int bootCount(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), "boot_count", -1);
    }

    /** Switch once per boot if we are still in the early-boot window. */
    static synchronized boolean maybeBootSwitch(Context c, String from) {
        if (!Prefs.autoHdmi(c) && !systemBootsToHdmi(c)) return false;
        if (!Prefs.wantsHdmi(c)) return false;
        SharedPreferences p = Prefs.p(c);
        int bc = bootCount(c);
        long up = SystemClock.elapsedRealtime();
        String key = bc != -1 ? "bc" + bc : "t" + (System.currentTimeMillis() - up) / 120000L;
        if (key.equals(p.getString("boot_done", ""))) return false;
        p.edit().putString("boot_done", key).commit();
        if (up > BOOT_WINDOW_MS) { Log.i(TAG, "boot switch skipped, uptime " + up + " via " + from); return false; }
        Log.i(TAG, "boot switch via " + from + " bootCount=" + bc);
        return switchTo(c, Prefs.hdmiPort(c));
    }

    // ---------------------------------------------------------------- desired + signal watch

    static void setDesired(Context c, String v) {
        Prefs.setDesired(c, v);
        Bus.post("where", where(c));
        if (app == null) app = c.getApplicationContext();
        updateStayAwake();
        if ("hdmi".equals(Prefs.desired(c))) h.post(checkNow);
    }

    static boolean systemBootsToHdmi(Context c) {
        if (Prefs.autoHdmi(c)) return true;
        String[] keys = {"boot_app_mode", "boot_source", "boot_tv_source", "tv_start_up_source", "start_up_source"};
        android.content.ContentResolver cr = c.getContentResolver();
        for (String k : keys) {
            for (String v : new String[]{
                    safeGet(Settings.Global.class, cr, k),
                    safeGet(Settings.System.class, cr, k),
                    safeGet(Settings.Secure.class, cr, k)}) {
                if (v != null && v.toLowerCase().contains("hdmi")) return true;
            }
        }
        return false;
    }

    private static String safeGet(Class<?> cls, android.content.ContentResolver cr, String k) {
        try {
            return (String) cls.getMethod("getString", android.content.ContentResolver.class, String.class).invoke(null, cr, k);
        } catch (Throwable t) { return null; }
    }

    static void initDesiredOnBoot(Context c) {
        setDesired(c, systemBootsToHdmi(c) ? "hdmi" : "home");
    }

    /** null = unknown / detection unavailable; else last debounced signal. */
    static Boolean hasSignal() { return lastHas; }

    static boolean detectAvailable() { return available != null && available; }

    static synchronized void startWatch(Context c) {
        app = c.getApplicationContext();
        if (watching) { updateStayAwake(); return; }
        try {
            tim = (TvInputManager) app.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) {
                available = false;
                detectNote = "信号检测不可用";
                Log.w(TAG, "信号检测不可用: TvInputManager is null");
                updateStayAwake();
                return;
            }
            String id = inputIdFor(app, Prefs.hdmiPort(app));
            int st = tim.getInputState(id);
            available = true;
            detectNote = "";
            lastRaw = st;
            cb = new TvInputManager.TvInputCallback() {
                @Override public void onInputStateChanged(String inputId, int state) {
                    String want = inputIdFor(app, Prefs.hdmiPort(app));
                    if (inputId != null && (inputId.equals(want) || inputId.contains("Hdmi" + Prefs.hdmiPort(app)))) {
                        Log.i(TAG, "hdmi state " + inputId + " -> " + state);
                        lastRaw = state;
                        schedule();
                    }
                }
            };
            tim.registerCallback(cb, h);
            watching = true;
            schedule();
            h.removeCallbacks(poll);
            h.postDelayed(poll, 10_000);
            Log.i(TAG, "hdmi watch started, state=" + st + " id=" + id);
            updateStayAwake();
        } catch (Throwable t) {
            available = false;
            detectNote = "信号检测不可用";
            watching = false;
            Log.w(TAG, "信号检测不可用", t);
            updateStayAwake();
        }
    }

    private static final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!watching) return;
            try {
                if (tim != null) lastRaw = tim.getInputState(inputIdFor(app, Prefs.hdmiPort(app)));
            } catch (Throwable t) {
                Log.w(TAG, "hdmi poll failed", t);
            }
            schedule();
            h.postDelayed(this, 10_000);
        }
    };

    private static final Runnable checkNow = Hdmi::apply;

    private static void schedule() {
        h.removeCallbacks(applyDebounced);
        h.postDelayed(applyDebounced, DEBOUNCE_MS);
    }

    private static final Runnable applyDebounced = Hdmi::apply;

    private static void apply() {
        if (app == null) return;
        if (available == null || !available) return;          // never guess
        Integer raw = lastRaw;
        if (raw == null) {
            try { if (tim != null) raw = lastRaw = tim.getInputState(inputIdFor(app, Prefs.hdmiPort(app))); }
            catch (Throwable t) {
                available = false;
                detectNote = "信号检测不可用";
                Log.w(TAG, "信号检测不可用", t);
                Bus.post("where", where(app));
                return;
            }
        }
        if (raw == null) return;
        boolean has = raw == TvInputManager.INPUT_STATE_CONNECTED;
        boolean changed = lastHas == null || lastHas != has;
        lastHas = has;
        Bus.post("where", where(app));
        updateStayAwake();
        if (!Prefs.wantsHdmi(app)) return;                     // respect the user on the desktop
        if (has) {
            boolean away = ScreenSaverActivity.onTop || MainActivity.onTop || ViewerActivity.onTop;
            if (!changed && !away) return;                    // already on the computer picture
            Log.i(TAG, "hdmi signal present, desired=hdmi -> switch (changed=" + changed + ")");
            Bus.post("hdmi_signal", true);
            switchTo(app, Prefs.hdmiPort(app));
        } else {
            if (ScreenSaverActivity.onTop && ScreenSaverActivity.noSignal) return;
            Log.i(TAG, "hdmi signal lost, desired=hdmi -> black clock");
            Intent i = new Intent(app, ScreenSaverActivity.class)
                    .putExtra("style", 0)
                    .putExtra("nosignal", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { app.startActivity(i); } catch (Throwable t) { Log.w(TAG, "start nosignal saver", t); }
            Bus.post("hdmi_signal", false);
        }
    }

    static String where(Context c) {
        if (ScreenSaverActivity.onTop) return "saver";
        if (ViewerActivity.onTop) return "viewer";
        if (MainActivity.onTop) return "home";
        return Prefs.wantsHdmi(c) ? "hdmi" : "home";
    }

    // Hold a screen wake lock while the user wants HDMI and the PC picture should stay up.
    // This stops the TV's own 2-minute idle screensaver from starting and flashing black.
    // Release on the desktop, on our black clock / 晚安 screensaver, or when the HDMI signal is gone.
    private static PowerManager.WakeLock stayAwake;
    private static boolean stayHeld;

    static void updateStayAwake() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            h.post(Hdmi::updateStayAwake);
            return;
        }
        if (app == null) return;
        // This TV is used as a PC monitor over HDMI most of the time.
        // Hold the screen on whenever we are not showing our own standby page,
        // even if desired is still "home", so the official 2-minute screensaver
        // cannot flash black. Release only for 晚安 / idle / no-signal standby.
        boolean hold = !ScreenSaverActivity.onTop;
        if (hold) acquireStayAwake();
        else releaseStayAwake();
    }

    @SuppressWarnings("deprecation")
    private static synchronized void acquireStayAwake() {
        try {
            if (stayAwake != null && stayAwake.isHeld()) { stayHeld = true; return; }
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            stayAwake = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                    "magazinehome:hdmi");
            stayAwake.setReferenceCounted(false);
            stayAwake.acquire();
            stayHeld = true;
            Log.i(TAG, "hdmi stay-awake on");
        } catch (Throwable t) {
            stayHeld = false;
            Log.w(TAG, "stay-awake failed", t);
        }
    }

    private static synchronized void releaseStayAwake() {
        try {
            if (stayAwake != null && stayAwake.isHeld()) {
                stayAwake.release();
                Log.i(TAG, "hdmi stay-awake off");
            }
        } catch (Throwable t) {
            Log.w(TAG, "stay-awake release", t);
        }
        stayHeld = false;
    }
}
