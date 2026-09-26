package com.amex.benefit_activation_engine.integration.feed;

import com.amex.benefit_activation_engine.dto.CreateTransactionRequest;
import com.amex.benefit_activation_engine.model.Transaction;
import com.amex.benefit_activation_engine.service.IngestionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the local feed drip: {@code emit()} must pull a purchase from
 * the generator and push it through the ingestion pipeline — and must emit
 * nothing while paused. Uses a recording fake so no Spring context or database
 * is needed.
 */
class LocalScheduledFeedTest {

    /** Fake IngestionService that records ingested requests and counts calls. */
    private static class RecordingIngestionService extends IngestionService {
        private CreateTransactionRequest captured;
        private int ingestCount;

        RecordingIngestionService() {
            super(null, null);
        }

        @Override
        public Transaction ingest(CreateTransactionRequest request) {
            this.captured = request;
            this.ingestCount++;
            return Transaction.builder()
                    .id(1L)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .merchantName(request.getMerchantName())
                    .cardProduct(request.getCardProduct())
                    .build();
        }
    }

    private LocalScheduledFeed feed(RecordingIngestionService ingestion, boolean startPaused) {
        return new LocalScheduledFeed(ingestion, new SamplePurchaseGenerator(), startPaused);
    }

    @Test
    void emitIngestsAGeneratedPurchase_whenRunning() {
        RecordingIngestionService ingestion = new RecordingIngestionService();
        LocalScheduledFeed feed = feed(ingestion, false);

        feed.emit();

        assertThat(ingestion.captured).isNotNull();
        assertThat(ingestion.captured.getCardProduct()).isIn("PLATINUM", "GOLD", "GREEN");
        assertThat(ingestion.ingestCount).isEqualTo(1);
        assertThat(feed.name()).isEqualTo("local-scheduled");
        assertThat(feed.isActive()).isTrue();
        assertThat(feed.isPaused()).isFalse();
    }

    @Test
    void emitDoesNothing_whilePaused() {
        RecordingIngestionService ingestion = new RecordingIngestionService();
        LocalScheduledFeed feed = feed(ingestion, false);

        feed.pause();
        feed.emit();
        feed.emit();

        assertThat(feed.isPaused()).isTrue();
        assertThat(feed.isActive()).isFalse();
        assertThat(ingestion.ingestCount).isZero();
    }

    @Test
    void resume_reenablesEmit() {
        RecordingIngestionService ingestion = new RecordingIngestionService();
        LocalScheduledFeed feed = feed(ingestion, false);

        feed.pause();
        feed.emit();          // skipped
        feed.resume();
        feed.emit();          // drips

        assertThat(feed.isPaused()).isFalse();
        assertThat(ingestion.ingestCount).isEqualTo(1);
    }

    @Test
    void startPaused_booted_doesNotEmit() {
        RecordingIngestionService ingestion = new RecordingIngestionService();
        LocalScheduledFeed feed = feed(ingestion, true);

        assertThat(feed.isPaused()).isTrue();

        feed.emit();
        assertThat(ingestion.ingestCount).isZero();
    }
}
