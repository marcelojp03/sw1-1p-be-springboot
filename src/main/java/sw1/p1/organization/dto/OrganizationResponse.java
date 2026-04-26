package sw1.p1.organization.dto;

import java.util.List;

public record OrganizationResponse(
        String id,
        String name,
        String businessType,
        String ruc,
        String logoUrl,
        boolean active,
        List<AreaResponse> areas
) {}
