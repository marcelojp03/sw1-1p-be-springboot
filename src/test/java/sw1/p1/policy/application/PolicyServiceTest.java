package sw1.p1.policy.application;

import org.junit.jupiter.api.Test;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.policy.domain.WorkflowPolicy;
import sw1.p1.policy.domain.WorkflowPolicyRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyServiceTest {

    private final WorkflowPolicyRepository policyRepository = mock(WorkflowPolicyRepository.class);
    private final PolicyService service = new PolicyService(policyRepository, mock(UserRepository.class));

    @Test
    void policyFromAnotherOrganizationIsHidden() {
        when(policyRepository.findById("p1")).thenReturn(Optional.of(
                WorkflowPolicy.builder().id("p1").organizationId("org-2").build()));

        assertThrows(NotFoundException.class, () -> service.findById("org-1", "p1"));
    }

    @Test
    void legacyAggregatePublicationIsRejected() {
        when(policyRepository.findById("p1")).thenReturn(Optional.of(
                WorkflowPolicy.builder().id("p1").organizationId("org-1").build()));

        assertThrows(ConflictException.class, () -> service.publish("org-1", "p1"));
    }

    @Test
    void legacyAggregateVersionCreationIsRejected() {
        assertThrows(ConflictException.class,
                () -> service.createNewVersion("org-1", "policy-key"));
    }
}
