# Real-Time Purchase Feed (Google Pub/Sub Integration)

This is **how purchases travel into the engine in real time**. The delivery
mechanism is abstracted behind the `TransactionFeed` interface so it can be
swapped with a single configuration flag — no code changes required.

## Swappable feeds

| `feed.type`       | Implementation        | Purpose                                            |
|-------------------|-----------------------|----------------------------------------------------|
| `local-scheduled` | `LocalScheduledFeed`  | Demo: drips a simulated purchase every few seconds |
| `pubsub`          | `PubSubFeed` (stub)   | Production: subscribes to a Pub/Sub topic          |
| `none`            | *(none active)*       | Disable the feed (used in tests)                   |

Switch mechanism in `application.yml`:

```yaml
feed:
  type: local-scheduled   # <-- change to `pubsub` or `none`
  local:
    interval-ms: 5000
    initial-delay-ms: 5000
```

Whatever the source, every feed ends at the **same** call —
`IngestionService.ingest(...)` — so the matching, pre-fill, and approval stages
never know or care where a purchase came from.

## Production flow (publisher → topic → subscriber → IngestionService)

```
 publisher (card issuer / Stripe forwarder / upstream service)
      │  publishes purchase JSON
      ▼
 Google Pub/Sub topic
      │
      ▼
 subscription  ──(push or pull)──▶  PubSubFeed (subscriber)
                                        │  deserialize → CreateTransactionRequest
                                        ▼
                                 IngestionService.ingest(...)
                                        │
                          validate → normalize → save (VALIDATED)
                                        │
                              TransactionIngestedEvent  ──▶  (Phase 3 matching engine)
```

## Local demo

With the default configuration (`feed.type=local-scheduled`), just run the app:

```bash
./mvnw spring-boot:run
```

Every 5 seconds you'll see a log line like:

```
[local-scheduled feed] dripped transaction 7 — 842.50 USD at Best Electronics (PLATINUM)
```

and `GET /api/transactions` will show the stream growing in real time. Card
products are limited to PLATINUM / GOLD / GREEN so they match the seeded
entitlements.

## Making Pub/Sub real

`PubSubFeed` is a documented stub so the project builds and runs offline. To
enable real delivery:

1. Add a Pub/Sub client dependency, e.g. `spring-cloud-gcp-starter-pubsub`
   (or `google-cloud-pubsub`).
2. Configure the GCP project id, subscription name, and credentials.
3. In `PubSubFeed`, register an inbound adapter / message handler that
   deserializes each message into a `CreateTransactionRequest` and calls
   `IngestionService.ingest(...)`, then acks the message.
4. Set `feed.type=pubsub`.

Because the downstream pipeline is unchanged, going from the local demo to
production Pub/Sub is a configuration + subscriber-wiring task only.

## Notes

- Scheduling is enabled by `config/SchedulingConfig` (`@EnableScheduling`).
- Tests set `feed.type=none` (see `src/test/resources/application.properties`)
  so the scheduled drip does not insert data during the test run.
