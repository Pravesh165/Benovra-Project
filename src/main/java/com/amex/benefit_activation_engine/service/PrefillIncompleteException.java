package com.amex.benefit_activation_engine.service;

import java.util.List;

/**
 * Raised by the PRE-FILL quality check when a generated claim is missing one or
 * more fields required for its benefit type. Guards against surfacing an
 * incomplete, un-submittable claim to the card member.
 */
public class PrefillIncompleteException extends RuntimeException {

    public PrefillIncompleteException(String benefitType, List<String> missingFields) {
        super("Pre-fill incomplete for " + benefitType
                + " claim; missing required field(s): " + missingFields);
    }
}
