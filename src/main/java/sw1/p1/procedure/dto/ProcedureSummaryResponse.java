package sw1.p1.procedure.dto;

import sw1.p1.shared.ProcedureStatus;

import java.time.Instant;

public record ProcedureSummaryResponse(
        String id,
        String organizationId,
        String clientId,
        String currentNodeId,
        ProcedureStatus status,
        String policyName,
        int policyVersion,
        Instant createdAt,
        Instant updatedAt
) {}
