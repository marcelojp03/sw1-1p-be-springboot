package sw1.p1.task.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import sw1.p1.shared.TaskAudience;
import sw1.p1.shared.TaskStatus;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends MongoRepository<Task, String> {

    Page<Task> findByAssignedAreaIdAndStatus(String assignedAreaId, TaskStatus status, Pageable pageable);

    Page<Task> findByAssignedUserIdAndStatus(String userId, TaskStatus status, Pageable pageable);

    Page<Task> findByAssignedClientIdAndTaskAudience(String clientId, TaskAudience audience, Pageable pageable);

    List<Task> findByProcedureIdAndNodeId(String procedureId, String nodeId);

    Optional<Task> findByProcedureIdAndNodeIdAndStatus(String procedureId, String nodeId, TaskStatus status);
}
