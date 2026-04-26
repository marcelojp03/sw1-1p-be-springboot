package sw1.p1.task.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sw1.p1.task.application.TaskService;
import sw1.p1.task.dto.CompleteTaskRequest;
import sw1.p1.task.dto.TaskResponse;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class TaskController {

    private final TaskService taskService;

    /** Bandeja por área */
    @GetMapping
    public ResponseEntity<Page<TaskResponse>> findByArea(
            @RequestParam String areaId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(taskService.findByArea(areaId, pageable));
    }

    /** Tareas del OFFICER autenticado */
    @GetMapping("/mine")
    public ResponseEntity<Page<TaskResponse>> myTasks(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(taskService.myTasks(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(taskService.findById(id));
    }

    /** Tomar una tarea de la bandeja */
    @PostMapping("/{id}/claim")
    public ResponseEntity<TaskResponse> claim(@PathVariable String id) {
        return ResponseEntity.ok(taskService.claimTask(id));
    }

    /** Completar tarea con respuesta de formulario → motor avanza el trámite */
    @PostMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> complete(
            @PathVariable String id,
            @RequestBody CompleteTaskRequest request) {
        return ResponseEntity.ok(taskService.completeTask(id, request));
    }

    /** Adjuntar archivos a una tarea interna (sube a S3) */
    @PostMapping(value = "/{id}/attachments", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse addAttachments(
            @PathVariable String id,
            @RequestParam("files") MultipartFile[] files) {
        return taskService.addAttachments(id, files);
    }
}
