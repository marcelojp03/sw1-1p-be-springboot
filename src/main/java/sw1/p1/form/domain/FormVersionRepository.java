package sw1.p1.form.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FormVersionRepository extends MongoRepository<FormVersion, String> {
    List<FormVersion> findByFormTemplateIdOrderByVersionNumberDesc(String formTemplateId);

    Optional<FormVersion> findByFormTemplateIdAndVersionNumber(String formTemplateId, int versionNumber);

    Optional<FormVersion> findByFormTemplateIdAndStatus(String formTemplateId, FormVersionStatus status);

    List<FormVersion> findByOrganizationIdAndStatus(String organizationId, FormVersionStatus status);
}
