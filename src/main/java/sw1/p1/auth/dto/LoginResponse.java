package sw1.p1.auth.dto;

import sw1.p1.auth.domain.Role;

import java.util.List;

public record LoginResponse(
        String token,
        String userId,
        String username,
        String email,
        String fullName,
        List<Role> roles
) {}
