package com.amex.benefit_activation_engine.integration.feed;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production delivery mechanism (STUB): a Google Cloud Pub/Sub subscriber that
 * receives purchase messages published to a topic and feeds them into the
 * ingestion pipeline.
 *
 * <p>Active only when {@code feed.type=pubsub}. This class is intentionally a
 * documented stub so the project stays offline-friendly — no GCP credentials or
 * network access are required to build and run the demo.</p>
 *
 * <h3>Production wiring</h3>
 * <pre>
 *   publisher (issuer / Stripe forwarder)
 *        │  publishes purchase JSON
 *        ▼
 *   Pub/Sub topic  ──▶  subscription
 *        │  push/pull delivery
 *        ▼
 *   PubSubFeed (this subscriber)
 *        │  deserialize message → CreateTransactionRequest
 *        ▼
 *   IngestionService.ingest(...)   ← identical downstream pipeline
 * </pre>
 *
 * To make this real:
 * <ol>
 *   <li>Add the {@code spring-cloud-gcp-starter-pubsub} (or
 *       {@code google-cloud-pubsub}) dependency.</li>
 *   <li>Configure the GCP project, subscription name, and credentials.</li>
 *   <li>Register a message handler that deserializes the payload and calls
 *       {@code IngestionService.ingest(...)} — the same method the local feed
 *       and Stripe webhook already use, so nothing downstream changes.</li>
 *   <li>Set {@code feed.type=pubsub}.</li>
 * </ol>
 *
 * @see docs/pubsub-integration.md
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "feed", name = "type", havingValue = "pubsub")
public class PubSubFeed implements TransactionFeed {

    @PostConstruct
    void warnStub() {
        log.warn("PubSubFeed is active but STUBBED — no messages will be received. "
                + "Add the Pub/Sub client dependency and implement the subscriber to enable "
                + "real delivery. See docs/pubsub-integration.md.");
    }

    @Override
    public String name() {
        return "pubsub";
    }

    @Override
    public boolean isActive() {
        // A real subscriber would report its subscription health here.
        return false;
    }

    // Production sketch (pseudocode):
    //
    // @Bean
    // public PubSubInboundChannelAdapter inboundAdapter(PubSubTemplate template) { ... }
    //
    // @ServiceActivator(inputChannel = "purchasesInputChannel")
    // public void onMessage(String payload, @Header(...) BasicAcknowledgeablePubsubMessage msg) {
    //     CreateTransactionRequest request = objectMapper.readValue(payload, CreateTransactionRequest.class);
    //     ingestionService.ingest(request);
    //     msg.ack();
    // }
}
