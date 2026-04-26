package sw1.p1.procedure.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sw1.p1.procedure.application.MobileProcedureService;
import sw1.p1.procedure.domain.ProcedureHistory;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.procedure.dto.ProcedureSummaryResponse;
import sw1.p1.procedure.dto.StartProcedureRequest;
import sw1.p1.task.dto.CompleteTaskRequest;
import sw1.p1.task.dto.TaskResponse;

import java.util.List;

@RestController
@RequestMapping("/api/mobile")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class MobileProcedureController {

    private final MobileProcedureService mobileService;

    /** Trámites del cliente autenticado */
    @GetMapping("/procedures")
    public Page<ProcedureSummaryResponse> myProcedures(Pageable pageable) {
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
}
