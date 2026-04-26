package sw1.p1.policy.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import sw1.p1.shared.PolicyStatus;

import java.util.List;
import java.util.Optional;

public interface WorkflowPolicyRepository extends MongoRepository<WorkflowPolicy, String> {

    Page<WorkflowPolicy> findByOrganizationId(String organizationId, Pageable pageable);

    List<WorkflowPolicy> findByOrganizationIdAndStatus(String organizationId, PolicyStatus status);

    List<WorkflowPolicy> findByOrganizationIdAndPolicyKey(String organizationId, String policyKey);

    Optional<WorkflowPolicy> findByOrganizationIdAndPolicyKeyAndStatus(
            String organizationId, String policyKey, PolicyStatus status);

    boolean existsByOrganizationIdAndPolicyKeyAndStatus(
            String organizationId, String policyKey, PolicyStatus status);

    /** Versión más alta de una policyKey en una organización */
    Optional<WorkflowPolicy> findTopByOrganizationIdAndPolicyKeyOrderByVersionDesc(
            String organizationId, String policyKey);

    /** Políticas PUBLISHED que permiten un canal de inicio específico (ej. "MOBILE") */
    List<WorkflowPolicy> findByOrganizationIdAndStatusAndAllowedStartChannelsContaining(
            String organizationId, PolicyStatus status, String channel);
}
