package sw1.p1.policyrequest.dto;

import sw1.p1.policyrequest.domain.PolicyRequestStatus;

public record UpdatePolicyRequestStatusRequest(
        PolicyRequestStatus status,
        String reviewNote
) {}
