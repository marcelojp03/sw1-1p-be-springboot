package sw1.p1.procedure.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProcedureHistoryRepository extends MongoRepository<ProcedureHistory, String> {

    List<ProcedureHistory> findByProcedureIdOrderByOccurredAtAsc(String procedureId);
}
