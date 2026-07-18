package sw1.p1.form.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sw1.p1.form.domain.*;
import sw1.p1.form.dto.*;
import sw1.p1.form.exception.FormStateConflictException;
import sw1.p1.form.exception.FormTemplateNotFoundException;
import sw1.p1.form.exception.FormValidationException;
import sw1.p1.form.exception.FormVersionNotFoundException;

import java.math.BigDecimal;
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
                new CreateFormTemplateRequest("form_test", "Test", "desc"), "admin");

        assertThat(r.code()).isEqualTo("FORM_TEST");
        assertThat(r.name()).isEqualTo("Test");
    }

    @Test
    void normalizeCodeUppercase() {
        when(templateRepository.findByOrganizationIdAndCode(ORG, "FORM_X"))
                .thenReturn(Optional.empty());
        when(templateRepository.save(any())).thenAnswer(inv -> {
            FormTemplate t = inv.getArgument(0);
            t.setId("t1");
            return t;
        });

        var r = service.createTemplate(ORG,
                new CreateFormTemplateRequest("  form_x  ", "X", null), "admin");

        assertThat(r.code()).isEqualTo("FORM_X");
    }

    @Test
    void createTemplateDuplicateCode() {
        when(templateRepository.findByOrganizationIdAndCode(ORG, "FORM_X"))
                .thenReturn(Optional.of(new FormTemplate()));

        assertThatThrownBy(() ->
                service.createTemplate(ORG, new CreateFormTemplateRequest("form_x", "X", null), "admin"))
                .isInstanceOf(FormStateConflictException.class)
                .hasMessageContaining("ya existe");
    }

    @Test
    void updateTemplateCodeImmutable() {
        FormTemplate t = template("t1");
        when(templateRepository.findById("t1")).thenReturn(Optional.of(t));
        when(templateRepository.save(any())).thenReturn(t);

        var r = service.updateTemplate(ORG, "t1",
                new UpdateFormTemplateRequest("New Name", null, true));

        assertThat(r.code()).isEqualTo("F");
        assertThat(r.name()).isEqualTo("New Name");
    }

    @Test
    void getTemplateNotFound() {
        when(templateRepository.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTemplate(ORG, "nope"))
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
    void createDraftEmptyAllowed() {
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
                new CreateFormVersionRequest(List.of()), "admin");

        assertThat(r.fields()).isEmpty();
        assertThat(r.status()).isEqualTo(FormVersionStatus.DRAFT);
    }

    @Test
    void updateDraftAllowedIncomplete() {
        FormVersion v = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.DRAFT).build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));
        when(versionRepository.save(any())).thenReturn(v);

        var r = service.updateVersion(ORG, "v1",
                new UpdateFormVersionRequest(List.of()));

        assertThat(r.fields()).isEmpty();
    }

    @Test
    void updatePublishedRejected() {
        FormVersion v = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.PUBLISHED).build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));

        assertThatThrownBy(() ->
                service.updateVersion(ORG, "v1", new UpdateFormVersionRequest(List.of(textField("x")))))
                .isInstanceOf(FormStateConflictException.class)
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
    void publishEmptyRejected() {
        FormVersion v = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.DRAFT).fields(List.of()).build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.publishVersion(ORG, "v1"))
                .isInstanceOf(FormValidationException.class)
                .hasMessageContaining("al menos un campo");
    }

    @Test
    void publishAlreadyPublished() {
        FormVersion v = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.PUBLISHED).build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.publishVersion(ORG, "v1"))
                .isInstanceOf(FormStateConflictException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void validateReturnsMultipleErrors() {
        FormVersion v = FormVersion.builder().id("v1").organizationId(ORG)
                .status(FormVersionStatus.DRAFT)
                .fields(List.of(
                        FormFieldDefinition.builder().id("f1").key("k1").type(FormFieldType.SELECT).build(),
                        FormFieldDefinition.builder().id("f2").key("k1").type(FormFieldType.GRID).build()
                ))
                .build();
        when(versionRepository.findById("v1")).thenReturn(Optional.of(v));

        var result = service.validateVersion(ORG, "v1");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).size().isGreaterThan(1);
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
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("dup").label("a").type(FormFieldType.TEXT).build(),
                FormFieldDefinition.builder().id("f2").key("dup").label("b").type(FormFieldType.TEXT).build()));
        assertThat(errors).isNotEmpty();
        assertThat(errors.stream().anyMatch(e -> e.contains("Key duplicada"))).isTrue();
    }

    @Test
    void rejectSelectWithoutOptions() {
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("sel").label("S").type(FormFieldType.SELECT).build()));
        assertThat(errors.stream().anyMatch(e -> e.contains("requiere opciones"))).isTrue();
    }

    @Test
    void rejectGridWithoutColumns() {
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("grid").label("G").type(FormFieldType.GRID).build()));
        assertThat(errors.stream().anyMatch(e -> e.contains("requiere columnas"))).isTrue();
    }

    @Test
    void rejectGridDuplicateColumns() {
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("grid").label("G").type(FormFieldType.GRID)
                        .columns(List.of(
                                GridColumnDefinition.builder().key("col").label("C1").type(GridColumnType.TEXT).build(),
                                GridColumnDefinition.builder().key("col").label("C2").type(GridColumnType.NUMBER).build()))
                        .build()));
        assertThat(errors.stream().anyMatch(e -> e.contains("Columna duplicada"))).isTrue();
    }

    @Test
    void rejectEmptyKey() {
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("").label("L").type(FormFieldType.TEXT).build()));
        assertThat(errors.stream().anyMatch(e -> e.contains("key"))).isTrue();
    }

    @Test
    void rejectMissingLabel() {
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("x").type(FormFieldType.TEXT).build()));
        assertThat(errors.stream().anyMatch(e -> e.contains("label"))).isTrue();
    }

    @Test
    void rejectNegativeMinLength() {
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("n").label("N").type(FormFieldType.TEXT)
                        .validation(FieldValidation.builder().minLength(-1).build()).build()));
        assertThat(errors.stream().anyMatch(e -> e.contains("minLength negativo"))).isTrue();
    }

    @Test
    void rejectInvalidRegex() {
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("r").label("R").type(FormFieldType.TEXT)
                        .validation(FieldValidation.builder().regex("[invalid").build()).build()));
        assertThat(errors.stream().anyMatch(e -> e.contains("Regex invalido"))).isTrue();
    }

    @Test
    void acceptNullOptions() {
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("sel").label("S").type(FormFieldType.SELECT)
                        .options(null).build()));
        assertThat(errors.stream().anyMatch(e -> e.contains("requiere opciones"))).isTrue();
    }

    @Test
    void rejectGridColumnWithoutId() {
        var errors = validationService.collectErrors(List.of(
                FormFieldDefinition.builder().id("f1").key("grid").label("G").type(FormFieldType.GRID)
                        .columns(List.of(
                                GridColumnDefinition.builder().key("c1").label("C1").type(GridColumnType.TEXT).build()))
                        .build()));
        assertThat(errors.stream().anyMatch(e -> e.contains("sin id"))).isTrue();
    }

    @Test
    void rejectNullFieldInList() {
        var fields = new java.util.ArrayList<FormFieldDefinition>();
        fields.add(textField("ok"));
        fields.add(null);
        var errors = validationService.collectErrors(fields);
        assertThat(errors.stream().anyMatch(e -> e.contains("nulo"))).isTrue();
    }

    @Test
    void acceptValidComplexForm() {
        var errors = validationService.collectErrors(List.of(
                textField("nombre"),
                FormFieldDefinition.builder().id("f2").key("tipo").label("Tipo").type(FormFieldType.SELECT)
                        .options(List.of(new FormOption("A", "Opcion A"), new FormOption("B", "Opcion B")))
                        .required(true).build(),
                FormFieldDefinition.builder().id("f3").key("grid").label("Grilla").type(FormFieldType.GRID)
                        .columns(List.of(
                                GridColumnDefinition.builder().id("gc1").key("col1").label("C1").type(GridColumnType.TEXT).build()))
                        .build()
        ));
        assertThat(errors).isEmpty();
    }

    @Test
    void availableVersionsOnlyPublished() {
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
                .id("f_" + key).key(key).label("Label " + key).type(FormFieldType.TEXT).build();
    }
}
