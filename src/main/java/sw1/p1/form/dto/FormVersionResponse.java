package sw1.p1.form.dto;

import sw1.p1.form.domain.FormFieldDefinition;
import sw1.p1.form.domain.FormVersionStatus;

import java.time.Instant;
import java.util.List;

public record FormVersionResponse(
        String id,
        String formTemplateId,
        int versionNumber,
        FormVersionStatus status,
        List<FormFieldDefinition> fields,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt
) {}
