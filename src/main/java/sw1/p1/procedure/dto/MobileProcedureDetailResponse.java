package sw1.p1.procedure.dto;

import sw1.p1.shared.ProcedureStatus;

import java.time.Instant;

public record MobileProcedureDetailResponse(
        String id,
        String code,
        String policyId,
        String policyVersionId,
        String policyName,
        ProcedureStatus status,
        String currentNodeId,
        String currentStepLabel,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt
) {}
