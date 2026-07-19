package sw1.p1.procedure.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sw1.p1.auth.domain.User;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.client.domain.Client;
import sw1.p1.client.domain.ClientRepository;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.ConflictException;
import sw1.p1.form.application.FormService;
import sw1.p1.form.application.FormValidationService;
import sw1.p1.form.domain.*;
import sw1.p1.form.dto.CreateFormTemplateRequest;
import sw1.p1.form.dto.CreateFormVersionRequest;
import sw1.p1.form.dto.FormTemplateResponse;
import sw1.p1.form.dto.FormVersionResponse;
import sw1.p1.form.dto.UpdateFormVersionRequest;
import sw1.p1.notification.application.NotificationService;
import sw1.p1.policy.application.BpmnValidationService;
import sw1.p1.policy.application.NodeExecutionProfileResolver;
import sw1.p1.policy.application.PolicyVersionService;
import sw1.p1.policy.application.PublishedPolicyAvailabilityService;
import sw1.p1.policy.domain.*;
import sw1.p1.policy.dto.NodeConfigurationRequest;
import sw1.p1.procedure.domain.*;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.shared.NodeType;
import sw1.p1.shared.PolicyStatus;
import sw1.p1.shared.PolicyVersionStatus;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Disposable in-memory smoke: no MongoDB connection, Atlas data, or migration is used.
 */
class PublishedFormWorkflowSmokeTest {

    private static final String ORG = "smoke-org";
    private static final String ADMIN_EMAIL = "admin@smoke.test";
    private static final String FORM_VERSION_ID = "form-v1";

    private final Map<String, WorkflowPolicy> policies = new HashMap<>();
    private final Map<String, PolicyVersion> policyVersions = new HashMap<>();
    private final Map<String, NodeConfiguration> configurations = new HashMap<>();
    private final Map<String, FormTemplate> templates = new HashMap<>();
    private final Map<String, FormVersion> formVersions = new HashMap<>();
    private final Map<String, Procedure> procedures = new HashMap<>();
    private final Map<String, Task> tasks = new HashMap<>();
    private final AtomicInteger procedureSequence = new AtomicInteger();

    private final WorkflowPolicyRepository policyRepository = mock(WorkflowPolicyRepository.class);
    private final PolicyVersionRepository policyVersionRepository = mock(PolicyVersionRepository.class);
    private final NodeConfigurationRepository nodeConfigurationRepository = mock(NodeConfigurationRepository.class);
    private final FormTemplateRepository templateRepository = mock(FormTemplateRepository.class);
    private final FormVersionRepository formVersionRepository = mock(FormVersionRepository.class);
    private final ProcedureRepository procedureRepository = mock(ProcedureRepository.class);
    private final ProcedureHistoryRepository historyRepository = mock(ProcedureHistoryRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final sw1.p1.form.application.CurrentOrganizationResolver organizationResolver =
            mock(sw1.p1.form.application.CurrentOrganizationResolver.class);

    private PolicyVersionService policyVersionService;
    private PublishedPolicyAvailabilityService availabilityService;
    private ProcedureService procedureService;

    @BeforeEach
    void setUp() {
        installInMemoryRepositories();

        BpmnValidationService validationService = mock(BpmnValidationService.class);
        when(validationService.validate(anyString(), anyString(), anyString()))
                .thenReturn(new BpmnValidationService.ValidationResult(true, List.of()));
        when(validationService.isUserTask(anyString(), anyString())).thenReturn(true);

        BpmnExecutionAdapter adapter = new BpmnExecutionAdapter(new NodeExecutionProfileResolver());
        availabilityService = new PublishedPolicyAvailabilityService(policyRepository, policyVersionRepository);
        WorkflowEngineService engine = new WorkflowEngineService(
                procedureRepository, historyRepository, taskRepository,
                mock(NotificationService.class), new NodeExecutionProfileResolver());
        policyVersionService = new PolicyVersionService(policyVersionRepository,
                nodeConfigurationRepository, policyRepository, validationService,
                formVersionRepository, templateRepository, adapter);
        procedureService = new ProcedureService(procedureRepository, historyRepository,
                policyRepository, nodeConfigurationRepository, adapter, userRepository,
                clientRepository, engine, organizationResolver, availabilityService);

        when(organizationResolver.requireOrganizationId()).thenReturn(ORG);
        when(organizationResolver.requireEmail()).thenReturn(ADMIN_EMAIL);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(
                User.builder().id("admin-1").organizationId(ORG).build()));
        when(clientRepository.findById("client-1")).thenReturn(Optional.of(
                Client.builder().id("client-1").organizationId(ORG).build()));
    }

