package sw1.p1.form.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sw1.p1.form.domain.*;
import sw1.p1.form.dto.*;
import sw1.p1.form.exception.FormTemplateNotFoundException;
import sw1.p1.form.exception.FormValidationException;
import sw1.p1.form.exception.FormVersionNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FormServiceTest {

    private final FormTemplateRepository templateRepository = mock(FormTemplateRepository.class);
    private final FormVersionRepository versionRepository = mock(FormVersionRepository.class);
    private final FormValidationService validationService = new FormValidationService();

    private FormService service;
    private static final String ORG = "org-1";

    @BeforeEach
    void setUp() {
        service = new FormService(templateRepository, versionRepository, validationService);
    }

    // ── Templates ──────────────────────────────────────────────────────────

    @Test
    void createTemplate() {
        when(templateRepository.findByOrganizationIdAndCode(ORG, "FORM_TEST"))
                .thenReturn(Optional.empty());
        when(templateRepository.save(any())).thenAnswer(inv -> {
            FormTemplate t = inv.getArgument(0);
            t.setId("template-1");
            return t;
        });

        var r = service.createTemplate(ORG,
                new CreateFormTemplateRequest("FORM_TEST", "Test", "desc"), "admin");

        assertThat(r.code()).isEqualTo("FORM_TEST");
        assertThat(r.name()).isEqualTo("Test");
        assertThat(r.active()).isTrue();
    }

    @Test
    void createTemplateDuplicateCode() {
        when(templateRepository.findByOrganizationIdAndCode(ORG, "FORM_TEST"))
                .thenReturn(Optional.of(new FormTemplate()));

        assertThatThrownBy(() ->
                service.createTemplate(ORG, new CreateFormTemplateRequest("FORM_TEST", "X", null), "admin"))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("ya existe");
    }

    @Test
    void getTemplateNotFound() {
        when(templateRepository.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTemplate(ORG, "nope"))
                .isInstanceOf(FormTemplateNotFoundException.class);
    }

    @Test
    void getTemplateWrongOrg() {
        FormTemplate t = FormTemplate.builder().id("t1").organizationId("org-2").build();
        when(templateRepository.findById("t1")).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.getTemplate(ORG, "t1"))
                .isInstanceOf(FormTemplateNotFoundException.class);
    }

    // ── Versions ───────────────────────────────────────────────────────────

    @Test
    void createVersionNumberOne() {
        FormTemplate t = template("t1");
        when(templateRepository.findById("t1")).thenReturn(Optional.of(t));
        when(versionRepository.findByFormTemplateIdOrderByVersionNumberDesc("t1"))
                .thenReturn(List.of());
        when(versionRepository.save(any())).thenAnswer(inv -> {
            FormVersion v = inv.getArgument(0);
            v.setId("v1");
            return v;
        });

        var r = service.createVersion(ORG, "t1",
                new CreateFormVersionRequest(List.of(textField("nombre"))), "admin");

        assertThat(r.versionNumber()).isEqualTo(1);
        assertThat(r.status()).isEqualTo(FormVersionStatus.DRAFT);
        assertThat(r.fields()).hasSize(1);
    }

    @Test
    void createVersionIncrementsNumber() {
        FormTemplate t = template("t1");
        when(templateRepository.findById("t1")).thenReturn(Optional.of(t));
        FormVersion prev = FormVersion.builder().versionNumber(3).build();
        when(versionRepository.findByFormTemplateIdOrderByVersionNumberDesc("t1"))
                .thenReturn(List.of(prev));
        when(versionRepository.save(any())).thenAnswer(inv -> {
            FormVersion v = inv.getArgument(0);
            v.setId("v4");
            return v;
        });

        var r = service.createVersion(ORG, "t1",
                new CreateFormVersionRequest(List.of(textField("x"))), "admin");

        assertThat(r.versionNumber()).isEqualTo(4);
    }

    @Test
    void updateDraftAllowed() {
        FormVersion v = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.DRAFT).build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));
        when(versionRepository.save(any())).thenReturn(v);

        var r = service.updateVersion(ORG, "v1",
                new UpdateFormVersionRequest(List.of(textField("updated"))));

        assertThat(r.fields()).hasSize(1);
        assertThat(r.fields().get(0).getKey()).isEqualTo("updated");
    }

    @Test
    void updatePublishedRejected() {
        FormVersion v = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.PUBLISHED).build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));

        assertThatThrownBy(() ->
                service.updateVersion(ORG, "v1", new UpdateFormVersionRequest(List.of(textField("x")))))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void publishValidVersion() {
        FormVersion v = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.DRAFT)
                .fields(List.of(textField("nombre")))
                .build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));
        when(versionRepository.save(any())).thenReturn(v);

        var r = service.publishVersion(ORG, "v1");

        assertThat(r.status()).isEqualTo(FormVersionStatus.PUBLISHED);
        assertThat(r.publishedAt()).isNotNull();
    }

    @Test
    void publishAlreadyPublished() {
        FormVersion v = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.PUBLISHED).build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.publishVersion(ORG, "v1"))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("ya esta publicada");
    }

    @Test
    void versionWrongOrg() {
        FormVersion v = FormVersion.builder().id("v1").organizationId("org-2").build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));
        assertThatThrownBy(() -> service.getVersion(ORG, "v1"))
                .isInstanceOf(FormVersionNotFoundException.class);
    }

    // ── Validation ─────────────────────────────────────────────────────────

    @Test
    void rejectDuplicateKeys() {
        assertThatThrownBy(() -> validationService.validate(List.of(
                FormFieldDefinition.builder().id("f1").key("dup").type(FormFieldType.TEXT).build(),
                FormFieldDefinition.builder().id("f2").key("dup").type(FormFieldType.TEXT).build())))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("Key duplicada");
    }

    @Test
    void rejectSelectWithoutOptions() {
        FormFieldDefinition f = FormFieldDefinition.builder()
                .id("f1").key("sel").type(FormFieldType.SELECT).build();
        assertThatThrownBy(() -> validationService.validate(List.of(f)))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("requiere opciones");
    }

    @Test
    void rejectGridWithoutColumns() {
        FormFieldDefinition f = FormFieldDefinition.builder()
                .id("f1").key("grid").type(FormFieldType.GRID).build();
        assertThatThrownBy(() -> validationService.validate(List.of(f)))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("requiere columnas");
    }

    @Test
    void rejectGridDuplicateColumns() {
        FormFieldDefinition f = FormFieldDefinition.builder()
                .id("f1").key("grid").type(FormFieldType.GRID)
                .columns(List.of(
                        GridColumnDefinition.builder().key("col").type(GridColumnType.TEXT).build(),
                        GridColumnDefinition.builder().key("col").type(GridColumnType.NUMBER).build()))
                .build();
        assertThatThrownBy(() -> validationService.validate(List.of(f)))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("Columna duplicada");
    }

    @Test
    void rejectEmptyKey() {
        FormFieldDefinition f = FormFieldDefinition.builder()
                .id("f1").key("").type(FormFieldType.TEXT).build();
        assertThatThrownBy(() -> validationService.validate(List.of(f)))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("key");
    }

    @Test
    void rejectMinGreaterThanMax() {
        FormFieldDefinition f = FormFieldDefinition.builder()
                .id("f1").key("n").type(FormFieldType.NUMBER)
                .validation(FieldValidation.builder().min(java.math.BigDecimal.TEN).max(java.math.BigDecimal.valueOf(5)).build())
                .build();
        assertThatThrownBy(() -> validationService.validate(List.of(f)))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("min > max");
    }

    @Test
    void acceptValidComplexForm() {
        var f = List.of(
                textField("nombre"),
                FormFieldDefinition.builder().id("f2").key("tipo").type(FormFieldType.SELECT)
                        .options(List.of(new FormOption("A", "Opcion A"), new FormOption("B", "Opcion B")))
                        .required(true).build(),
                FormFieldDefinition.builder().id("f3").key("grid").type(FormFieldType.GRID)
                        .columns(List.of(
                                GridColumnDefinition.builder().key("col1").type(GridColumnType.TEXT).build()))
                        .build()
        );
        assertThatCode(() -> validationService.validate(f)).doesNotThrowAnyException();
    }

    @Test
    void availableVersionsOnlyPublished() {
        FormVersion draft = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.DRAFT).build();
        FormVersion pub = FormVersion.builder().id("v2").organizationId(ORG)
                .status(FormVersionStatus.PUBLISHED).build();
        when(versionRepository.findByOrganizationIdAndStatus(ORG, FormVersionStatus.PUBLISHED))
                .thenReturn(List.of(pub));

        var result = service.availableVersions(ORG);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("v2");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private FormTemplate template(String id) {
        return FormTemplate.builder().id(id).organizationId(ORG).code("F").name("F").active(true).build();
    }

    private FormFieldDefinition textField(String key) {
        return FormFieldDefinition.builder()
                .id("f_" + key).key(key).type(FormFieldType.TEXT).build();
    }
}
