package sw1.p1.organization.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAreaRequest(
        @NotBlank String name,
        String description
) {}
