package sw1.p1.ai.dto;

import java.util.List;

public record AnalyzeBottlenecksRequest(
        String policyName,
        List<NodeMetricDto> metrics,
        String language
) {
    public record NodeMetricDto(
            String nodeId,
            String label,
            double avgDurationHours,
            Double expectedHours,
            int pendingTasks,
            int completedTasks,
            int cancelledTasks
    ) {}
}
