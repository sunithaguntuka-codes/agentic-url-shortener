
package com.example.shortener;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShortUrlService {

    private final ConcurrentHashMap<String, String> urls =
            new ConcurrentHashMap<>();

    public String shorten(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "URL must not be empty");
        }

        URI uri;

        try {
            uri = URI.create(originalUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL");
        }

        String scheme = uri.getScheme();

        if (scheme == null
                || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "Enter a valid HTTP or HTTPS URL");
        }

        String code;

        do {
            code = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8);
        } while (urls.containsKey(code));

        urls.put(code, uri.toASCIIString());

        return code;
    }

    public String getOriginalUrl(String code) {
        String originalUrl = urls.get(code);

        if (originalUrl == null) {
            throw new java.util.NoSuchElementException(
                    "Short URL not found");
        }

        return originalUrl;
    }
}
