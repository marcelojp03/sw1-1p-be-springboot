package sw1.p1.policyrequest.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sw1.p1.exception.NotFoundException;
import sw1.p1.policyrequest.domain.PolicyRequest;
import sw1.p1.policyrequest.domain.PolicyRequestRepository;
import sw1.p1.policyrequest.domain.PolicyRequestStatus;
import sw1.p1.policyrequest.dto.CreatePolicyRequestCommand;
import sw1.p1.policyrequest.dto.PolicyRequestResponse;
import sw1.p1.policyrequest.dto.UpdatePolicyRequestStatusRequest;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PolicyRequestService {

    private final PolicyRequestRepository repository;

    /**
     * Crea una solicitud de política cuando identify_policy retorna confidence < 40%.
     */
    public PolicyRequestResponse create(CreatePolicyRequestCommand cmd) {
        PolicyRequest req = PolicyRequest.builder()
                .organizationId(cmd.organizationId())
                .requestText(cmd.requestText())
                .suggestedPolicyKey(cmd.suggestedPolicyKey())
                .confidence(cmd.confidence())
                .createdBy(cmd.createdBy())
                .createdAt(Instant.now())
                .build();
        return toResponse(repository.save(req));
    }

    public Page<PolicyRequestResponse> list(String organizationId, PolicyRequestStatus status, Pageable pageable) {
        if (status != null) {
            return repository.findByOrganizationIdAndStatus(organizationId, status, pageable)
                    .map(this::toResponse);
        }
        return repository.findByOrganizationId(organizationId, pageable)
                .map(this::toResponse);
    }

    public PolicyRequestResponse updateStatus(String id, UpdatePolicyRequestStatusRequest req) {
        PolicyRequest pr = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("PolicyRequest no encontrada: " + id));

        String reviewer = SecurityContextHolder.getContext().getAuthentication().getName();

        pr.setStatus(req.status());
        pr.setReviewedBy(reviewer);
        pr.setReviewedAt(Instant.now());
        pr.setReviewNote(req.reviewNote());

        return toResponse(repository.save(pr));
    }

    private PolicyRequestResponse toResponse(PolicyRequest pr) {
        return new PolicyRequestResponse(
                pr.getId(),
                pr.getOrganizationId(),
                pr.getRequestText(),
                pr.getSuggestedPolicyKey(),
                pr.getConfidence(),
                pr.getStatus(),
                pr.getCreatedAt(),
                pr.getCreatedBy(),
                pr.getReviewedBy(),
                pr.getReviewedAt(),
                pr.getReviewNote()
        );
    }
}
