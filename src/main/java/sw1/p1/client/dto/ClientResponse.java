package sw1.p1.client.dto;

import java.time.Instant;

public record ClientResponse(
        String id,
        String organizationId,
        String fullName,
        String documentType,
        String documentNumber,
        String phone,
        String email,
        String address,
        String userId,
        String createdBy,
        Instant createdAt
) {}