    @Test
    void clientTaskCarriesExactPublishedFormVersionAndPromotesV2() {
        FormVersionResponse formVersion = createAndPublishForm();
        WorkflowPolicy policy = newPolicy("client-policy", List.of("WEB", "MOBILE"));
        PolicyVersion v1 = createConfigureAndPublish(policy, clientTaskBpmn("client-task-1"),
                "client-task-1", "CLIENT_TASK", null, formVersion.id());

        assertThat(policy.getLatestPublishedVersionId()).isEqualTo(v1.getId());
        assertThat(availabilityService.requireAvailable(ORG, policy.getId(), v1.getId(), "WEB").version())
                .isSameAs(v1);

        ProcedureResponse procedureV1 = procedureService.startFromVersionForInternal(
                policy.getId(), v1.getId(), "client-1");
        Task clientTask = onlyTaskFor(procedureV1.id());

        assertThat(clientTask.getPolicyVersionId()).isEqualTo(v1.getId());
        assertThat(clientTask.getFormVersionId()).isEqualTo(FORM_VERSION_ID);
        assertThat(clientTask.getForm()).isNull();
        assertThat(clientTask.getTaskAudience().name()).isEqualTo("CLIENT");
        assertThat(procedureV1.policySnapshot().getNodes()).allSatisfy(node ->
                assertThat(node.getForm()).isNull());

        PolicyVersion v2 = policyVersionService.createDraft(ORG, policy.getId(), "admin-1");
        assertThat(policy.getLatestPublishedVersionId()).isEqualTo(v1.getId());
        policyVersionService.updateDiagram(ORG, policy.getId(), v2.getId(), clientTaskBpmn("client-task-2"));
        policyVersionService.saveNodeConfiguration(ORG, policy.getId(), v2.getId(), "client-task-2",
                nodeRequest("CLIENT_TASK", null, FORM_VERSION_ID));
        policyVersionService.publish(ORG, policy.getId(), v2.getId(), "admin-1");

        assertThat(policy.getLatestPublishedVersionId()).isEqualTo(v2.getId());
        assertThatThrownBy(() -> procedureService.startFromVersionForInternal(
                policy.getId(), v1.getId(), "client-1")).isInstanceOf(ConflictException.class);
        assertThat(procedures.get(procedureV1.id()).getPolicyVersionId()).isEqualTo(v1.getId());
        assertThat(procedures.get(procedureV1.id()).getPolicySnapshot().getNodes())
                .extracting(WorkflowNode::getType).contains(NodeType.CLIENT_TASK);

        policy.setAllowedStartChannels(List.of("WEB"));
        assertThatThrownBy(() -> availabilityService.requireAvailable(
                ORG, policy.getId(), v2.getId(), "MOBILE")).isInstanceOf(BusinessException.class);
    }

    @Test
    void officerTaskMaterializesAsManualFormWithDepartmentAndExactFormVersion() {
        FormVersionResponse formVersion = createAndPublishForm();
        WorkflowPolicy policy = newPolicy("officer-policy", List.of("WEB"));
        PolicyVersion version = createConfigureAndPublish(policy, officerTaskBpmn("officer-task-1"),
                "officer-task-1", "OFFICER_TASK", "department-1", formVersion.id());

        ProcedureResponse procedure = procedureService.startFromVersionForInternal(
                policy.getId(), version.getId(), null);
        Task officerTask = onlyTaskFor(procedure.id());

        assertThat(procedure.policySnapshot().getNodes())
                .filteredOn(node -> node.getNodeId().equals("officer-task-1"))
                .extracting(WorkflowNode::getType).containsExactly(NodeType.MANUAL_FORM);
        assertThat(officerTask.getAssignedDepartmentId()).isEqualTo("department-1");
        assertThat(officerTask.getFormVersionId()).isEqualTo(FORM_VERSION_ID);
        assertThat(officerTask.getForm()).isNull();
        assertThat(officerTask.getTaskAudience().name()).isEqualTo("INTERNAL");
    }

    private FormVersionResponse createAndPublishForm() {
        FormService formService = new FormService(templateRepository, formVersionRepository,
                new FormValidationService());
        FormTemplateResponse template = formService.createTemplate(ORG,
                new CreateFormTemplateRequest("SMOKE", "Formulario smoke", null), "admin-1");
        FormVersionResponse draft = formService.createVersion(ORG, template.id(),
                new CreateFormVersionRequest(List.of(FormFieldDefinition.builder()
                        .id("field-1").key("documento").type(FormFieldType.TEXT)
                        .label("Documento").build())), "admin-1");
        return formService.publishVersion(ORG, draft.id());
    }

    private WorkflowPolicy newPolicy(String key, List<String> channels) {
        WorkflowPolicy policy = WorkflowPolicy.builder().id(key).organizationId(ORG)
                .policyKey(key).name(key).status(PolicyStatus.DRAFT)
                .allowedStartChannels(new ArrayList<>(channels)).build();
        policies.put(policy.getId(), policy);
        return policy;
    }

    private PolicyVersion createConfigureAndPublish(WorkflowPolicy policy, String bpmnXml,
                                                     String elementId, String taskKind,
                                                     String departmentId, String formVersionId) {
        PolicyVersion draft = policyVersionService.createDraft(ORG, policy.getId(), "admin-1");
        policyVersionService.updateDiagram(ORG, policy.getId(), draft.getId(), bpmnXml);
        policyVersionService.saveNodeConfiguration(ORG, policy.getId(), draft.getId(), elementId,
                nodeRequest(taskKind, departmentId, formVersionId));
        return policyVersionService.publish(ORG, policy.getId(), draft.getId(), "admin-1");
    }

