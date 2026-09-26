package com.amex.benefit_activation_engine.model;

/**
 * The categories of card protection benefits the engine can match a purchase to.
 */
public enum BenefitType {

    /** Covers damage or theft of an eligible item shortly after purchase. */
    PURCHASE_PROTECTION,

    /** Reimburses a purchase when the merchant refuses an otherwise valid return. */
    RETURN_PROTECTION,

    /** Reimburses expenses incurred when covered travel is delayed. */
    TRAVEL_DELAY
}
