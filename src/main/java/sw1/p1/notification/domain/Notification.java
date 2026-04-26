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
@CompoundIndex(name = "client_read", def = "{'clientId': 1, 'read': 1}")
public class Notification {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    /** ID del cliente externo destinatario */
    @Indexed
    private String clientId;

    /** ID del usuario móvil vinculado al cliente */
    @Indexed
    private String userId;

    /** Código del trámite relacionado */
    private String procedureCode;

    /**
     * Tipo de notificación: PROCEDURE_UPDATE, TASK_COMPLETED,
     * PROCEDURE_APPROVED, PROCEDURE_REJECTED, PROCEDURE_OBSERVED, CUSTOM
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
