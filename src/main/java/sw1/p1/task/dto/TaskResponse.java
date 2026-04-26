package sw1.p1.task.dto;

import sw1.p1.policy.domain.FormDefinition;
import sw1.p1.shared.TaskAudience;
import sw1.p1.shared.TaskStatus;

import java.time.Instant;
import java.util.Map;

public record TaskResponse(
        String id,
        String procedureId,
        String nodeId,
        String nodeLabel,
        String organizationId,
        String areaId,
        TaskAudience taskAudience,
        TaskStatus status,
        String assignedOfficerId,
        String assignedClientId,
        FormDefinition form,
        Map<String, Object> formResponse,
        String notes,
        Instant createdAt,
        Instant completedAt
) {}
