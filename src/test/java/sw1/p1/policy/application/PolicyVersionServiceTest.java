package sw1.p1.policy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.exception.ValidationException;
import sw1.p1.form.domain.FormTemplate;
import sw1.p1.form.domain.FormTemplateRepository;
import sw1.p1.form.domain.FormVersion;
import sw1.p1.form.domain.FormVersionRepository;
import sw1.p1.form.domain.FormVersionStatus;
import sw1.p1.form.exception.FormVersionNotFoundException;
import sw1.p1.policy.domain.*;
import sw1.p1.policy.dto.NodeConfigurationRequest;
import sw1.p1.policy.dto.NodeConfigurationResponse;
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
    @Mock private FormVersionRepository formVersionRepository;
    @Mock private FormTemplateRepository formTemplateRepository;

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

        when(policyRepository.findById("POL-001")).thenReturn(Optional.of(policy));

        assertThrows(ConflictException.class,
                () -> service.saveNodeConfiguration("ORG-001", "POL-001", "POL-001-V1",
                        "u1", request("CLIENT_TASK", null)));
    }

    @Test
    void getVersion_WhenVersionNotBelongToPolicy_ShouldThrow() {
        PolicyVersion version = PolicyVersion.builder()
                .id("POL-001-V1").policyId("POL-002").build();

        when(versionRepository.findById("POL-001-V1")).thenReturn(Optional.of(version));

        assertThrows(NotFoundException.class,
                () -> service.getVersion("POL-001", "POL-001-V1"));
    }

    @Test
    void saveNodeConfiguration_AssociatesPublishedFormVersion() {
        PolicyVersion draft = draftVersion();
        FormVersion formVersion = publishedForm("ORG-001");
        mockEditableVersion(draft);
        when(nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId("POL-001-V1", "u1"))
                .thenReturn(Optional.empty());
        when(validationService.isUserTask(draft.getBpmnXml(), "u1")).thenReturn(true);
        when(formVersionRepository.findById("form-v1")).thenReturn(Optional.of(formVersion));
        when(formTemplateRepository.findById("form-t1")).thenReturn(Optional.of(formTemplate("ORG-001")));
        when(nodeConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NodeConfigurationResponse response = service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1",
                request("CLIENT_TASK", "form-v1"));

        assertEquals("form-v1", response.formVersionId());
        assertEquals("u1", response.bpmnElementId());
        assertEquals("POL-001-V1", response.policyVersionId());
    }

    @Test
    void saveNodeConfiguration_AllowsPublishedFormForOfficerTask() {
        PolicyVersion draft = draftVersion();
        mockEditableVersion(draft);
        when(nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId("POL-001-V1", "u1"))
                .thenReturn(Optional.empty());
        when(validationService.isUserTask(draft.getBpmnXml(), "u1")).thenReturn(true);
        when(formVersionRepository.findById("form-v1")).thenReturn(Optional.of(publishedForm("ORG-001")));
        when(formTemplateRepository.findById("form-t1")).thenReturn(Optional.of(formTemplate("ORG-001")));
        when(nodeConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NodeConfigurationResponse response = service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1",
                request("OFFICER_TASK", "form-v1"));

        assertEquals("OFFICER_TASK", response.taskKind());
        assertEquals("DEP-1", response.departmentId());
        assertEquals("form-v1", response.formVersionId());
    }

    @Test
    void saveNodeConfiguration_NullRemovesAssociation() {
        PolicyVersion draft = draftVersion();
        NodeConfiguration existing = NodeConfiguration.builder()
                .id("nc1").policyId("POL-001").policyVersionId("POL-001-V1")
                .bpmnElementId("u1").taskKind("CLIENT_TASK").formVersionId("form-v1")
                .build();
        mockEditableVersion(draft);
        when(nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId("POL-001-V1", "u1"))
                .thenReturn(Optional.of(existing));
        when(nodeConfigRepository.save(existing)).thenReturn(existing);

        NodeConfigurationResponse response = service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1",
                request("CLIENT_TASK", null));

        assertNull(response.formVersionId());
        verify(formVersionRepository, never()).findById(anyString());
    }

    @Test
    void saveNodeConfiguration_RejectsMissingFormVersion() {
        PolicyVersion draft = draftVersion();
        mockEditableVersion(draft);
        when(nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId("POL-001-V1", "u1"))
                .thenReturn(Optional.empty());
        when(validationService.isUserTask(draft.getBpmnXml(), "u1")).thenReturn(true);
        when(formVersionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(FormVersionNotFoundException.class, () -> service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1",
                request("CLIENT_TASK", "missing")));
    }

    @Test
    void saveNodeConfiguration_RejectsDraftFormVersion() {
        assertInvalidFormStatus(FormVersionStatus.DRAFT);
    }

    @Test
    void saveNodeConfiguration_RejectsArchivedFormVersion() {
        assertInvalidFormStatus(FormVersionStatus.ARCHIVED);
    }

    @Test
    void saveNodeConfiguration_HidesFormVersionFromAnotherOrganization() {
        PolicyVersion draft = draftVersion();
        mockEditableVersion(draft);
        when(nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId("POL-001-V1", "u1"))
                .thenReturn(Optional.empty());
        when(validationService.isUserTask(draft.getBpmnXml(), "u1")).thenReturn(true);
        when(formVersionRepository.findById("form-v1")).thenReturn(Optional.of(publishedForm("ORG-OTHER")));

        assertThrows(FormVersionNotFoundException.class, () -> service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1",
                request("OFFICER_TASK", "form-v1")));
    }

    @Test
    void saveNodeConfiguration_RejectsMissingFormTemplate() {
        PolicyVersion draft = draftVersion();
        mockEditableVersion(draft);
        when(nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId("POL-001-V1", "u1"))
                .thenReturn(Optional.empty());
        when(validationService.isUserTask(draft.getBpmnXml(), "u1")).thenReturn(true);
        when(formVersionRepository.findById("form-v1")).thenReturn(Optional.of(publishedForm("ORG-001")));
        when(formTemplateRepository.findById("form-t1")).thenReturn(Optional.empty());

        assertThrows(FormVersionNotFoundException.class, () -> service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1",
                request("CLIENT_TASK", "form-v1")));
    }

    @Test
    void saveNodeConfiguration_RejectsFormOnNonUserTask() {
        PolicyVersion draft = draftVersion();
        mockEditableVersion(draft);
        when(nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId("POL-001-V1", "service1"))
                .thenReturn(Optional.empty());
        when(validationService.isUserTask(draft.getBpmnXml(), "service1")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "service1",
                request("AUTOMATIC_TASK", "form-v1")));
        verify(formVersionRepository, never()).findById(anyString());
    }

    @Test
    void saveNodeConfiguration_RejectsCrossOrganizationPolicy() {
        policy.setOrganizationId("ORG-OTHER");
        when(policyRepository.findById("POL-001")).thenReturn(Optional.of(policy));

        assertThrows(NotFoundException.class, () -> service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1",
                request("CLIENT_TASK", null)));
        verify(versionRepository, never()).findById(anyString());
    }

    @Test
    void getNodeConfigurations_RejectsCrossOrganizationPolicy() {
        policy.setOrganizationId("ORG-OTHER");
        when(policyRepository.findById("POL-001")).thenReturn(Optional.of(policy));

        assertThrows(NotFoundException.class, () -> service.getNodeConfigurations(
                "ORG-001", "POL-001", "POL-001-V1"));
        verify(nodeConfigRepository, never()).findByPolicyVersionId(anyString());
    }

    @Test
    void deleteNodeConfiguration_RejectsCrossOrganizationPolicy() {
        policy.setOrganizationId("ORG-OTHER");
        when(policyRepository.findById("POL-001")).thenReturn(Optional.of(policy));

        assertThrows(NotFoundException.class, () -> service.deleteNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1"));
        verify(nodeConfigRepository, never()).delete(any());
    }

    @Test
    void saveNodeConfiguration_RejectsNonPositiveSla() {
        PolicyVersion draft = draftVersion();
        mockEditableVersion(draft);
        NodeConfigurationRequest invalid = new NodeConfigurationRequest(
                "CLIENT_TASK", "CLIENT", null, null, 0, "Tarea", null);

        assertThrows(BusinessException.class, () -> service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1", invalid));
        verify(nodeConfigRepository, never()).save(any());
    }

    @Test
    void deleteNodeConfiguration_DraftDeletesConfiguration() {
        PolicyVersion draft = draftVersion();
        NodeConfiguration existing = NodeConfiguration.builder().id("nc1").build();
        mockEditableVersion(draft);
        when(nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId("POL-001-V1", "u1"))
                .thenReturn(Optional.of(existing));

        service.deleteNodeConfiguration("ORG-001", "POL-001", "POL-001-V1", "u1");

        verify(nodeConfigRepository).delete(existing);
    }

    @Test
    void deleteNodeConfiguration_PublishedReturnsConflict() {
        assertDeleteConflict(PolicyVersionStatus.PUBLISHED);
    }

    @Test
    void deleteNodeConfiguration_ArchivedReturnsConflict() {
        assertDeleteConflict(PolicyVersionStatus.ARCHIVED);
    }

    private void assertInvalidFormStatus(FormVersionStatus status) {
        PolicyVersion draft = draftVersion();
        FormVersion formVersion = publishedForm("ORG-001");
        formVersion.setStatus(status);
        mockEditableVersion(draft);
        when(nodeConfigRepository.findByPolicyVersionIdAndBpmnElementId("POL-001-V1", "u1"))
                .thenReturn(Optional.empty());
        when(validationService.isUserTask(draft.getBpmnXml(), "u1")).thenReturn(true);
        when(formVersionRepository.findById("form-v1")).thenReturn(Optional.of(formVersion));

        assertThrows(BusinessException.class, () -> service.saveNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1",
                request("CLIENT_TASK", "form-v1")));
        verify(nodeConfigRepository, never()).save(any());
    }

    private void assertDeleteConflict(PolicyVersionStatus status) {
        PolicyVersion version = draftVersion();
        version.setStatus(status);
        mockEditableVersion(version);

        assertThrows(ConflictException.class, () -> service.deleteNodeConfiguration(
                "ORG-001", "POL-001", "POL-001-V1", "u1"));
        verify(nodeConfigRepository, never()).delete(any());
    }

    private void mockEditableVersion(PolicyVersion version) {
        when(policyRepository.findById("POL-001")).thenReturn(Optional.of(policy));
        when(versionRepository.findById("POL-001-V1")).thenReturn(Optional.of(version));
    }

    private PolicyVersion draftVersion() {
        return PolicyVersion.builder()
                .id("POL-001-V1")
                .policyId("POL-001")
                .status(PolicyVersionStatus.DRAFT)
                .bpmnXml("<definitions><process><userTask id=\"u1\"/></process></definitions>")
                .build();
    }

    private FormVersion publishedForm(String organizationId) {
        return FormVersion.builder()
                .id("form-v1")
                .formTemplateId("form-t1")
                .organizationId(organizationId)
                .status(FormVersionStatus.PUBLISHED)
                .build();
    }

    private FormTemplate formTemplate(String organizationId) {
        return FormTemplate.builder().id("form-t1").organizationId(organizationId).build();
    }

    private NodeConfigurationRequest request(String taskKind, String formVersionId) {
        return new NodeConfigurationRequest(
                taskKind, "CLIENT", taskKind.equals("OFFICER_TASK") ? "DEP-1" : null,
                formVersionId, 24, "Tarea", "Descripción");
    }
}
