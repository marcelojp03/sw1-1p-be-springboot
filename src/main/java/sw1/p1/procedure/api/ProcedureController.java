package sw1.p1.procedure.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sw1.p1.procedure.application.ProcedureService;
import sw1.p1.procedure.domain.ProcedureHistory;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.procedure.dto.ProcedureSummaryResponse;
import sw1.p1.procedure.dto.StartProcedureRequest;
import sw1.p1.shared.ProcedureStatus;

import java.util.List;

@RestController
@RequestMapping("/api/procedures")
@RequiredArgsConstructor
public class ProcedureController {

    private final ProcedureService procedureService;

    /** Iniciar un trámite (OFFICER o ADMIN) */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<ProcedureResponse> start(@Valid @RequestBody StartProcedureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procedureService.start(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<Page<ProcedureSummaryResponse>> findByOrganization(
            @RequestParam String organizationId,
            @RequestParam(required = false) ProcedureStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        if (status != null) {
            return ResponseEntity.ok(
                    procedureService.findByOrganizationAndStatus(organizationId, status, pageable));
        }
        return ResponseEntity.ok(procedureService.findByOrganization(organizationId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<ProcedureResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(procedureService.findById(id));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<List<ProcedureHistory>> getHistory(@PathVariable String id) {
        return ResponseEntity.ok(procedureService.getHistory(id));
    }
}
