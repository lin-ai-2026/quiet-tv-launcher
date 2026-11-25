package com.lin.magazinehome;

import java.net.InetAddress;
import java.security.SecureRandom;

/** Host-side checks for pure helpers. Not an Android instrumentation test and not a device test. */
public class HostCheck {
    private static int failed;

    public static void main(String[] args) throws Exception {
        pairing();
        lan();
        text();
        solar();
        if (failed != 0) {
            System.err.println(failed + " host check(s) failed");
            System.exit(1);
        }
        System.out.println("host checks ok");
    }

    private static void pairing() {
        SecureRandom r = new SecureRandom();
        for (int n = 0; n < 40; n++) {
            String code = Pairing.newCode(r);
            eq(code.length(), Pairing.LENGTH, "code length");
            for (int i = 0; i < code.length(); i++) {
                check(Pairing.ALPHABET.indexOf(code.charAt(i)) >= 0, "alphabet");
            }
            check(Pairing.matches(code, new String(code.toCharArray())), "match copy");
            check(!Pairing.matches(code, code.substring(1) + "2"), "mismatch");
        }
        check(!Pairing.matches(null, "ABCDEFGH"), "null expected");
        check(!Pairing.matches("ABCDEFGH", null), "null given");
        check(!Pairing.matches("ABC", "ABCD"), "length");
        check(!Pairing.ALPHABET.contains("0") && !Pairing.ALPHABET.contains("1"), "ambiguous digits omitted");
        check(!Pairing.ALPHABET.contains("REDACTED"), "placeholder is not the alphabet");
    }

    private static void lan() throws Exception {
        check(LanGuard.lanPeer(InetAddress.getByName("127.0.0.1")), "loopback");
        check(LanGuard.lanPeer(InetAddress.getByName("192.168.1.20")), "rfc1918");
        check(LanGuard.lanPeer(InetAddress.getByName("10.1.2.3")), "ten");
        check(!LanGuard.lanPeer(InetAddress.getByName("8.8.8.8")), "public");
        check(!LanGuard.lanPeer(null), "null peer");
        check(LanGuard.hostAllowed("192.168.1.20:8686", 8686), "host lan");
        check(LanGuard.hostAllowed("10.0.0.8:8686", 8686), "host ten");
        check(LanGuard.hostAllowed("127.0.0.1:8686", 8686), "host loop");
        check(LanGuard.hostAllowed("169.254.3.4:8686", 8686), "host link");
        check(LanGuard.hostAllowed("172.16.0.1:8686", 8686), "host 172");
        check(LanGuard.hostAllowed("localhost:8686", 8686), "localhost");
        check(!LanGuard.hostAllowed("172.32.0.1:8686", 8686), "172 out of range");
        check(!LanGuard.hostAllowed("8.8.8.8:8686", 8686), "public host");
        check(!LanGuard.hostAllowed("evil.example:8686", 8686), "dns rebinding name");
        check(!LanGuard.hostAllowed("192.168.1.20:8687", 8686), "wrong port");
        check(!LanGuard.hostAllowed("192.168.001.20:8686", 8686), "leading zero");
        check(!LanGuard.hostAllowed(null, 8686), "null host");
        check(LanGuard.originAllowed(null, "192.168.1.20:8686"), "no origin");
        check(LanGuard.originAllowed("", "192.168.1.20:8686"), "empty origin");
        check(!LanGuard.originAllowed("null", "192.168.1.20:8686"), "opaque origin");
        check(LanGuard.originAllowed("http://192.168.1.20:8686", "192.168.1.20:8686"), "same origin");
        check(!LanGuard.originAllowed("http://evil.example:8686", "192.168.1.20:8686"), "cross origin");
        check(!LanGuard.originAllowed("https://192.168.1.20:8686", "192.168.1.20:8686"), "https origin");
        check(LanGuard.fetchSiteAllowed(null), "no fetch site");
        check(LanGuard.fetchSiteAllowed("same-origin"), "same origin site");
        check(LanGuard.fetchSiteAllowed("none"), "navigate");
        check(!LanGuard.fetchSiteAllowed("cross-site"), "cross site");
    }

    private static void text() {
        eq(Logic.clean(" <b>你好</b>&nbsp;&amp;  x "), "你好 & x", "clean");
        eq(Logic.clean(null), "", "clean null");
        eq(Logic.formatPlaque("2020-08-01 12:00:00", "杭州"), "2020年8月1日  ·  杭州", "plaque");
        eq(Logic.formatPlaque("2020:08:01", ""), "2020年8月1日", "plaque colon");
        eq(Logic.formatPlaque("", "杭州"), "杭州", "plaque loc");
        check(Logic.httpUrl("https://cn.bing.com/HPImageArchive.aspx?format=js"), "https");
        check(Logic.httpUrl("http://ip-api.com/json/"), "http");
        check(!Logic.httpUrl("file:///etc/passwd"), "file");
        check(!Logic.httpUrl("javascript:alert(1)"), "javascript");
        check(!Logic.httpUrl("http:///etc/passwd"), "empty host");
        check(!Logic.httpUrl(null), "null url");
    }

    private static void solar() {
        check(Logic.crossed(10, 20, 15) && Logic.crossed(10, 20, 10) && !Logic.crossed(10, 20, 20), "crossed");
        check(Logic.crossed(350, 10, 355) && Logic.crossed(350, 10, 5) && !Logic.crossed(350, 10, 20), "wrap");
        double lon = Logic.solarLon(946728000000L);
        check(lon >= 0 && lon < 360, "lon range");
        check(Logic.solarLon(946728000000L) == lon, "lon stable");
        String term = Logic.solarTerm(new java.util.Date(946728000000L));
        check(term != null, "term");
    }

    private static void check(boolean ok, String name) {
        if (!ok) {
            failed++;
            System.err.println("FAIL " + name);
        }
    }

    private static void eq(Object actual, Object expected, String name) {
        if (actual == null ? expected != null : !actual.equals(expected)) {
            failed++;
            System.err.println("FAIL " + name + " actual=" + actual + " expected=" + expected);
        }
    }

    private static void eq(int actual, int expected, String name) {
        if (actual != expected) {
            failed++;
            System.err.println("FAIL " + name + " actual=" + actual + " expected=" + expected);
        }
    }
}
