package sw1.p1.task.dto;

import sw1.p1.shared.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MobileTaskResponse(
        String id,
        String procedureId,
        String procedureCode,
        String policyId,
        String policyVersionId,
        String nodeId,
        String label,
        TaskStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant dueAt
) {}
