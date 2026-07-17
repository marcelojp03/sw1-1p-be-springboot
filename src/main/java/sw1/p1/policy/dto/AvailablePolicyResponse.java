package sw1.p1.policy.dto;

import java.util.List;

public record AvailablePolicyResponse(
        String id,
        String policyKey,
        String name,
        String description,
        String policyVersionId,
        int version,
        List<String> allowedStartChannels
) {}
