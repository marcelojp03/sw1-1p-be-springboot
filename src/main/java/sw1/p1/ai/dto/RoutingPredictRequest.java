package sw1.p1.ai.dto;

import java.util.Map;

public record RoutingPredictRequest(
        String procedureId,
        String currentNodeId,
        Map<String, Object> formData,
        Map<String, Object> policySnapshot
) {}
