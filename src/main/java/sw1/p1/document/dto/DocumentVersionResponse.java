package sw1.p1.document.dto;

import sw1.p1.document.domain.DocumentStatus;
import sw1.p1.document.domain.VersionType;

import java.time.Instant;

public record DocumentVersionResponse(
        String id,
        String documentId,
        int versionNumber,
        VersionType versionType,
        String storageKey,
        String fileName,
        String mimeType,
        long sizeBytes,
        String uploadedBy,
        Instant uploadedAt,
        String changeReason,
        DocumentStatus status,
        String checksum,
        String derivedFromStorageKey,
        String downloadUrl
) {}
