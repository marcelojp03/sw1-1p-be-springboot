package sw1.p1.policy.dto;

import sw1.p1.policy.domain.Swimlane;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.policy.domain.WorkflowTransition;
import sw1.p1.shared.PolicyStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PolicyResponse(
        String id,
        String organizationId,
        String policyKey,
        String name,
        String description,
        int version,
        PolicyStatus status,
        List<String> allowedStartChannels,
        Map<String, Object> diagram,
        List<WorkflowNode> nodes,
        List<WorkflowTransition> transitions,
        List<Swimlane> swimlanes,
        String createdBy,
        String publishedBy,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {}
