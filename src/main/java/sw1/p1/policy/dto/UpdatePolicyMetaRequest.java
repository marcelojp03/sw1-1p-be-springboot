package sw1.p1.policy.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePolicyMetaRequest(
        @NotBlank String name,
        String description
) {}
