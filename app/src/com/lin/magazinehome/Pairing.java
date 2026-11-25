package com.lin.magazinehome;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * LAN pairing code for the phone page.
 *
 * <p>The sanitized snapshot replaced one quoted literal in {@code Prefs.newToken} with a
 * non-functional placeholder. A secrets scanner had flagged that literal as a high-entropy
 * Base64 string. In context it was only the character table used to draw an 8-character code,
 * not an API key or password. The placeholder is not a usable key and is not kept. This class
 * is an independent implementation: eight draws from a public 32-character alphabet using
 * {@link SecureRandom}. The alphabet is not secret. Each install stores its own code in private
 * app preferences. Nothing here is a credential for an external service, and the previous
 * literal was not copied back from anywhere.
 */
final class Pairing {
    /** Unambiguous public alphabet: no 0/O or 1/I. Not a secret. */
    static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final int LENGTH = 8;

    static String newCode(SecureRandom random) {
        StringBuilder b = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) b.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return b.toString();
    }

    /** Equal-length comparison that does not stop at the first differing byte. */
    static boolean matches(String expected, String given) {
        if (expected == null || given == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = given.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
