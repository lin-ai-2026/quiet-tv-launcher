package com.lin.magazinehome;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal LAN web server for the phone page (no library): one accept thread, one short-lived thread
 * per connection. Starts on boot and stays running. Idle cost: one thread blocked in accept().
 */
final class PhoneServer {
    static final int[] PORTS = {8686, 8687, 8688};
    private static ServerSocket ss;
    private static volatile int port;
    private static Context app;
    static final AtomicInteger clients = new AtomicInteger();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable stopper = PhoneServer::stopIfUnused;

    static synchronized void start(Context c) {
        app = c.getApplicationContext();
        main.removeCallbacks(stopper);
        if (ss != null && !ss.isClosed()) return;
        for (int p : PORTS) {
            try { ss = new ServerSocket(p, 8); port = p; break; } catch (Exception e) { ss = null; }
        }
        if (ss == null) { Log.w(Hdmi.TAG, "phone server: no port"); return; }
        Hdmi.startWatch(app);
        final ServerSocket me = ss;
        Thread t = new Thread(() -> {
            while (!me.isClosed()) {
                try {
                    final Socket s = me.accept();
                    Thread w = new Thread(() -> handle(s), "phone-conn");
                    w.setDaemon(true);
                    w.start();
                } catch (Exception e) { if (!me.isClosed()) Log.w(Hdmi.TAG, "accept", e); }
            }
        }, "phone-server");
        t.setDaemon(true);
        t.start();
        Log.i(Hdmi.TAG, "phone server on " + port);
    }

    static boolean running() { return ss != null && !ss.isClosed(); }

    /** Kept so older call sites compile; the server stays up after boot. */
    static void releaseSoon() { /* no-op in v5.3 */ }

    private static synchronized void stopIfUnused() { /* no-op */ }

