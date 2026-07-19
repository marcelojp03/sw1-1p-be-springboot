package sw1.p1.task.application;

import org.junit.jupiter.api.Test;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.procedure.application.WorkflowEngineService;
import sw1.p1.procedure.domain.ProcedureRepository;
import sw1.p1.shared.TaskAudience;
import sw1.p1.shared.TaskStatus;
import sw1.p1.shared.storage.StorageService;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final TaskService service = new TaskService(
            taskRepository,
            mock(ProcedureRepository.class),
            mock(UserRepository.class),
            mock(WorkflowEngineService.class),
            mock(StorageService.class));

    @Test
    void responseExposesExactFormVersionAndDepartment() {
        var task = Task.builder()
                .id("task-1")
                .procedureId("procedure-1")
                .policyId("policy-1")
                .policyVersionId("policy-v2")
                .nodeId("user-task-1")
                .assignedDepartmentId("dep-3")
                .formVersionId("form-v7")
                .taskAudience(TaskAudience.INTERNAL)
                .status(TaskStatus.PENDING)
                .build();
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));

        var response = service.findById("task-1");

        assertThat(response.policyVersionId()).isEqualTo("policy-v2");
        assertThat(response.assignedDepartmentId()).isEqualTo("dep-3");
        assertThat(response.formVersionId()).isEqualTo("form-v7");
    }
}
