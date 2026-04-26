package sw1.p1.auth.dto;

import sw1.p1.auth.domain.Role;

import java.util.List;

public record UserResponse(
        String id,
        String username,
        String email,
        String fullName,
        List<Role> roles,
        String phone,
        String positionName,
        String organizationId,
        String areaId,
        String clientId,
        boolean enabled
) {}
