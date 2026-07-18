package sw1.p1.form.dto;

import sw1.p1.form.domain.FormVersionStatus;

import java.time.Instant;
import java.util.List;

public record FormTemplateResponse(
        String id,
        String code,
        String name,
        String description,
        boolean active,
        String organizationId,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
