package sw1.p1.policy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.policy.domain.WorkflowPolicy;
import sw1.p1.policy.domain.WorkflowPolicyRepository;
import sw1.p1.policy.dto.*;
import sw1.p1.shared.NodeType;import sw1.p1.shared.PolicyStatus;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final WorkflowPolicyRepository policyRepository;
    private final UserRepository userRepository;

    // ── CRUD básico ──────────────────────────────────────────────────────────

    public PolicyResponse create(CreatePolicyRequest request) {
        // No puede haber otra política DRAFT con la misma clave en la misma org
        if (policyRepository.existsByOrganizationIdAndPolicyKeyAndStatus(
                request.organizationId(), request.policyKey(), PolicyStatus.DRAFT)) {
            throw new ConflictException(
                    "Ya existe un borrador para la clave '" + request.policyKey() + "'");
        }

        String createdBy = currentUserId();

        WorkflowPolicy policy = WorkflowPolicy.builder()
                .organizationId(request.organizationId())
                .policyKey(request.policyKey())
                .name(request.name())
                .description(request.description())
                .version(nextVersion(request.organizationId(), request.policyKey()))
                .status(PolicyStatus.DRAFT)
                .allowedStartChannels(request.allowedStartChannels())
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return toResponse(policyRepository.save(policy));
    }

    public Page<PolicySummaryResponse> findByOrganization(String organizationId, PolicyStatus status, Pageable pageable) {
        if (status != null) {
            return policyRepository.findByOrganizationIdAndStatus(organizationId, status, pageable)
                    .map(this::toSummary);
        }
        return policyRepository.findByOrganizationId(organizationId, pageable)
                .map(this::toSummary);
    }

    public PolicyResponse findById(String id) {
        return toResponse(getOrThrow(id));
    }

    // ── Diagrama ─────────────────────────────────────────────────────────────

    public PolicyResponse updateDiagram(String id, DiagramUpdateRequest request) {
        WorkflowPolicy policy = getOrThrow(id);
        requireDraft(policy);

        policy.setDiagram(request.diagram());
        policy.setNodes(request.nodes());
        policy.setTransitions(request.transitions());
        policy.setSwimlanes(request.swimlanes());
        policy.setUpdatedAt(Instant.now());

        return toResponse(policyRepository.save(policy));
    }

    // ── Publicar ─────────────────────────────────────────────────────────────

    public PolicyResponse publish(String id) {
        WorkflowPolicy policy = getOrThrow(id);
        requireDraft(policy);
        validateStructure(policy);

        // Archivar la versión PUBLISHED anterior si existe
        policyRepository.findByOrganizationIdAndPolicyKeyAndStatus(
                        policy.getOrganizationId(), policy.getPolicyKey(), PolicyStatus.PUBLISHED)
                .ifPresent(prev -> {
                    prev.setStatus(PolicyStatus.ARCHIVED);
                    prev.setUpdatedAt(Instant.now());
                    policyRepository.save(prev);
                });

        String publishedBy = currentUserId();
        policy.setStatus(PolicyStatus.PUBLISHED);
        policy.setPublishedBy(publishedBy);
        policy.setPublishedAt(Instant.now());
        policy.setUpdatedAt(Instant.now());

        return toResponse(policyRepository.save(policy));
    }

    /** Crear nueva versión DRAFT a partir de la versión más reciente de una policyKey */
    public PolicyResponse createNewVersion(String organizationId, String policyKey) {
        WorkflowPolicy latest = policyRepository
                .findTopByOrganizationIdAndPolicyKeyOrderByVersionDesc(organizationId, policyKey)
                .orElseThrow(() -> new NotFoundException(
                        "No existe ninguna política con clave: " + policyKey));

        if (policyRepository.existsByOrganizationIdAndPolicyKeyAndStatus(
                organizationId, policyKey, PolicyStatus.DRAFT)) {
            throw new ConflictException("Ya existe un borrador para esta política");
        }

        WorkflowPolicy newVersion = WorkflowPolicy.builder()
                .organizationId(latest.getOrganizationId())
                .policyKey(latest.getPolicyKey())
                .name(latest.getName())
                .description(latest.getDescription())
                .version(latest.getVersion() + 1)
                .status(PolicyStatus.DRAFT)
                .allowedStartChannels(latest.getAllowedStartChannels())
                .diagram(latest.getDiagram())
                .nodes(latest.getNodes())
                .transitions(latest.getTransitions())
                .swimlanes(latest.getSwimlanes())
                .createdBy(currentUserId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return toResponse(policyRepository.save(newVersion));
    }

    public PolicyResponse updateMeta(String id, UpdatePolicyMetaRequest request) {
        WorkflowPolicy policy = getOrThrow(id);
        requireDraft(policy);
        policy.setName(request.name());
        policy.setDescription(request.description());
        policy.setUpdatedAt(Instant.now());
        return toResponse(policyRepository.save(policy));
    }

    public void delete(String id) {
        WorkflowPolicy policy = getOrThrow(id);
        if (policy.getStatus() != PolicyStatus.DRAFT) {
            throw new BusinessException("Solo se pueden eliminar políticas en estado DRAFT");
        }
        policyRepository.deleteById(id);
    }

    public void archive(String id) {
        WorkflowPolicy policy = getOrThrow(id);
        if (policy.getStatus() == PolicyStatus.ARCHIVED) {
            throw new BusinessException("La política ya está archivada");
        }
        policy.setStatus(PolicyStatus.ARCHIVED);
        policy.setUpdatedAt(Instant.now());
        policyRepository.save(policy);
    }

    // ── Validación de estructura ─────────────────────────────────────────────

    private void validateStructure(WorkflowPolicy policy) {
        List<WorkflowNode> nodes = policy.getNodes();
        var transitions = policy.getTransitions();

        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException("La política debe tener al menos un nodo");
        }

        long startCount = nodes.stream()
                .filter(n -> n.getType() == NodeType.START).count();
        if (startCount != 1) {
            throw new BusinessException("La política debe tener exactamente un nodo START");
        }

        long endCount = nodes.stream()
                .filter(n -> n.getType() == NodeType.END).count();
        if (endCount < 1) {
            throw new BusinessException("La política debe tener al menos un nodo END");
        }

        // Nodos manuales deben tener areaId
        nodes.stream()
                .filter(n -> n.getType() == NodeType.MANUAL_FORM
                        || n.getType() == NodeType.MANUAL_ACTION
                        || n.getType() == NodeType.CLIENT_TASK)
                .filter(n -> n.getAreaId() == null || n.getAreaId().isBlank())
                .findFirst()
                .ifPresent(n -> {
                    throw new BusinessException(
                            "El nodo '" + n.getLabel() + "' requiere un areaId");
                });

        // Las transiciones deben apuntar a nodos existentes
        if (transitions != null) {
            java.util.Set<String> nodeIds = nodes.stream()
                    .map(WorkflowNode::getNodeId)
                    .collect(java.util.stream.Collectors.toSet());
            transitions.forEach(t -> {
                if (!nodeIds.contains(t.getFrom())) {
                    throw new BusinessException(
                            "La transición hace referencia a un nodo origen inexistente: " + t.getFrom());
                }
                if (!nodeIds.contains(t.getTo())) {
                    throw new BusinessException(
                            "La transición hace referencia a un nodo destino inexistente: " + t.getTo());
                }
            });

            // Nodos huérfanos: todos los nodos no-START deben tener al menos una transición entrante
            java.util.Set<String> reachable = transitions.stream()
                    .map(sw1.p1.policy.domain.WorkflowTransition::getTo)
                    .collect(java.util.stream.Collectors.toSet());
            nodes.stream()
                    .filter(n -> n.getType() != NodeType.START)
                    .filter(n -> !reachable.contains(n.getNodeId()))
                    .findFirst()
                    .ifPresent(n -> {
                        throw new BusinessException(
                                "El nodo '" + n.getLabel() + "' es huérfano (ninguna transición llega a él)");
                    });
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private WorkflowPolicy getOrThrow(String id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Política no encontrada: " + id));
    }

    private void requireDraft(WorkflowPolicy policy) {
        if (policy.getStatus() != PolicyStatus.DRAFT) {
            throw new BusinessException(
                    "Solo se pueden modificar políticas en estado DRAFT");
        }
    }

    private int nextVersion(String organizationId, String policyKey) {
        return policyRepository
                .findTopByOrganizationIdAndPolicyKeyOrderByVersionDesc(organizationId, policyKey)
                .map(p -> p.getVersion() + 1)
                .orElse(1);
    }

    private String currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(username)
                .map(u -> u.getId())
                .orElse(null);
    }

    private PolicyResponse toResponse(WorkflowPolicy p) {
        return new PolicyResponse(
                p.getId(), p.getOrganizationId(), p.getPolicyKey(), p.getName(),
                p.getDescription(), p.getVersion(), p.getStatus(),
                p.getAllowedStartChannels(), p.getDiagram(),
                p.getNodes(), p.getTransitions(), p.getSwimlanes(),
                p.getCreatedBy(), p.getPublishedBy(), p.getPublishedAt(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    private PolicySummaryResponse toSummary(WorkflowPolicy p) {
        return new PolicySummaryResponse(
                p.getId(), p.getPolicyKey(), p.getName(), p.getVersion(), p.getStatus(),
                p.getAllowedStartChannels(), p.getUpdatedAt()
        );
    }
}
