package com.lin.magazinehome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.nayuki.qrcodegen.QrCode;

/** 扫码: QR for the phone page (换壁纸 / 看图 / 回桌面或切到 HDMI). Server stays on after boot. */
public class ConnectActivity extends Activity {
    private TextView status;
    private ImageView photo;
    private Bitmap shown;

    private final Bus.L bus = (ev, arg) -> {
        if ("clients".equals(ev) || "remote".equals(ev) || "adb".equals(ev)) updateStatus(ev.equals("adb") ? String.valueOf(arg) : null);
        else if ("upload".equals(ev)) showPhoto(String.valueOf(arg));
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        PhoneServer.start(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B0A);
        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setAlpha(0f);
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF20B0B0A, 0xB30B0B0A, 0x400B0B0A}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(dp(110), dp(60), dp(110), dp(60));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, Ui.bold(this), 50, Ui.INK);
        title.setText("用手机扫一扫");
        left.addView(title);
        View rule = new View(this);
        rule.setBackgroundColor(0x40ECE7DD);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(420), Math.max(1, dp(0.8f)));
        rl.topMargin = dp(26); rl.bottomMargin = dp(22);
        left.addView(rule, rl);
        String[][] items = {{"换壁纸", "从相册选照片，设为电视壁纸，也可以在电视上看图"}, {"切画面", "回自制桌面，或切回 HDMI"}, {"投屏", "页面里仍有投屏说明和定时关机"}};
        for (String[] it : items) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView h = Ui.text(this, Ui.bold(this), 22, Ui.INK);
            h.setText(it[0]);
            r.addView(h, new LinearLayout.LayoutParams(dp(110), -2));
            TextView d = Ui.text(this, Ui.reg(this), 18, Ui.GREY);
            d.setText(it[1]);
            r.addView(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.bottomMargin = dp(16);
            left.addView(r, lp);
        }
        status = Ui.text(this, Ui.sans(), 15, 0xFF8C877E);
        status.setLineSpacing(0, 1.35f);
        status.setMaxWidth(dp(620));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = dp(22);
        left.addView(status, sl);
        page.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        String url = PhoneServer.url(this);
        FrameLayout qrBox = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(18));
        qrBox.setBackground(bg);
        qrBox.setPadding(dp(18), dp(18), dp(18), dp(18));
        ImageView qr = new ImageView(this);
        if (url != null) {
            BitmapDrawable d = new BitmapDrawable(getResources(), qrBitmap(url));
            d.setFilterBitmap(false);
            qr.setImageDrawable(d);
        }
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrBox.addView(qr, new FrameLayout.LayoutParams(-1, -1));
        page.addView(qrBox, new LinearLayout.LayoutParams(dp(330), dp(330)));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        Bus.add(bus);
        updateStatus(null);
    }

    private int dp(float v) { return Ui.dp(this, v); }

    static Bitmap qrBitmap(String text) {
        QrCode q = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int n = q.size, border = 1;
        Bitmap b = Bitmap.createBitmap(n + border * 2, n + border * 2, Bitmap.Config.RGB_565);
        b.eraseColor(0xFFFFFFFF);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) if (q.getModule(x, y)) b.setPixel(x + border, y + border, 0xFF111111);
        return b;
    }

    private void updateStatus(String extra) {
        String url = PhoneServer.url(this);
        StringBuilder sb = new StringBuilder();
        if (url == null) sb.append("电视没有连上局域网。请先把电视连上 Wi-Fi，再重新打开这一页。");
        else {
            sb.append("用手机或电脑浏览器扫码打开。手机和电视要在同一个 Wi-Fi 下。");
            int n = PhoneServer.clients.get();
            if (n > 0) sb.append("\n已连接 ").append(n).append(" 台设备");
        }
        if (extra != null && !extra.equals("null")) sb.append("\n").append(extra);
        status.setText(sb.toString());
    }

    private void showPhoto(final String path) {
        new Thread(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            final Bitmap b = BitmapFactory.decodeFile(path, o);
            runOnUiThread(() -> {
                if (b == null || isFinishing()) return;
                photo.setImageBitmap(b);
                if (shown != null) shown.recycle();
                shown = b;
                photo.setAlpha(0f);
                photo.animate().alpha(1f).setDuration(900).start();
                updateStatus("已收到照片");
            });
        }).start();
    }

    @Override protected void onDestroy() {
        Bus.remove(bus);
        photo.setImageDrawable(null);
        if (shown != null) { shown.recycle(); shown = null; }
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP && (e.getKeyCode() == KeyEvent.KEYCODE_BACK || e.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { finish(); return true; }
        return super.dispatchKeyEvent(e);
    }
}
