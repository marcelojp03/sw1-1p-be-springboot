package sw1.p1.policy.dto;

import sw1.p1.shared.PolicyStatus;

import java.time.Instant;

/** Respuesta resumida para listados */
public record PolicySummaryResponse(
        String id,
        String policyKey,
        String name,
        int version,
        PolicyStatus status,
        java.util.List<String> allowedStartChannels,
        Instant updatedAt
) {}
