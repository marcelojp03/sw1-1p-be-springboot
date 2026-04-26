package sw1.p1.procedure.dto;

import sw1.p1.procedure.domain.PolicySnapshot;
import sw1.p1.shared.ProcedureStatus;

import java.time.Instant;
import java.util.Map;

public record ProcedureResponse(
        String id,
        String organizationId,
        String clientId,
        String startedBy,
        String currentNodeId,
        ProcedureStatus status,
        PolicySnapshot policySnapshot,
        Map<String, Map<String, Object>> formData,
        String startChannel,
        Instant createdAt,
        Instant updatedAt
) {}
