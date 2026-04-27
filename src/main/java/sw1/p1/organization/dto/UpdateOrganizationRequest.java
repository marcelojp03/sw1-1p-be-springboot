package sw1.p1.organization.dto;

public record UpdateOrganizationRequest(
        String name,
        String businessType,
        String ruc,
        String logoUrl,
        Boolean active
) {}
