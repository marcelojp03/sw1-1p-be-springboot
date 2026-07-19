package sw1.p1.procedure.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sw1.p1.notification.application.NotificationService;
import sw1.p1.policy.application.NodeExecutionProfileResolver;
import sw1.p1.policy.domain.FormDefinition;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.procedure.domain.PolicySnapshot;
import sw1.p1.procedure.domain.Procedure;
import sw1.p1.procedure.domain.ProcedureHistoryRepository;
import sw1.p1.procedure.domain.ProcedureRepository;
import sw1.p1.shared.NodeType;
import sw1.p1.shared.ProcedureStatus;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;

class WorkflowEngineServiceTest {

    private final ProcedureRepository procedureRepository = org.mockito.Mockito.mock(ProcedureRepository.class);
    private final ProcedureHistoryRepository historyRepository = org.mockito.Mockito.mock(ProcedureHistoryRepository.class);
    private final TaskRepository taskRepository = org.mockito.Mockito.mock(TaskRepository.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);

    private WorkflowEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new WorkflowEngineService(
                procedureRepository, historyRepository, taskRepository, notificationService,
                new NodeExecutionProfileResolver());
    }

    private Procedure procedure(String id, List<sw1.p1.policy.domain.WorkflowNode> nodes,
                                 List<sw1.p1.policy.domain.WorkflowTransition> transitions) {
        var nodesCopy = new ArrayList<>(nodes);
        var transitionsCopy = new ArrayList<>(transitions);
        var snapshot = PolicySnapshot.builder()
                .policyId("policy-1").policyKey("TEST").policyName("Test")
                .version(1).nodes(nodesCopy).transitions(transitionsCopy)
                .build();
        return Procedure.builder()
                .id(id).organizationId("org-1").policyId("policy-1")
                .policyVersionId("policy-v1").clientId("client-1")
                .policySnapshot(snapshot)
                .currentNodeIds(new ArrayList<>())
                .status(ProcedureStatus.IN_PROGRESS)
                .formData(new java.util.HashMap<>())
                .build();
    }

    private sw1.p1.policy.domain.WorkflowNode node(String id, NodeType type) {
        return sw1.p1.policy.domain.WorkflowNode.builder().nodeId(id).type(type).label(id).build();
    }

    private sw1.p1.policy.domain.WorkflowNode manualNode(String id) {
        return sw1.p1.policy.domain.WorkflowNode.builder()
                .nodeId(id).type(NodeType.MANUAL_FORM).label(id)
                .departmentId("dep-1")
                .formVersionId("form-" + id)
                .slaHours(24)
                .build();
    }

    private sw1.p1.policy.domain.WorkflowTransition tr(String id, String from, String to, String condition, String label) {
        return sw1.p1.policy.domain.WorkflowTransition.builder()
                .transitionId(id).from(from).to(to).condition(condition).label(label).build();
    }

    // ── CONDITION ────────────────────────────────────────────────────────────

    @Test
    void conditionRoutesToMatchingBranch() {
        var step1 = manualNode("n1");
        var cond = node("n2", NodeType.CONDITION);
        var yes = manualNode("n3");
        var no = manualNode("n4");

        var proc = procedure("p1", List.of(step1, cond, yes, no), List.of(
                tr("t1", "n1", "n2", null, null),
                tr("ty", "n2", "n3", "f1 == alto", "Alto"),
                tr("tn", "n2", "n4", "f1 == bajo", "Bajo")));
        proc.getFormData().put("n1", Map.of("f1", "alto"));

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            return Task.builder().id(t.getNodeId() + "-task").nodeId(t.getNodeId())
                    .procedureId(t.getProcedureId()).status(t.getStatus())
                    .taskAudience(t.getTaskAudience())
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();
        });
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.advance(proc, "n1", "user-1", Map.of("f1", "alto"));

        assertThat(proc.getCurrentNodeIds()).containsExactly("n3");
    }

    @Test
    void conditionUsesDefaultWhenNoMatch() {
        var step1 = manualNode("n1");
        var cond = node("n2", NodeType.CONDITION);
        var def = manualNode("n3");

        var proc = procedure("p1", List.of(step1, cond, def), List.of(
                tr("t1", "n1", "n2", null, null),
                tr("td", "n2", "n3", null, "Default")));

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            return Task.builder().id(t.getNodeId() + "-task").nodeId(t.getNodeId())
                    .procedureId(t.getProcedureId()).status(t.getStatus())
                    .taskAudience(t.getTaskAudience())
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();
        });
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.advance(proc, "n1", "user-1", Map.of("f1", "unknown"));

        assertThat(proc.getCurrentNodeIds()).containsExactly("n3");
    }

    // ── PARALLEL SPLIT / JOIN ─────────────────────────────────────────────────

    @Test
    void parallelSplitSetsBothBranchesAsActive() {
        var start = node("nStart", NodeType.START);
        var split = node("nSplit", NodeType.PARALLEL_SPLIT);
        var branchA = manualNode("nA");
        var branchB = manualNode("nB");
        var join = node("nJ", NodeType.PARALLEL_JOIN);
        var end = node("nEnd", NodeType.END);

        var proc = procedure("p2", List.of(start, split, branchA, branchB, join, end), List.of(
                tr("ts", "nStart", "nSplit", null, null),
                tr("tA", "nSplit", "nA", null, null),
                tr("tB", "nSplit", "nB", null, null),
                tr("tJA", "nA", "nJ", null, null),
                tr("tJB", "nB", "nJ", null, null),
                tr("tOut", "nJ", "nEnd", null, null)));

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            return Task.builder().id(t.getNodeId() + "-task").nodeId(t.getNodeId())
                    .procedureId(t.getProcedureId()).procedureCode(t.getProcedureCode())
                    .policyId(t.getPolicyId()).organizationId(t.getOrganizationId())
                    .assignedDepartmentId(t.getAssignedDepartmentId())
                    .taskAudience(t.getTaskAudience()).status(t.getStatus())
                    .formVersionId(t.getFormVersionId()).createdAt(t.getCreatedAt())
                    .dueAt(t.getDueAt()).updatedAt(t.getUpdatedAt())
                    .build();
        });
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.start(proc, "user-1");

        assertThat(proc.getCurrentNodeIds()).containsExactlyInAnyOrder("nA", "nB");
    }

    @Test
    void parallelJoinWaitsThenAdvances() {
        var branchA = manualNode("nA");
        var branchB = manualNode("nB");
        var join = node("nJ", NodeType.PARALLEL_JOIN);
        var end = node("nEnd", NodeType.END);

        var proc = procedure("p3", List.of(branchA, branchB, join, end), List.of(
                tr("tJA", "nA", "nJ", null, null),
                tr("tJB", "nB", "nJ", null, null),
                tr("tOut", "nJ", "nEnd", null, null)));
        proc.setCurrentNodeIds(new ArrayList<>(List.of("nA", "nB")));

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            return Task.builder().id(t.getNodeId() + "-task").nodeId(t.getNodeId())
                    .procedureId(t.getProcedureId()).status(t.getStatus())
                    .taskAudience(t.getTaskAudience())
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();
        });
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.advance(proc, "nA", "user-1", Map.of("f1", "ok"));
        assertThat(proc.getCurrentNodeIds()).containsExactly("nB");

        engine.advance(proc, "nB", "user-1", Map.of("f1", "ok"));
        assertThat(proc.getStatus()).isEqualTo(ProcedureStatus.COMPLETED);
        assertThat(proc.getCompletedAt()).isNotNull();
    }

    @Test
    void reachingEndEventSetsCompletedAt() {
        var start = node("nStart", NodeType.START);
        var end = node("nEnd", NodeType.END);

        var proc = procedure("p4", List.of(start, end), List.of(
                tr("t1", "nStart", "nEnd", null, null)));

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.start(proc, "user-1");

        assertThat(proc.getStatus()).isEqualTo(ProcedureStatus.COMPLETED);
        assertThat(proc.getCompletedAt()).isNotNull();
    }

    @Test
    void manualFormTaskCopiesExactRuntimeMetadataAndSla() {
        var taskNode = WorkflowNode.builder()
                .nodeId("n1").type(NodeType.MANUAL_FORM).label("Revisar")
                .departmentId("dep-7").formVersionId("form-v9").slaHours(12)
                .build();
        var proc = startTo(taskNode);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.start(proc, "admin-1");

        Task saved = capturedTask();
        assertThat(saved.getAssignedDepartmentId()).isEqualTo("dep-7");
        assertThat(saved.getFormVersionId()).isEqualTo("form-v9");
        assertThat(saved.getForm()).isNull();
        assertThat(saved.getDueAt()).isEqualTo(saved.getCreatedAt().plusSeconds(12 * 3600L));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void manualActionTaskHasNoFormAndNullSlaHasNoDueAt() {
        var taskNode = WorkflowNode.builder()
                .nodeId("n1").type(NodeType.MANUAL_ACTION).label("Aprobar")
                .departmentId("dep-7").build();
        var proc = startTo(taskNode);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.start(proc, "admin-1");

        Task saved = capturedTask();
        assertThat(saved.getFormVersionId()).isNull();
        assertThat(saved.getForm()).isNull();
        assertThat(saved.getDueAt()).isNull();
    }

    @Test
    void clientTaskCopiesExactFormVersionWithoutDepartmentOrLegacyForm() {
        var taskNode = WorkflowNode.builder()
                .nodeId("n1").type(NodeType.CLIENT_TASK).label("Completar")
                .formVersionId("form-client-v2").slaHours(6).build();
        var proc = startTo(taskNode);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.start(proc, "admin-1");

        Task saved = capturedTask();
        assertThat(saved.getAssignedClientId()).isEqualTo("client-1");
        assertThat(saved.getAssignedDepartmentId()).isNull();
        assertThat(saved.getFormVersionId()).isEqualTo("form-client-v2");
        assertThat(saved.getForm()).isNull();
        assertThat(saved.getDueAt()).isEqualTo(saved.getCreatedAt().plusSeconds(6 * 3600L));
    }

    @Test
    void clientTaskWithoutFormKeepsNullReference() {
        var taskNode = WorkflowNode.builder()
                .nodeId("n1").type(NodeType.CLIENT_TASK).label("Confirmar").build();
        var proc = startTo(taskNode);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.start(proc, "admin-1");

        Task saved = capturedTask();
        assertThat(saved.getFormVersionId()).isNull();
        assertThat(saved.getForm()).isNull();
    }

    @Test
    void legacyUnversionedExecutionDoesNotWriteEmbeddedForm() {
        var legacyForm = FormDefinition.builder().formId("legacy-form").fields(List.of()).build();
        var taskNode = WorkflowNode.builder()
                .nodeId("n1").type(NodeType.MANUAL_FORM).label("Legacy")
                .departmentId("dep-1").form(legacyForm).build();
        var proc = startTo(taskNode);
        proc.setPolicyVersionId(null);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.start(proc, "admin-1");

        Task saved = capturedTask();
        assertThat(saved.getFormVersionId()).isNull();
        assertThat(saved.getForm()).isNull();
    }

    private Procedure startTo(WorkflowNode taskNode) {
        var start = node("start", NodeType.START);
        return procedure("runtime-proc", List.of(start, taskNode),
                List.of(tr("flow", "start", taskNode.getNodeId(), null, null)));
    }

    private Task capturedTask() {
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
