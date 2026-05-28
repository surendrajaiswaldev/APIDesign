package com.apidesign.controller;

import com.apidesign.dto.ratelimit.BucketSnapshot;
import com.apidesign.dto.ratelimit.RateLimitStatusResponse;
import com.apidesign.ratelimit.RateLimitFilter;
import com.apidesign.response.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rate-limit introspection endpoints.
 *
 * <ul>
 *   <li>{@code GET /rate-limit/status} — caller's own current bucket; no auth required.</li>
 *   <li>{@code GET /admin/rate-limit/buckets} — paginated list of every cached bucket;
 *       admin only (gated by {@code @PreAuthorize("hasRole('ADMIN')")}, which is bypassed
 *       when {@code app.security.jwt.enabled=false} because {@code MethodSecurityConfig}
 *       isn't active in that mode).</li>
 * </ul>
 *
 * Both endpoints are listed in {@link RateLimitFilter}'s skip patterns so calling them
 * never consumes a token.
 */
@RestController
@RequestMapping
public class RateLimitController {

    private final RateLimitFilter rateLimitFilter;

    public RateLimitController(RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
    }

    @GetMapping("/rate-limit/status")
    public ResponseEntity<RateLimitStatusResponse> status(HttpServletRequest request) {
        return ResponseEntity.ok(rateLimitFilter.inspect(request));
    }

    @GetMapping("/admin/rate-limit/buckets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<BucketSnapshot>> listBuckets(
        @PageableDefault(size = 50) Pageable pageable) {
        Page<BucketSnapshot> page = rateLimitFilter.listBuckets(pageable);
        return ResponseEntity.ok(PagedResponse.from(page));
    }
}
