package sw1.p1.policy.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sw1.p1.policy.application.PolicyVersionService;
import sw1.p1.policy.domain.PolicyVersion;
import sw1.p1.policy.dto.NodeConfigurationRequest;
import sw1.p1.policy.dto.NodeConfigurationResponse;
import sw1.p1.procedure.application.ProcedureService;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.procedure.dto.StartVersionedProcedureRequest;
import sw1.p1.form.application.CurrentOrganizationResolver;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies/{policyId}/versions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PolicyVersionController {

    private final PolicyVersionService versionService;
    private final ProcedureService procedureService;
    private final CurrentOrganizationResolver organizationResolver;

    @PostMapping
    public ResponseEntity<PolicyVersion> createDraft(@PathVariable String policyId) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(versionService.createDraft(
                        organizationResolver.requireOrganizationId(), policyId, userId));
    }

    @GetMapping
    public ResponseEntity<List<PolicyVersion>> listVersions(@PathVariable String policyId) {
        return ResponseEntity.ok(versionService.listVersions(
                organizationResolver.requireOrganizationId(), policyId));
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<PolicyVersion> getVersion(@PathVariable String policyId,
                                                      @PathVariable String versionId) {
        return ResponseEntity.ok(versionService.getVersion(
                organizationResolver.requireOrganizationId(), policyId, versionId));
    }

    @PutMapping("/{versionId}/diagram")
    public ResponseEntity<PolicyVersion> updateDiagram(@PathVariable String policyId,
                                                        @PathVariable String versionId,
                                                        @RequestBody Map<String, String> body) {
        String bpmnXml = body.get("bpmnXml");
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(versionService.updateDiagram(
                organizationResolver.requireOrganizationId(), policyId, versionId, bpmnXml));
    }

    @GetMapping("/{versionId}/nodes")
    public ResponseEntity<List<NodeConfigurationResponse>> getNodes(@PathVariable String policyId,
                                                                     @PathVariable String versionId) {
        return ResponseEntity.ok(versionService.getNodeConfigurations(
                organizationResolver.requireOrganizationId(), policyId, versionId));
    }

    @PutMapping("/{versionId}/nodes/{elementId}")
    public ResponseEntity<NodeConfigurationResponse> saveNode(@PathVariable String policyId,
                                                               @PathVariable String versionId,
                                                               @PathVariable String elementId,
                                                               @Valid @RequestBody NodeConfigurationRequest request) {
        return ResponseEntity.ok(versionService.saveNodeConfiguration(
                organizationResolver.requireOrganizationId(), policyId, versionId, elementId, request));
    }

    @DeleteMapping("/{versionId}/nodes/{elementId}")
    public ResponseEntity<Void> deleteNode(@PathVariable String policyId,
                                            @PathVariable String versionId,
                                            @PathVariable String elementId) {
        versionService.deleteNodeConfiguration(
                organizationResolver.requireOrganizationId(), policyId, versionId, elementId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{versionId}/validate")
    public ResponseEntity<Map<String, Object>> validate(@PathVariable String policyId,
                                                         @PathVariable String versionId) {
        String organizationId = organizationResolver.requireOrganizationId();
        PolicyVersion version = versionService.getVersion(organizationId, policyId, versionId);
        var result = versionService.validate(
                organizationId, policyId, versionId, version.getBpmnXml());

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("valid", result.valid());
        body.put("violations", result.violations());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{versionId}/publish")
    public ResponseEntity<PolicyVersion> publish(@PathVariable String policyId,
                                                  @PathVariable String versionId) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(versionService.publish(
                organizationResolver.requireOrganizationId(), policyId, versionId, userId));
    }

    @PostMapping("/{versionId}/procedures")
    public ResponseEntity<ProcedureResponse> startProcedure(
            @PathVariable String policyId,
            @PathVariable String versionId,
            @RequestBody(required = false) StartVersionedProcedureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(procedureService.startFromVersionForInternal(
                        policyId, versionId, request != null ? request.clientId() : null));
    }
}
