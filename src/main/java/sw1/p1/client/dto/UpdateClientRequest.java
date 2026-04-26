package sw1.p1.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateClientRequest(
        @NotBlank String fullName,
        String phone,
        @Email String email,
        String address
) {}
