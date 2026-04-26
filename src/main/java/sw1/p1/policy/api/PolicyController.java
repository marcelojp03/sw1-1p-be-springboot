package sw1.p1.policy.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sw1.p1.policy.application.PolicyService;
import sw1.p1.policy.dto.*;

@RestController
@RequestMapping({"/api/policies", "/api/workflow-policies"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody CreatePolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<PolicySummaryResponse>> findByOrganization(
            @RequestParam String organizationId,
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable) {
        return ResponseEntity.ok(policyService.findByOrganization(organizationId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(policyService.findById(id));
    }

    /** Guardar diagrama + nodos + transiciones (diseñador visual) */
    @PutMapping("/{id}/diagram")
    public ResponseEntity<PolicyResponse> updateDiagram(
            @PathVariable String id,
            @RequestBody DiagramUpdateRequest request) {
        return ResponseEntity.ok(policyService.updateDiagram(id, request));
    }

    /** Publicar política: valida estructura y cambia estado a PUBLISHED */
    @PostMapping("/{id}/publish")
    public ResponseEntity<PolicyResponse> publish(@PathVariable String id) {
        return ResponseEntity.ok(policyService.publish(id));
    }

    /** Crear nueva versión DRAFT basada en la más reciente */
    @PostMapping("/new-version")
    public ResponseEntity<PolicyResponse> createNewVersion(
            @RequestParam String organizationId,
            @RequestParam String policyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(policyService.createNewVersion(organizationId, policyKey));
    }

    /** Archivar manualmente una política */
    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable String id) {
        policyService.archive(id);
        return ResponseEntity.noContent().build();
    }
}
