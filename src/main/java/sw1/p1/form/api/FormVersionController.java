package sw1.p1.form.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sw1.p1.form.application.CurrentOrganizationResolver;
import sw1.p1.form.application.FormService;
import sw1.p1.form.dto.*;

@RestController
@RequestMapping("/api/admin/form-versions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FormVersionController {

    private final FormService formService;
    private final CurrentOrganizationResolver orgResolver;

    @GetMapping("/{versionId}")
    public ResponseEntity<FormVersionResponse> get(@PathVariable String versionId) {
        return ResponseEntity.ok(formService.getVersion(orgResolver.requireOrganizationId(), versionId));
    }

    @PutMapping("/{versionId}")
    public ResponseEntity<FormVersionResponse> update(@PathVariable String versionId,
                                                       @Valid @RequestBody UpdateFormVersionRequest req) {
        return ResponseEntity.ok(formService.updateVersion(orgResolver.requireOrganizationId(), versionId, req));
    }

    @PostMapping("/{versionId}/validate")
    public ResponseEntity<ValidationResult> validate(@PathVariable String versionId) {
        return ResponseEntity.ok(formService.validateVersion(orgResolver.requireOrganizationId(), versionId));
    }

    @PostMapping("/{versionId}/publish")
    public ResponseEntity<FormVersionResponse> publish(@PathVariable String versionId) {
        return ResponseEntity.ok(formService.publishVersion(orgResolver.requireOrganizationId(), versionId));
    }
}
