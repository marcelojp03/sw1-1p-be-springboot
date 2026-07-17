package sw1.p1.policy.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sw1.p1.policy.application.PolicyVersionService;
import sw1.p1.policy.domain.NodeConfiguration;
import sw1.p1.policy.domain.PolicyVersion;
import sw1.p1.procedure.application.ProcedureService;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.procedure.dto.StartProcedureRequest;

import jakarta.validation.Valid;
import sw1.p1.procedure.application.ProcedureService;
import sw1.p1.procedure.dto.ProcedureResponse;
import sw1.p1.procedure.dto.StartProcedureRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies/{policyId}/versions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PolicyVersionController {

    private final PolicyVersionService versionService;
    private final ProcedureService procedureService;

    @PostMapping
    public ResponseEntity<PolicyVersion> createDraft(@PathVariable String policyId) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(versionService.createDraft(policyId, userId));
    }

    @GetMapping
    public ResponseEntity<List<PolicyVersion>> listVersions(@PathVariable String policyId) {
        return ResponseEntity.ok(versionService.listVersions(policyId));
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<PolicyVersion> getVersion(@PathVariable String policyId,
                                                     @PathVariable String versionId) {
        return ResponseEntity.ok(versionService.getVersion(policyId, versionId));
    }

    @PutMapping("/{versionId}/diagram")
    public ResponseEntity<PolicyVersion> updateDiagram(@PathVariable String policyId,
                                                        @PathVariable String versionId,
                                                        @RequestBody Map<String, String> body) {
        String bpmnXml = body.get("bpmnXml");
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(versionService.updateDiagram(policyId, versionId, bpmnXml));
    }

    @GetMapping("/{versionId}/nodes")
    public ResponseEntity<List<NodeConfiguration>> getNodes(@PathVariable String policyId,
                                                             @PathVariable String versionId) {
        return ResponseEntity.ok(versionService.getNodeConfigurations(policyId, versionId));
    }

    @PutMapping("/{versionId}/nodes/{elementId}")
    public ResponseEntity<NodeConfiguration> saveNode(@PathVariable String policyId,
                                                       @PathVariable String versionId,
                                                       @PathVariable String elementId,
                                                       @RequestBody NodeConfiguration config) {
        config.setBpmnElementId(elementId);
        return ResponseEntity.ok(versionService.saveNodeConfiguration(policyId, versionId, config));
    }

    @DeleteMapping("/{versionId}/nodes/{elementId}")
    public ResponseEntity<Void> deleteNode(@PathVariable String policyId,
                                            @PathVariable String versionId,
                                            @PathVariable String elementId) {
        versionService.deleteNodeConfiguration(policyId, versionId, elementId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{versionId}/validate")
    public ResponseEntity<Map<String, Object>> validate(@PathVariable String policyId,
                                                         @PathVariable String versionId) {
        PolicyVersion version = versionService.getVersion(policyId, versionId);
        var result = versionService.validate(policyId, versionId, version.getBpmnXml());

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("valid", result.valid());
        body.put("violations", result.violations());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{versionId}/publish")
    public ResponseEntity<PolicyVersion> publish(@PathVariable String policyId,
                                                  @PathVariable String versionId) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(versionService.publish(policyId, versionId, userId));
    }

    @PostMapping("/{versionId}/procedures")
    public ResponseEntity<ProcedureResponse> startProcedure(
            @PathVariable String policyId,
            @PathVariable String versionId,
            @Valid @RequestBody StartProcedureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(procedureService.startFromVersion(
                        policyId, versionId, request.clientId(), request.organizationId()));
    }
}
