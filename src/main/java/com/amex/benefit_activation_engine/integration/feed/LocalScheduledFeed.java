package com.amex.benefit_activation_engine.integration.feed;

import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.service.IngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demo delivery mechanism: a scheduled job that "drips" a randomized purchase
 * into the ingestion pipeline every few seconds, simulating a live stream of
 * card activity without any external infrastructure.
 *
 * <p>Active only when {@code feed.type=local-scheduled}. Interchange with
 * {@link PubSubFeed} by flipping that one flag.</p>
 *
 * <p>The drip can be paused and resumed at runtime (no restart) via an
 * in-memory flag — see {@code /api/admin/feed}. The scheduled tick keeps
 * firing while paused but simply emits nothing, so staged demo data is never
 * disturbed. The initial state is controlled by
 * {@code feed.local.start-paused} (default {@code false}).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "feed", name = "type", havingValue = "local-scheduled")
public class LocalScheduledFeed implements TransactionFeed {

    private final IngestionService ingestionService;
    private final SamplePurchaseGenerator generator;

    /** Runtime on/off switch, toggled by the admin endpoints (thread-safe). */
    private final AtomicBoolean paused;

    public LocalScheduledFeed(IngestionService ingestionService,
                              SamplePurchaseGenerator generator,
                              @Value("${feed.local.start-paused:false}") boolean startPaused) {
        this.ingestionService = ingestionService;
        this.generator = generator;
        this.paused = new AtomicBoolean(startPaused);
        log.info("LocalScheduledFeed initialised (startPaused={})", startPaused);
    }

    @Override
    public String name() {
        return "local-scheduled";
    }

    /** The feed is "active" (delivering) only when it is not paused. */
    @Override
    public boolean isActive() {
        return !paused.get();
    }

    /** Pauses the drip; the scheduled tick keeps firing but emits nothing. */
    public void pause() {
        if (paused.compareAndSet(false, true)) {
            log.info("[local-scheduled feed] paused");
        }
    }

    /** Resumes the drip; dripping restarts on the next scheduled tick. */
    public void resume() {
        if (paused.compareAndSet(true, false)) {
            log.info("[local-scheduled feed] resumed");
        }
    }

    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Emits one simulated purchase per tick unless paused. Interval and initial
     * delay are configurable via {@code feed.local.interval-ms} /
     * {@code feed.local.initial-delay-ms}.
     */
    @Scheduled(
            fixedDelayString = "${feed.local.interval-ms:5000}",
            initialDelayString = "${feed.local.initial-delay-ms:5000}")
    public void emit() {
        if (paused.get()) {
            return; // tick fired, but the drip is switched off
        }
        Transaction transaction = ingestionService.ingest(generator.next());
        log.info("[local-scheduled feed] dripped transaction {} — {} {} at {} ({})",
                transaction.getId(), transaction.getAmount(), transaction.getCurrency(),
                transaction.getMerchantName(), transaction.getCardProduct());
    }
}
