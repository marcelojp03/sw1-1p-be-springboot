package sw1.p1.auth.dto;

import sw1.p1.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String fullName,
        @NotEmpty List<Role> roles,
        String phone,
        String positionName,
        String organizationId,
        String areaId
) {}
