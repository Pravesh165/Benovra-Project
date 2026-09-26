package com.amex.benefit_activation_engine.controller;

import com.amex.benefit_activation_engine.dto.FeedStatusResponse;
import com.amex.benefit_activation_engine.integration.feed.LocalScheduledFeed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime control for the demo transaction feed (WATCH-stage drip). Lets you
 * pause and resume the simulated purchase stream without restarting the app —
 * handy for keeping the screen clean while recording a demo.
 *
 * <p>The underlying {@link LocalScheduledFeed} bean only exists when
 * {@code feed.type=local-scheduled}; when it isn't, these endpoints report
 * {@code NOT_ENABLED} rather than failing.</p>
 */
@RestController
@RequestMapping("/api/admin/feed")
@RequiredArgsConstructor
@Tag(name = "Feed Admin", description = "Pause/resume the demo transaction feed at runtime")
public class FeedAdminController {

    private final ObjectProvider<LocalScheduledFeed> feedProvider;

    @Value("${feed.type:none}")
    private String feedType;

    @Value("${feed.local.interval-ms:5000}")
    private long intervalMs;

    @GetMapping
    @Operation(summary = "Feed status",
            description = "Returns whether the demo feed is RUNNING, PAUSED, or NOT_ENABLED.")
    public FeedStatusResponse status() {
        return statusOf(feedProvider.getIfAvailable());
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause the demo feed",
            description = "Stops the simulated purchase drip (no new transactions) without a restart.")
    public ResponseEntity<FeedStatusResponse> pause() {
        LocalScheduledFeed feed = feedProvider.getIfAvailable();
        if (feed == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(statusOf(null));
        }
        feed.pause();
        return ResponseEntity.ok(statusOf(feed));
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume the demo feed",
            description = "Restarts the simulated purchase drip; dripping resumes on the next tick.")
    public ResponseEntity<FeedStatusResponse> resume() {
        LocalScheduledFeed feed = feedProvider.getIfAvailable();
        if (feed == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(statusOf(null));
        }
        feed.resume();
        return ResponseEntity.ok(statusOf(feed));
    }

    private FeedStatusResponse statusOf(LocalScheduledFeed feed) {
        String state = (feed == null)
                ? FeedStatusResponse.NOT_ENABLED
                : (feed.isPaused() ? FeedStatusResponse.PAUSED : FeedStatusResponse.RUNNING);
        return new FeedStatusResponse(state, feedType, intervalMs);
    }
}
