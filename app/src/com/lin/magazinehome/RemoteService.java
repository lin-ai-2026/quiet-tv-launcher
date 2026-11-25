package com.lin.magazinehome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 手机遥控: cursor overlay + clicks/scroll/focus navigation via the accessibility API.
 * Receives almost no events (window-state only, 1 s throttle) and does nothing until the phone sends a command.
 * Android 11 TVs often drop dispatchGesture(), so clicks try the node under the cursor first.
 */
public class RemoteService extends AccessibilityService {
    static volatile RemoteService instance;

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private CursorView cursor;
    private WindowManager.LayoutParams lp;
    private float cx = -1, cy = -1;
    private int sw, sh;
    private final Runnable hide = this::hideCursor;

    @Override protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Prefs.remoteOn(this)) PhoneServer.start(getApplicationContext());
        Bus.post("remote", "on");
        Log.i(Hdmi.TAG, "remote service connected");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        instance = null;
        hideCursor();
        PhoneServer.releaseSoon();
        Bus.post("remote", "off");
        super.onDestroy();
    }

    // ---------------------------------------------------------------- cursor
    private static final class CursorView extends View {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        CursorView(Context c) {
            super(c);
            fill.setColor(0xF2FFFFFF);
            ring.setColor(0x99000000); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(Ui.dp(c, 1.6f));
        }
        @Override protected void onDraw(Canvas cv) {
            float r = getWidth() / 2f - Ui.dp(getContext(), 2);
            cv.drawCircle(getWidth() / 2f, getHeight() / 2f, r, fill);
            cv.drawCircle(getWidth() / 2f, getHeight() / 2f, r, ring);
        }
    }

    private void ensureCursor() {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        sw = dm.widthPixels; sh = dm.heightPixels;
        if (cx < 0) { cx = sw / 2f; cy = sh / 2f; }
        if (cursor != null) return;
        cursor = new CursorView(this);
        int size = Ui.dp(this, 22);
        lp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        place();
        try { wm.addView(cursor, lp); } catch (Throwable t) { Log.w(Hdmi.TAG, "cursor overlay", t); cursor = null; }
    }

    private void place() {
        int size = lp.width;
        lp.x = Math.round(cx - size / 2f); lp.y = Math.round(cy - size / 2f);
    }

    private void hideCursor() {
        if (cursor != null) { try { wm.removeView(cursor); } catch (Throwable ignore) {} cursor = null; }
    }

    private void touchCursor() { h.removeCallbacks(hide); h.postDelayed(hide, 8000); }

    void moveBy(float dx, float dy) {
        ensureCursor();
        float speed = (float) Math.min(4.0, 1.4 + Math.hypot(dx, dy) / 18.0);   // light acceleration
        cx = Math.max(0, Math.min(sw - 1, cx + dx * speed * sw / 1280f));
        cy = Math.max(0, Math.min(sh - 1, cy + dy * speed * sw / 1280f));
        if (cursor != null) { place(); wm.updateViewLayout(cursor, lp); }
        touchCursor();
    }

    int[] cursorPoint() {
        if (cx < 0) ensureCursor();
        return new int[]{Math.round(cx), Math.round(cy)};
    }

    // ---------------------------------------------------------------- pointer actions
    void tap(boolean longPress) {
        ensureCursor();
        touchCursor();
        if (cursor != null) cursor.animate().scaleX(0.7f).scaleY(0.7f).setDuration(80).withEndAction(() -> {
            if (cursor != null) cursor.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); }).start();
        int x = Math.round(cx), y = Math.round(cy);
        AccessibilityNodeInfo n = nodeAt(x, y, longPress ? AccessibilityNodeInfo.ACTION_LONG_CLICK : AccessibilityNodeInfo.ACTION_CLICK);
        if (n != null) {
            n.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if (n.performAction(longPress ? AccessibilityNodeInfo.ACTION_LONG_CLICK : AccessibilityNodeInfo.ACTION_CLICK)) return;
        }
        gesture(x, y, x, y, longPress ? 700 : 40);
    }

    void scroll(float dx, float dy) {
        ensureCursor();
        touchCursor();
        int x = Math.round(cx), y = Math.round(cy);
        AccessibilityNodeInfo n = nodeAt(x, y, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        if (n == null) n = nodeAt(x, y, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        float main = Math.abs(dy) >= Math.abs(dx) ? dy : dx;
        if (n != null && n.performAction(main < 0 ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return;
        int ex = Math.max(1, Math.min(sw - 2, Math.round(x + dx * 3))), ey = Math.max(1, Math.min(sh - 2, Math.round(y + dy * 3)));
        gesture(x, y, ex, ey, 200);
    }

    private void gesture(int x1, int y1, int x2, int y2, long ms) {
        try {
            Path p = new Path();
            p.moveTo(x1, y1);
            if (x1 != x2 || y1 != y2) p.lineTo(x2, y2);
            GestureDescription g = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p, 0, ms)).build();
            dispatchGesture(g, null, null);
        } catch (Throwable t) { Log.w(Hdmi.TAG, "gesture", t); }
    }

    /** Smallest visible node under (x,y) that supports the action (or whose ancestor does). */
    private AccessibilityNodeInfo nodeAt(int x, int y, int action) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo hit = deepest(root, x, y, 0);
        for (AccessibilityNodeInfo n = hit; n != null; n = n.getParent()) {
            for (AccessibilityNodeInfo.AccessibilityAction a : n.getActionList()) if (a.getId() == action) return n;
        }
        return null;
    }

    private final Rect r = new Rect();
    private AccessibilityNodeInfo deepest(AccessibilityNodeInfo n, int x, int y, int depth) {
        if (n == null || depth > 40 || !n.isVisibleToUser()) return null;
        n.getBoundsInScreen(r);
        if (!r.contains(x, y)) return null;
        for (int i = n.getChildCount() - 1; i >= 0; i--) {
            AccessibilityNodeInfo d = deepest(n.getChild(i), x, y, depth + 1);
            if (d != null) return d;
        }
        return n;
    }

    // ---------------------------------------------------------------- keys without root
    void key(String k) {
        switch (k) {
            case "back": performGlobalAction(GLOBAL_ACTION_BACK); return;
            case "home": performGlobalAction(GLOBAL_ACTION_HOME); return;
            case "power": performGlobalAction(8 /* GLOBAL_ACTION_LOCK_SCREEN, API 28 */); return;
            case "ok": case "enter": {
                AccessibilityNodeInfo f = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (f == null) { focusFirst(); return; }
                if (f.isEditable() && android.os.Build.VERSION.SDK_INT >= 30) {
                    f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId()); return; }
                for (AccessibilityNodeInfo n = f; n != null; n = n.getParent())
                    if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                return;
            }
        }
        int dir = k.equals("up") ? View.FOCUS_UP : k.equals("down") ? View.FOCUS_DOWN
                : k.equals("left") ? View.FOCUS_LEFT : k.equals("right") ? View.FOCUS_RIGHT : 0;
        if (dir == 0) return;
        AccessibilityNodeInfo f = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null) { focusFirst(); return; }
        AccessibilityNodeInfo n = f.focusSearch(dir);
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            return;
        }
        // nothing focusable that way: try scrolling the container instead
        for (AccessibilityNodeInfo p = f.getParent(); p != null; p = p.getParent()) {
            if (p.isScrollable()) {
                p.performAction(dir == View.FOCUS_UP || dir == View.FOCUS_LEFT
                        ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                return;
            }
        }
    }

    private void focusFirst() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo n = firstFocusable(root, 0);
        if (n != null) n.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    }

    private AccessibilityNodeInfo firstFocusable(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 40) return null;
        if (n.isFocusable() && n.isVisibleToUser()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo d = firstFocusable(n.getChild(i), depth + 1);
            if (d != null) return d;
        }
        return null;
    }

    /** Types Chinese or any text into the focused (or first) input box. */
    void typeText(String text) {
        AccessibilityNodeInfo f = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null || !f.isEditable()) f = firstEditable(getRootInActiveWindow(), 0);
        if (f == null) return;
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        f.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 40) return null;
        if (n.isEditable() && n.isVisibleToUser()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo d = firstEditable(n.getChild(i), depth + 1);
            if (d != null) return d;
        }
        return null;
    }

    static boolean enabledInSettings(Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
        return s != null && s.contains(c.getPackageName() + "/");
    }
}
