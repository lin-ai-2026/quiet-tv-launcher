package com.lin.magazinehome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Apps {
    static final class App {
        final String pkg; final String label; final ComponentName cn; final ResolveInfo ri;
        App(String p, String l, ComponentName c, ResolveInfo r) { pkg = p; label = l; cn = c; ri = r; }
        Drawable icon(PackageManager pm) { return ri.loadIcon(pm); }

        /** High-density icon drawn to fill a rounded tile (crops adaptive-icon padding). */
        Bitmap tileBitmap(Context c, int size) {
            Drawable d = hiResIcon(c);
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            if (Build.VERSION.SDK_INT >= 26 && d instanceof AdaptiveIconDrawable) {
                int extra = size / 5;
                d.setBounds(-extra, -extra, size + extra, size + extra);
            } else {
                d.setBounds(0, 0, size, size);
            }
            d.draw(canvas);
            return b;
        }

        private Drawable hiResIcon(Context c) {
            PackageManager pm = c.getPackageManager();
            int dens = Math.max(c.getResources().getDisplayMetrics().densityDpi, DisplayMetrics.DENSITY_XXXHIGH);
            int[] ids = {
                    ri.activityInfo.getIconResource(),
                    ri.activityInfo.applicationInfo != null ? ri.activityInfo.applicationInfo.icon : 0
            };
            for (int id : ids) {
                if (id == 0) continue;
                try {
                    Resources res = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                    Drawable d = res.getDrawableForDensity(id, dens, null);
                    if (d != null) return d;
                } catch (Exception ignore) {}
            }
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                Drawable d = pm.getResourcesForApplication(ai).getDrawableForDensity(ai.icon, dens, null);
                if (d != null) return d;
            } catch (Exception ignore) {}
            return ri.loadIcon(pm);
        }
    }

    static List<App> all(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> m = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
                String p = r.activityInfo.packageName;
                if (p.equals(c.getPackageName()) || m.containsKey(p)) continue;
                String label = String.valueOf(r.loadLabel(pm)).trim();
                m.put(p, new App(p, label, new ComponentName(p, r.activityInfo.name), r));
            }
        }
        List<App> l = new ArrayList<>(m.values());
        final Collator col = Collator.getInstance(Locale.CHINA);
        Collections.sort(l, (a, b) -> col.compare(a.label, b.label));
        return l;
    }

    static App find(List<App> all, String pkg) {
        for (App a : all) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    static void launch(Context c, App a) {
        Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
        if (i == null) i = new Intent(Intent.ACTION_MAIN).setComponent(a.cn).addCategory(Intent.CATEGORY_LAUNCHER);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { c.startActivity(i); } catch (Throwable t) {
            android.widget.Toast.makeText(c, "无法打开 " + a.label, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** Short display names for the home row. */
    static String shortName(App a) {
        switch (a.pkg) {
            case "com.tcl.ui_mediaCenter": return "媒体中心";
            case "com.tcl.MultiScreenInteraction_TV": return "投屏";
            case "com.tcl.appmarket2": return "应用商店";
            case "com.tcl.settings": return "设置";
        }
        String l = a.label;
        return l.length() > 8 ? l.substring(0, 8) : l;
    }
}
