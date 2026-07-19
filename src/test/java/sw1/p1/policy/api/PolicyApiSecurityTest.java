package sw1.p1.policy.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sw1.p1.exception.ConflictException;
import sw1.p1.exception.GlobalExceptionHandler;
import sw1.p1.form.application.CurrentOrganizationResolver;
import sw1.p1.policy.application.PolicyService;
import sw1.p1.policy.domain.PolicyVersionRepository;
import sw1.p1.policy.domain.WorkflowPolicyRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
@Import({GlobalExceptionHandler.class, PolicyApiSecurityTest.TestSecurityConfig.class})
class PolicyApiSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PolicyService policyService;
    @MockitoBean private PolicyVersionRepository versionRepository;
    @MockitoBean private WorkflowPolicyRepository policyRepository;
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
    void legacyPublicationIsRejected() throws Exception {
        when(policyService.publish("org-1", "p1"))
                .thenThrow(new ConflictException("Use PolicyVersion"));

        mockMvc.perform(post("/api/policies/p1/publish").with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listIgnoresRequestedOrganizationAndUsesAuthenticatedOrganization() throws Exception {
        when(policyRepository.findByOrganizationId(eq("org-1"), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/policies").param("organizationId", "org-2"))
                .andExpect(status().isOk());

        verify(policyRepository).findByOrganizationId(eq("org-1"), any());
    }
}
