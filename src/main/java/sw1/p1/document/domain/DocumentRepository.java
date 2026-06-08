package sw1.p1.document.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentRepository extends MongoRepository<Document, String> {

    Page<Document> findByOrganizationId(String organizationId, Pageable pageable);

    Page<Document> findByOrganizationIdAndScope(String organizationId, DocumentScope scope, Pageable pageable);

    List<Document> findByScopeReferenceId(String scopeReferenceId);

    Page<Document> findByOrganizationIdAndStatus(String organizationId, DocumentStatus status, Pageable pageable);
}
