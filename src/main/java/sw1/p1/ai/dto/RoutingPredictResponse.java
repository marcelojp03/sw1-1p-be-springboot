package sw1.p1.ai.dto;

import java.util.Map;

public record RoutingPredictResponse(
        String transitionId,
        Double confidence,
        Double riskScore,
        Map<String, Object> features
) {}