    static String ip() {
        try {
            String best = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String h = a.getHostAddress();
                    if (ni.getName().startsWith("wlan") || ni.getName().startsWith("eth")) return h;
                    if (best == null && a.isSiteLocalAddress()) best = h;
                }
            }
            return best;
        } catch (Exception e) { return null; }
    }

    static String url(Context c) {
        String ip = ip();
        if (ip == null) return null;
        return "http://" + ip + ":" + (port == 0 ? PORTS[0] : port) + "/?k=" + Prefs.token(c);
    }

    // ---------------------------------------------------------------- HTTP
    private static void handle(Socket s) {
        try {
            if (!LanGuard.lanPeer(s.getInetAddress())) return;
            s.setSoTimeout(30_000);
            s.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(s.getInputStream(), 8192);
            OutputStream out = s.getOutputStream();
            for (int n = 0; n < 50; n++) {                         // keep-alive loop
                String head = readHead(in);
                if (head == null) break;
                String[] lines = head.split("\r\n");
                String[] rl = lines[0].split(" ");
                if (rl.length < 2) break;
                Map<String, String> hd = new HashMap<>();
                for (int i = 1; i < lines.length; i++) {
                    int k = lines[i].indexOf(':');
                    if (k > 0) hd.put(lines[i].substring(0, k).trim().toLowerCase(), lines[i].substring(k + 1).trim());
                }
                String method = rl[0], target = rl[1], path = target;
                Map<String, String> q = new HashMap<>();
                int qi = target.indexOf('?');
                if (qi >= 0) {
                    path = target.substring(0, qi);
                    for (String kv : target.substring(qi + 1).split("&")) {
                        int e = kv.indexOf('=');
                        if (e > 0) q.put(kv.substring(0, e), URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
                    }
                }
                if (!LanGuard.hostAllowed(hd.get("host"), s.getLocalPort())) {
                    send(out, 403, "text/plain", "forbidden".getBytes("UTF-8"), false);
                    break;
                }
                if (!LanGuard.originAllowed(hd.get("origin"), hd.get("host"))
                        || !LanGuard.fetchSiteAllowed(hd.get("sec-fetch-site"))) {
                    send(out, 403, "text/plain", "forbidden".getBytes("UTF-8"), false);
                    break;
                }
                long len = 0;
                try { len = Long.parseLong(hd.containsKey("content-length") ? hd.get("content-length") : "0"); } catch (Exception ignore) {}
                if (len < 0 || len > 16_000_000) { send(out, 413, "text/plain", "too large".getBytes("UTF-8"), false); break; }

                boolean authed = Pairing.matches(Prefs.token(app), q.get("k"));
                if (path.equals("/favicon.ico")) {
                    send(out, 404, "text/plain", new byte[0]);
                    if (len > 0) break;
                    continue;
                }
                if (!authed) {
                    send(out, 403, "text/html; charset=utf-8", ("<!doctype html><meta name=viewport content='width=device-width'><meta charset=utf-8>"
                            + "<body style='font:18px/1.6 sans-serif;padding:48px 28px;background:#0b0b0a;color:#ece7dd'>"
                            + "这个链接已经失效。<br>请在电视首页打开「扫码」，重新扫一次。").getBytes("UTF-8"), false);
                    try { Thread.sleep(300); } catch (InterruptedException ignore) {}
                    break;
                }
                byte[] body = new byte[(int) len];
                int got = 0;
                while (got < len) { int r = in.read(body, got, (int) len - got); if (r < 0) break; got += r; }
                if (path.equals("/ws") && "websocket".equalsIgnoreCase(hd.get("upgrade"))) {
                    websocket(s, in, out, hd.get("sec-websocket-key"));
                    return;
                }
                if (path.equals("/") || path.equals("/index.html")) {
                    send(out, 200, "text/html; charset=utf-8", asset("web/index.html"));
                } else if (path.equals("/state")) {
                    send(out, 200, "application/json", Remote.state(app).getBytes("UTF-8"));
                } else if (path.equals("/cmd") && method.equals("POST")) {
                    send(out, 200, "text/plain", Remote.exec(app, new String(body, "UTF-8")).getBytes("UTF-8"));
                } else if (path.equals("/photos")) {
                    send(out, 200, "application/json; charset=utf-8", Remote.state(app).getBytes("UTF-8"));
                } else if (path.equals("/thumb")) {
                    byte[] th = Wallpapers.thumb(app, q.get("id"));
                    if (th == null) send(out, 404, "text/plain", "missing".getBytes());
                    else send(out, 200, "image/jpeg", th);
                } else if (path.equals("/geo")) {
                    send(out, 200, "application/json; charset=utf-8", geo(q.get("q")).getBytes("UTF-8"));
                } else if (path.equals("/upload") && method.equals("POST")) {
                    String plaque = Wallpapers.formatPlaque(q.get("date"), q.get("loc"));
                    File f = Wallpapers.savePhone(app, body, plaque);
                    if (f == null) { send(out, 400, "text/plain", "bad image".getBytes()); continue; }
                    String mode = q.containsKey("mode") ? q.get("mode") : "wall";
                    if (mode.equals("show")) {
                        boolean back = Prefs.wantsHdmi(app) || "hdmi".equals(Hdmi.where(app));
                        Intent i = new Intent(app, ViewerActivity.class).putExtra("path", f.getPath())
                                .putExtra("return_hdmi", back)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        app.startActivity(i);
                    } else if (mode.equals("keep")) {
                        // album only, already saved; do not change the current wallpaper
                    } else {
                        Prefs.setIdx(app, "wall_src", 5);
                        Bus.post("wall", f.getPath());
                    }
                    Bus.post("upload", f.getPath());
                    send(out, 200, "application/json; charset=utf-8", ("{\"ok\":true,\"id\":\"" + Remote.esc(f.getName()) + "\"}").getBytes("UTF-8"));

                } else send(out, 404, "text/plain", "not found".getBytes());
                if ("close".equalsIgnoreCase(hd.get("connection"))) break;
            }
        } catch (Exception e) {
            // client went away
        } finally {
            try { s.close(); } catch (Exception ignore) {}
        }
    }

    private static String readHead(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream(512);
        int st = 0, c;
        while ((c = in.read()) >= 0) {
            b.write(c);
            st = (c == '\r' && (st == 0 || st == 2)) ? st + 1 : (c == '\n' && (st == 1 || st == 3)) ? st + 1 : 0;
            if (st == 4) return b.toString("UTF-8");
            if (b.size() > 16384) return null;
        }
        return null;
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws Exception {
        send(out, code, type, body, true);
    }

    private static void send(OutputStream out, int code, String type, byte[] body, boolean keep) throws Exception {
        String msg = code == 200 ? "OK" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : "Error";
        String h = "HTTP/1.1 " + code + " " + msg + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: " + (keep ? "keep-alive" : "close") + "\r\n\r\n";
        out.write(h.getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }

    private static byte[] asset(String name) throws Exception {
        try (InputStream in = app.getAssets().open(name)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    // ---------------------------------------------------------------- WebSocket (RFC 6455, text frames only)
    private static void websocket(Socket s, InputStream in, OutputStream out, String key) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        String accept = Base64.encodeToString(sha.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC11B9D").getBytes("UTF-8")), Base64.NO_WRAP);
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                + accept + "\r\n\r\n").getBytes("UTF-8"));
        out.flush();
        s.setSoTimeout(90_000);
        clients.incrementAndGet();
        Bus.post("clients", clients.get());
        try {
            wsSend(out, "hello " + Remote.state(app));
            while (true) {
                int b0 = in.read(), b1 = in.read();
                if (b0 < 0 || b1 < 0) return;
                int op = b0 & 0x0f;
                long len = b1 & 0x7f;
                if (len == 126) len = (in.read() << 8) | in.read();
                else if (len == 127) { len = 0; for (int i = 0; i < 8; i++) len = (len << 8) | in.read(); }
                if (len > 65536 || (b1 & 0x80) == 0) return;
                byte[] mask = new byte[4];
                for (int i = 0; i < 4; i++) mask[i] = (byte) in.read();
                byte[] p = new byte[(int) len];
                int got = 0;
                while (got < len) { int r = in.read(p, got, (int) len - got); if (r < 0) return; got += r; }
                for (int i = 0; i < p.length; i++) p[i] ^= mask[i & 3];
                if (op == 8) return;                                  // close
                if (op == 9) { wsFrame(out, 0x8A, p); continue; }     // ping -> pong
                if (op != 1) continue;
                String msg = new String(p, "UTF-8");
                for (String line : msg.split("\n")) {
                    String r;
                    try { r = Remote.exec(app, line); } catch (Exception e) { r = "err"; }
                    if (!r.equals("ok")) wsSend(out, r);
                    else if (line.startsWith("ping")) wsSend(out, "state " + Remote.state(app));
                }
            }
        } finally {
            clients.decrementAndGet();
            Bus.post("clients", clients.get());
        }
    }

    private static void wsSend(OutputStream out, String text) throws Exception { wsFrame(out, 0x81, text.getBytes("UTF-8")); }

    private static synchronized void wsFrame(OutputStream out, int b0, byte[] p) throws Exception {
        ByteArrayOutputStream f = new ByteArrayOutputStream(p.length + 4);
        f.write(b0);
        if (p.length < 126) f.write(p.length);
        else { f.write(126); f.write(p.length >> 8); f.write(p.length & 0xff); }
        f.write(p);
        out.write(f.toByteArray());
        out.flush();
    }


    /** Open-Meteo geocoding; returns {"ok":true,"list":[...]} or {"ok":false,"msg":"..."} */
    static String geo(String q) {
        if (q == null) q = "";
        q = q.trim();
        if (q.isEmpty()) return "{\"ok\":false,\"msg\":\"请输入城市名\"}";
        try {
            String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + java.net.URLEncoder.encode(q, "UTF-8")
                    + "&count=6&language=zh&format=json";
            String raw = new String(Wallpapers.get(url), "UTF-8");
            org.json.JSONObject o = new org.json.JSONObject(raw);
            if (!o.has("results")) return "{\"ok\":false,\"msg\":\"没有找到这个地方，换个名字再试\"}";
            org.json.JSONArray a = o.getJSONArray("results");
            StringBuilder b = new StringBuilder("{\"ok\":true,\"list\":[");
            int n = 0;
            for (int i = 0; i < a.length() && n < 6; i++) {
                org.json.JSONObject r = a.getJSONObject(i);
                String name = r.optString("name", "");
                String admin = r.optString("admin1", "");
                String country = r.optString("country", "");
                String label = name;
                if (!admin.isEmpty() && !admin.equals(name)) label += " · " + admin;
                if (!country.isEmpty() && !"中国".equals(country) && !"China".equals(country)) label += " · " + country;
                if (n > 0) b.append(',');
                b.append("{\"name\":\"").append(Remote.esc(name)).append("\"");
                b.append(",\"label\":\"").append(Remote.esc(label)).append("\"");
                b.append(",\"lat\":").append(r.getDouble("latitude"));
                b.append(",\"lon\":").append(r.getDouble("longitude")).append("}");
                n++;
            }
            b.append("]}");
            if (n == 0) return "{\"ok\":false,\"msg\":\"没有找到这个地方，换个名字再试\"}";
            return b.toString();
        } catch (Throwable e) {
            android.util.Log.w(Hdmi.TAG, "geo search failed", e);
            return "{\"ok\":false,\"msg\":\"搜索失败，电视现在连不上网，请稍后再试\"}";
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) { if (ch == '"' || ch == '\\') b.append('\\'); if (ch >= 0x20) b.append(ch); }
        return b.toString();
    }
}
