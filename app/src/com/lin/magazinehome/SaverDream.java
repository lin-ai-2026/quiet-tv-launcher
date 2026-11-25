package com.lin.magazinehome;

import android.service.dreams.DreamService;

/** The same standby styles as the Android system screensaver (设置 › 屏幕保护). */
public class SaverDream extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(StandbyView.fromPrefs(this, -1));
    }
}
