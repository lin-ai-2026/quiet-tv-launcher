package com.lin.magazinehome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

/** Quick cast = open the TV's own receiver (TCL 多屏互动 handles DLNA / AirPlay / Miracast); fall back to known receivers. */
final class Cast {
    static final String[] RECEIVERS = {
            "com.tcl.MultiScreenInteraction_TV",   // TCL 多屏互动 / 投屏 (built in)
            "com.hpplay.happyplay.aw",             // 乐播投屏 TV
            "com.ecloud.eshare.server",            // EShare
            "com.waxrain.airplaydmr2", "com.waxrain.airplaydmr"}; // AirPin

    static String pkg(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String p : RECEIVERS) if (launch(pm, p) != null) return p;
        return null;
    }

    static String label(Context c) {
        String p = pkg(c);
        if (p == null) return null;
        try { return String.valueOf(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(p, 0))); }
        catch (Exception e) { return p; }
    }

    private static Intent launch(PackageManager pm, String p) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(p);
        if (i == null) i = pm.getLaunchIntentForPackage(p);
        return i;
    }

    static boolean open(Context c) {
        String p = pkg(c);
        if (p == null) {
            Toast.makeText(c, "没有找到投屏接收应用（TCL 多屏互动 / 乐播投屏）", Toast.LENGTH_LONG).show();
            return false;
        }
        Intent i = launch(c.getPackageManager(), p);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { c.startActivity(i); return true; } catch (Throwable t) { return false; }
    }
}
