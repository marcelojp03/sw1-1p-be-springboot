package sw1.p1.procedure.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import sw1.p1.auth.domain.User;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.client.domain.Client;
import sw1.p1.client.domain.ClientRepository;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.form.application.CurrentOrganizationResolver;
import sw1.p1.policy.application.PublishedPolicyAvailabilityService;
import sw1.p1.policy.domain.*;
import sw1.p1.procedure.domain.Procedure;
import sw1.p1.procedure.domain.ProcedureHistoryRepository;
import sw1.p1.procedure.domain.ProcedureRepository;
import sw1.p1.procedure.dto.StartProcedureRequest;
import sw1.p1.shared.PolicyStatus;
import sw1.p1.shared.PolicyVersionStatus;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcedureServiceTest {

    private final ProcedureRepository procedureRepository = mock(ProcedureRepository.class);
    private final ProcedureHistoryRepository historyRepository = mock(ProcedureHistoryRepository.class);
    private final WorkflowPolicyRepository policyRepository = mock(WorkflowPolicyRepository.class);
    private final PolicyVersionRepository versionRepository = mock(PolicyVersionRepository.class);
    private final NodeConfigurationRepository nodeConfigRepository = mock(NodeConfigurationRepository.class);
    private final BpmnExecutionAdapter bpmnAdapter = mock(BpmnExecutionAdapter.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final WorkflowEngineService workflowEngine = mock(WorkflowEngineService.class);
    private final CurrentOrganizationResolver organizationResolver = mock(CurrentOrganizationResolver.class);
    private final PublishedPolicyAvailabilityService availabilityService =
            new PublishedPolicyAvailabilityService(policyRepository, versionRepository);

    private ProcedureService service;
    private WorkflowPolicy policy;
    private PolicyVersion version;
    private final AtomicReference<Procedure> savedProcedure = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new ProcedureService(
                procedureRepository, historyRepository, policyRepository, nodeConfigRepository,
                bpmnAdapter, userRepository, clientRepository, workflowEngine,
                organizationResolver, availabilityService);
        policy = WorkflowPolicy.builder()
                .id("p1").organizationId("org-1").policyKey("POL")
                .name("Política").status(PolicyStatus.PUBLISHED)
                .latestPublishedVersionId("v2").allowedStartChannels(List.of("WEB", "MOBILE"))
                .build();
        version = PolicyVersion.builder()
                .id("v2").policyId("p1").versionNumber(2)
                .status(PolicyVersionStatus.PUBLISHED).bpmnXml("<xml/>").build();
        when(policyRepository.findById("p1")).thenReturn(Optional.of(policy));
        when(versionRepository.findById("v2")).thenReturn(Optional.of(version));
        when(nodeConfigRepository.findByPolicyVersionId("v2")).thenReturn(List.of());
        when(bpmnAdapter.parse(eq(version), anyList())).thenReturn(
                new BpmnExecutionAdapter.BpmnProcessDefinition(List.of(), List.of(), java.util.Map.of()));
        when(procedureRepository.count()).thenReturn(0L);
        when(procedureRepository.save(any(Procedure.class))).thenAnswer(invocation -> {
            Procedure procedure = invocation.getArgument(0);
            procedure.setId("proc-1");
            savedProcedure.set(procedure);
            return procedure;
        });
        when(procedureRepository.findById("proc-1")).thenAnswer(invocation -> Optional.of(savedProcedure.get()));
    }

    @Test
    void internalStartUsesAuthenticatedOrganizationAndExactPointer() {
        when(organizationResolver.requireOrganizationId()).thenReturn("org-1");
        when(organizationResolver.requireEmail()).thenReturn("admin@test.com");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(
                User.builder().id("user-1").organizationId("org-1").build()));

        var response = service.startFromVersionForInternal("p1", "v2", null);

        assertThat(response.organizationId()).isEqualTo("org-1");
        assertThat(response.policyVersionId()).isEqualTo("v2");
        assertThat(response.startChannel()).isEqualTo("WEB");
        verify(workflowEngine).start(any(Procedure.class), eq("user-1"));
    }

    @Test
    void internalStartRejectsClientFromAnotherOrganization() {
        givenInternalActor();
        when(clientRepository.findById("client-2")).thenReturn(Optional.of(
                Client.builder().id("client-2").organizationId("org-2").build()));

        assertThatThrownBy(() -> service.startFromVersionForInternal("p1", "v2", "client-2"))
                .isInstanceOf(NotFoundException.class);
        verify(procedureRepository, never()).save(any());
    }

    @Test
    void mobileStartDerivesClientOrganizationAndRecordsMobileChannel() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("client@test.com");
        User user = User.builder().id("user-2").organizationId("org-1").build();
        Client client = Client.builder().id("client-1").userId("user-2").organizationId("org-1").build();
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(user));
        when(clientRepository.findByUserId("user-2")).thenReturn(Optional.of(client));

        var response = service.startFromVersionForClient("p1", "v2", authentication);

        assertThat(response.organizationId()).isEqualTo("org-1");
        assertThat(response.clientId()).isEqualTo("client-1");
        assertThat(response.startChannel()).isEqualTo("MOBILE");
    }

    @Test
    void mobileStartRejectsUserClientOrganizationMismatch() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("client@test.com");
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(
                User.builder().id("user-2").organizationId("org-1").build()));
        when(clientRepository.findByUserId("user-2")).thenReturn(Optional.of(
                Client.builder().id("client-1").organizationId("org-2").build()));

        assertThatThrownBy(() -> service.startFromVersionForClient("p1", "v2", authentication))
                .isInstanceOf(NotFoundException.class);
        verify(procedureRepository, never()).save(any());
    }

    @Test
    void oldPublishedVersionCannotStartNewProcedure() {
        givenInternalActor();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(
                PolicyVersion.builder().id("v1").policyId("p1")
                        .status(PolicyVersionStatus.ARCHIVED).build()));

        assertThatThrownBy(() -> service.startFromVersionForInternal("p1", "v1", null))
                .isInstanceOf(ConflictException.class);
        verify(procedureRepository, never()).save(any());
    }

    @Test
    void legacyInternalStartIgnoresBodyOrganizationAndUsesLatestPointer() {
        givenInternalActor();
        when(clientRepository.findById("client-1")).thenReturn(Optional.of(
                Client.builder().id("client-1").organizationId("org-1").build()));

        var response = service.start(new StartProcedureRequest("p1", "client-1", "org-evil"));

        assertThat(response.organizationId()).isEqualTo("org-1");
        assertThat(response.policyVersionId()).isEqualTo("v2");
    }

    @Test
    void nonPublishedPointedVersionCannotStart() {
        givenInternalActor();
        version.setStatus(PolicyVersionStatus.DRAFT);

        assertThatThrownBy(() -> service.startFromVersionForInternal("p1", "v2", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void policyFromAnotherOrganizationIsHidden() {
        givenInternalActor();
        policy.setOrganizationId("org-2");

        assertThatThrownBy(() -> service.startFromVersionForInternal("p1", "v2", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void disabledWebChannelRejectsInternalStart() {
        givenInternalActor();
        policy.setAllowedStartChannels(List.of("MOBILE"));

        assertThatThrownBy(() -> service.startFromVersionForInternal("p1", "v2", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void clientTaskRequiresClientForInternalStart() {
        givenInternalActor();
        when(bpmnAdapter.parse(eq(version), anyList())).thenReturn(
                new BpmnExecutionAdapter.BpmnProcessDefinition(
                        List.of(WorkflowNode.builder().nodeId("client-task")
                                .type(sw1.p1.shared.NodeType.CLIENT_TASK).build()),
                        List.of(), java.util.Map.of()));

        assertThatThrownBy(() -> service.startFromVersionForInternal("p1", "v2", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clientId");
        verify(procedureRepository, never()).save(any());
    }

    private void givenInternalActor() {
        when(organizationResolver.requireOrganizationId()).thenReturn("org-1");
        when(organizationResolver.requireEmail()).thenReturn("admin@test.com");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(
                User.builder().id("user-1").organizationId("org-1").build()));
    }
}
