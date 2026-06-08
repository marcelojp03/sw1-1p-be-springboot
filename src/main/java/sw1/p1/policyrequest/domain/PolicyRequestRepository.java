package sw1.p1.policyrequest.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PolicyRequestRepository extends MongoRepository<PolicyRequest, String> {

    Page<PolicyRequest> findByOrganizationId(String organizationId, Pageable pageable);

    Page<PolicyRequest> findByOrganizationIdAndStatus(
            String organizationId, PolicyRequestStatus status, Pageable pageable);
}
