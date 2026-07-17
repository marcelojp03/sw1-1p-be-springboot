package sw1.p1.policy.dto;

import java.time.Instant;
import java.util.List;

public record PolicyVersionSummaryResponse(
        String id,
        String policyKey,
        String name,
        String latestVersionId,
        Integer latestVersionNumber,
        String latestVersionStatus,
        Instant publishedAt,
        Instant updatedAt,
        String draftVersionId,
        String publishedVersionId,
        List<String> allowedStartChannels
) {}