    private NodeConfigurationRequest nodeRequest(String taskKind, String departmentId, String formVersionId) {
        return new NodeConfigurationRequest(taskKind,
                "CLIENT_TASK".equals(taskKind) ? "CLIENT" : "DEPARTMENT", departmentId,
                formVersionId, 24, "Tarea smoke", null);
    }

    private Task onlyTaskFor(String procedureId) {
        return tasks.values().stream().filter(task -> procedureId.equals(task.getProcedureId()))
                .findFirst().orElseThrow();
    }

    private String clientTaskBpmn(String taskId) {
        return bpmn(taskId, "Client task");
    }

    private String officerTaskBpmn(String taskId) {
        return bpmn(taskId, "Officer task");
    }

    private String bpmn(String taskId, String taskName) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="p" isExecutable="true">
                    <bpmn:startEvent id="start"/>
                    <bpmn:userTask id="%s" name="%s"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="%s"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="%s" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(taskId, taskName, taskId, taskId);
    }

    private void installInMemoryRepositories() {
        when(policyRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(policies.get(inv.getArgument(0))));
        when(policyRepository.save(any(WorkflowPolicy.class))).thenAnswer(inv -> {
            WorkflowPolicy policy = inv.getArgument(0);
            policies.put(policy.getId(), policy);
            return policy;
        });
        when(policyVersionRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(policyVersions.get(inv.getArgument(0))));
        when(policyVersionRepository.save(any(PolicyVersion.class))).thenAnswer(inv -> {
            PolicyVersion version = inv.getArgument(0);
            policyVersions.put(version.getId(), version);
            return version;
        });
        when(policyVersionRepository.findByPolicyIdOrderByVersionNumberDesc(anyString())).thenAnswer(inv ->
                policyVersions.values().stream().filter(v -> inv.getArgument(0).equals(v.getPolicyId()))
                        .sorted(Comparator.comparingInt(PolicyVersion::getVersionNumber).reversed()).toList());
        when(policyVersionRepository.existsByPolicyIdAndStatus(anyString(), any())).thenAnswer(inv ->
                policyVersions.values().stream().anyMatch(v -> inv.getArgument(0).equals(v.getPolicyId())
                        && inv.getArgument(1) == v.getStatus()));
        when(nodeConfigurationRepository.save(any(NodeConfiguration.class))).thenAnswer(inv -> {
            NodeConfiguration config = inv.getArgument(0);
            configurations.put(config.getId(), config);
            return config;
        });
        when(nodeConfigurationRepository.findByPolicyVersionId(anyString())).thenAnswer(inv ->
                configurations.values().stream().filter(c -> inv.getArgument(0).equals(c.getPolicyVersionId())).toList());
        when(nodeConfigurationRepository.findByPolicyVersionIdAndBpmnElementId(anyString(), anyString()))
                .thenAnswer(inv -> configurations.values().stream().filter(c ->
                        inv.getArgument(0).equals(c.getPolicyVersionId())
                                && inv.getArgument(1).equals(c.getBpmnElementId())).findFirst());

        when(templateRepository.findByOrganizationIdAndCode(anyString(), anyString())).thenAnswer(inv ->
                templates.values().stream().filter(t -> inv.getArgument(0).equals(t.getOrganizationId())
                        && inv.getArgument(1).equals(t.getCode())).findFirst());
        when(templateRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(templates.get(inv.getArgument(0))));
        when(templateRepository.save(any(FormTemplate.class))).thenAnswer(inv -> {
            FormTemplate template = inv.getArgument(0);
            if (template.getId() == null) template.setId("template-1");
            templates.put(template.getId(), template);
            return template;
        });
        when(formVersionRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(formVersions.get(inv.getArgument(0))));
        when(formVersionRepository.findByFormTemplateIdOrderByVersionNumberDesc(anyString())).thenAnswer(inv ->
                formVersions.values().stream().filter(v -> inv.getArgument(0).equals(v.getFormTemplateId()))
                        .sorted(Comparator.comparingInt(FormVersion::getVersionNumber).reversed()).toList());
        when(formVersionRepository.save(any(FormVersion.class))).thenAnswer(inv -> {
            FormVersion version = inv.getArgument(0);
            if (version.getId() == null) version.setId(FORM_VERSION_ID);
            formVersions.put(version.getId(), version);
            return version;
        });

        when(procedureRepository.count()).thenAnswer(inv -> (long) procedures.size());
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> {
            Procedure procedure = inv.getArgument(0);
            if (procedure.getId() == null) procedure.setId("procedure-" + procedureSequence.incrementAndGet());
            procedures.put(procedure.getId(), procedure);
            return procedure;
        });
        when(procedureRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(procedures.get(inv.getArgument(0))));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task task = inv.getArgument(0);
            if (task.getId() == null) task.setId("task-" + (tasks.size() + 1));
            tasks.put(task.getId(), task);
            return task;
        });
    }
}
