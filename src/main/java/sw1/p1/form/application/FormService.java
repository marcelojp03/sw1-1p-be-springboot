package sw1.p1.form.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sw1.p1.form.domain.*;
import sw1.p1.form.dto.*;
import sw1.p1.form.exception.FormTemplateNotFoundException;
import sw1.p1.form.exception.FormValidationException;
import sw1.p1.form.exception.FormVersionNotFoundException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FormService {

    private final FormTemplateRepository templateRepository;
    private final FormVersionRepository versionRepository;
    private final FormValidationService validationService;

    // ── Templates ──────────────────────────────────────────────────────────

    public FormTemplateResponse createTemplate(String organizationId, CreateFormTemplateRequest req,
                                                String createdBy) {
        if (templateRepository.findByOrganizationIdAndCode(organizationId, req.code()).isPresent()) {
            throw new FormValidationException("El codigo '" + req.code() + "' ya existe en tu organizacion");
        }
        FormTemplate template = FormTemplate.builder()
                .organizationId(organizationId)
                .code(req.code().trim())
                .name(req.name().trim())
                .description(req.description())
                .active(true)
                .createdBy(createdBy)
                .build();
        template = templateRepository.save(template);
        return toResponse(template);
    }

    public List<FormTemplateResponse> listTemplates(String organizationId) {
        return templateRepository.findByOrganizationIdAndActiveTrue(organizationId)
                .stream().map(this::toResponse).toList();
    }

    public FormTemplateResponse getTemplate(String organizationId, String templateId) {
        FormTemplate t = findTemplateOrThrow(organizationId, templateId);
        return toResponse(t);
    }

    public FormTemplateResponse updateTemplate(String organizationId, String templateId,
                                                UpdateFormTemplateRequest req) {
        FormTemplate t = findTemplateOrThrow(organizationId, templateId);

        if (templateRepository.existsByOrganizationIdAndCodeAndIdNot(
                organizationId, req.name().trim(), templateId)) {
            throw new FormValidationException("El codigo ya esta en uso");
        }

        t.setName(req.name().trim());
        t.setDescription(req.description());
        t.setActive(req.active());
        t.setUpdatedAt(Instant.now());
        t = templateRepository.save(t);
        return toResponse(t);
    }

    // ── Versions ───────────────────────────────────────────────────────────

    public FormVersionResponse createVersion(String organizationId, String templateId,
                                              CreateFormVersionRequest req, String createdBy) {
        FormTemplate template = findTemplateOrThrow(organizationId, templateId);
        validationService.validate(req.fields());

        List<FormVersion> existing = versionRepository.findByFormTemplateIdOrderByVersionNumberDesc(templateId);
        int nextNumber = existing.isEmpty() ? 1 : existing.get(0).getVersionNumber() + 1;

        FormVersion version = FormVersion.builder()
                .formTemplateId(templateId)
                .organizationId(organizationId)
                .versionNumber(nextNumber)
                .status(FormVersionStatus.DRAFT)
                .fields(req.fields())
                .createdBy(createdBy)
                .build();
        version = versionRepository.save(version);
        return toResponse(version);
    }

    public List<FormVersionResponse> listVersions(String organizationId, String templateId) {
        findTemplateOrThrow(organizationId, templateId);
        return versionRepository.findByFormTemplateIdOrderByVersionNumberDesc(templateId)
                .stream().map(this::toResponse).toList();
    }

    public FormVersionResponse getVersion(String organizationId, String versionId) {
        return toResponse(findVersionOrThrow(organizationId, versionId));
    }

    public FormVersionResponse updateVersion(String organizationId, String versionId,
                                              UpdateFormVersionRequest req) {
        FormVersion version = findVersionOrThrow(organizationId, versionId);
        if (version.getStatus() != FormVersionStatus.DRAFT) {
            throw new FormValidationException("Solo se puede editar una version en estado DRAFT");
        }
        validationService.validate(req.fields());
        version.setFields(req.fields());
        version.setUpdatedAt(Instant.now());
        version = versionRepository.save(version);
        return toResponse(version);
    }

    public ValidationResult validateVersion(String organizationId, String versionId) {
        FormVersion version = findVersionOrThrow(organizationId, versionId);
        try {
            validationService.validate(version.getFields());
            return new ValidationResult(true, List.of());
        } catch (FormValidationException e) {
            return new ValidationResult(false, List.of(e.getMessage()));
        }
    }

    public FormVersionResponse publishVersion(String organizationId, String versionId) {
        FormVersion version = findVersionOrThrow(organizationId, versionId);
        if (version.getStatus() == FormVersionStatus.PUBLISHED) {
            throw new FormValidationException("La version ya esta publicada");
        }
        validationService.validate(version.getFields());

        version.setStatus(FormVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now());
        version.setUpdatedAt(Instant.now());
        version = versionRepository.save(version);
        return toResponse(version);
    }

    public List<FormVersionResponse> availableVersions(String organizationId) {
        return versionRepository.findByOrganizationIdAndStatus(organizationId, FormVersionStatus.PUBLISHED)
                .stream().map(this::toResponse).toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private FormTemplate findTemplateOrThrow(String organizationId, String templateId) {
        FormTemplate t = templateRepository.findById(templateId)
                .orElseThrow(() -> new FormTemplateNotFoundException("FormTemplate no encontrado: " + templateId));
        if (!t.getOrganizationId().equals(organizationId)) {
            throw new FormTemplateNotFoundException("FormTemplate no encontrado: " + templateId);
        }
        return t;
    }

    private FormVersion findVersionOrThrow(String organizationId, String versionId) {
        FormVersion v = versionRepository.findById(versionId)
                .orElseThrow(() -> new FormVersionNotFoundException("FormVersion no encontrada: " + versionId));
        if (!v.getOrganizationId().equals(organizationId)) {
            throw new FormVersionNotFoundException("FormVersion no encontrada: " + versionId);
        }
        return v;
    }

    private FormTemplateResponse toResponse(FormTemplate t) {
        return new FormTemplateResponse(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.isActive(), t.getOrganizationId(),
                t.getCreatedBy(), t.getCreatedAt(), t.getUpdatedAt()
        );
    }

    private FormVersionResponse toResponse(FormVersion v) {
        return new FormVersionResponse(
                v.getId(), v.getFormTemplateId(), v.getVersionNumber(), v.getStatus(),
                v.getFields(), v.getCreatedBy(),
                v.getCreatedAt(), v.getUpdatedAt(), v.getPublishedAt()
        );
    }
}
