package sw1.p1.policyrequest.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sw1.p1.policyrequest.application.PolicyRequestService;
import sw1.p1.policyrequest.domain.PolicyRequestStatus;
import sw1.p1.policyrequest.dto.PolicyRequestResponse;
import sw1.p1.policyrequest.dto.UpdatePolicyRequestStatusRequest;

@RestController
@RequestMapping("/api/admin/policy-requests")
@RequiredArgsConstructor
public class PolicyRequestController {

    private final PolicyRequestService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<PolicyRequestResponse> list(
            @RequestParam String organizationId,
            @RequestParam(required = false) PolicyRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(organizationId, status, pageable);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyRequestResponse updateStatus(
            @PathVariable String id,
            @RequestBody UpdatePolicyRequestStatusRequest req) {
        return service.updateStatus(id, req);
    }
}
