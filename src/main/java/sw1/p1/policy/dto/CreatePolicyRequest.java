package sw1.p1.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreatePolicyRequest(
        @NotBlank String organizationId,
        @NotBlank String policyKey,
        @NotBlank String name,
        String description,
        @NotEmpty List<String> allowedStartChannels
) {}
