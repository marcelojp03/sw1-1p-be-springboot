package sw1.p1.ai.dto;

public record IdentifyPolicyResponse(
        String policyKey,
        String policyId,
        Double confidence,
        String suggestion
) {}
