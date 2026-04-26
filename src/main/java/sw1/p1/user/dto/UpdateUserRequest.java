package sw1.p1.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import sw1.p1.auth.domain.Role;

import java.util.List;

public record UpdateUserRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        String phone,
        String positionName,
        @NotEmpty List<Role> roles,
        String organizationId,
        String areaId
) {}
