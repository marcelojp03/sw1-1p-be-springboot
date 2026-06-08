package sw1.p1.policyrequest.dto;

import sw1.p1.policyrequest.domain.PolicyRequestStatus;

import java.time.Instant;

public record PolicyRequestResponse(
        String id,
        String organizationId,
        String requestText,
        String suggestedPolicyKey,
        Double confidence,
        PolicyRequestStatus status,
        Instant createdAt,
        String createdBy,
        String reviewedBy,
        Instant reviewedAt,
        String reviewNote
) {}
