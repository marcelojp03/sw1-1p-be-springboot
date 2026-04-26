package sw1.p1.procedure.dto;

import sw1.p1.shared.ProcedureStatus;

import java.time.Instant;
import java.util.List;

public record ProcedureSummaryResponse(
        String id,
        String code,
        String organizationId,
        String clientId,
        List<String> currentNodeIds,
        ProcedureStatus status,
        String policyName,
        int policyVersion,
        Instant createdAt,
        Instant updatedAt
) {}
