package sw1.p1.ai.dto;

import java.util.List;
import java.util.Map;

public record IdentifyPolicyRequest(
        String text,
        String organizationId,
        List<Map<String, Object>> availablePolicies
) {}
