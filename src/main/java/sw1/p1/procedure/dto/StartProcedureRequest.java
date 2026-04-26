package sw1.p1.procedure.dto;

import jakarta.validation.constraints.NotBlank;

public record StartProcedureRequest(
        @NotBlank String policyId,
        @NotBlank String clientId,
        @NotBlank String organizationId
) {}
