package com.lin.magazinehome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Sleep timer: one AlarmManager alarm, nothing running in between. */
public class Sleep extends BroadcastReceiver {
    static void set(Context c, int minutes) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7, new Intent(c, Sleep.class), PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (minutes <= 0) { Prefs.p(c).edit().putLong("sleep_at", 0).apply(); return; }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
        Prefs.p(c).edit().putLong("sleep_at", at).apply();
    }

    static long remainingMin(Context c) {
        long at = Prefs.p(c).getLong("sleep_at", 0);
        long left = at - System.currentTimeMillis();
        return left > 0 ? (left + 59_999) / 60_000 : 0;
    }

    @Override public void onReceive(Context c, Intent i) {
        Prefs.p(c).edit().putLong("sleep_at", 0).apply();
        Log.i(Hdmi.TAG, "sleep timer fired");
        now(c.getApplicationContext());
    }

    /** Standby: accessibility lock-screen action → local ADB KEYCODE_SLEEP → black standby page. */
    static String now(Context c) {
        RemoteService s = RemoteService.instance;
        if (s != null && s.performGlobalAction(8 /* GLOBAL_ACTION_LOCK_SCREEN */)) return "ok";
        if (Prefs.adbBoost(c) && LocalAdb.get(c).exec("input keyevent 223")) return "ok";
        Intent ss = new Intent(c, ScreenSaverActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(ss);
        return "saver";
    }
}
