package sw1.p1.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNotificationRequest(
        @NotBlank String organizationId,
        @NotBlank String recipientId,
        @NotBlank String type,
        @NotBlank String title,
        @NotBlank String message,
        String procedureId,
        String taskId
) {}
