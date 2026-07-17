package sw1.p1.policy.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NodeConfigurationRepository extends MongoRepository<NodeConfiguration, String> {

    List<NodeConfiguration> findByPolicyVersionId(String policyVersionId);

    Optional<NodeConfiguration> findByPolicyVersionIdAndBpmnElementId(
            String policyVersionId, String bpmnElementId);

    void deleteByPolicyVersionId(String policyVersionId);
}
