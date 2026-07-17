package sw1.p1.procedure.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sw1.p1.notification.dto.NotificationResponse;
import sw1.p1.policy.dto.AvailablePolicyResponse;
import sw1.p1.procedure.application.MobileProcedureService;
import sw1.p1.procedure.application.ProcedureService;
import sw1.p1.procedure.domain.ProcedureHistory;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.procedure.dto.ProcedureSummaryResponse;
import sw1.p1.procedure.dto.StartProcedureRequest;
import sw1.p1.task.dto.CompleteTaskRequest;
import sw1.p1.task.dto.TaskResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class MobileProcedureController {

    private final MobileProcedureService mobileService;
    private final ProcedureService procedureService;

    // ── Políticas disponibles ──────────────────────────────────────────────────

    /** Políticas PUBLISHED que permiten inicio por canal MOBILE */
    @GetMapping("/workflow-policies/available")
    public List<AvailablePolicyResponse> availablePolicies(@RequestParam String organizationId) {
        return mobileService.availablePolicies(organizationId);
    }

    // ── Trámites ───────────────────────────────────────────────────────────────

    /** Trámites del cliente autenticado */
    @GetMapping("/procedures")
    public Page<ProcedureSummaryResponse> myProcedures(Pageable pageable) {
        return mobileService.myProcedures(pageable);
    }

    /** Trámites del cliente autenticado (alias sin paginación para Flutter) */
    @GetMapping("/procedures/my")
    public Page<ProcedureSummaryResponse> myProceduresAlias(Pageable pageable) {
        return mobileService.myProcedures(pageable);
    }

    /** Detalle de un trámite */
    @GetMapping("/procedures/{id}")
    public ProcedureResponse findById(@PathVariable String id) {
        return mobileService.findById(id);
    }

    /** Historial de un trámite */
    @GetMapping("/procedures/{id}/history")
    public List<ProcedureHistory> getHistory(@PathVariable String id) {
        return mobileService.getHistory(id);
    }

    /** Iniciar un trámite (solo si la política permite canal MOBILE) */
    @PostMapping("/procedures")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcedureResponse start(@Valid @RequestBody StartProcedureRequest request) {
        return mobileService.start(request);
    }

    /** Iniciar trámite desde una PolicyVersion publicada (CLIENT autenticado) */
    @PostMapping("/procedures/from-version/{policyId}/{versionId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcedureResponse startFromVersion(@PathVariable String policyId,
                                               @PathVariable String versionId,
                                               Authentication authentication) {
        return procedureService.startFromVersionForClient(policyId, versionId, authentication);
    }

    // ── Tareas ─────────────────────────────────────────────────────────────────

    /** CLIENT_TASKs pendientes del cliente autenticado */
    @GetMapping("/tasks")
    public Page<TaskResponse> myTasks(Pageable pageable) {
        return mobileService.myTasks(pageable);
    }

    /** Completar una CLIENT_TASK */
    @PostMapping("/tasks/{id}/complete")
    public TaskResponse completeTask(@PathVariable String id,
                                     @Valid @RequestBody CompleteTaskRequest request) {
        return mobileService.completeTask(id, request);
    }

    /** Adjuntar documentos a una CLIENT_TASK (sube a S3) */
    @PostMapping(value = "/tasks/{id}/attachments", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse uploadAttachments(
            @PathVariable String id,
            @RequestParam("files") MultipartFile[] files) {
        return mobileService.uploadAttachments(id, files);
    }

    /** Obtener URL pre-firmada para descargar un attachment */
    @GetMapping("/tasks/{id}/attachments/{fileName}/download-url")
    public Map<String, String> getAttachmentDownloadUrl(
            @PathVariable String id,
            @PathVariable String fileName) {
        return mobileService.getAttachmentDownloadUrl(id, fileName);
    }

    // ── Notificaciones ─────────────────────────────────────────────────────────

    /** Notificaciones del cliente autenticado */
    @GetMapping("/notifications")
    public Page<NotificationResponse> myNotifications(
            @RequestParam(required = false) Boolean unreadOnly,
            Pageable pageable) {
        return mobileService.myNotifications(unreadOnly, pageable);
    }

    /** Marcar una notificación como leída */
    @PutMapping("/notifications/{id}/read")
    public NotificationResponse markNotificationAsRead(@PathVariable String id) {
        return mobileService.markNotificationAsRead(id);
    }
}
