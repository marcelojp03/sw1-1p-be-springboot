package sw1.p1.form.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sw1.p1.form.application.CurrentOrganizationResolver;
import sw1.p1.form.application.FormService;
import sw1.p1.form.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/form-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FormTemplateController {

    private final FormService formService;
    private final CurrentOrganizationResolver orgResolver;

    @PostMapping
    public ResponseEntity<FormTemplateResponse> create(@Valid @RequestBody CreateFormTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formService.createTemplate(orgResolver.requireOrganizationId(), req, orgResolver.requireEmail()));
    }

    @GetMapping
    public ResponseEntity<List<FormTemplateResponse>> list() {
        return ResponseEntity.ok(formService.listTemplates(orgResolver.requireOrganizationId()));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<FormTemplateResponse> get(@PathVariable String templateId) {
        return ResponseEntity.ok(formService.getTemplate(orgResolver.requireOrganizationId(), templateId));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<FormTemplateResponse> update(@PathVariable String templateId,
                                                        @Valid @RequestBody UpdateFormTemplateRequest req) {
        return ResponseEntity.ok(formService.updateTemplate(orgResolver.requireOrganizationId(), templateId, req));
    }

    @PostMapping("/{templateId}/versions")
    public ResponseEntity<FormVersionResponse> createVersion(@PathVariable String templateId,
                                                              @Valid @RequestBody CreateFormVersionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formService.createVersion(orgResolver.requireOrganizationId(), templateId, req, orgResolver.requireEmail()));
    }

    @GetMapping("/{templateId}/versions")
    public ResponseEntity<List<FormVersionResponse>> listVersions(@PathVariable String templateId) {
        return ResponseEntity.ok(formService.listVersions(orgResolver.requireOrganizationId(), templateId));
    }
}
