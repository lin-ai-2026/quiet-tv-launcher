package com.lin.magazinehome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import java.io.File;

/** Shows a photo sent from the phone ("在电视上看"), full screen. Any key closes it. */
public class ViewerActivity extends Activity {
    static volatile boolean onTop;
    private ImageView iv;
    private Bitmap bmp;
    private boolean returnHdmi;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable autoBack = this::leave;

    private final Bus.L bus = (ev, arg) -> {
        if ("go_home".equals(ev) || "hdmi_signal".equals(ev)) finish();
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        iv = new ImageView(this);
        iv.setBackgroundColor(0xFF000000);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        setContentView(iv);
        Bus.add(bus);
        load(getIntent());
    }

    @Override protected void onNewIntent(android.content.Intent i) { super.onNewIntent(i); load(i); }

    private void load(android.content.Intent i) {
        h.removeCallbacks(autoBack);
        String path = i == null ? null : i.getStringExtra("path");
        returnHdmi = i != null && i.getBooleanExtra("return_hdmi", false);
        if (!allowedPhoto(path)) { finish(); return; }
        if (returnHdmi) h.postDelayed(autoBack, 8000);
        new Thread(() -> {
            final Bitmap b = Wallpapers.load(this, new Wallpapers.Entry(null, new File(path), ""));
            runOnUiThread(() -> {
                if (b == null) return;
                iv.setAlpha(0f);
                iv.setImageBitmap(b);
                if (bmp != null) bmp.recycle();
                bmp = b;
                iv.animate().alpha(1f).setDuration(500).start();
            });
        }).start();
    }

    @Override protected void onResume() { super.onResume(); onTop = true; Bus.post("where", "viewer"); }
    @Override protected void onPause() { onTop = false; super.onPause(); }

    @Override protected void onDestroy() {
        h.removeCallbacks(autoBack);
        Bus.remove(bus);
        iv.setImageDrawable(null);
        if (bmp != null) bmp.recycle();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP) leave();
        return true;
    }

    private boolean allowedPhoto(String path) {
        if (path == null || path.indexOf("..") >= 0) return false;
        try {
            File f = new File(path);
            String fc = f.getCanonicalPath();
            String dc = Wallpapers.phoneDir(this).getCanonicalPath();
            return f.isFile() && fc.startsWith(dc + File.separator);
        } catch (Exception e) { return false; }
    }

    private void leave() {
        h.removeCallbacks(autoBack);
        if (returnHdmi) {
            Hdmi.setDesired(this, "hdmi");
            Hdmi.switchTo(this, Prefs.hdmiPort(this));
        }
        finish();
    }
}
