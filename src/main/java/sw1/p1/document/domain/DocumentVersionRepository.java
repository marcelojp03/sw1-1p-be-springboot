package sw1.p1.document.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends MongoRepository<DocumentVersion, String> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(String documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(String documentId, int versionNumber);

    int countByDocumentId(String documentId);
}
