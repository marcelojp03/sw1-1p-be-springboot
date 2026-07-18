package sw1.p1.form.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.form.application.FormService;
import sw1.p1.form.dto.*;
import sw1.p1.exception.BusinessException;

@RestController
@RequestMapping("/api/admin/form-versions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FormVersionController {

    private final FormService formService;
    private final UserRepository userRepository;

    @GetMapping("/{versionId}")
    public ResponseEntity<FormVersionResponse> get(@PathVariable String versionId) {
        return ResponseEntity.ok(formService.getVersion(currentOrgId(), versionId));
    }

    @PutMapping("/{versionId}")
    public ResponseEntity<FormVersionResponse> update(
            @PathVariable String versionId,
            @Valid @RequestBody UpdateFormVersionRequest req) {
        return ResponseEntity.ok(formService.updateVersion(currentOrgId(), versionId, req));
    }

    @PostMapping("/{versionId}/validate")
    public ResponseEntity<ValidationResult> validate(@PathVariable String versionId) {
        return ResponseEntity.ok(formService.validateVersion(currentOrgId(), versionId));
    }

    @PostMapping("/{versionId}/publish")
    public ResponseEntity<FormVersionResponse> publish(@PathVariable String versionId) {
        return ResponseEntity.ok(formService.publishVersion(currentOrgId(), versionId));
    }

    private String currentOrgId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuario autenticado no encontrado"))
                .getOrganizationId();
    }
}
