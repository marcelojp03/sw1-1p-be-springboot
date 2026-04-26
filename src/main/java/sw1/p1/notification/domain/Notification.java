package sw1.p1.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndex(name = "recipient_read", def = "{'recipientId': 1, 'read': 1}")
public class Notification {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    /** ID del usuario destinatario */
    @Indexed
    private String recipientId;

    /**
     * Tipo de notificación: PROCEDURE_STARTED, TASK_ASSIGNED, TASK_COMPLETED,
     * CLIENT_ACTION_REQUIRED, PROCEDURE_COMPLETED, SYSTEM, etc.
     */
    private String type;

    private String title;

    private String message;

    /** ID del trámite relacionado (puede ser null) */
    private String procedureId;

    /** ID de la tarea relacionada (puede ser null) */
    private String taskId;

    private boolean read;

    private Instant createdAt;
    private Instant readAt;
}
