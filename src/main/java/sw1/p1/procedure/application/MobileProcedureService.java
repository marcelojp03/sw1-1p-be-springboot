package sw1.p1.procedure.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sw1.p1.auth.domain.User;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.client.domain.Client;
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
import sw1.p1.shared.storage.AttachmentRef;
import sw1.p1.shared.storage.StorageService;
import sw1.p1.notification.application.NotificationService;
import sw1.p1.notification.dto.NotificationResponse;
import sw1.p1.policy.dto.AvailablePolicyResponse;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;
import sw1.p1.task.dto.CompleteTaskRequest;
import sw1.p1.task.dto.TaskResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final NotificationService notificationService;
    private final StorageService storageService;

    /** Obtiene el clientId del usuario autenticado */
    private String currentClientId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuario autenticado no encontrado"));
        return clientRepository.findByUserId(user.getId())
                .map(Client::getId)
                .orElseThrow(() -> new BusinessException("El usuario no tiene un cliente asociado"));
    }

    private String currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(username)
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

        var client = clientRepository.findById(clientId).orElse(null);
        Procedure.RequesterInfo requester = null;
        if (client != null) {
            requester = Procedure.RequesterInfo.builder()
                    .fullName(client.getFullName())
                    .documentType(client.getDocumentType())
                    .documentNumber(client.getDocumentNumber())
                    .phone(client.getPhone())
                    .email(client.getEmail())
                    .build();
        }

        Instant now = Instant.now();
        long sequential = procedureRepository.count() + 1;
        int year = ZonedDateTime.now(ZoneOffset.UTC).getYear();
        String code = String.format("TRM-%d-%04d", year, sequential);

        Procedure procedure = Procedure.builder()
                .code(code)
                .organizationId(request.organizationId())
                .policyId(policy.getId())
                .policyVersion(policy.getVersion())
                .clientId(clientId)
                .startedBy(userId)
                .requester(requester)
                .status(ProcedureStatus.CREATED)
                .policySnapshot(snapshot)
                .startChannel("MOBILE")
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        procedure = procedureRepository.save(procedure);
        workflowEngine.start(procedure, userId);

        procedure = getOrThrow(procedure.getId());
        return toResponse(procedure);
    }

    /** Políticas disponibles para iniciar trámites desde canal MOBILE */
    public List<AvailablePolicyResponse> availablePolicies(String organizationId) {
        return policyRepository
                .findByOrganizationIdAndStatusAndAllowedStartChannelsContaining(
                        organizationId, PolicyStatus.PUBLISHED, "MOBILE")
                .stream()
                .map(p -> new AvailablePolicyResponse(
                        p.getId(), p.getPolicyKey(), p.getName(),
                        p.getDescription(), p.getVersion(), p.getAllowedStartChannels()))
                .toList();
    }

    public Page<TaskResponse> myTasks(Pageable pageable) {        String clientId = currentClientId();
        return taskRepository.findByAssignedClientIdAndTaskAudience(clientId, TaskAudience.CLIENT, pageable)
                .map(this::toTaskResponse);
    }

    public TaskResponse findTaskById(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Tarea no encontrada: " + taskId));
        String clientId = currentClientId();
        if (!clientId.equals(task.getAssignedClientId())) {
            throw new BusinessException("No tiene permiso para ver esta tarea");
        }
        return toTaskResponse(task);
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

        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new ConflictException("La tarea ya fue completada");
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

    /** Agregar archivos a una CLIENT_TASK subiéndolos a S3 y registrando un evento por archivo */
    public Map<String, String> getAttachmentDownloadUrl(String taskId, String fileName) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Tarea no encontrada: " + taskId));

        if (task.getAttachments() == null) {
            throw new NotFoundException("La tarea no tiene archivos adjuntos");
        }

        AttachmentRef ref = task.getAttachments().stream()
                .filter(a -> a.getFileName().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Archivo no encontrado: " + fileName));

        String url = storageService.generatePresignedUrl(ref.getStorageKey(), Duration.ofHours(1));
        return Map.of("url", url, "fileName", ref.getFileName());
    }

    public TaskResponse uploadAttachments(String taskId, MultipartFile[] files) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Tarea no encontrada: " + taskId));

        if (task.getTaskAudience() != TaskAudience.CLIENT) {
            throw new BusinessException("Esta tarea no es de tipo CLIENT");
        }

        String clientId = currentClientId();
        if (!clientId.equals(task.getAssignedClientId())) {
            throw new BusinessException("No tiene permiso para adjuntar documentos a esta tarea");
        }

        List<AttachmentRef> current = task.getAttachments() == null
                ? new ArrayList<>() : new ArrayList<>(task.getAttachments());

        for (MultipartFile file : files) {
            String storageKey = String.format("procedures/%s/client/%s/%s",
                    task.getProcedureId(), taskId, file.getOriginalFilename());
            AttachmentRef ref = storageService.upload(file, storageKey);
            current.add(ref);

            // Un evento por cada archivo subido
            ProcedureHistory event = ProcedureHistory.builder()
                    .procedureId(task.getProcedureId())
                    .nodeId(task.getNodeId())
                    .eventType("CLIENT_DOCUMENT_UPLOADED")
                    .userId(currentUserId())
                    .notes("Cliente subió: " + ref.getFileName())
                    .attachment(ref)
                    .occurredAt(Instant.now())
                    .build();
            historyRepository.save(event);
        }

        task.setAttachments(current);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        return toTaskResponse(task);
    }

    /** Notificaciones del cliente autenticado */
    public Page<NotificationResponse> myNotifications(Boolean unreadOnly, Pageable pageable) {
        String clientId = currentClientId();
        return notificationService.myClientNotifications(clientId, unreadOnly, pageable);
    }

    /** Marcar notificación como leída */
    public NotificationResponse markNotificationAsRead(String notificationId) {
        String clientId = currentClientId();
        return notificationService.markAsReadForClient(notificationId, clientId);
    }

    private Procedure getOrThrow(String id) {
        return procedureRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Trámite no encontrado: " + id));
    }

    private ProcedureResponse toResponse(Procedure p) {
        return new ProcedureResponse(
                p.getId(), p.getCode(), p.getOrganizationId(), p.getPolicyId(),
                p.getPolicyVersionId(), p.getPolicyVersion(),
                p.getClientId(), p.getStartedBy(), p.getRequester(), p.getCurrentNodeIds(),
                p.getStatus(), p.getPolicySnapshot(), p.getFormData(), p.getStartChannel(),
                p.getStartedAt(), p.getCompletedAt(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    private ProcedureSummaryResponse toSummary(Procedure p) {
        PolicySnapshot snap = p.getPolicySnapshot();
        return new ProcedureSummaryResponse(
                p.getId(), p.getCode(), p.getOrganizationId(), p.getClientId(),
                p.getCurrentNodeIds(), p.getStatus(),
                snap != null ? snap.getPolicyName() : null,
                snap != null ? snap.getVersion() : 0,
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    private TaskResponse toTaskResponse(Task t) {
        return new TaskResponse(
                t.getId(), t.getProcedureId(), t.getProcedureCode(), t.getPolicyId(),
                t.getNodeId(), t.getLabel(), t.getOrganizationId(), t.getAssignedAreaId(),
                t.getTaskAudience(), t.getStatus(), t.getAssignedUserId(), t.getAssignedClientId(),
                t.getForm(), t.getFormResponse(), t.getNotes(), t.getCompletedBy(),
                t.getAttachments(),
                t.getCreatedAt(), t.getStartedAt(), t.getDueAt(), t.getCompletedAt()
        );
    }
}
