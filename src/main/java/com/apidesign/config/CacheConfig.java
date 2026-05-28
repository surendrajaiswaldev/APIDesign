package com.apidesign.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache abstraction backed by Caffeine. Used on read-heavy product queries via
 * {@code @Cacheable} / {@code @CacheEvict} annotations in {@link com.apidesign.service.ProductService}.
 *
 * <p>Both caches share the same spec — 10k entries max, 10 minute TTL after write, with
 * stats recorded so they can be scraped from {@code /actuator/metrics/cache.*}. Pre-registering
 * cache names disables Spring's lazy creation and lets a cache hit before the first write
 * still return {@code null}-as-miss correctly.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS_CACHE = "products";
    public static final String PRODUCTS_BY_CATEGORY_CACHE = "productsByCategory";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(PRODUCTS_CACHE, PRODUCTS_BY_CATEGORY_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats());
        // Null values would be cached as misses and confuse downstream callers — refuse them.
        manager.setAllowNullValues(false);
        return manager;
    }
}
