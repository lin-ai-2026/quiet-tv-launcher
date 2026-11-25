package com.lin.magazinehome;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CopyOnWriteArrayList;

/** Tiny in-process event bus (phone uploads, remote status) — no broadcasts, no extra threads. */
final class Bus {
    interface L { void on(String event, Object arg); }
    private static final CopyOnWriteArrayList<L> ls = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    static void add(L l) { ls.add(l); }
    static void remove(L l) { ls.remove(l); }
    static void post(final String ev, final Object arg) {
        main.post(() -> { for (L l : ls) l.on(ev, arg); });
    }
}
