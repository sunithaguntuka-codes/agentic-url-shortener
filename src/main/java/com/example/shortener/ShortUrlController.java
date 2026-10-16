package com.example.shortener;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ShortUrlController {

    private final UrlService urlService;

    public ShortUrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/api/shorten")
    public ResponseEntity<Map<String, String>> shorten(
            @RequestBody Map<String, String> request) {

        String originalUrl = request.get("url");

        if (originalUrl == null || originalUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "URL must not be empty"));
        }

        Url url = urlService.create(originalUrl);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "originalUrl", url.getOriginalUrl(),
                        "shortCode", url.getCode(),
                        "shortUrl",
                        "http://localhost:8080/" + url.getCode()
                ));
    }
}