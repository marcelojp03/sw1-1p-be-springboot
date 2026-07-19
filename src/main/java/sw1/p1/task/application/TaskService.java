package sw1.p1.task.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.procedure.application.WorkflowEngineService;
import sw1.p1.procedure.domain.Procedure;
import sw1.p1.procedure.domain.ProcedureRepository;
import sw1.p1.shared.TaskStatus;
import sw1.p1.shared.storage.AttachmentRef;
import sw1.p1.shared.storage.StorageService;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;
import sw1.p1.task.dto.CompleteTaskRequest;
import sw1.p1.task.dto.TaskResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProcedureRepository procedureRepository;
    private final UserRepository userRepository;
    private final WorkflowEngineService workflowEngine;
    private final StorageService storageService;

    /** Bandeja: tareas PENDING del departamento. */
    public Page<TaskResponse> findByDepartment(String departmentId, Pageable pageable) {
        return taskRepository.findByAssignedDepartmentIdAndStatus(departmentId, TaskStatus.PENDING, pageable)
                .map(this::toResponse);
    }

    /** Tareas asignadas al OFFICER actual */
    public Page<TaskResponse> myTasks(Pageable pageable) {
        String officerId = currentUserId();
        return taskRepository.findByAssignedUserIdAndStatus(officerId, TaskStatus.IN_PROGRESS, pageable)
                .map(this::toResponse);
    }

    public TaskResponse findById(String id) {
        return toResponse(getOrThrow(id));
    }

    /** El OFFICER toma la tarea (asignación) */
    public TaskResponse claimTask(String taskId) {
        Task task = getOrThrow(taskId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new BusinessException("Solo se pueden tomar tareas en estado PENDING");
        }
        String officerId = currentUserId();
        task.setAssignedUserId(officerId);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return toResponse(taskRepository.save(task));
    }

    /** El OFFICER completa la tarea y el motor avanza el trámite */
    public TaskResponse completeTask(String taskId, CompleteTaskRequest request) {
        Task task = getOrThrow(taskId);

        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BusinessException("Solo se pueden completar tareas en estado IN_PROGRESS");
        }

        String officerId = currentUserId();
        if (!officerId.equals(task.getAssignedUserId())) {
            throw new BusinessException("Solo el funcionario asignado puede completar esta tarea");
        }

        task.setFormResponse(request.formResponse());
        task.setNotes(request.notes());
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedBy(officerId);
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        // Avanzar el motor de workflow
        Procedure procedure = procedureRepository.findById(task.getProcedureId())
                .orElseThrow(() -> new NotFoundException("Trámite no encontrado: " + task.getProcedureId()));

        workflowEngine.advance(procedure, task.getNodeId(), officerId, request.formResponse());

        return toResponse(task);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Task getOrThrow(String id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tarea no encontrada: " + id));
    }

    private String currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(username)
                .map(u -> u.getId())
                .orElse(null);
    }

    private TaskResponse toResponse(Task t) {
        return new TaskResponse(
                t.getId(), t.getProcedureId(), t.getProcedureCode(), t.getPolicyId(), t.getPolicyVersionId(),
                t.getNodeId(), t.getLabel(), t.getOrganizationId(), t.getAssignedDepartmentId(),
                t.getTaskAudience(), t.getStatus(), t.getAssignedUserId(), t.getAssignedClientId(),
                t.getFormVersionId(), t.getForm(), t.getFormResponse(), t.getNotes(), t.getCompletedBy(),
                t.getAttachments(),
                t.getCreatedAt(), t.getStartedAt(), t.getDueAt(), t.getCompletedAt()
        );
    }

    /** El OFFICER adjunta archivos a una tarea (los sube a S3) */
    public TaskResponse addAttachments(String taskId, MultipartFile[] files) {
        Task task = getOrThrow(taskId);

        List<AttachmentRef> current = task.getAttachments() == null
                ? new ArrayList<>() : new ArrayList<>(task.getAttachments());

        for (MultipartFile file : files) {
            String storageKey = String.format("procedures/%s/tasks/%s/%s",
                    task.getProcedureId(), taskId, file.getOriginalFilename());
            AttachmentRef ref = storageService.upload(file, storageKey);
            current.add(ref);
        }

        task.setAttachments(current);
        task.setUpdatedAt(Instant.now());
        return toResponse(taskRepository.save(task));
    }
}
