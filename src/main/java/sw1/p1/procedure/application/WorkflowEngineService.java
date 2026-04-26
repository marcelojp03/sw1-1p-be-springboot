package sw1.p1.procedure.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sw1.p1.exception.BusinessException;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.policy.domain.WorkflowTransition;
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
        procedure.setCurrentNodeIds(List.of(nextNode.getNodeId()));
        procedure.setUpdatedAt(Instant.now());

        processNode(procedure, nextNode, actorId);
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
                createClientTask(procedure, node);
                procedureRepository.save(procedure);
            }

            case NOTIFICATION -> {
                recordHistory(procedure.getId(), node, "NOTIFICATION_SENT", actorId,
                        null, node.getNotificationTemplate());
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

            case AUTOMATIC -> {
                recordHistory(procedure.getId(), node, "AUTOMATIC_PROCESSED", actorId, null, null);
                procedureRepository.save(procedure);
                advanceAutomatic(procedure, node.getNodeId(), actorId);
            }

            case END -> {
                procedure.setStatus(ProcedureStatus.COMPLETED);
                procedure.setUpdatedAt(Instant.now());
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
        Task task = Task.builder()
                .procedureId(procedure.getId())
                .procedureCode(procedure.getCode())
                .policyId(procedure.getPolicyId())
                .nodeId(node.getNodeId())
                .label(node.getLabel())
                .organizationId(procedure.getOrganizationId())
                .assignedAreaId(node.getAreaId())
                .taskAudience(TaskAudience.INTERNAL)
                .status(TaskStatus.PENDING)
                .form(node.getForm())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        taskRepository.save(task);
    }

    private void createClientTask(Procedure procedure, WorkflowNode node) {
        Task task = Task.builder()
                .procedureId(procedure.getId())
                .procedureCode(procedure.getCode())
                .policyId(procedure.getPolicyId())
                .nodeId(node.getNodeId())
                .label(node.getLabel())
                .organizationId(procedure.getOrganizationId())
                .assignedAreaId(node.getAreaId())
                .taskAudience(TaskAudience.CLIENT)
                .status(TaskStatus.PENDING)
                .assignedClientId(procedure.getClientId())
                .form(node.getForm())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        taskRepository.save(task);
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
     */
    private boolean evaluateSimpleCondition(String condition,
                                             Map<String, Object> lastFormData,
                                             Map<String, Map<String, Object>> allFormData) {
        if (condition == null || condition.isBlank()) return true;
        if (lastFormData == null) return false;

        try {
            if (condition.contains("!=")) {
                String[] parts = condition.split("!=", 2);
                Object val = lastFormData.get(parts[0].trim());
                return val != null && !val.toString().equalsIgnoreCase(parts[1].trim());
            }
            if (condition.contains("==")) {
                String[] parts = condition.split("==", 2);
                Object val = lastFormData.get(parts[0].trim());
                return val != null && val.toString().equalsIgnoreCase(parts[1].trim());
            }
        } catch (Exception e) {
            log.warn("Error al evaluar condición '{}': {}", condition, e.getMessage());
        }
        return false;
    }

    /**
     * Evaluación de nodo CONDITION.
     * Retorna "true" o "false" (o el valor del campo de decisión si está definido).
     */
    private String evaluateCondition(Procedure procedure, WorkflowNode node) {
        // Por ahora: siempre "true". Se puede extender con scripting o SpEL.
        log.debug("Evaluando nodo CONDITION {} — devolviendo 'true' por defecto", node.getNodeId());
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
