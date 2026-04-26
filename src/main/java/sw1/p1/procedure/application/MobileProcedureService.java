package sw1.p1.procedure.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.client.domain.ClientRepository;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.policy.domain.WorkflowPolicy;
import sw1.p1.policy.domain.WorkflowPolicyRepository;
import sw1.p1.procedure.domain.*;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.procedure.dto.ProcedureSummaryResponse;
import sw1.p1.procedure.dto.StartProcedureRequest;
import sw1.p1.shared.PolicyStatus;
import sw1.p1.shared.ProcedureStatus;
import sw1.p1.shared.TaskAudience;
import sw1.p1.shared.TaskStatus;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;
import sw1.p1.task.dto.CompleteTaskRequest;
import sw1.p1.task.dto.TaskResponse;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileProcedureService {

    private final ProcedureRepository procedureRepository;
    private final ProcedureHistoryRepository historyRepository;
    private final WorkflowPolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final TaskRepository taskRepository;
    private final WorkflowEngineService workflowEngine;

    /** Obtiene el clientId del usuario autenticado */
    private String currentClientId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .map(u -> u.getClientId())
                .orElseThrow(() -> new BusinessException("El usuario no tiene un clientId asociado"));
    }

    private String currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .map(u -> u.getId())
                .orElse(null);
    }

    public Page<ProcedureSummaryResponse> myProcedures(Pageable pageable) {
        String clientId = currentClientId();
        return procedureRepository.findByClientId(clientId, pageable)
                .map(this::toSummary);
    }

    public ProcedureResponse findById(String id) {
        String clientId = currentClientId();
        Procedure procedure = getOrThrow(id);
        if (!clientId.equals(procedure.getClientId())) {
            throw new NotFoundException("Trámite no encontrado: " + id);
        }
        return toResponse(procedure);
    }

    public List<ProcedureHistory> getHistory(String procedureId) {
        String clientId = currentClientId();
        Procedure procedure = getOrThrow(procedureId);
        if (!clientId.equals(procedure.getClientId())) {
            throw new NotFoundException("Trámite no encontrado: " + procedureId);
        }
        return historyRepository.findByProcedureIdOrderByOccurredAtAsc(procedureId);
    }

    public ProcedureResponse start(StartProcedureRequest request) {
        WorkflowPolicy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new NotFoundException("Política no encontrada: " + request.policyId()));

        if (policy.getStatus() != PolicyStatus.PUBLISHED) {
            throw new BusinessException("Solo se pueden iniciar trámites con políticas PUBLISHED");
        }
        if (!policy.getAllowedStartChannels().contains("MOBILE")) {
            throw new BusinessException("Esta política no permite iniciar trámites por canal MOBILE");
        }

        String clientId = currentClientId();
        String userId = currentUserId();

        // Verificar que el clientId de la solicitud corresponde al cliente autenticado
        if (request.clientId() != null && !request.clientId().equals(clientId)) {
            throw new BusinessException("No puede iniciar trámites en nombre de otro cliente");
        }

        PolicySnapshot snapshot = PolicySnapshot.builder()
                .policyId(policy.getId())
                .policyKey(policy.getPolicyKey())
                .policyName(policy.getName())
                .version(policy.getVersion())
                .status(policy.getStatus())
                .nodes(policy.getNodes())
                .transitions(policy.getTransitions())
                .snapshotAt(Instant.now())
                .build();

        Procedure procedure = Procedure.builder()
                .organizationId(request.organizationId())
                .clientId(clientId)
                .startedBy(userId)
                .status(ProcedureStatus.CREATED)
                .policySnapshot(snapshot)
                .startChannel("MOBILE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        procedure = procedureRepository.save(procedure);
        workflowEngine.start(procedure, userId);

        procedure = getOrThrow(procedure.getId());
        return toResponse(procedure);
    }

    public Page<TaskResponse> myTasks(Pageable pageable) {
        String clientId = currentClientId();
        return taskRepository.findByAssignedClientIdAndTaskAudience(clientId, TaskAudience.CLIENT, pageable)
                .map(this::toTaskResponse);
    }

    public TaskResponse completeTask(String taskId, CompleteTaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Tarea no encontrada: " + taskId));

        if (task.getTaskAudience() != TaskAudience.CLIENT) {
            throw new BusinessException("Esta tarea no es de tipo CLIENT");
        }

        String clientId = currentClientId();
        if (!clientId.equals(task.getAssignedClientId())) {
            throw new BusinessException("No tiene permiso para completar esta tarea");
        }

        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BusinessException("La tarea no puede completarse en su estado actual");
        }

        String userId = currentUserId();
        task.setFormResponse(request.formResponse());
        task.setNotes(request.notes());
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        Procedure procedure = procedureRepository.findById(task.getProcedureId())
                .orElseThrow(() -> new NotFoundException("Trámite no encontrado: " + task.getProcedureId()));

        workflowEngine.advance(procedure, task.getNodeId(), userId, request.formResponse());

        return toTaskResponse(task);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Procedure getOrThrow(String id) {
        return procedureRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Trámite no encontrado: " + id));
    }

    private ProcedureResponse toResponse(Procedure p) {
        return new ProcedureResponse(
                p.getId(), p.getOrganizationId(), p.getClientId(), p.getStartedBy(),
                p.getCurrentNodeId(), p.getStatus(), p.getPolicySnapshot(),
                p.getFormData(), p.getStartChannel(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    private ProcedureSummaryResponse toSummary(Procedure p) {
        PolicySnapshot snap = p.getPolicySnapshot();
        return new ProcedureSummaryResponse(
                p.getId(), p.getOrganizationId(), p.getClientId(),
                p.getCurrentNodeId(), p.getStatus(),
                snap != null ? snap.getPolicyName() : null,
                snap != null ? snap.getVersion() : 0,
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    private TaskResponse toTaskResponse(Task t) {
        return new TaskResponse(
                t.getId(), t.getProcedureId(), t.getNodeId(), t.getNodeLabel(),
                t.getOrganizationId(), t.getAreaId(), t.getTaskAudience(), t.getStatus(),
                t.getAssignedOfficerId(), t.getAssignedClientId(),
                t.getForm(), t.getFormResponse(), t.getNotes(),
                t.getCreatedAt(), t.getCompletedAt()
        );
    }
}
