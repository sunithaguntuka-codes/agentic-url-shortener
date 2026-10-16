package com.example.shortener;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;

@Service
public class UrlService {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UrlRepository repository;

    public UrlService(UrlRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Url create(String rawUrl) {
        URI uri;

        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "URL must be a valid HTTP or HTTPS URL");
        }

        String scheme = uri.getScheme();

        if (scheme == null
                || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "URL must be a valid HTTP or HTTPS URL");
        }

        String code;

        do {
            code = randomCode(7);
        } while (repository.existsById(code));

        Url url = new Url(code, uri.toString());

        return repository.save(url);
    }

    @Transactional(readOnly = true)
    public Url find(String code) {
        return repository.findById(code)
                .orElseThrow(LinkNotFoundException::new);
    }

    @Transactional
    public Url recordClick(String code) {
        Url url = repository.findById(code)
                .orElseThrow(LinkNotFoundException::new);

        url.recordClick();

        return repository.save(url);
    }

    private String randomCode(int length) {
        StringBuilder result = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            result.append(
                    ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }

        return result.toString();
    }

    public static final class LinkNotFoundException
            extends RuntimeException {
    }
}