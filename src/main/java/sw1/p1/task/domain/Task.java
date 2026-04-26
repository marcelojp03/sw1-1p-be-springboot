package sw1.p1.task.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import sw1.p1.shared.TaskAudience;
import sw1.p1.shared.TaskStatus;
import sw1.p1.shared.storage.AttachmentRef;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "proc_node", def = "{'procedureId': 1, 'nodeId': 1}"),
    @CompoundIndex(name = "area_status", def = "{'assignedAreaId': 1, 'status': 1}"),
    @CompoundIndex(name = "assignee_status", def = "{'assignedUserId': 1, 'status': 1}")
})
public class Task {

    @Id
    private String id;

    @Indexed
    private String procedureId;

    private String procedureCode;

    private String policyId;

    private String nodeId;

    private String label;

    private String organizationId;

    /** Área responsable (para bandeja de OFFICER) */
    private String assignedAreaId;

    private TaskAudience taskAudience;

    private TaskStatus status;

    /** Usuario (OFFICER o CLIENT) asignado a la tarea */
    private String assignedUserId;

    /**
     * Cliente al que pertenece la tarea (para CLIENT_TASK).
     * Requerido cuando taskAudience == CLIENT.
     */
    private String assignedClientId;

    /** Definición del formulario serializada desde el nodo */
    private sw1.p1.policy.domain.FormDefinition form;

    /** Respuesta del formulario al completar la tarea */
    private Map<String, Object> formResponse;

    private String notes;

    /** Quién completó la tarea */
    private String completedBy;

    /** Adjuntos/documentos subidos (referencias a S3) */
    private List<AttachmentRef> attachments;

    private Instant createdAt;
    private Instant startedAt;
    private Instant dueAt;
    private Instant updatedAt;
    private Instant completedAt;
}
