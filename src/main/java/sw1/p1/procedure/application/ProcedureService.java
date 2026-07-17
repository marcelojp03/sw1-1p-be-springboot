package sw1.p1.procedure.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.client.domain.Client;
import sw1.p1.client.domain.ClientRepository;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.policy.domain.*;
import sw1.p1.policy.domain.PolicyVersionRepository;
import sw1.p1.policy.domain.NodeConfigurationRepository;
import sw1.p1.procedure.domain.*;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.procedure.dto.ProcedureSummaryResponse;
import sw1.p1.procedure.dto.StartProcedureRequest;
import sw1.p1.shared.PolicyStatus;
import sw1.p1.shared.ProcedureStatus;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcedureService {

    private final ProcedureRepository procedureRepository;
    private final ProcedureHistoryRepository historyRepository;
    private final WorkflowPolicyRepository policyRepository;
    private final PolicyVersionRepository versionRepository;
    private final NodeConfigurationRepository nodeConfigRepository;
    private final BpmnExecutionAdapter bpmnAdapter;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
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
        String startedBy = userRepository.findByEmail(currentUsername)
                .map(u -> u.getId())
                .orElse(null);

        Client client = clientRepository.findById(request.clientId()).orElse(null);
        Procedure.RequesterInfo requester = null;
        if (client != null) {
            requester = Procedure.RequesterInfo.builder()
                    .fullName(client.getFullName())
                    .documentType(client.getDocumentType())
                    .documentNumber(client.getDocumentNumber())
                    .phone(client.getPhone())
                    .email(client.getEmail())
                    .build();
        }

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

        Instant now = Instant.now();
        long sequential = procedureRepository.count() + 1;
        int year = ZonedDateTime.now(ZoneOffset.UTC).getYear();
        String code = String.format("TRM-%d-%04d", year, sequential);

        Procedure procedure = Procedure.builder()
                .code(code)
                .organizationId(request.organizationId())
                .policyId(policy.getId())
                .policyVersion(policy.getVersion())
                .clientId(request.clientId())
                .startedBy(startedBy)
                .requester(requester)
                .status(ProcedureStatus.CREATED)
                .policySnapshot(snapshot)
                .startChannel("WEB")
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        procedure = procedureRepository.save(procedure);
        workflowEngine.start(procedure, startedBy);

        // Recargar para reflejar los cambios del motor
        procedure = getOrThrow(procedure.getId());
        return toResponse(procedure);
    }

    public ProcedureResponse startFromVersion(String policyId, String versionId,
                                               String clientId, String organizationId) {
        PolicyVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Versión no encontrada: " + versionId));

        if (!version.getPolicyId().equals(policyId)) {
            throw new BusinessException("La versión no pertenece a esta política");
        }

        List<NodeConfiguration> configs = nodeConfigRepository.findByPolicyVersionId(versionId);
        BpmnExecutionAdapter.BpmnProcessDefinition def = bpmnAdapter.parse(version, configs);

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        String startedBy = userRepository.findByEmail(currentUsername)
                .map(u -> u.getId()).orElse(null);

        PolicySnapshot snapshot = PolicySnapshot.builder()
                .policyId(policyId)
                .policyKey(version.getPolicyId() + "-v" + version.getVersionNumber())
                .policyName(policyRepository.findById(policyId)
                        .map(WorkflowPolicy::getName)
                        .orElse("Política " + policyId))
                .version(version.getVersionNumber())
                .nodes(def.nodes())
                .transitions(def.transitions())
                .snapshotAt(Instant.now())
                .build();

        Instant now = Instant.now();
        long sequential = procedureRepository.count() + 1;
        int year = ZonedDateTime.now(ZoneOffset.UTC).getYear();
        String code = String.format("TRM-%d-%04d", year, sequential);

        Procedure procedure = Procedure.builder()
                .code(code)
                .organizationId(organizationId)
                .policyId(policyId)
                .policyVersionId(versionId)
                .policyVersion(version.getVersionNumber())
                .clientId(clientId)
                .startedBy(startedBy)
                .status(ProcedureStatus.CREATED)
                .policySnapshot(snapshot)
                .startChannel("WEB")
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        procedure = procedureRepository.save(procedure);
        workflowEngine.start(procedure, startedBy);
        procedure = getOrThrow(procedure.getId());
        return toResponse(procedure);
    }

    public ProcedureResponse startFromVersionForClient(String policyId, String versionId,
                                                        org.springframework.security.core.Authentication auth) {
        String email = auth.getName();
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + email));
        Client client = clientRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado para el usuario: " + email));
        return startFromVersion(policyId, versionId, client.getId(), client.getOrganizationId());
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
                p.getId(), p.getCode(), p.getOrganizationId(), p.getPolicyId(),
                p.getPolicyVersionId(), p.getPolicyVersion(),
                p.getClientId(), p.getStartedBy(), p.getRequester(), p.getCurrentNodeIds(),
                p.getStatus(), p.getPolicySnapshot(), p.getFormData(), p.getStartChannel(),
                p.getStartedAt(), p.getCompletedAt(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    private ProcedureSummaryResponse toSummary(Procedure p) {
        PolicySnapshot snap = p.getPolicySnapshot();
        return new ProcedureSummaryResponse(
                p.getId(), p.getCode(), p.getOrganizationId(), p.getClientId(),
                p.getCurrentNodeIds(), p.getStatus(),
                snap != null ? snap.getPolicyName() : null,
                snap != null ? snap.getVersion() : 0,
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
