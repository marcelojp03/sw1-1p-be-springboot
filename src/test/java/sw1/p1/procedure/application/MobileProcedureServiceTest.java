package sw1.p1.procedure.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import sw1.p1.auth.domain.User;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.client.domain.Client;
import sw1.p1.client.domain.ClientRepository;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.notification.application.NotificationService;
import sw1.p1.policy.domain.FormDefinition;
import sw1.p1.policy.domain.PolicyVersionRepository;
import sw1.p1.policy.domain.WorkflowPolicyRepository;
import sw1.p1.procedure.domain.Procedure;
import sw1.p1.procedure.domain.ProcedureRepository;
import sw1.p1.procedure.domain.ProcedureHistoryRepository;
import sw1.p1.shared.TaskAudience;
import sw1.p1.shared.TaskStatus;
import sw1.p1.shared.storage.AttachmentRef;
import sw1.p1.shared.storage.StorageService;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;
import sw1.p1.task.dto.CompleteTaskRequest;
import sw1.p1.task.dto.MobileTaskResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MobileProcedureServiceTest {

    private final ProcedureRepository procedureRepository = mock(ProcedureRepository.class);
    private final ProcedureHistoryRepository historyRepository = mock(ProcedureHistoryRepository.class);
    private final WorkflowPolicyRepository policyRepository = mock(WorkflowPolicyRepository.class);
    private final PolicyVersionRepository versionRepository = mock(PolicyVersionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final WorkflowEngineService workflowEngine = mock(WorkflowEngineService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final StorageService storageService = mock(StorageService.class);

    private final SecurityContext securityContext = mock(SecurityContext.class);
    private final Authentication authentication = mock(Authentication.class);

    private MockedStatic<SecurityContextHolder> holderMock;

    private MobileProcedureService service;

    @BeforeEach
    void setUp() {
        holderMock = mockStatic(SecurityContextHolder.class);
        holderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        service = new MobileProcedureService(
                procedureRepository, historyRepository, policyRepository, versionRepository,
                userRepository, clientRepository, taskRepository,
                workflowEngine, notificationService, storageService);
    }

    @AfterEach
    void tearDown() {
        holderMock.close();
    }

    private void givenClient(String email, String userId, String clientId) {
        when(authentication.getName()).thenReturn(email);
        var user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        var client = mockClient(clientId);
        when(clientRepository.findByUserId(userId)).thenReturn(Optional.of(client));
    }

    private Client mockClient(String id) {
        var c = mock(Client.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    private Task task(String id, String procedureId, String policyId, String policyVersionId,
                      String nodeId, String label, TaskStatus status, TaskAudience audience,
                      String assignedClientId) {
        return Task.builder()
                .id(id).procedureId(procedureId).procedureCode("TRM-0001")
                .policyId(policyId).policyVersionId(policyVersionId)
                .nodeId(nodeId).label(label).status(status)
                .taskAudience(audience).assignedClientId(assignedClientId)
                .formVersionId("form-version-1")
                .form(FormDefinition.builder().formId("f").fields(List.of()).build())
                .formResponse(Map.of())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    // ── findTaskById ───────────────────────────────────────────────────────

    @Test
    void findTaskByIdOwnTaskReturnsDto() {
        givenClient("c@demo.com", "user-1", "client-1");
        var t = task("t1", "proc-1", "pol-1", "pv-1", "n1", "completar solicitud",
                TaskStatus.PENDING, TaskAudience.CLIENT, "client-1");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(t));

        MobileTaskResponse r = service.findTaskById("t1");

        assertThat(r.id()).isEqualTo("t1");
        assertThat(r.label()).isEqualTo("completar solicitud");
        assertThat(r.policyVersionId()).isEqualTo("pv-1");
        assertThat(r.formVersionId()).isEqualTo("form-version-1");
        assertThat(r.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(r.form()).isNotNull();
    }

    @Test
    void findTaskByIdOtherClientThrows403() {
        givenClient("c@demo.com", "user-1", "client-1");
        var t = task("t1", "proc-1", "pol-1", "pv-1", "n1", "x",
                TaskStatus.PENDING, TaskAudience.CLIENT, "client-2");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.findTaskById("t1"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void findTaskByIdOfficerTaskThrows403() {
        givenClient("c@demo.com", "user-1", "client-1");
        var t = task("t1", "proc-1", "pol-1", "pv-1", "n1", "x",
                TaskStatus.PENDING, TaskAudience.INTERNAL, "client-1");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.findTaskById("t1"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void findTaskByIdNotFoundThrows404() {
        givenClient("c@demo.com", "user-1", "client-1");
        when(taskRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTaskById("nope"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── completeTask ───────────────────────────────────────────────────────

    @Test
    void completeTaskPendingToCompleted() {
        givenClient("c@demo.com", "user-1", "client-1");
        var t = task("t1", "proc-1", "pol-1", "pv-1", "n1", "completar solicitud",
                TaskStatus.PENDING, TaskAudience.CLIENT, "client-1");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(t));
        var proc = Procedure.builder().id("proc-1").clientId("client-1")
                .policyId("pol-1").policyVersionId("pv-1").build();
        when(procedureRepository.findById("proc-1")).thenReturn(Optional.of(proc));

        MobileTaskResponse r = service.completeTask("t1", new CompleteTaskRequest(Map.of(), ""));

        verify(workflowEngine).advance(any(), eq("n1"), eq("user-1"), eq(Map.of()));
        assertThat(r.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void completeTaskTwiceThrows409() {
        givenClient("c@demo.com", "user-1", "client-1");
        var t = task("t1", "proc-1", "pol-1", "pv-1", "n1", "x",
                TaskStatus.COMPLETED, TaskAudience.CLIENT, "client-1");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(t));
        var proc = Procedure.builder().id("proc-1").clientId("client-1").build();
        when(procedureRepository.findById("proc-1")).thenReturn(Optional.of(proc));

        assertThatThrownBy(() -> service.completeTask("t1", new CompleteTaskRequest(Map.of(), "")))
                .isInstanceOf(ConflictException.class);
    }

    // ── toMobileTaskResponse attachments ───────────────────────────────────

    @Test
    void attachmentsExcludeStorageKey() {
        givenClient("c@demo.com", "user-1", "client-1");
        var att = mock(AttachmentRef.class);
        when(att.getFileName()).thenReturn("doc.pdf");
        when(att.getMimeType()).thenReturn("application/pdf");
        when(att.getSizeBytes()).thenReturn(1024L);
        when(att.getUploadedAt()).thenReturn(Instant.now());
        var t = Task.builder()
                .id("t1").procedureId("proc-1").procedureCode("TRM-0001")
                .policyId("pol-1").policyVersionId("pv-1")
                .nodeId("n1").label("x").status(TaskStatus.PENDING)
                .taskAudience(TaskAudience.CLIENT).assignedClientId("client-1")
                .attachments(List.of(att))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(taskRepository.findById("t1")).thenReturn(Optional.of(t));

        MobileTaskResponse r = service.findTaskById("t1");

        assertThat(r.attachments()).hasSize(1);
        var a = r.attachments().get(0);
        assertThat(a.fileName()).isEqualTo("doc.pdf");
        assertThat(a.mimeType()).isEqualTo("application/pdf");
        assertThat(a.sizeBytes()).isEqualTo(1024L);
        assertThat(a.uploadedAt()).isNotNull();
        // No storageKey accessor on MobileAttachmentResponse — guaranteed by type system
    }

    // ── policyVersionId en DTO ────────────────────────────────────────────

    @Test
    void dtoPolicyVersionIdMatchesTask() {
        givenClient("c@demo.com", "user-1", "client-1");
        var t = task("t1", "proc-1", "pol-1", "ver-5", "n1", "completar solicitud",
                TaskStatus.PENDING, TaskAudience.CLIENT, "client-1");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(t));

        MobileTaskResponse r = service.findTaskById("t1");

        assertThat(r.policyVersionId()).isEqualTo("ver-5");
        assertThat(r.policyId()).isEqualTo("pol-1");
        assertThat(r.procedureCode()).isEqualTo("TRM-0001");
    }

    @Test
    void dtoSupportsNullForm() {
        givenClient("c@demo.com", "user-1", "client-1");
        var t = Task.builder()
                .id("t1").procedureId("proc-1").procedureCode("TRM-0001")
                .policyId("pol-1").policyVersionId("pv-1")
                .nodeId("n1").label("x").status(TaskStatus.PENDING)
                .taskAudience(TaskAudience.CLIENT).assignedClientId("client-1")
                .formVersionId("form-v2")
                .form(null)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(taskRepository.findById("t1")).thenReturn(Optional.of(t));

        MobileTaskResponse r = service.findTaskById("t1");

        assertThat(r.form()).isNull();
        assertThat(r.formVersionId()).isEqualTo("form-v2");
    }

    @Test
    void dtoSupportsEmptyAttachments() {
        givenClient("c@demo.com", "user-1", "client-1");
        var t = task("t1", "proc-1", "pol-1", "pv-1", "n1", "x",
                TaskStatus.PENDING, TaskAudience.CLIENT, "client-1");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(t));

        MobileTaskResponse r = service.findTaskById("t1");

        assertThat(r.attachments()).isEmpty();
    }
}
