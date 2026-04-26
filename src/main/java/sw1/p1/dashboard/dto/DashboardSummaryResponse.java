package sw1.p1.dashboard.dto;

import java.util.Map;

public record DashboardSummaryResponse(
        long totalProcedures,
        Map<String, Long> proceduresByStatus,
        long pendingTasks,
        long inProgressTasks,
        long overdueTasks,
        long totalTasks
) {}
