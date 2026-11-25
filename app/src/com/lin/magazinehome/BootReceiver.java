package com.lin.magazinehome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        Prefs.p(app).edit().putLong("sleep_at", 0).apply();   // alarms do not survive a reboot
        Prefs.migrate53(app);
        PhoneServer.start(app);
        Hdmi.initDesiredOnBoot(app);
        Hdmi.startWatch(app);
        final PendingResult pr = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { Hdmi.maybeBootSwitch(app, "receiver"); } finally { pr.finish(); }
        }, 2500);
    }
}
