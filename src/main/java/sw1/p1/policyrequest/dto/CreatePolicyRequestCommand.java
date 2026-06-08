package sw1.p1.policyrequest.dto;

public record CreatePolicyRequestCommand(
        String organizationId,
        String requestText,
        String suggestedPolicyKey,
        Double confidence,
        String createdBy
) {}
