package com.taskflow.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque keyset cursor: the {@code (createdAt, id)} pair of the last row of a page.
 *
 * <p>Base64 of {@code <epochMillis>:<id>} so callers treat it as a token. The id tiebreaker is
 * what makes paging stable when several jobs share a timestamp.
 */
record PageCursor(Instant createdAt, Long id) {

    static PageCursor of(Instant createdAt, Long id) {
        return new PageCursor(createdAt, id);
    }

    String encode() {
        String raw = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return null when the cursor is absent; a malformed cursor yields null too, which restarts
     *     from the first page rather than failing the request
     */
    static PageCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf(':');
            if (separator < 1) {
                return null;
            }
            return new PageCursor(
                    Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
                    Long.parseLong(raw.substring(separator + 1)));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}