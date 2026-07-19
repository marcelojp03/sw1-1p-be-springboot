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
import sw1.p1.form.application.CurrentOrganizationResolver;
import sw1.p1.policy.application.PublishedPolicyAvailabilityService;
import sw1.p1.policy.domain.*;
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
    private final NodeConfigurationRepository nodeConfigRepository;
    private final BpmnExecutionAdapter bpmnAdapter;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final WorkflowEngineService workflowEngine;
    private final CurrentOrganizationResolver organizationResolver;
    private final PublishedPolicyAvailabilityService availabilityService;

    public ProcedureResponse start(StartProcedureRequest request) {
        return startLatestForInternal(request.policyId(), request.clientId());
    }

    public ProcedureResponse startLatestForInternal(String policyId, String clientId) {
        String organizationId = organizationResolver.requireOrganizationId();
        WorkflowPolicy policy = requirePolicyForOrganization(organizationId, policyId);
        return startFromVersionForInternal(policyId, requirePublishedPointer(policy), clientId);
    }

    public ProcedureResponse startFromVersionForInternal(String policyId, String versionId,
                                                          String clientId) {
        String organizationId = organizationResolver.requireOrganizationId();
        String email = organizationResolver.requireEmail();
        String startedBy = userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new NotFoundException("Usuario autenticado no encontrado"));
        Client client = resolveClient(clientId, organizationId);
        return startAvailableVersion(policyId, versionId, organizationId, client, startedBy, "WEB");
    }

    private ProcedureResponse startAvailableVersion(String policyId, String versionId,
                                                     String organizationId, Client client,
                                                     String startedBy, String channel) {
        var available = availabilityService.requireAvailable(
                organizationId, policyId, versionId, channel);
        WorkflowPolicy policy = available.policy();
        PolicyVersion version = available.version();
        List<NodeConfiguration> configs = nodeConfigRepository.findByPolicyVersionId(versionId);
        BpmnExecutionAdapter.BpmnProcessDefinition def = bpmnAdapter.parse(version, configs);
        if (client == null && def.nodes().stream()
                .anyMatch(node -> node.getType() == sw1.p1.shared.NodeType.CLIENT_TASK)) {
            throw new BusinessException("El trámite requiere un clientId para sus CLIENT_TASK");
        }
        PolicySnapshot snapshot = PolicySnapshot.builder()
                .policyId(policyId)
                .policyKey(policy.getPolicyKey())
                .policyName(policy.getName())
                .version(version.getVersionNumber())
                .status(policy.getStatus())
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
                .clientId(client != null ? client.getId() : null)
                .startedBy(startedBy)
                .requester(toRequester(client))
                .status(ProcedureStatus.CREATED)
                .policySnapshot(snapshot)
                .startChannel(channel)
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
        if (user.getOrganizationId() == null
                || !user.getOrganizationId().equals(client.getOrganizationId())) {
            throw new NotFoundException("Cliente no encontrado para el usuario: " + email);
        }
        return startAvailableVersion(
                policyId, versionId, user.getOrganizationId(), client, user.getId(), "MOBILE");
    }

    public ProcedureResponse startLatestForClient(String policyId, String requestedClientId,
                                                    org.springframework.security.core.Authentication auth) {
        String email = auth.getName();
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + email));
        Client client = clientRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado para el usuario: " + email));
        if (requestedClientId != null && !requestedClientId.equals(client.getId())) {
            throw new BusinessException("No puede iniciar trámites en nombre de otro cliente");
        }
        if (user.getOrganizationId() == null
                || !user.getOrganizationId().equals(client.getOrganizationId())) {
            throw new NotFoundException("Cliente no encontrado para el usuario: " + email);
        }
        WorkflowPolicy policy = requirePolicyForOrganization(user.getOrganizationId(), policyId);
        return startAvailableVersion(policyId, requirePublishedPointer(policy),
                user.getOrganizationId(), client, user.getId(), "MOBILE");
    }

    private WorkflowPolicy requirePolicyForOrganization(String organizationId, String policyId) {
        return policyRepository.findById(policyId)
                .filter(policy -> organizationId.equals(policy.getOrganizationId()))
                .orElseThrow(() -> new NotFoundException("Política no encontrada: " + policyId));
    }

    private String requirePublishedPointer(WorkflowPolicy policy) {
        if (policy.getLatestPublishedVersionId() == null
                || policy.getLatestPublishedVersionId().isBlank()) {
            throw new sw1.p1.exception.ConflictException(
                    "La política publicada no tiene latestPublishedVersionId");
        }
        return policy.getLatestPublishedVersionId();
    }

    private Client resolveClient(String clientId, String organizationId) {
        if (clientId == null || clientId.isBlank()) return null;
        return clientRepository.findById(clientId)
                .filter(client -> organizationId.equals(client.getOrganizationId()))
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado: " + clientId));
    }

    private Procedure.RequesterInfo toRequester(Client client) {
        if (client == null) return null;
        return Procedure.RequesterInfo.builder()
                .fullName(client.getFullName())
                .documentType(client.getDocumentType())
                .documentNumber(client.getDocumentNumber())
                .phone(client.getPhone())
                .email(client.getEmail())
                .build();
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
