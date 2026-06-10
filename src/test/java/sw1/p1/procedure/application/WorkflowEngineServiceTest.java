package sw1.p1.procedure.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sw1.p1.notification.application.NotificationService;
import sw1.p1.policy.domain.FormField;
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
                procedureRepository, historyRepository, taskRepository, notificationService);
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
                .areaId("area-1")
                .form(sw1.p1.policy.domain.FormDefinition.builder()
                        .formId("f" + id)
                        .fields(List.of(FormField.builder()
                                .fieldId("f1").type("TEXT").label("Field").build()))
                        .build())
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
                    .assignedAreaId(t.getAssignedAreaId())
                    .taskAudience(t.getTaskAudience()).status(t.getStatus())
                    .form(t.getForm()).createdAt(Instant.now()).updatedAt(Instant.now())
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
    }
}
