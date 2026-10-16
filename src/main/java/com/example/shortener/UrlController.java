package com.example.shortener;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class UrlController {

    private final UrlService service;

    public UrlController(UrlService service) {
        this.service = service;
    }

    @PostMapping("/api/links")
    public LinkResponse create(
            @Valid @RequestBody CreateLinkRequest request) {

        Url url = service.create(request.url());

        return new LinkResponse(
                url.getCode(),
                url.getOriginalUrl(),
                "http://localhost:8080/" + url.getCode()
        );
    }

    @GetMapping("/{code}")
    public RedirectView redirect(@PathVariable String code) {
        Url url = service.recordClick(code);

        RedirectView view = new RedirectView(url.getOriginalUrl());
        view.setStatusCode(HttpStatus.FOUND);

        return view;
    }

    @GetMapping("/api/links/{code}")
    public LinkResponse details(@PathVariable String code) {
        Url url = service.find(code);

        return new LinkResponse(
                url.getCode(),
                url.getOriginalUrl(),
                url.getClicks()
        );
    }

    @ExceptionHandler(UrlService.LinkNotFoundException.class)
    public ResponseEntity<String> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Short link not found");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(
            IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    public record CreateLinkRequest(@NotBlank String url) {
    }

    public record LinkResponse(
            String code,
            String originalUrl,
            Object shortUrlOrClicks) {
    }
}