package sw1.p1.policy.application;

import org.junit.jupiter.api.Test;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.policy.domain.PolicyVersion;
import sw1.p1.policy.domain.PolicyVersionRepository;
import sw1.p1.policy.domain.WorkflowPolicy;
import sw1.p1.policy.domain.WorkflowPolicyRepository;
import sw1.p1.shared.PolicyStatus;
import sw1.p1.shared.PolicyVersionStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublishedPolicyAvailabilityServiceTest {

    private final WorkflowPolicyRepository policyRepository = mock(WorkflowPolicyRepository.class);
    private final PolicyVersionRepository versionRepository = mock(PolicyVersionRepository.class);
    private final PublishedPolicyAvailabilityService service =
            new PublishedPolicyAvailabilityService(policyRepository, versionRepository);

    @Test
    void exactPublishedPointerIsAvailable() {
        var policy = policy("org-1", PolicyStatus.PUBLISHED, "v2", List.of("MOBILE"));
        var version = version("v2", "p1", PolicyVersionStatus.PUBLISHED);
        when(policyRepository.findById("p1")).thenReturn(Optional.of(policy));
        when(versionRepository.findById("v2")).thenReturn(Optional.of(version));

        var available = service.requireAvailable("org-1", "p1", "v2", "MOBILE");

        assertSame(policy, available.policy());
        assertSame(version, available.version());
    }

    @Test
    void crossOrganizationPolicyIsHidden() {
        when(policyRepository.findById("p1")).thenReturn(Optional.of(
                policy("other", PolicyStatus.PUBLISHED, "v2", List.of("MOBILE"))));

        assertThrows(NotFoundException.class,
                () -> service.requireAvailable("org-1", "p1", "v2", "MOBILE"));
    }

    @Test
    void nonPublishedPolicyIsRejected() {
        when(policyRepository.findById("p1")).thenReturn(Optional.of(
                policy("org-1", PolicyStatus.DRAFT, "v2", List.of("MOBILE"))));

        assertThrows(ConflictException.class,
                () -> service.requireAvailable("org-1", "p1", "v2", "MOBILE"));
    }

    @Test
    void disabledChannelIsRejected() {
        when(policyRepository.findById("p1")).thenReturn(Optional.of(
                policy("org-1", PolicyStatus.PUBLISHED, "v2", List.of("WEB"))));

        assertThrows(BusinessException.class,
                () -> service.requireAvailable("org-1", "p1", "v2", "MOBILE"));
    }

    @Test
    void nonCurrentPublishedVersionIsRejectedWithoutFallback() {
        when(policyRepository.findById("p1")).thenReturn(Optional.of(
                policy("org-1", PolicyStatus.PUBLISHED, "v2", List.of("MOBILE"))));
        when(versionRepository.findById("v1")).thenReturn(Optional.of(
                version("v1", "p1", PolicyVersionStatus.ARCHIVED)));

        assertThrows(ConflictException.class,
                () -> service.requireAvailable("org-1", "p1", "v1", "MOBILE"));
    }

    @Test
    void missingPointerIsRejected() {
        when(policyRepository.findById("p1")).thenReturn(Optional.of(
                policy("org-1", PolicyStatus.PUBLISHED, null, List.of("MOBILE"))));

        assertThrows(ConflictException.class,
                () -> service.requireAvailable("org-1", "p1", "v2", "MOBILE"));
    }

    @Test
    void missingPointedVersionIsRejected() {
        when(policyRepository.findById("p1")).thenReturn(Optional.of(
                policy("org-1", PolicyStatus.PUBLISHED, "v2", List.of("MOBILE"))));
        when(versionRepository.findById("v2")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.requireAvailable("org-1", "p1", "v2", "MOBILE"));
    }

    @Test
    void pointerToAnotherPolicyIsRejected() {
        var policy = policy("org-1", PolicyStatus.PUBLISHED, "v2", List.of("MOBILE"));
        when(policyRepository.findById("p1")).thenReturn(Optional.of(policy));
        when(versionRepository.findById("v2")).thenReturn(Optional.of(
                version("v2", "p2", PolicyVersionStatus.PUBLISHED)));

        assertThrows(NotFoundException.class,
                () -> service.requireAvailable("org-1", "p1", "v2", "MOBILE"));
    }

    @Test
    void draftOrArchivedPointerIsExcludedFromCatalog() {
        var policy = policy("org-1", PolicyStatus.PUBLISHED, "v2", List.of("MOBILE"));
        for (PolicyVersionStatus status : List.of(PolicyVersionStatus.DRAFT, PolicyVersionStatus.ARCHIVED)) {
            when(versionRepository.findById("v2")).thenReturn(Optional.of(version("v2", "p1", status)));
            assertTrue(service.resolveCatalogPointer(policy).isEmpty());
        }
    }

    private WorkflowPolicy policy(String organizationId, PolicyStatus status,
                                  String pointer, List<String> channels) {
        return WorkflowPolicy.builder()
                .id("p1").organizationId(organizationId).status(status)
                .latestPublishedVersionId(pointer).allowedStartChannels(channels)
                .build();
    }

    private PolicyVersion version(String id, String policyId, PolicyVersionStatus status) {
        return PolicyVersion.builder().id(id).policyId(policyId).status(status).build();
    }
}
