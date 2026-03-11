package com.fryrank.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CursorUtils {

    private static final String DELIMITER = "|";
    private static final int EXPECTED_PARTS = 2;

    public record CompositeCursor(String isoDateTime, String reviewId) {}

    public static String encode(final String isoDateTime, final String reviewId) {
        String raw = isoDateTime + DELIMITER + reviewId;
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static CompositeCursor decode(final String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", EXPECTED_PARTS);
            if (parts.length != EXPECTED_PARTS || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new IllegalArgumentException("Cursor does not contain required isoDateTime and reviewId parts.");
            }
            return new CompositeCursor(parts[0], parts[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid composite cursor: " + e.getMessage(), e);
        }
    }
}
