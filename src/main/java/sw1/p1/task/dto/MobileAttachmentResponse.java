package sw1.p1.task.dto;

import java.time.Instant;

public record MobileAttachmentResponse(
        String fileName,
        String mimeType,
        Long sizeBytes,
        Instant uploadedAt
) {}
