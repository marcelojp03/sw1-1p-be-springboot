package sw1.p1.task.dto;

import sw1.p1.policy.domain.FormDefinition;
import sw1.p1.shared.TaskAudience;
import sw1.p1.shared.TaskStatus;
import sw1.p1.shared.storage.AttachmentRef;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TaskResponse(
        String id,
        String procedureId,
        String procedureCode,
        String policyId,
        String policyVersionId,
        String nodeId,
        String label,
        String organizationId,
        String assignedDepartmentId,
        TaskAudience taskAudience,
        TaskStatus status,
        String assignedUserId,
        String assignedClientId,
        String formVersionId,
        FormDefinition form,
        Map<String, Object> formResponse,
        String notes,
        String completedBy,
        List<AttachmentRef> attachments,
        Instant createdAt,
        Instant startedAt,
        Instant dueAt,
        Instant completedAt
) {}
