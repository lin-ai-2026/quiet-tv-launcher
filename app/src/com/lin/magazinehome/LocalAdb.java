package com.lin.magazinehome;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;

/**
 * "Remote boost": talks the plain ADB protocol to the TV's own adbd on 127.0.0.1:5555
 * (the network debugging the user already turned on), so the phone page can send real key events
 * (`input keyevent`) and taps exactly like the remote. The first connection shows the system
 * "allow debugging" prompt once. Nothing listens or polls: the socket is opened on demand
 * and closed after 60 s idle.
 */
final class LocalAdb {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
    private static LocalAdb inst;

    static synchronized LocalAdb get(Context c) {
        if (inst == null) inst = new LocalAdb(c.getApplicationContext());
        return inst;
    }

    private final Context c;
    private Socket s;
    private DataInputStream in;
    private OutputStream out;
    private int nextId = 1;
    private long lastUse;
    volatile String status = "未连接";

    private LocalAdb(Context c) { this.c = c; }

    boolean ready() { return s != null && !s.isClosed(); }

    /** Runs a shell command, waits for it to finish (max ~4 s). Thread-safe, serialised. */
    synchronized boolean exec(String cmd) { return run(cmd) != null; }

    synchronized String run(String cmd) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (!ready()) connect(attempt == 0 ? 3000 : 25000);
                String r = shell(cmd);
                lastUse = System.currentTimeMillis();
                scheduleClose();
                return r;
            } catch (Exception e) {
                Log.w(Hdmi.TAG, "adb: " + e);
                close();
                status = "连接失败：" + e.getMessage();
            }
        }
        return null;
    }

    /** First-time pairing: waits up to 60 s for the user to tap "allow" on the TV. */
    synchronized boolean pair() {
        try { close(); connect(60000); status = "已连接"; scheduleClose(); return true; }
        catch (Exception e) { status = "未授权或未开启网络调试"; close(); return false; }
    }

    private void connect(int authWaitMs) throws Exception {
        s = new Socket();
        s.connect(new InetSocketAddress("127.0.0.1", 5555), 1500);
        s.setTcpNoDelay(true);
        s.setSoTimeout(authWaitMs);
        in = new DataInputStream(s.getInputStream());
        out = s.getOutputStream();
        send(CNXN, 0x01000000, 4096, "host::\0".getBytes("UTF-8"));
        KeyPair kp = keys();
        boolean sentSig = false;
        while (true) {
            Msg m = read();
            if (m.cmd == CNXN) { status = "已连接"; s.setSoTimeout(8000); return; }
            if (m.cmd != AUTH || m.arg0 != 1) throw new Exception("unexpected " + Integer.toHexString(m.cmd));
            if (!sentSig) {
                Cipher ci = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                ci.init(Cipher.ENCRYPT_MODE, kp.getPrivate());
                ci.update(SHA1_PREFIX);
                ci.update(m.data);
                send(AUTH, 2, 0, ci.doFinal());
                sentSig = true;
            } else {
                status = "请在电视上点「允许」";
                Bus.post("adb", status);
                send(AUTH, 3, 0, (pubKeyString((RSAPublicKey) kp.getPublic()) + "\0").getBytes("UTF-8"));
            }
        }
    }

    private String shell(String cmd) throws Exception {
        int local = nextId++;
        send(OPEN, local, 0, ("shell:" + cmd + "\0").getBytes("UTF-8"));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int remote = 0;
        long end = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < end) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == OKAY) remote = m.arg0;
            else if (m.cmd == WRTE) { o.write(m.data); send(OKAY, local, m.arg0, null); }
            else if (m.cmd == CLSE) { send(CLSE, local, m.arg0, null); return o.toString("UTF-8"); }
        }
        if (remote != 0) send(CLSE, local, remote, null);
        return o.toString("UTF-8");
    }

    private void scheduleClose() {
        final long mark = lastUse;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> new Thread(() -> {
            synchronized (LocalAdb.this) { if (lastUse == mark) close(); }
        }).start(), 60_000);
    }

    synchronized void close() {
        try { if (s != null) s.close(); } catch (Exception ignore) {}
        s = null;
    }

    private static final class Msg { int cmd, arg0, arg1; byte[] data; }

    private Msg read() throws Exception {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt(); m.arg0 = b.getInt(); m.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > 1 << 20) throw new Exception("bad length");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    private void send(int cmd, int a0, int a1, byte[] data) throws Exception {
        int len = data == null ? 0 : data.length, sum = 0;
        if (data != null) for (byte x : data) sum += x & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(a0).putInt(a1).putInt(len).putInt(sum).putInt(cmd ^ 0xffffffff);
        if (data != null) b.put(data);
        out.write(b.array());
        out.flush();
    }

    // ---- key pair, stored privately in the app's files dir
    private KeyPair keys() throws Exception {
        File pri = new File(c.getFilesDir(), "adbkey"), pub = new File(c.getFilesDir(), "adbkey.pub");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (pri.exists() && pub.exists()) {
            return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readAll(pub))),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(pri))));
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        try (FileOutputStream o = new FileOutputStream(pri)) { o.write(kp.getPrivate().getEncoded()); }
        try (FileOutputStream o = new FileOutputStream(pub)) { o.write(kp.getPublic().getEncoded()); }
        return kp;
    }

    private static byte[] readAll(File f) throws Exception {
        byte[] b = new byte[(int) f.length()];
        try (DataInputStream d = new DataInputStream(new FileInputStream(f))) { d.readFully(b); }
        return b;
    }

    /** Android's adb public key format (mincrypt RSAPublicKey, little endian) + " name". */
    private static String pubKeyString(RSAPublicKey k) {
        BigInteger n = k.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.remainder(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);
        b.putInt(n0inv.intValue());
        b.put(le(n, 256));
        b.put(le(rr, 256));
        b.putInt(k.getPublicExponent().intValue());
        return Base64.encodeToString(b.array(), Base64.NO_WRAP) + " magazinehome@tv";
    }

    private static byte[] le(BigInteger v, int len) {
        byte[] be = v.toByteArray(), o = new byte[len];
        for (int i = 0; i < len && i < be.length; i++) o[i] = be[be.length - 1 - i];
        return o;
    }
}
