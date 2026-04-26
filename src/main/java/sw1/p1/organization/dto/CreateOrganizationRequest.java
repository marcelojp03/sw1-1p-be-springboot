package sw1.p1.organization.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrganizationRequest(
        @NotBlank String name,
        String description
) {}
