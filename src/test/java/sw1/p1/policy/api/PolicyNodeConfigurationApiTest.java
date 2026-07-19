package sw1.p1.policy.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.GlobalExceptionHandler;
import sw1.p1.form.application.CurrentOrganizationResolver;
import sw1.p1.form.exception.FormVersionNotFoundException;
import sw1.p1.policy.application.PolicyVersionService;
import sw1.p1.policy.dto.NodeConfigurationResponse;
import sw1.p1.procedure.application.ProcedureService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PolicyVersionController.class)
@Import({GlobalExceptionHandler.class, PolicyNodeConfigurationApiTest.TestSecurityConfig.class})
class PolicyNodeConfigurationApiTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PolicyVersionService versionService;
    @MockitoBean private ProcedureService procedureService;
    @MockitoBean private CurrentOrganizationResolver organizationResolver;
    @MockitoBean private sw1.p1.security.JwtTokenProvider jwtTokenProvider;
    @MockitoBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockitoBean private sw1.p1.auth.domain.UserRepository userRepository;

    @BeforeEach
    void setUp() {
        when(organizationResolver.requireOrganizationId()).thenReturn("org-1");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAssociatesExactFormVersion() throws Exception {
        when(versionService.saveNodeConfiguration(
                eq("org-1"), eq("p1"), eq("pv1"), eq("userTask1"), any()))
                .thenReturn(response("form-v2"));

        mockMvc.perform(put("/api/policies/p1/versions/pv1/nodes/userTask1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskKind":"CLIENT_TASK","formVersionId":"form-v2","slaHours":24}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formVersionId").value("form-v2"))
                .andExpect(jsonPath("$.bpmnElementId").value("userTask1"))
                .andExpect(jsonPath("$.fields").doesNotExist())
                .andExpect(jsonPath("$.formSchema").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void officerCannotChangeNodeConfiguration() throws Exception {
        mockMvc.perform(put("/api/policies/p1/versions/pv1/nodes/userTask1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskKind\":\"OFFICER_TASK\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(versionService);
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientCannotChangeNodeConfiguration() throws Exception {
        mockMvc.perform(put("/api/policies/p1/versions/pv1/nodes/userTask1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskKind\":\"CLIENT_TASK\",\"slaHours\":0}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(versionService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void publishedPolicyVersionMutationReturns409() throws Exception {
        when(versionService.saveNodeConfiguration(
                eq("org-1"), eq("p1"), eq("pv1"), eq("userTask1"), any()))
                .thenThrow(new ConflictException("Solo DRAFT"));

        mockMvc.perform(put("/api/policies/p1/versions/pv1/nodes/userTask1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskKind\":\"CLIENT_TASK\",\"slaHours\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo DRAFT"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidAssociationReturns422() throws Exception {
        when(versionService.saveNodeConfiguration(
                eq("org-1"), eq("p1"), eq("pv1"), eq("service1"), any()))
                .thenThrow(new BusinessException("formVersionId solo se permite en UserTask"));

        mockMvc.perform(put("/api/policies/p1/versions/pv1/nodes/service1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskKind\":\"AUTOMATIC_TASK\",\"formVersionId\":\"fv1\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void hiddenFormVersionReturns404() throws Exception {
        when(versionService.saveNodeConfiguration(
                eq("org-1"), eq("p1"), eq("pv1"), eq("userTask1"), any()))
                .thenThrow(new FormVersionNotFoundException("FormVersion no encontrada"));

        mockMvc.perform(put("/api/policies/p1/versions/pv1/nodes/userTask1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskKind\":\"CLIENT_TASK\",\"formVersionId\":\"other-org\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteFromPublishedPolicyVersionReturns409() throws Exception {
        doThrow(new ConflictException("Solo DRAFT"))
                .when(versionService).deleteNodeConfiguration("org-1", "p1", "pv1", "userTask1");

        mockMvc.perform(delete("/api/policies/p1/versions/pv1/nodes/userTask1").with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nonPositiveSlaReturns422() throws Exception {
        when(versionService.saveNodeConfiguration(
                eq("org-1"), eq("p1"), eq("pv1"), eq("userTask1"), any()))
                .thenThrow(new BusinessException("slaHours debe ser mayor que cero"));

        mockMvc.perform(put("/api/policies/p1/versions/pv1/nodes/userTask1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskKind\":\"CLIENT_TASK\",\"slaHours\":0}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private NodeConfigurationResponse response(String formVersionId) {
        return new NodeConfigurationResponse(
                "nc1", "p1", "pv1", "userTask1", "CLIENT_TASK", "CLIENT",
                null, formVersionId, 24, "Solicitud", null);
    }
}
