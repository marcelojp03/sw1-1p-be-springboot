package sw1.p1.document.dto;

import sw1.p1.document.domain.DocumentPermission;
import sw1.p1.document.domain.DocumentScope;

import java.util.List;

public record CreateDocumentRequest(
        String organizationId,
        DocumentScope scope,
        String scopeReferenceId,
        String title,
        String description,
        List<String> allowedRoles,
        List<DocumentPermission> permissions,
        List<String> tags
) {}
