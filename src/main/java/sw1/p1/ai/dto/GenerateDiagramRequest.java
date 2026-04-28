package sw1.p1.ai.dto;

import java.util.List;

public record GenerateDiagramRequest(
        String organizationName,
        String policyName,
        String policyDescription,
        List<String> areas,
        String language
) {}
