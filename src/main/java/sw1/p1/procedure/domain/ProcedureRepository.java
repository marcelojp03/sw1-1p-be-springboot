package sw1.p1.procedure.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import sw1.p1.shared.ProcedureStatus;

import java.util.List;

public interface ProcedureRepository extends MongoRepository<Procedure, String> {

    Page<Procedure> findByOrganizationId(String organizationId, Pageable pageable);

    Page<Procedure> findByOrganizationIdAndStatus(String organizationId, ProcedureStatus status, Pageable pageable);

    Page<Procedure> findByClientId(String clientId, Pageable pageable);
}
