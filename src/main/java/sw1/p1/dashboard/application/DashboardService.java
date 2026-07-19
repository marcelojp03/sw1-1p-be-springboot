package sw1.p1.dashboard.application;

import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import sw1.p1.dashboard.dto.AverageTimeByNodeResponse;
import sw1.p1.dashboard.dto.DashboardSummaryResponse;
import sw1.p1.dashboard.dto.ProceduresByStatusResponse;
import sw1.p1.dashboard.dto.TaskOverdueResponse;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.policy.domain.WorkflowPolicy;
import sw1.p1.procedure.domain.Procedure;
import sw1.p1.procedure.domain.ProcedureRepository;
import sw1.p1.shared.ProcedureStatus;
import sw1.p1.shared.TaskStatus;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProcedureRepository procedureRepository;
    private final TaskRepository taskRepository;
    private final MongoTemplate mongoTemplate;

    // ──────────────── summary ────────────────

    public DashboardSummaryResponse getSummary(String organizationId) {
        // Cargar todos los procedures de la organización para agrupar por status con streams
        List<Procedure> procedures = mongoTemplate.find(
                Query.query(Criteria.where("organizationId").is(organizationId)),
                Procedure.class
        );

        long totalProcedures = procedures.size();

        Map<String, Long> proceduresByStatus = procedures.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStatus() != null ? p.getStatus().name() : "UNKNOWN",
                        Collectors.counting()
                ));

        Instant now = Instant.now();

        // Contar tasks por organización y status usando MongoTemplate
        long pendingTasks = mongoTemplate.count(
                Query.query(Criteria.where("organizationId").is(organizationId)
                        .and("status").is(TaskStatus.PENDING.name())),
                Task.class
        );

        long inProgressTasks = mongoTemplate.count(
                Query.query(Criteria.where("organizationId").is(organizationId)
                        .and("status").is(TaskStatus.IN_PROGRESS.name())),
                Task.class
        );

        long overdueTasks = mongoTemplate.count(
                Query.query(Criteria.where("organizationId").is(organizationId)
                        .and("dueAt").lt(now)
                        .and("status").in(TaskStatus.PENDING.name(), TaskStatus.IN_PROGRESS.name())),
                Task.class
        );

        long totalTasks = mongoTemplate.count(
                Query.query(Criteria.where("organizationId").is(organizationId)),
                Task.class
        );

        return new DashboardSummaryResponse(
                totalProcedures, proceduresByStatus,
                pendingTasks, inProgressTasks, overdueTasks, totalTasks
        );
    }

    // ──────────────── procedures by status ────────────────

    public ProceduresByStatusResponse getProceduresByStatus(
            String organizationId, String policyId, String startDate, String endDate) {

        Criteria criteria = Criteria.where("organizationId").is(organizationId);

        if (policyId != null && !policyId.isBlank()) {
            criteria = criteria.and("policyId").is(policyId);
        }
        if (startDate != null && !startDate.isBlank()) {
            criteria = criteria.and("createdAt").gte(Instant.parse(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            Criteria existing = criteria;
            // Si ya se aplicó gte en createdAt, necesitamos and adicional
            criteria = existing.and("createdAt").lte(Instant.parse(endDate));
        }

        List<Procedure> procedures = mongoTemplate.find(Query.query(criteria), Procedure.class);

        Map<String, Long> grouped = procedures.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStatus() != null ? p.getStatus().name() : "UNKNOWN",
                        Collectors.counting()
                ));

        List<ProceduresByStatusResponse.StatusCount> items = grouped.entrySet().stream()
                .map(e -> new ProceduresByStatusResponse.StatusCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ProceduresByStatusResponse.StatusCount::status))
                .collect(Collectors.toList());

        return new ProceduresByStatusResponse(items);
    }

    // ──────────────── tasks overdue ────────────────

    public Page<TaskOverdueResponse> getTasksOverdue(
            String organizationId, String departmentId, int page, int size) {

        Instant now = Instant.now();

        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("dueAt").lt(now)
                .and("status").in(TaskStatus.PENDING.name(), TaskStatus.IN_PROGRESS.name());

        if (departmentId != null && !departmentId.isBlank()) {
            criteria = criteria.and("assignedDepartmentId").is(departmentId);
        }

        Query countQuery = Query.query(criteria);
        long total = mongoTemplate.count(countQuery, Task.class);

        Query pageQuery = Query.query(criteria)
                .with(PageRequest.of(page, size));
        List<Task> tasks = mongoTemplate.find(pageQuery, Task.class);

        List<TaskOverdueResponse> items = tasks.stream()
                .map(t -> new TaskOverdueResponse(
                        t.getId(),
                        t.getProcedureId(),
                        t.getProcedureCode(),
                        t.getLabel(),
                        t.getAssignedDepartmentId(),
                        t.getAssignedUserId(),
                        t.getDueAt(),
                        t.getCreatedAt(),
                        ChronoUnit.DAYS.between(t.getDueAt(), now)
                ))
                .collect(Collectors.toList());

        return new PageImpl<>(items, PageRequest.of(page, size), total);
    }

    // ──────────────── average time by node ────────────────

    public List<AverageTimeByNodeResponse> getAverageTimeByNode(
            String organizationId, String policyId) {

        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("status").is(TaskStatus.COMPLETED.name())
                .and("completedAt").ne(null)
                .and("createdAt").ne(null);

        if (policyId != null && !policyId.isBlank()) {
            criteria = criteria.and("policyId").is(policyId);
        }

        List<Task> completedTasks = mongoTemplate.find(Query.query(criteria), Task.class);

        // Construir mapa nodeId → slaHours desde la(s) política(s) relevante(s)
        Map<String, Integer> nodeExpectedHoursMap = new HashMap<>();
        if (policyId != null && !policyId.isBlank()) {
            WorkflowPolicy policy = mongoTemplate.findById(policyId, WorkflowPolicy.class);
            if (policy != null && policy.getNodes() != null) {
                for (WorkflowNode node : policy.getNodes()) {
                    if (node.getNodeId() != null && node.getSlaHours() != null) {
                        nodeExpectedHoursMap.put(node.getNodeId(), node.getSlaHours());
                    }
                }
            }
        } else {
            // Sin filtro de política: buscar todas las políticas de la organización
            List<WorkflowPolicy> policies = mongoTemplate.find(
                    Query.query(Criteria.where("organizationId").is(organizationId)),
                    WorkflowPolicy.class);
            for (WorkflowPolicy policy : policies) {
                if (policy.getNodes() != null) {
                    for (WorkflowNode node : policy.getNodes()) {
                        if (node.getNodeId() != null && node.getSlaHours() != null) {
                            nodeExpectedHoursMap.putIfAbsent(node.getNodeId(), node.getSlaHours());
                        }
                    }
                }
            }
        }

        // Agrupar por nodeId y calcular promedio con Java streams
        Map<String, List<Task>> byNode = completedTasks.stream()
                .filter(t -> t.getNodeId() != null)
                .collect(Collectors.groupingBy(Task::getNodeId));

        return byNode.entrySet().stream()
                .map(entry -> {
                    String nodeId = entry.getKey();
                    List<Task> nodeTasks = entry.getValue();
                    String nodeLabel = nodeTasks.stream()
                            .map(Task::getLabel)
                            .filter(l -> l != null)
                            .findFirst()
                            .orElse(nodeId);
                    double avgHours = nodeTasks.stream()
                            .mapToDouble(t -> ChronoUnit.SECONDS.between(t.getCreatedAt(), t.getCompletedAt()) / 3600.0)
                            .average()
                            .orElse(0.0);
                    Integer slaHours = nodeExpectedHoursMap.get(nodeId);
                    Double expectedHours = slaHours != null ? slaHours.doubleValue() : null;
                    return new AverageTimeByNodeResponse(nodeId, nodeLabel, avgHours, nodeTasks.size(), expectedHours);
                })
                .sorted(java.util.Comparator.comparing(AverageTimeByNodeResponse::nodeLabel))
                .collect(Collectors.toList());
    }
}
