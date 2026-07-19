package sw1.p1.procedure.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sw1.p1.exception.BusinessException;
import sw1.p1.notification.application.NotificationService;
import sw1.p1.notification.dto.CreateNotificationRequest;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.policy.domain.WorkflowTransition;
import sw1.p1.policy.application.NodeExecutionProfileResolver;
import sw1.p1.procedure.domain.Procedure;
import sw1.p1.procedure.domain.ProcedureHistory;
import sw1.p1.procedure.domain.ProcedureHistoryRepository;
import sw1.p1.procedure.domain.ProcedureRepository;
import sw1.p1.shared.NodeType;
import sw1.p1.shared.ProcedureStatus;
import sw1.p1.shared.TaskAudience;
import sw1.p1.shared.TaskStatus;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Motor de workflow.
 * Avanza el trámite de nodo en nodo cuando un funcionario (u otro actor) completa una tarea.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowEngineService {

    private final ProcedureRepository procedureRepository;
    private final ProcedureHistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final NodeExecutionProfileResolver profileResolver;

    /**
     * Avanza el trámite al siguiente nodo después de que una tarea fue completada.
     *
     * @param procedure el trámite a avanzar
     * @param completedNodeId el nodo que acaba de completarse
     * @param actorId ID del usuario que completó la tarea
     * @param formData datos del formulario enviados en este paso (puede ser null)
     */
    public void advance(Procedure procedure, String completedNodeId,
                        String actorId, Map<String, Object> formData) {

        List<WorkflowNode> nodes = procedure.getPolicySnapshot().getNodes();
        List<WorkflowTransition> transitions = procedure.getPolicySnapshot().getTransitions();

        WorkflowNode completedNode = findNode(nodes, completedNodeId);

        // Persistir datos del formulario acumulado
        if (formData != null && !formData.isEmpty()) {
            if (procedure.getFormData() == null) {
                procedure.setFormData(new HashMap<>());
            }
            procedure.getFormData().put(completedNodeId, formData);
        }

        // Registrar evento de tarea completada
        recordHistory(procedure.getId(), completedNode, "TASK_COMPLETED", actorId, formData, null);

        // Determinar la transición a seguir
        WorkflowTransition nextTransition = resolveTransition(
                transitions, completedNodeId, procedure.getFormData(), formData);

        if (nextTransition == null) {
            log.warn("No hay transición desde el nodo {} en el trámite {}",
                    completedNodeId, procedure.getId());
            throw new BusinessException("No existe una transición saliente para el nodo completado");
        }

        WorkflowNode nextNode = findNode(nodes, nextTransition.getTo());
        procedure.setUpdatedAt(Instant.now());

        if (nextNode.getType() == NodeType.PARALLEL_JOIN) {
            handleParallelJoin(procedure, completedNodeId, nextNode, actorId, formData);
        } else {
            procedure.setCurrentNodeIds(List.of(nextNode.getNodeId()));
            processNode(procedure, nextNode, actorId);
        }
    }

    /**
     * Procesa un nodo: crea tareas, registra historia, actualiza estado del trámite.
     */
    private void processNode(Procedure procedure, WorkflowNode node, String actorId) {
        log.debug("Procesando nodo {} [{}] en trámite {}", node.getNodeId(), node.getType(), procedure.getId());

        switch (node.getType()) {

            case START -> {
                procedure.setStatus(ProcedureStatus.IN_PROGRESS);
                recordHistory(procedure.getId(), node, "NODE_STARTED", actorId, null, null);
                // START lleva a la siguiente transición automáticamente
                advanceAutomatic(procedure, node.getNodeId(), actorId);
            }

            case MANUAL_FORM, MANUAL_ACTION -> {
                procedure.setStatus(ProcedureStatus.IN_PROGRESS);
                recordHistory(procedure.getId(), node, "TASK_CREATED", actorId, null, null);
                createInternalTask(procedure, node);
                procedureRepository.save(procedure);
            }

            case CLIENT_TASK -> {
                procedure.setStatus(ProcedureStatus.WAITING_CLIENT);
                recordHistory(procedure.getId(), node, "CLIENT_TASK_CREATED", actorId, null, null);
                Task clientTask = createClientTask(procedure, node);
                procedureRepository.save(procedure);
                // Notificar al cliente por WebSocket
                notifyClient(procedure, clientTask);
            }

            case NOTIFICATION -> {
                String tmpl = node.getNotificationTemplate();
                recordHistory(procedure.getId(), node, "NOTIFICATION_SENT", actorId,
                        null, tmpl);
                // Enviar notificación push al cliente si hay clientId y plantilla
                if (procedure.getClientId() != null) {
                    sendNotificationToClient(procedure, node.getLabel(), tmpl);
                }
                procedureRepository.save(procedure);
                // Las notificaciones no detienen el flujo; avanzar automáticamente
                advanceAutomatic(procedure, node.getNodeId(), actorId);
            }

            case CONDITION -> {
                String result = evaluateCondition(procedure, node);
                recordHistory(procedure.getId(), node, "CONDITION_EVALUATED", actorId,
                        null, "result=" + result);
                // Buscar transición con etiqueta o condición que coincida con el resultado
                List<WorkflowTransition> allTransitions = procedure.getPolicySnapshot().getTransitions();
                WorkflowTransition branch = allTransitions.stream()
                        .filter(t -> t.getFrom().equals(node.getNodeId()))
                        .filter(t -> result.equalsIgnoreCase(t.getLabel())
                                || result.equalsIgnoreCase(t.getCondition()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                "No hay rama de condición para resultado: " + result));

                List<WorkflowNode> nodes = procedure.getPolicySnapshot().getNodes();
                WorkflowNode branchNode = findNode(nodes, branch.getTo());
                procedure.setCurrentNodeIds(List.of(branchNode.getNodeId()));
                procedureRepository.save(procedure);
                processNode(procedure, branchNode, actorId);
            }

            case PARALLEL_SPLIT -> {
                recordHistory(procedure.getId(), node, "PARALLEL_SPLIT", actorId, null, null);
                List<WorkflowTransition> outgoing = procedure.getPolicySnapshot().getTransitions().stream()
                        .filter(t -> t.getFrom().equals(node.getNodeId()))
                        .toList();
                List<String> terminals = new ArrayList<>();
                for (WorkflowTransition t : outgoing) {
                    WorkflowNode branchStart = findNode(procedure.getPolicySnapshot().getNodes(), t.getTo());
                    recordHistory(procedure.getId(), branchStart, "PARALLEL_BRANCH_STARTED", actorId, null, null);
                    terminals.addAll(activateBranch(procedure, branchStart, actorId));
                }
                procedure.setCurrentNodeIds(terminals.stream().distinct().toList());
                procedure.setStatus(ProcedureStatus.IN_PROGRESS);
                procedure.setUpdatedAt(Instant.now());
                procedureRepository.save(procedure);
            }

            case PARALLEL_JOIN -> {
                log.warn("PARALLEL_JOIN alcanzado directamente desde processNode (sin advance). Verificando…");
                handleParallelJoin(procedure, null, node, actorId, null);
            }

            case AUTOMATIC -> {
                recordHistory(procedure.getId(), node, "AUTOMATIC_PROCESSED", actorId, null, null);
                procedureRepository.save(procedure);
                advanceAutomatic(procedure, node.getNodeId(), actorId);
            }

            case END -> {
                var now = Instant.now();
                procedure.setStatus(ProcedureStatus.COMPLETED);
                procedure.setCompletedAt(now);
                procedure.setUpdatedAt(now);
                recordHistory(procedure.getId(), node, "PROCEDURE_COMPLETED", actorId, null, null);
                procedureRepository.save(procedure);
                log.info("Trámite {} completado", procedure.getId());
            }

            default -> {
                log.warn("Tipo de nodo no manejado: {}", node.getType());
                procedureRepository.save(procedure);
            }
        }
    }

    /**
     * Avanza automáticamente al siguiente nodo (sin intervención humana).
     * Usado por START, NOTIFICATION y AUTOMATIC.
     */
    private void advanceAutomatic(Procedure procedure, String fromNodeId, String actorId) {
        List<WorkflowTransition> transitions = procedure.getPolicySnapshot().getTransitions();
        List<WorkflowNode> nodes = procedure.getPolicySnapshot().getNodes();

        WorkflowTransition next = transitions.stream()
                .filter(t -> t.getFrom().equals(fromNodeId) && t.getCondition() == null)
                .findFirst()
                .orElse(null);

        if (next == null) return;

        WorkflowNode nextNode = findNode(nodes, next.getTo());
        procedure.setCurrentNodeIds(List.of(nextNode.getNodeId()));
        procedureRepository.save(procedure);
        processNode(procedure, nextNode, actorId);
    }

    // ── Creación de tareas ────────────────────────────────────────────────────

    private void createInternalTask(Procedure procedure, WorkflowNode node) {
        boolean versionedExecution = procedure.getPolicyVersionId() != null;
        if (versionedExecution) profileResolver.validateRuntimeNode(node);
        Instant activatedAt = Instant.now();
        Task task = Task.builder()
                .procedureId(procedure.getId())
                .procedureCode(procedure.getCode())
                .policyId(procedure.getPolicyId())
                .policyVersionId(procedure.getPolicyVersionId())
                .nodeId(node.getNodeId())
                .label(node.getLabel())
                .organizationId(procedure.getOrganizationId())
                .assignedDepartmentId(node.getDepartmentId())
                .taskAudience(TaskAudience.INTERNAL)
                .status(TaskStatus.PENDING)
                .formVersionId(node.getFormVersionId())
                .createdAt(activatedAt)
                .dueAt(calculateDueAt(activatedAt, node.getSlaHours()))
                .updatedAt(activatedAt)
                .build();
        taskRepository.save(task);
    }

    private Task createClientTask(Procedure procedure, WorkflowNode node) {
        boolean versionedExecution = procedure.getPolicyVersionId() != null;
        if (versionedExecution) profileResolver.validateRuntimeNode(node);
        Instant activatedAt = Instant.now();
        Task task = Task.builder()
                .procedureId(procedure.getId())
                .procedureCode(procedure.getCode())
                .policyId(procedure.getPolicyId())
                .policyVersionId(procedure.getPolicyVersionId())
                .nodeId(node.getNodeId())
                .label(node.getLabel())
                .organizationId(procedure.getOrganizationId())
                .assignedDepartmentId(node.getDepartmentId())
                .taskAudience(TaskAudience.CLIENT)
                .status(TaskStatus.PENDING)
                .assignedClientId(procedure.getClientId())
                .formVersionId(node.getFormVersionId())
                .createdAt(activatedAt)
                .dueAt(calculateDueAt(activatedAt, node.getSlaHours()))
                .updatedAt(activatedAt)
                .build();
        return taskRepository.save(task);
    }

    private Instant calculateDueAt(Instant activatedAt, Integer slaHours) {
        return slaHours == null ? null : activatedAt.plus(slaHours, ChronoUnit.HOURS);
    }

    /** Envía notificación WebSocket al cliente cuando se le asigna una CLIENT_TASK */
    private void notifyClient(Procedure procedure, Task task) {
        try {
            notificationService.create(new CreateNotificationRequest(
                    procedure.getOrganizationId(),
                    procedure.getClientId(),
                    null,  // userId del cliente (no disponible aquí; el push va por clientId)
                    procedure.getCode(),
                    "CLIENT_TASK_CREATED",
                    "Acción requerida en su trámite",
                    "El trámite " + procedure.getCode() + " requiere su atención: " + task.getLabel(),
                    procedure.getId(),
                    task.getId()
            ));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación para el trámite {}: {}", procedure.getCode(), e.getMessage());
        }
    }

    /** Envía notificación push al cliente desde un nodo NOTIFICATION */
    private void sendNotificationToClient(Procedure procedure, String title, String message) {
        try {
            String body = (message != null && !message.isBlank()) ? message
                    : "Su trámite " + procedure.getCode() + " ha sido actualizado.";
            notificationService.create(new CreateNotificationRequest(
                    procedure.getOrganizationId(),
                    procedure.getClientId(),
                    null,
                    procedure.getCode(),
                    "NOTIFICATION",
                    title != null ? title : "Actualización de trámite",
                    body,
                    procedure.getId(),
                    null
            ));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación al cliente para trámite {}: {}", procedure.getCode(), e.getMessage());
        }
    }

    // ── Resolución de transiciones ────────────────────────────────────────────

    /**
     * Elige la transición saliente del nodo completado.
     * Prioriza transiciones sin condición (por defecto).
     */
    private WorkflowTransition resolveTransition(
            List<WorkflowTransition> transitions,
            String fromNodeId,
            Map<String, Map<String, Object>> allFormData,
            Map<String, Object> lastFormData) {

        List<WorkflowTransition> outgoing = transitions.stream()
                .filter(t -> t.getFrom().equals(fromNodeId))
                .toList();

        if (outgoing.isEmpty()) return null;

        // Si hay solo una transición, tomarla directamente
        if (outgoing.size() == 1) return outgoing.getFirst();

        // Si alguna tiene condición evaluable, evaluarla
        for (WorkflowTransition t : outgoing) {
            if (t.getCondition() == null) continue;
            if (evaluateSimpleCondition(t.getCondition(), lastFormData, allFormData)) {
                return t;
            }
        }

        // Transición por defecto (sin condición)
        return outgoing.stream()
                .filter(t -> t.getCondition() == null)
                .findFirst()
                .orElse(outgoing.getFirst());
    }

    /**
     * Evalúa una condición simple del tipo "fieldId == value" o "fieldId != value".
     * Busca el fieldId primero en lastFormData, luego en allFormData (acumulado).
     */
    private boolean evaluateSimpleCondition(String condition,
                                             Map<String, Object> lastFormData,
                                             Map<String, Map<String, Object>> allFormData) {
        if (condition == null || condition.isBlank()) return true;

        try {
            String fieldId;
            String expectedValue;
            boolean isEquality;

            if (condition.contains("!=")) {
                String[] parts = condition.split("!=", 2);
                fieldId = parts[0].trim();
                expectedValue = parts[1].trim();
                isEquality = false;
            } else if (condition.contains("==")) {
                String[] parts = condition.split("==", 2);
                fieldId = parts[0].trim();
                expectedValue = parts[1].trim();
                isEquality = true;
            } else {
                return false;
            }

            Object actual = resolveFieldValue(fieldId, lastFormData, allFormData);
            if (actual == null) return false;

            boolean match = actual.toString().equalsIgnoreCase(expectedValue);
            return isEquality == match;
        } catch (Exception e) {
            log.warn("Error al evaluar condición '{}': {}", condition, e.getMessage());
        }
        return false;
    }

    private Object resolveFieldValue(String fieldId,
                                      Map<String, Object> lastFormData,
                                      Map<String, Map<String, Object>> allFormData) {
        if (lastFormData != null && lastFormData.containsKey(fieldId)) {
            return lastFormData.get(fieldId);
        }
        if (allFormData != null) {
            for (Map<String, Object> nodeForm : allFormData.values()) {
                if (nodeForm != null && nodeForm.containsKey(fieldId)) {
                    return nodeForm.get(fieldId);
                }
            }
        }
        return null;
    }

    /**
     * Evaluación de nodo CONDITION.
     * Evalúa cada transición saliente contra los formData acumulados
     * y retorna la etiqueta de la primera que coincida.
     * Si ninguna condición coincide, toma la transición por defecto (sin condición).
     */
    private String evaluateCondition(Procedure procedure, WorkflowNode node) {
        Map<String, Map<String, Object>> allFormData = procedure.getFormData();

        // Último formData disponible: el del nodo inmediatamente anterior
        Map<String, Object> lastFormData = null;
        if (allFormData != null && !allFormData.isEmpty()) {
            List<String> keys = new ArrayList<>(allFormData.keySet());
            lastFormData = allFormData.get(keys.getLast());
        }

        List<WorkflowTransition> outgoing = procedure.getPolicySnapshot().getTransitions().stream()
                .filter(t -> t.getFrom().equals(node.getNodeId()))
                .toList();

        String defaultLabel = null;

        for (WorkflowTransition t : outgoing) {
            if (t.getCondition() == null || t.getCondition().isBlank()) {
                if (defaultLabel == null) defaultLabel = t.getLabel();
                continue;
            }
            if (evaluateSimpleCondition(t.getCondition(), lastFormData, allFormData)) {
                log.debug("CONDITION {} → coincide con transición '{}'", node.getNodeId(), t.getLabel());
                return t.getLabel();
            }
        }

        if (defaultLabel != null) {
            log.debug("CONDITION {} → sin condiciones coincidentes, usa default '{}'", node.getNodeId(), defaultLabel);
            return defaultLabel;
        }

        log.debug("CONDITION {} → sin transiciones, retorna 'true'", node.getNodeId());
        return "true";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkflowNode findNode(List<WorkflowNode> nodes, String nodeId) {
        return nodes.stream()
                .filter(n -> n.getNodeId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Nodo no encontrado en snapshot: " + nodeId));
    }

    private void recordHistory(String procedureId, WorkflowNode node, String eventType,
                                String actorId, Map<String, Object> formData, String notes) {
        ProcedureHistory event = ProcedureHistory.builder()
                .procedureId(procedureId)
                .nodeId(node.getNodeId())
                .nodeLabel(node.getLabel())
                .eventType(eventType)
                .userId(actorId)
                .formData(formData)
                .notes(notes)
                .occurredAt(Instant.now())
                .build();
        historyRepository.save(event);
    }

    // ── Parallel split / join ──────────────────────────────────────────────

    /**
     * Activa una rama desde su nodo inicial, recorriendo nodos automáticos
     * hasta llegar a un nodo que crea tarea (MANUAL_FORM, MANUAL_ACTION, CLIENT_TASK)
     * y devuelve los nodeId terminales activos.
     */
    private List<String> activateBranch(Procedure procedure, WorkflowNode node, String actorId) {
        return switch (node.getType()) {
            case MANUAL_FORM, MANUAL_ACTION -> {
                createInternalTask(procedure, node);
                yield List.of(node.getNodeId());
            }
            case CLIENT_TASK -> {
                Task task = createClientTask(procedure, node);
                notifyClient(procedure, task);
                yield List.of(node.getNodeId());
            }
            case NOTIFICATION -> {
                if (procedure.getClientId() != null) {
                    sendNotificationToClient(procedure, node.getLabel(), node.getNotificationTemplate());
                }
                yield advanceThroughAuto(procedure, node.getNodeId(), actorId);
            }
            case AUTOMATIC -> advanceThroughAuto(procedure, node.getNodeId(), actorId);
            case CONDITION -> {
                String result = evaluateCondition(procedure, node);
                List<WorkflowTransition> branches = procedure.getPolicySnapshot().getTransitions().stream()
                        .filter(t -> t.getFrom().equals(node.getNodeId()))
                        .filter(t -> result.equalsIgnoreCase(t.getLabel())
                                || result.equalsIgnoreCase(t.getCondition()))
                        .toList();
                if (branches.isEmpty()) yield List.of();
                WorkflowNode next = findNode(procedure.getPolicySnapshot().getNodes(), branches.getFirst().getTo());
                yield activateBranch(procedure, next, actorId);
            }
            case PARALLEL_SPLIT -> {
                List<WorkflowTransition> outgoing = procedure.getPolicySnapshot().getTransitions().stream()
                        .filter(t -> t.getFrom().equals(node.getNodeId()))
                        .toList();
                List<String> terminals = new ArrayList<>();
                for (WorkflowTransition t : outgoing) {
                    WorkflowNode branchStart = findNode(procedure.getPolicySnapshot().getNodes(), t.getTo());
                    terminals.addAll(activateBranch(procedure, branchStart, actorId));
                }
                yield terminals;
            }
            default -> List.of(node.getNodeId());
        };
    }

    /**
     * Avanza automáticamente por nodos sin intervención humana
     * hasta dar con un nodo que cree una tarea. Usado desde activateBranch.
     */
    private List<String> advanceThroughAuto(Procedure procedure, String fromNodeId, String actorId) {
        List<WorkflowTransition> outgoing = procedure.getPolicySnapshot().getTransitions().stream()
                .filter(t -> t.getFrom().equals(fromNodeId) && t.getCondition() == null)
                .toList();
        if (outgoing.isEmpty()) return List.of();
        WorkflowNode next = findNode(procedure.getPolicySnapshot().getNodes(), outgoing.getFirst().getTo());
        return activateBranch(procedure, next, actorId);
    }

    /**
     * Maneja la llegada de una rama paralela a un nodo PARALLEL_JOIN.
     * Acumula las ramas que ya llegaron y cuando todas completan,
     * avanza más allá del join.
     */
    private void handleParallelJoin(Procedure procedure, String completedNodeId,
                                     WorkflowNode joinNode, String actorId,
                                     Map<String, Object> formData) {
        List<String> currentIds = new ArrayList<>(procedure.getCurrentNodeIds());

        if (completedNodeId != null) {
            recordHistory(procedure.getId(), joinNode, "PARALLEL_BRANCH_ARRIVED", actorId, formData,
                    "branch=" + completedNodeId);
            currentIds.remove(completedNodeId);
        }

        List<WorkflowTransition> incoming = procedure.getPolicySnapshot().getTransitions().stream()
                .filter(t -> t.getTo().equals(joinNode.getNodeId()))
                .toList();

        boolean allArrived = incoming.stream()
                .allMatch(t -> !currentIds.contains(t.getFrom()));

        if (allArrived) {
            log.info("Todas las ramas paralelas llegaron al join {} en trámite {}",
                    joinNode.getNodeId(), procedure.getId());
            recordHistory(procedure.getId(), joinNode, "PARALLEL_JOIN_COMPLETED", actorId, null, null);

            List<WorkflowTransition> outgoing = procedure.getPolicySnapshot().getTransitions().stream()
                    .filter(t -> t.getFrom().equals(joinNode.getNodeId()))
                    .toList();

            if (outgoing.isEmpty()) {
                procedure.setCurrentNodeIds(List.of());
                procedure.setUpdatedAt(Instant.now());
                procedureRepository.save(procedure);
            } else {
                WorkflowNode nextNode = findNode(procedure.getPolicySnapshot().getNodes(), outgoing.getFirst().getTo());
                procedure.setCurrentNodeIds(List.of(nextNode.getNodeId()));
                procedure.setUpdatedAt(Instant.now());
                procedure.setStatus(ProcedureStatus.IN_PROGRESS);
                procedureRepository.save(procedure);
                processNode(procedure, nextNode, actorId);
            }
        } else {
            log.info("Join {} esperando ramas restantes en trámite {} (activos: {})",
                    joinNode.getNodeId(), procedure.getId(), currentIds);
            procedure.setCurrentNodeIds(currentIds);
            procedure.setUpdatedAt(Instant.now());
            procedureRepository.save(procedure);
        }
    }

    /**
     * Inicializa el trámite posicionándolo en el nodo START y avanzando.
     */
    public void start(Procedure procedure, String actorId) {
        List<WorkflowNode> nodes = procedure.getPolicySnapshot().getNodes();
        WorkflowNode startNode = nodes.stream()
                .filter(n -> n.getType() == NodeType.START)
                .findFirst()
                .orElseThrow(() -> new BusinessException("La política no tiene nodo START"));

        procedure.setCurrentNodeIds(List.of(startNode.getNodeId()));
        procedure.setStatus(ProcedureStatus.IN_PROGRESS);
        procedureRepository.save(procedure);

        processNode(procedure, startNode, actorId);
    }
}
