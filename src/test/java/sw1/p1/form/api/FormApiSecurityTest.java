package sw1.p1.form.api;

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
import sw1.p1.exception.GlobalExceptionHandler;
import sw1.p1.form.application.CurrentOrganizationResolver;
import sw1.p1.form.application.FormService;
import sw1.p1.form.domain.FormVersionStatus;
import sw1.p1.form.dto.*;
import sw1.p1.form.exception.FormStateConflictException;
import sw1.p1.form.exception.FormTemplateNotFoundException;
import sw1.p1.form.exception.FormValidationException;
import sw1.p1.form.exception.FormVersionNotFoundException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({FormTemplateController.class, FormVersionController.class})
@Import({GlobalExceptionHandler.class, FormApiSecurityTest.TestSecurityConfig.class})
class FormApiSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FormService formService;
    @MockitoBean private CurrentOrganizationResolver orgResolver;
    @MockitoBean private sw1.p1.security.JwtTokenProvider jwtTokenProvider;
    @MockitoBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockitoBean private sw1.p1.auth.domain.UserRepository userRepository;

    @BeforeEach
    void setUp() {
        when(orgResolver.requireOrganizationId()).thenReturn("org-1");
        when(orgResolver.requireEmail()).thenReturn("admin@demo.com");
    }

    // ── ADMIN ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreatesTemplateReturns201() throws Exception {
        when(formService.createTemplate(eq("org-1"), any(), eq("admin@demo.com")))
                .thenReturn(new FormTemplateResponse("t1", "FORM_TEST", "Test", null, true, "org-1",
                        "admin", Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/admin/form-templates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FORM_TEST\",\"name\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FORM_TEST"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetsVersionReturns200() throws Exception {
        when(formService.getVersion("org-1", "v1")).thenReturn(
                new FormVersionResponse("v1", "t1", 1, FormVersionStatus.DRAFT,
                        List.of(), "admin", Instant.now(), Instant.now(), null));

        mockMvc.perform(get("/api/admin/form-versions/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminValidateReturnsMultipleErrors() throws Exception {
        when(formService.validateVersion("org-1", "v1"))
                .thenReturn(new ValidationResult(false, List.of("Error 1", "Error 2")));

        mockMvc.perform(post("/api/admin/form-versions/v1/validate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyFieldsAllowed() throws Exception {
        when(formService.updateVersion(eq("org-1"), eq("v1"), any()))
                .thenReturn(new FormVersionResponse("v1", "t1", 1, FormVersionStatus.DRAFT,
                        List.of(), "admin", Instant.now(), Instant.now(), null));

        mockMvc.perform(put("/api/admin/form-versions/v1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[]}"))
                .andExpect(status().isOk());
    }

    // ── OFFICER / CLIENT ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OFFICER")
    void officerCannotCreateTemplate() throws Exception {
        mockMvc.perform(post("/api/admin/form-templates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FORM_X\",\"name\":\"Formulario de prueba\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(formService);
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientCannotAccessTemplates() throws Exception {
        mockMvc.perform(get("/api/admin/form-templates"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(formService);
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientCannotAccessVersions() throws Exception {
        mockMvc.perform(get("/api/admin/form-versions/v1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(formService);
    }

    // ── 404 not found ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void otherOrgTemplateReturns404() throws Exception {
        when(formService.getTemplate("org-1", "t2"))
                .thenThrow(new FormTemplateNotFoundException("no encontrado"));

        mockMvc.perform(get("/api/admin/form-templates/t2"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void otherOrgVersionReturns404() throws Exception {
        when(formService.getVersion("org-1", "v2"))
                .thenThrow(new FormVersionNotFoundException("no encontrada"));

        mockMvc.perform(get("/api/admin/form-versions/v2"))
                .andExpect(status().isNotFound());
    }

    // ── 409 conflict ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateCodeReturns409() throws Exception {
        when(formService.createTemplate(eq("org-1"), any(), eq("admin@demo.com")))
                .thenThrow(new FormStateConflictException("ya existe"));

        mockMvc.perform(post("/api/admin/form-templates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FORM_X\",\"name\":\"Formulario\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePublishedVersionReturns409() throws Exception {
        when(formService.updateVersion(eq("org-1"), eq("v1"), any()))
                .thenThrow(new FormStateConflictException("Solo DRAFT"));

        mockMvc.perform(put("/api/admin/form-versions/v1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[]}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void publishNonDraftReturns409() throws Exception {
        when(formService.publishVersion("org-1", "v1"))
                .thenThrow(new FormStateConflictException("Solo DRAFT"));

        mockMvc.perform(post("/api/admin/form-versions/v1/publish").with(csrf()))
                .andExpect(status().isConflict());
    }

    // ── 422 validation ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void publishInvalidReturns422() throws Exception {
        when(formService.publishVersion("org-1", "v1"))
                .thenThrow(new FormValidationException("Error 1; Error 2"));

        mockMvc.perform(post("/api/admin/form-versions/v1/publish").with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── 400 bad request ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void missingCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/form-templates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Formulario\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nullFieldsReturns400() throws Exception {
        mockMvc.perform(put("/api/admin/form-versions/v1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
