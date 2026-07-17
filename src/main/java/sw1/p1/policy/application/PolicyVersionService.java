package sw1.p1.policy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.exception.ValidationException;
import sw1.p1.policy.domain.*;
import sw1.p1.shared.PolicyVersionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyVersionService {

    private final PolicyVersionRepository versionRepository;
    private final NodeConfigurationRepository nodeConfigRepository;
    private final WorkflowPolicyRepository policyRepository;
    private final BpmnValidationService validationService;

    @Transactional
    public PolicyVersion createDraft(String policyId, String createdBy) {
        WorkflowPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new NotFoundException("Política no encontrada: " + policyId));

        if (versionRepository.existsByPolicyIdAndStatus(policyId, PolicyVersionStatus.DRAFT)) {
            throw new BusinessException("Ya existe un borrador para esta política");
        }

        int nextNumber = versionRepository.findByPolicyIdOrderByVersionNumberDesc(policyId)
                .stream()
                .findFirst()
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        String versionId = policyId + "-V" + nextNumber;
        PolicyVersion version = PolicyVersion.builder()
                .id(versionId)
                .policyId(policyId)
                .versionNumber(nextNumber)
                .status(PolicyVersionStatus.DRAFT)
                .bpmnXml(null)
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .build();

        PolicyVersion saved = versionRepository.save(version);

        policy.setCurrentDraftVersionId(saved.getId());
        policyRepository.save(policy);

        return saved;
    }

    public List<PolicyVersion> listVersions(String policyId) {
        return versionRepository.findByPolicyIdOrderByVersionNumberDesc(policyId);
    }

    public PolicyVersion getVersion(String policyId, String versionId) {
        PolicyVersion v = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Versión no encontrada: " + versionId));
        if (!v.getPolicyId().equals(policyId)) {
            throw new NotFoundException("La versión no pertenece a esta política");
        }
        return v;
    }

    public PolicyVersion updateDiagram(String policyId, String versionId, String bpmnXml) {
        PolicyVersion version = getVersion(policyId, versionId);
        if (version.getStatus() != PolicyVersionStatus.DRAFT) {
            throw new BusinessException("Solo se puede editar una versión en estado DRAFT");
        }
        log.info("PERSIST dia versionId={} status={} xmlLen={}",
                versionId, version.getStatus(), bpmnXml != null ? bpmnXml.length() : 0);
        version.setBpmnXml(bpmnXml);
        return versionRepository.save(version);
    }

    public List<NodeConfiguration> getNodeConfigurations(String policyId, String versionId) {
        getVersion(policyId, versionId);
        return nodeConfigRepository.findByPolicyVersionId(versionId);
    }

    public NodeConfiguration saveNodeConfiguration(String policyId, String versionId,
                                                    NodeConfiguration config) {
        PolicyVersion version = getVersion(policyId, versionId);
        if (version.getStatus() != PolicyVersionStatus.DRAFT) {
            throw new BusinessException("Solo se puede configurar una versión en estado DRAFT");
        }

        NodeConfiguration existing = nodeConfigRepository
                .findByPolicyVersionIdAndBpmnElementId(versionId, config.getBpmnElementId())
                .orElse(null);

        if (existing != null) {
            existing.setTaskKind(coalesce(config.getTaskKind(), existing.getTaskKind()));
            existing.setDepartmentId(coalesce(config.getDepartmentId(), existing.getDepartmentId()));
            existing.setAssignmentMode(coalesce(config.getAssignmentMode(), existing.getAssignmentMode()));
            existing.setSlaHours(config.getSlaHours() != null ? config.getSlaHours() : existing.getSlaHours());
            existing.setLabel(coalesce(config.getLabel(), existing.getLabel()));
            existing.setDescription(coalesce(config.getDescription(), existing.getDescription()));
            return nodeConfigRepository.save(existing);
        }

        config.setPolicyId(policyId);
        config.setPolicyVersionId(versionId);
        if (config.getId() == null || config.getId().isBlank()) {
            config.setId(UUID.randomUUID().toString());
        }
        return nodeConfigRepository.save(config);
    }

    private static String coalesce(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    public void deleteNodeConfiguration(String policyId, String versionId, String elementId) {
        getVersion(policyId, versionId);
        nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId(versionId, elementId)
                .ifPresent(nodeConfigRepository::delete);
    }

    @Transactional
    public PolicyVersion publish(String policyId, String versionId, String publishedBy) {
        PolicyVersion version = getVersion(policyId, versionId);
        if (version.getStatus() != PolicyVersionStatus.DRAFT) {
            throw new BusinessException("Solo se puede publicar una versión en estado DRAFT");
        }

        log.info("PUBLISH versionId={} xmlLen={}", versionId,
                version.getBpmnXml() != null ? version.getBpmnXml().length() : 0);
        var result = validationService.validate(policyId, versionId, version.getBpmnXml());
        if (!result.valid()) {
            throw new ValidationException(result.violations());
        }

        WorkflowPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new NotFoundException("Política no encontrada"));

        // Archivar versión publicada anterior si existe
        versionRepository.findTopByPolicyIdAndStatusOrderByVersionNumberDesc(
                        policyId, PolicyVersionStatus.PUBLISHED)
                .ifPresent(prev -> {
                    prev.setStatus(PolicyVersionStatus.ARCHIVED);
                    versionRepository.save(prev);
                });

        version.setStatus(PolicyVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now());
        PolicyVersion published = versionRepository.save(version);
        log.info("PUBLISHED versionId={} status={} xmlLen={}",
                published.getId(), published.getStatus(),
                published.getBpmnXml() != null ? published.getBpmnXml().length() : 0);

        policy.setCurrentDraftVersionId(null);
        policy.setLatestPublishedVersionId(published.getId());
        policy.setVersion(version.getVersionNumber());
        policyRepository.save(policy);

        return published;
    }

    public BpmnValidationService.ValidationResult validate(String policyId, String versionId,
                                                            String bpmnXml) {
        return validationService.validate(policyId, versionId, bpmnXml);
    }

    private String defaultBpmnTemplate(String policyName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
                + "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" "
                + "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" "
                + "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" "
                + "id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n"
                + "  <bpmn:process id=\"Process_1\" name=\"" + escapeXml(policyName) + "\" isExecutable=\"true\">\n"
                + "    <bpmn:startEvent id=\"StartEvent_1\" name=\"Inicio\">\n"
                + "      <bpmn:outgoing>Flow_1</bpmn:outgoing>\n"
                + "    </bpmn:startEvent>\n"
                + "    <bpmn:endEvent id=\"EndEvent_1\" name=\"Fin\">\n"
                + "      <bpmn:incoming>Flow_1</bpmn:incoming>\n"
                + "    </bpmn:endEvent>\n"
                + "    <bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"EndEvent_1\" />\n"
                + "  </bpmn:process>\n"
                + "  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">\n"
                + "    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"Process_1\">\n"
                + "    </bpmndi:BPMNPlane>\n"
                + "  </bpmndi:BPMNDiagram>\n"
                + "</bpmn:definitions>";
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
