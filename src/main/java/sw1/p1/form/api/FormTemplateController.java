package sw1.p1.form.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.form.application.FormService;
import sw1.p1.form.dto.*;
import sw1.p1.exception.BusinessException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/form-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FormTemplateController {

    private final FormService formService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<FormTemplateResponse> create(
            @Valid @RequestBody CreateFormTemplateRequest req) {
        String orgId = currentOrgId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formService.createTemplate(orgId, req, currentEmail()));
    }

    @GetMapping
    public ResponseEntity<List<FormTemplateResponse>> list() {
        return ResponseEntity.ok(formService.listTemplates(currentOrgId()));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<FormTemplateResponse> get(@PathVariable String templateId) {
        return ResponseEntity.ok(formService.getTemplate(currentOrgId(), templateId));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<FormTemplateResponse> update(
            @PathVariable String templateId,
            @Valid @RequestBody UpdateFormTemplateRequest req) {
        return ResponseEntity.ok(formService.updateTemplate(currentOrgId(), templateId, req));
    }

    @PostMapping("/{templateId}/versions")
    public ResponseEntity<FormVersionResponse> createVersion(
            @PathVariable String templateId,
            @Valid @RequestBody CreateFormVersionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formService.createVersion(currentOrgId(), templateId, req, currentEmail()));
    }

    @GetMapping("/{templateId}/versions")
    public ResponseEntity<List<FormVersionResponse>> listVersions(
            @PathVariable String templateId) {
        return ResponseEntity.ok(formService.listVersions(currentOrgId(), templateId));
    }

    private String currentOrgId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuario autenticado no encontrado"))
                .getOrganizationId();
    }

    private String currentEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
