package sw1.p1.policy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.exception.ValidationException;
import sw1.p1.policy.domain.*;
import sw1.p1.shared.PolicyVersionStatus;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyVersionServiceTest {

    @Mock private PolicyVersionRepository versionRepository;
    @Mock private NodeConfigurationRepository nodeConfigRepository;
    @Mock private WorkflowPolicyRepository policyRepository;
    @Mock private BpmnValidationService validationService;

    @InjectMocks
    private PolicyVersionService service;

    private WorkflowPolicy policy;

    @BeforeEach
    void setUp() {
        policy = WorkflowPolicy.builder()
                .id("POL-001")
                .name("Test Policy")
                .organizationId("ORG-001")
                .policyKey("test")
                .version(1)
                .build();
    }

    @Test
    void createDraft_ShouldCreateFirstVersion() {
        when(policyRepository.findById("POL-001")).thenReturn(Optional.of(policy));
        when(versionRepository.existsByPolicyIdAndStatus("POL-001", PolicyVersionStatus.DRAFT))
                .thenReturn(false);
        when(versionRepository.findByPolicyIdOrderByVersionNumberDesc("POL-001"))
                .thenReturn(List.of());
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyVersion result = service.createDraft("POL-001", "user-1");

        assertNotNull(result);
        assertEquals("POL-001-V1", result.getId());
        assertEquals(1, result.getVersionNumber());
        assertEquals(PolicyVersionStatus.DRAFT, result.getStatus());
        assertNull(result.getBpmnXml());
    }

    @Test
    void createDraft_WhenDraftExists_ShouldThrow() {
        when(policyRepository.findById("POL-001")).thenReturn(Optional.of(policy));
        when(versionRepository.existsByPolicyIdAndStatus("POL-001", PolicyVersionStatus.DRAFT))
                .thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.createDraft("POL-001", "user-1"));
    }

    @Test
    void updateDiagram_WhenPublished_ShouldThrow() {
        PolicyVersion published = PolicyVersion.builder()
                .id("POL-001-V1").policyId("POL-001")
                .versionNumber(1).status(PolicyVersionStatus.PUBLISHED)
                .bpmnXml("<xml/>").build();

        when(versionRepository.findById("POL-001-V1")).thenReturn(Optional.of(published));

        assertThrows(BusinessException.class,
                () -> service.updateDiagram("POL-001", "POL-001-V1", "<new/>"));
    }

    @Test
    void updateDiagram_WhenDraft_ShouldSucceed() {
        PolicyVersion draft = PolicyVersion.builder()
                .id("POL-001-V1").policyId("POL-001")
                .versionNumber(1).status(PolicyVersionStatus.DRAFT)
                .bpmnXml("<old/>").build();

        when(versionRepository.findById("POL-001-V1")).thenReturn(Optional.of(draft));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyVersion result = service.updateDiagram("POL-001", "POL-001-V1", "<new/>");

        assertEquals("<new/>", result.getBpmnXml());
    }

    @Test
    void publish_ShouldSetPublishedStatus() {
        PolicyVersion draft = PolicyVersion.builder()
                .id("POL-001-V1").policyId("POL-001")
                .versionNumber(1).status(PolicyVersionStatus.DRAFT)
                .bpmnXml("<xml/>")
                .build();

        when(versionRepository.findById("POL-001-V1")).thenReturn(Optional.of(draft));
        when(validationService.validate("POL-001", "POL-001-V1", "<xml/>"))
                .thenReturn(new BpmnValidationService.ValidationResult(true, Collections.emptyList()));
        when(versionRepository.findTopByPolicyIdAndStatusOrderByVersionNumberDesc(
                "POL-001", PolicyVersionStatus.PUBLISHED)).thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(policyRepository.findById("POL-001")).thenReturn(Optional.of(policy));
        when(policyRepository.save(any())).thenReturn(policy);

        PolicyVersion result = service.publish("POL-001", "POL-001-V1", "user-1");

        assertEquals(PolicyVersionStatus.PUBLISHED, result.getStatus());
        assertNotNull(result.getPublishedAt());
    }

    @Test
    void publish_WithoutStartEvent_ShouldThrow() {
        PolicyVersion draft = PolicyVersion.builder()
                .id("POL-001-V1").policyId("POL-001")
                .versionNumber(1).status(PolicyVersionStatus.DRAFT)
                .bpmnXml("<definitions><process><endEvent id=\"e1\"/></process></definitions>")
                .build();

        when(versionRepository.findById("POL-001-V1")).thenReturn(Optional.of(draft));
        when(validationService.validate(any(), any(), any()))
                .thenReturn(new BpmnValidationService.ValidationResult(false, List.of(
                        new BpmnValidationService.Violation("BPMN_START_EVENT_COUNT", null, "falta StartEvent")
                )));

        assertThrows(ValidationException.class,
                () -> service.publish("POL-001", "POL-001-V1", "user-1"));
    }

    @Test
    void saveNodeConfiguration_WhenPublished_ShouldThrow() {
        PolicyVersion published = PolicyVersion.builder()
                .id("POL-001-V1").policyId("POL-001")
                .status(PolicyVersionStatus.PUBLISHED).build();

        when(versionRepository.findById("POL-001-V1")).thenReturn(Optional.of(published));

        assertThrows(BusinessException.class,
                () -> service.saveNodeConfiguration("POL-001", "POL-001-V1",
                        new NodeConfiguration()));
    }

    @Test
    void getVersion_WhenVersionNotBelongToPolicy_ShouldThrow() {
        PolicyVersion version = PolicyVersion.builder()
                .id("POL-001-V1").policyId("POL-002").build();

        when(versionRepository.findById("POL-001-V1")).thenReturn(Optional.of(version));

        assertThrows(NotFoundException.class,
                () -> service.getVersion("POL-001", "POL-001-V1"));
    }
}
