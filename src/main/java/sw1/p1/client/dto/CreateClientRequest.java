package sw1.p1.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateClientRequest(
        @NotBlank String organizationId,
        @NotBlank String fullName,
        @NotBlank String documentType,
        @NotBlank String documentNumber,
        String phone,
        @Email String email,
        String address
) {}
