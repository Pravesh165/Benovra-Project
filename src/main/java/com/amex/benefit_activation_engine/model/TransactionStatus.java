package com.amex.benefit_activation_engine.model;

/**
 * Processing state of an ingested purchase as it flows through the
 * WATCH -> MATCH pipeline.
 */
public enum TransactionStatus {

    /** Ingested but not yet validated. */
    RECEIVED,

    /** Passed validation and is ready for benefit matching. */
    VALIDATED,

    /** Matched to at least one benefit; a claim has been generated. */
    MATCHED,

    /** Validated but no entitled benefit applies. */
    NO_MATCH,

    /** Failed validation and will not be processed. */
    REJECTED
}
