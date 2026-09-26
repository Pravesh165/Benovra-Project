# Stripe Issuing Integration

This is **where purchases come from**. Stripe Issuing emits a webhook event for
every card authorization; we accept those events, translate them into the
engine's internal transaction shape, and push them through the same ingestion
pipeline as the manual feed.

## Flow

```
Stripe Issuing  ──(issuing_authorization.created)──▶  POST /api/stripe/webhook
                                                            │
                                        StripeTransactionAdapter (maps fields, MCC→category)
                                                            │
                                        IngestionService.ingest()  ← same pipeline as manual feed
                                                            │
                                   validate → normalize → save (VALIDATED) → TransactionIngestedEvent
```

The webhook is the only Stripe-aware code on the write path. Everything after
`IngestionService.ingest()` is unchanged, whether the transaction came from
Stripe or the manual `POST /api/transactions` feed.

## Field mapping

| Stripe field                                   | Internal `Transaction` field | Notes                                        |
|------------------------------------------------|------------------------------|----------------------------------------------|
| `data.object.card.cardholder.id`               | `cardMemberId`               |                                              |
| `data.object.card.metadata.card_product`       | `cardProduct`                | Normalized to upper-case on ingest           |
| `data.object.merchant_data.name`               | `merchantName`               |                                              |
| `data.object.merchant_data.category_code` (MCC)| `merchantCategory`           | Mapped via `MccCategoryMapper` (e.g. 5732 → ELECTRONICS); falls back to `merchant_data.category`, then `OTHER` |
| `data.object.amount` (minor units / cents)     | `amount`                     | Divided by 100 (e.g. 49999 → 499.99)         |
| `data.object.currency`                         | `currency`                   | Normalized to upper-case on ingest           |
| `data.object.created` (epoch seconds)          | `purchaseDate`               | Converted to `LocalDate` (system zone)       |

Unknown Stripe fields are ignored (`@JsonIgnoreProperties(ignoreUnknown = true)`),
so real Stripe payloads can be posted as-is.

## Try it offline

A realistic sample lives at `samples/stripe-issuing-authorization.json`. With the
app running (`./mvnw spring-boot:run`):

```bash
curl -X POST http://localhost:8080/api/stripe/webhook \
  -H "Content-Type: application/json" \
  --data @samples/stripe-issuing-authorization.json
```

Expected: `201 Created` with the ingested transaction — `cardProduct: PLATINUM`,
`merchantCategory: ELECTRONICS`, `amount: 499.99`, `currency: USD`,
`status: VALIDATED`.

Events whose `type` is not an `issuing_authorization*` event are acknowledged
with `200 OK` and ignored:

```json
{ "received": true, "processed": false, "type": "issuing_card.created" }
```

## Test mode → live: zero downstream changes

Going live does **not** touch the adapter, the mapping, or the pipeline. Only
configuration changes:

1. Point a real Stripe **test-mode** webhook at `/api/stripe/webhook` (via the
   Stripe CLI `stripe listen --forward-to` or the dashboard). The exact same
   JSON shape is delivered, so the adapter already handles it.
2. To go live, swap the Stripe **test key for the live key** and register the
   live webhook endpoint. No code on the ingestion path changes.

## Security (before real deployment)

⚠️ For the offline demo this endpoint **does not verify the `Stripe-Signature`
header** and is unauthenticated. Before any real deployment you must:

- Verify the webhook signature using the endpoint's signing secret
  (`whsec_...`) to ensure events genuinely originate from Stripe.
- Restrict/authenticate access to the endpoint.
- Consider idempotency on the Stripe event `id` to avoid double-processing
  retried deliveries.
