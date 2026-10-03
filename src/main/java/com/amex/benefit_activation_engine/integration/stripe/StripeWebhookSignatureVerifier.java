package com.amex.benefit_activation_engine.integration.stripe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class StripeWebhookSignatureVerifier {

    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final String signingSecret;

    public StripeWebhookSignatureVerifier(@Value("${stripe.webhook.secret:}") String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public boolean isValid(String payload, String signatureHeader) {
        if (signingSecret == null || signingSecret.isBlank() || payload == null || signatureHeader == null) {
            return false;
        }

        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                timestamp = pair[1];
            } else if ("v1".equals(pair[0]) && SIGNATURE_PATTERN.matcher(pair[1]).matches()) {
                signatures.add(pair[1]);
            }
        }

        if (timestamp == null) {
            return false;
        }
        try {
            long signedAt = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (signedAt < now - TIMESTAMP_TOLERANCE_SECONDS || signedAt > now + TIMESTAMP_TOLERANCE_SECONDS) {
                return false;
            }
            byte[] expected = signatureBytes(timestamp, payload);
            boolean valid = false;
            for (String signature : signatures) {
                valid |= MessageDigest.isEqual(expected, HEX_FORMAT.parseHex(signature));
            }
            return valid;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] signatureBytes(String timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available", ex);
        }
    }
}
