package sw1.p1.ai.dto;

import java.util.List;

public record AnalyzeBottlenecksResponse(
        List<BottleneckDto> bottlenecks,
        List<String> generalRecommendations
) {
    public record BottleneckDto(
            String nodeId,
            String label,
            String severity,
            String issue,
            String recommendation
    ) {}
}
