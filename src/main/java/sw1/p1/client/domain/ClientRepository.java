package sw1.p1.client.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends MongoRepository<Client, String> {

    List<Client> findByOrganizationId(String organizationId);

    Optional<Client> findByDocumentNumberAndOrganizationId(String documentNumber, String organizationId);

    boolean existsByDocumentNumberAndOrganizationId(String documentNumber, String organizationId);

    Optional<Client> findByUserId(String userId);
}
