package sw1.p1.document.dto;

import sw1.p1.document.domain.*;

import java.time.Instant;
import java.util.List;

public record DocumentResponse(
        String id,
        String organizationId,
        DocumentScope scope,
        String scopeReferenceId,
        String title,
        String description,
        DocumentStatus status,
        String currentVersionId,
        List<String> allowedRoles,
        List<DocumentPermission> permissions,
        List<DocumentComment> comments,
        List<String> tags,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
