package sw1.p1.policy.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import sw1.p1.shared.PolicyVersionStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyVersionRepository extends MongoRepository<PolicyVersion, String> {

    List<PolicyVersion> findByPolicyIdOrderByVersionNumberDesc(String policyId);

    Optional<PolicyVersion> findTopByPolicyIdAndStatusOrderByVersionNumberDesc(
            String policyId, PolicyVersionStatus status);

    Optional<PolicyVersion> findByPolicyIdAndVersionNumber(String policyId, int versionNumber);

    boolean existsByPolicyIdAndStatus(String policyId, PolicyVersionStatus status);
}
