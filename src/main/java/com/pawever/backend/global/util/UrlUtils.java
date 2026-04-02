package com.pawever.backend.global.util;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static String toHttpsUrl(String url) {
        if (url == null) {
            return null;
        }

        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("http://")) {
            return "https://" + trimmed.substring("http://".length());
        }
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (trimmed.startsWith("/")) {
            // Relative path: caller likely expects a path, not an absolute URL.
            return trimmed;
        }

        return "https://" + trimmed;
    }

    public static String stripScheme(String url) {
        if (url == null) {
            return null;
        }

        String trimmed = url.trim();
        if (trimmed.startsWith("https://")) {
            return trimmed.substring("https://".length());
        }
        if (trimmed.startsWith("http://")) {
            return trimmed.substring("http://".length());
        }
        return trimmed;
    }

    public static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
