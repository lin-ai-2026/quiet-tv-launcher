package com.lin.magazinehome;

import java.net.InetAddress;
import java.net.URI;

/**
 * Checks for the on-TV phone page. The pairing code is still required.
 * These checks only narrow who can present that code.
 */
final class LanGuard {
    static boolean lanPeer(InetAddress address) {
        if (address == null) return false;
        return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
    }

    /**
     * Host must be an IPv4 LAN literal (or localhost) plus the real port.
     * A DNS-rebinding client would send a domain name here; numeric LAN addresses used by the QR link pass.
     * Parsed by hand so a hostile Host cannot trigger a DNS lookup.
     */
    static boolean hostAllowed(String hostHeader, int localPort) {
        if (hostHeader == null || localPort <= 0) return false;
        String hh = hostHeader.trim();
        int colon = hh.lastIndexOf(':');
        if (colon <= 0 || hh.indexOf(':') != colon) return false;
        String host = hh.substring(0, colon);
        int port;
        try { port = Integer.parseInt(hh.substring(colon + 1)); }
        catch (NumberFormatException e) { return false; }
        if (port != localPort) return false;
        if (host.equalsIgnoreCase("localhost")) return true;
        return ipv4LanLiteral(host);
    }

    /** Browsers send Origin. Missing Origin is allowed for non-browser clients that already have the code. */
    static boolean originAllowed(String origin, String hostHeader) {
        if (origin == null || origin.isEmpty()) return true;
        if ("null".equalsIgnoreCase(origin)) return false;
        if (hostHeader == null) return false;
        try {
            URI u = new URI(origin);
            if (u.getScheme() == null || !u.getScheme().equalsIgnoreCase("http")) return false;
            if (u.getHost() == null) return false;
            String hh = hostHeader.trim();
            int colon = hh.lastIndexOf(':');
            if (colon <= 0 || hh.indexOf(':') != colon) return false;
            String host = hh.substring(0, colon);
            int port = Integer.parseInt(hh.substring(colon + 1));
            int originPort = u.getPort();
            if (originPort < 0) originPort = 80;
            return u.getHost().equalsIgnoreCase(host) && originPort == port;
        } catch (Exception e) {
            return false;
        }
    }

    /** When the browser sends this header, only the phone page itself (or a direct navigation) may call in. */
    static boolean fetchSiteAllowed(String secFetchSite) {
        if (secFetchSite == null || secFetchSite.isEmpty()) return true;
        return "same-origin".equalsIgnoreCase(secFetchSite) || "none".equalsIgnoreCase(secFetchSite);
    }

    static boolean ipv4LanLiteral(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] o = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) return false;
            for (int c = 0; c < parts[i].length(); c++) {
                if (parts[i].charAt(c) < '0' || parts[i].charAt(c) > '9') return false;
            }
            if (parts[i].length() > 1 && parts[i].charAt(0) == '0') return false;
            o[i] = Integer.parseInt(parts[i]);
            if (o[i] > 255) return false;
        }
        if (o[0] == 10 || o[0] == 127) return true;
        if (o[0] == 192 && o[1] == 168) return true;
        if (o[0] == 172 && o[1] >= 16 && o[1] <= 31) return true;
        if (o[0] == 169 && o[1] == 254) return true;
        return false;
    }
}
