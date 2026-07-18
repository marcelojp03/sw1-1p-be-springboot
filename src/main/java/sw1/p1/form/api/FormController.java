package sw1.p1.form.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sw1.p1.form.application.FormService;
import sw1.p1.form.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/form-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FormController {

    private final FormService formService;

    @PostMapping
    public ResponseEntity<FormTemplateResponse> create(
            @Valid @RequestBody CreateFormTemplateRequest req,
            Authentication auth) {
        String orgId = extractOrgId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formService.createTemplate(orgId, req, auth.getName()));
    }

    @GetMapping
    public ResponseEntity<List<FormTemplateResponse>> list(Authentication auth) {
        return ResponseEntity.ok(formService.listTemplates(extractOrgId(auth)));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<FormTemplateResponse> get(
            @PathVariable String templateId, Authentication auth) {
        return ResponseEntity.ok(formService.getTemplate(extractOrgId(auth), templateId));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<FormTemplateResponse> update(
            @PathVariable String templateId,
            @Valid @RequestBody UpdateFormTemplateRequest req,
            Authentication auth) {
        return ResponseEntity.ok(formService.updateTemplate(extractOrgId(auth), templateId, req));
    }

    @PostMapping("/{templateId}/versions")
    public ResponseEntity<FormVersionResponse> createVersion(
            @PathVariable String templateId,
            @Valid @RequestBody CreateFormVersionRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formService.createVersion(extractOrgId(auth), templateId, req, auth.getName()));
    }

    @GetMapping("/{templateId}/versions")
    public ResponseEntity<List<FormVersionResponse>> listVersions(
            @PathVariable String templateId, Authentication auth) {
        return ResponseEntity.ok(formService.listVersions(extractOrgId(auth), templateId));
    }

    @GetMapping("/versions/{versionId}")
    public ResponseEntity<FormVersionResponse> getVersion(
            @PathVariable String versionId, Authentication auth) {
        return ResponseEntity.ok(formService.getVersion(extractOrgId(auth), versionId));
    }

    @PutMapping("/versions/{versionId}")
    public ResponseEntity<FormVersionResponse> updateVersion(
            @PathVariable String versionId,
            @Valid @RequestBody UpdateFormVersionRequest req,
            Authentication auth) {
        return ResponseEntity.ok(formService.updateVersion(extractOrgId(auth), versionId, req));
    }

    @PostMapping("/versions/{versionId}/validate")
    public ResponseEntity<ValidationResult> validate(
            @PathVariable String versionId, Authentication auth) {
        return ResponseEntity.ok(formService.validateVersion(extractOrgId(auth), versionId));
    }

    @PostMapping("/versions/{versionId}/publish")
    public ResponseEntity<FormVersionResponse> publish(
            @PathVariable String versionId, Authentication auth) {
        return ResponseEntity.ok(formService.publishVersion(extractOrgId(auth), versionId));
    }

    private String extractOrgId(Authentication auth) {
        return "org-1"; // TODO: derivar del usuario autenticado
    }
}
