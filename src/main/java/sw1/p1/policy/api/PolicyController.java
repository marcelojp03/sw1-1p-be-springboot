package sw1.p1.policy.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sw1.p1.form.application.CurrentOrganizationResolver;
import sw1.p1.policy.application.PolicyService;
import sw1.p1.policy.domain.PolicyVersion;
import sw1.p1.policy.domain.PolicyVersionRepository;
import sw1.p1.policy.domain.WorkflowPolicy;
import sw1.p1.policy.domain.WorkflowPolicyRepository;
import sw1.p1.policy.dto.*;
import sw1.p1.shared.PolicyVersionStatus;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PolicyController {

    private final PolicyService policyService;
    private final PolicyVersionRepository versionRepository;
    private final WorkflowPolicyRepository policyRepository;
    private final CurrentOrganizationResolver organizationResolver;

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody CreatePolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.create(
                organizationResolver.requireOrganizationId(), request));
    }

    @GetMapping
    public ResponseEntity<Page<PolicyVersionSummaryResponse>> findByOrganization(
            @RequestParam String organizationId,
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable) {
        String authenticatedOrganizationId = organizationResolver.requireOrganizationId();
        Page<WorkflowPolicy> policies = policyRepository.findByOrganizationId(
                authenticatedOrganizationId, pageable);
        List<PolicyVersionSummaryResponse> summaries = policies.getContent().stream()
                .map(this::buildSummary)
                .toList();
        return ResponseEntity.ok(new PageImpl<>(summaries, pageable, policies.getTotalElements()));
    }

    private PolicyVersionSummaryResponse buildSummary(WorkflowPolicy p) {
        List<PolicyVersion> versions = versionRepository
                .findByPolicyIdOrderByVersionNumberDesc(p.getId());
        PolicyVersion latest = versions.isEmpty() ? null : versions.get(0);
        PolicyVersion draft = versions.stream()
                .filter(v -> v.getStatus() == PolicyVersionStatus.DRAFT).findFirst().orElse(null);
        PolicyVersion published = versions.stream()
                .filter(v -> v.getStatus() == PolicyVersionStatus.PUBLISHED).findFirst().orElse(null);

        return new PolicyVersionSummaryResponse(
                p.getId(), p.getPolicyKey(), p.getName(),
                latest != null ? latest.getId() : null,
                latest != null ? latest.getVersionNumber() : null,
                latest != null ? latest.getStatus().name() : null,
                latest != null ? latest.getPublishedAt() : null,
                latest != null ? latest.getCreatedAt() : null,
                draft != null ? draft.getId() : null,
                published != null ? published.getId() : null,
                p.getAllowedStartChannels()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(policyService.findById(
                organizationResolver.requireOrganizationId(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PolicyResponse> updateMeta(
            @PathVariable String id,
            @Valid @RequestBody UpdatePolicyMetaRequest request) {
        return ResponseEntity.ok(policyService.updateMeta(
                organizationResolver.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        policyService.delete(organizationResolver.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/diagram")
    public ResponseEntity<PolicyResponse> updateDiagram(
            @PathVariable String id,
            @RequestBody DiagramUpdateRequest request) {
        return ResponseEntity.ok(policyService.updateDiagram(
                organizationResolver.requireOrganizationId(), id, request));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<PolicyResponse> publish(@PathVariable String id) {
        return ResponseEntity.ok(policyService.publish(
                organizationResolver.requireOrganizationId(), id));
    }

    @PostMapping("/new-version")
    public ResponseEntity<PolicyResponse> createNewVersion(
            @RequestParam String organizationId,
            @RequestParam String policyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(policyService.createNewVersion(
                        organizationResolver.requireOrganizationId(), policyKey));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable String id) {
        policyService.archive(organizationResolver.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
