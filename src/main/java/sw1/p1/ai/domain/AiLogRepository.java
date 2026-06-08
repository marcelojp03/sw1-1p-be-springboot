package sw1.p1.ai.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiLogRepository extends MongoRepository<AiLog, String> {
    Page<AiLog> findByOrganizationIdAndContext(String organizationId, String context, Pageable pageable);
}
