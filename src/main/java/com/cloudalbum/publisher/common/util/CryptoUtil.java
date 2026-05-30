package com.cloudalbum.publisher.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Simple utility for API key obfuscation using Base64 encoding.
 * This provides basic obfuscation, not cryptographic security.
 * For production use, consider upgrading to proper encryption with a key management system.
 */
public final class CryptoUtil {

    private CryptoUtil() {
    }

    /**
     * Encode a plaintext API key to Base64.
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        return Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode a Base64-encoded API key back to plaintext.
     */
    public static String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return encoded;
        }
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // If decoding fails, assume it's stored as plaintext (legacy)
            return encoded;
        }
    }

    /**
     * Mask an API key for display purposes.
     * Shows first 3 and last 3 characters with *** in between.
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 6) {
            return "***";
        }
        return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
    }
}
