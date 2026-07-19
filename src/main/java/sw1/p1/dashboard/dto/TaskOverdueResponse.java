package sw1.p1.dashboard.dto;

import java.time.Instant;

public record TaskOverdueResponse(
        String taskId,
        String procedureId,
        String procedureCode,
        String nodeLabel,
        String assignedDepartmentId,
        String assignedUserId,
        Instant dueAt,
        Instant createdAt,
        long overdueDays
) {}
