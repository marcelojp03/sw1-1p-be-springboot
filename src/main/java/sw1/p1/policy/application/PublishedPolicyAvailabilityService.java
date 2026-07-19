package sw1.p1.policy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.policy.domain.PolicyVersion;
import sw1.p1.policy.domain.PolicyVersionRepository;
import sw1.p1.policy.domain.WorkflowPolicy;
import sw1.p1.policy.domain.WorkflowPolicyRepository;
import sw1.p1.shared.PolicyStatus;
import sw1.p1.shared.PolicyVersionStatus;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublishedPolicyAvailabilityService {

    public record AvailableVersion(WorkflowPolicy policy, PolicyVersion version) {}

    private final WorkflowPolicyRepository policyRepository;
    private final PolicyVersionRepository versionRepository;

    public AvailableVersion requireAvailable(String organizationId, String policyId,
                                               String versionId, String channel) {
        WorkflowPolicy policy = policyRepository.findById(policyId)
                .filter(candidate -> organizationId.equals(candidate.getOrganizationId()))
                .orElseThrow(() -> new NotFoundException("Política no encontrada: " + policyId));

        if (policy.getStatus() != PolicyStatus.PUBLISHED) {
            throw new ConflictException("La política no está publicada");
        }
        if (policy.getAllowedStartChannels() == null
                || !policy.getAllowedStartChannels().contains(channel)) {
            throw new BusinessException("La política no permite iniciar trámites por canal " + channel);
        }
        String pointer = policy.getLatestPublishedVersionId();
        if (pointer == null || pointer.isBlank()) {
            throw new ConflictException("La política publicada no tiene latestPublishedVersionId");
        }
        PolicyVersion requestedVersion = versionRepository.findById(versionId)
                .filter(candidate -> policyId.equals(candidate.getPolicyId()))
                .orElseThrow(() -> new NotFoundException("Versión no encontrada: " + versionId));
        if (!pointer.equals(versionId)) {
            throw new ConflictException("La versión solicitada no es la versión publicada vigente");
        }
        requireConsistentPointer(policy, requestedVersion);
        return new AvailableVersion(policy, requestedVersion);
    }

    public Optional<AvailableVersion> resolveCatalogPointer(WorkflowPolicy policy) {
        String pointer = policy.getLatestPublishedVersionId();
        if (pointer == null || pointer.isBlank()) {
            log.warn("Política publicada {} excluida del catálogo: pointer ausente", policy.getId());
            return Optional.empty();
        }
        Optional<PolicyVersion> version = versionRepository.findById(pointer);
        if (version.isEmpty()) {
            log.warn("Política publicada {} excluida del catálogo: pointer {} inexistente",
                    policy.getId(), pointer);
            return Optional.empty();
        }
        try {
            requireConsistentPointer(policy, version.get());
            return Optional.of(new AvailableVersion(policy, version.get()));
        } catch (ConflictException exception) {
            log.warn("Política publicada {} excluida del catálogo: {}",
                    policy.getId(), exception.getMessage());
            return Optional.empty();
        }
    }

    private void requireConsistentPointer(WorkflowPolicy policy, PolicyVersion version) {
        if (!policy.getId().equals(version.getPolicyId())) {
            throw new ConflictException("latestPublishedVersionId apunta a otra política");
        }
        if (version.getStatus() != PolicyVersionStatus.PUBLISHED) {
            throw new ConflictException("latestPublishedVersionId no apunta a una versión PUBLISHED");
        }
    }
}
