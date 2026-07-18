package sw1.p1.form.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FormTemplateRepository extends MongoRepository<FormTemplate, String> {
    List<FormTemplate> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

    Optional<FormTemplate> findByOrganizationIdAndCode(String organizationId, String code);
}
