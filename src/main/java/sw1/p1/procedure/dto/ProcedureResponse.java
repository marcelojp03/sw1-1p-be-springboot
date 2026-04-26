package sw1.p1.procedure.dto;

import sw1.p1.procedure.domain.PolicySnapshot;
import sw1.p1.procedure.domain.Procedure;
import sw1.p1.shared.ProcedureStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProcedureResponse(
        String id,
        String code,
        String organizationId,
        String policyId,
        int policyVersion,
        String clientId,
        String startedBy,
        Procedure.RequesterInfo requester,
        List<String> currentNodeIds,
        ProcedureStatus status,
        PolicySnapshot policySnapshot,
        Map<String, Map<String, Object>> formData,
        String startChannel,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {}
