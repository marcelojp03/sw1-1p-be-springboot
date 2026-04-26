package sw1.p1.notification.dto;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String organizationId,
        String clientId,
        String userId,
        String procedureCode,
        String type,
        String title,
        String message,
        String procedureId,
        String taskId,
        boolean read,
        Instant createdAt,
        Instant readAt
) {}
