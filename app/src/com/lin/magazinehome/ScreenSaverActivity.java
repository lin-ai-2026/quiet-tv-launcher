package com.lin.magazinehome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Standby page: one of the built-in black, monochrome StandbyView styles. Any key leaves. */
public class ScreenSaverActivity extends Activity {
    static volatile boolean onTop;
    static volatile boolean noSignal;

    private final Bus.L bus = (ev, arg) -> {
        if ("go_home".equals(ev)) { noSignal = false; finish(); }
        else if ("hdmi_signal".equals(ev) && Boolean.TRUE.equals(arg) && noSignal) finish();
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        apply(getIntent());
        Bus.add(bus);
    }

    @Override
    protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        apply(i);
    }

    private void apply(android.content.Intent i) {
        int style = i.getIntExtra("style", -1);
        boolean night = i.getBooleanExtra("night", false);
        noSignal = i.getBooleanExtra("nosignal", false);
        float dim = night ? StandbyView.DIMS[StandbyView.DIMS.length - 1] : -1;
        if (noSignal && style < 0) style = 0;
        setContentView(StandbyView.create(this, style, dim, null));
    }

    @Override protected void onResume() { super.onResume(); onTop = true; Bus.post("where", "saver"); Hdmi.updateStayAwake(); }
    @Override protected void onPause() { onTop = false; super.onPause(); Hdmi.updateStayAwake(); }

    @Override protected void onDestroy() {
        Bus.remove(bus);
        if (isFinishing()) noSignal = false;
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP) leave();
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) { leave(); return true; }

    private void leave() {
        if (noSignal) Hdmi.setDesired(this, "home");
        noSignal = false;
        finish();
    }
}
