package sw1.p1.procedure.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.policy.domain.WorkflowPolicy;
import sw1.p1.policy.domain.WorkflowPolicyRepository;
import sw1.p1.procedure.domain.*;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.procedure.dto.ProcedureSummaryResponse;
import sw1.p1.procedure.dto.StartProcedureRequest;
import sw1.p1.shared.PolicyStatus;
import sw1.p1.shared.ProcedureStatus;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcedureService {

    private final ProcedureRepository procedureRepository;
    private final ProcedureHistoryRepository historyRepository;
    private final WorkflowPolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final WorkflowEngineService workflowEngine;

    public ProcedureResponse start(StartProcedureRequest request) {
        WorkflowPolicy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new NotFoundException("Política no encontrada: " + request.policyId()));

        if (policy.getStatus() != PolicyStatus.PUBLISHED) {
            throw new BusinessException("Solo se pueden iniciar trámites con políticas PUBLISHED");
        }
        if (!policy.getAllowedStartChannels().contains("WEB")) {
            throw new BusinessException("Esta política no permite iniciar trámites por canal WEB");
        }

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        String startedBy = userRepository.findByUsername(currentUsername)
                .map(u -> u.getId())
                .orElse(null);

        PolicySnapshot snapshot = PolicySnapshot.builder()
                .policyId(policy.getId())
                .policyKey(policy.getPolicyKey())
                .policyName(policy.getName())
                .version(policy.getVersion())
                .status(policy.getStatus())
                .nodes(policy.getNodes())
                .transitions(policy.getTransitions())
                .snapshotAt(Instant.now())
                .build();

        Procedure procedure = Procedure.builder()
                .organizationId(request.organizationId())
                .clientId(request.clientId())
                .startedBy(startedBy)
                .status(ProcedureStatus.CREATED)
                .policySnapshot(snapshot)
                .startChannel("WEB")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        procedure = procedureRepository.save(procedure);
        workflowEngine.start(procedure, startedBy);

        // Recargar para reflejar los cambios del motor
        procedure = getOrThrow(procedure.getId());
        return toResponse(procedure);
    }

    public Page<ProcedureSummaryResponse> findByOrganization(String organizationId, Pageable pageable) {
        return procedureRepository.findByOrganizationId(organizationId, pageable)
                .map(this::toSummary);
    }

    public Page<ProcedureSummaryResponse> findByOrganizationAndStatus(
            String organizationId, ProcedureStatus status, Pageable pageable) {
        return procedureRepository.findByOrganizationIdAndStatus(organizationId, status, pageable)
                .map(this::toSummary);
    }

    public ProcedureResponse findById(String id) {
        return toResponse(getOrThrow(id));
    }

    public List<ProcedureHistory> getHistory(String procedureId) {
        // Verificar que el trámite existe
        if (!procedureRepository.existsById(procedureId)) {
            throw new NotFoundException("Trámite no encontrado: " + procedureId);
        }
        return historyRepository.findByProcedureIdOrderByOccurredAtAsc(procedureId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Procedure getOrThrow(String id) {
        return procedureRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Trámite no encontrado: " + id));
    }

    private ProcedureResponse toResponse(Procedure p) {
        return new ProcedureResponse(
                p.getId(), p.getOrganizationId(), p.getClientId(), p.getStartedBy(),
                p.getCurrentNodeId(), p.getStatus(), p.getPolicySnapshot(),
                p.getFormData(), p.getStartChannel(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    private ProcedureSummaryResponse toSummary(Procedure p) {
        PolicySnapshot snap = p.getPolicySnapshot();
        return new ProcedureSummaryResponse(
                p.getId(), p.getOrganizationId(), p.getClientId(),
                p.getCurrentNodeId(), p.getStatus(),
                snap != null ? snap.getPolicyName() : null,
                snap != null ? snap.getVersion() : 0,
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
