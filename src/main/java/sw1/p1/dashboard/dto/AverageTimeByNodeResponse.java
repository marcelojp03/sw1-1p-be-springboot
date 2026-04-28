package sw1.p1.dashboard.dto;

public record AverageTimeByNodeResponse(
        String nodeId,
        String nodeLabel,
        double avgDurationHours,
        long completedCount,
        Double expectedHours
) {}
