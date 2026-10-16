package com.example.shortener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "short_urls")
public class Url {

    @Id
    @Column(nullable = false, unique = true, length = 7)
    private String code;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false)
    private long clicks = 0;

    public Url() {
    }

    public Url(String code, String originalUrl) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.clicks = 0;
    }

    public String getCode() {
        return code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public long getClicks() {
        return clicks;
    }

    public void recordClick() {
        this.clicks++;
    }
}