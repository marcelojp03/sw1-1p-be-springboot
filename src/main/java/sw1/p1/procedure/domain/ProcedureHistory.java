package sw1.p1.procedure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Evento histórico de un trámite.
 * Colección separada para permitir crecimiento ilimitado.
 */
@Document(collection = "procedure_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcedureHistory {

    @Id
    private String id;

    @Indexed
    private String procedureId;

    /** Nodo que originó el evento */
    private String nodeId;

    private String nodeLabel;

    /**
     * Tipo de evento: NODE_STARTED, TASK_COMPLETED, CONDITION_EVALUATED,
     * STATUS_CHANGED, CLIENT_TASK_CREATED, CLIENT_TASK_COMPLETED, etc.
     */
    private String eventType;

    /** ID del actor humano que generó el evento (puede ser null para eventos automáticos) */
    private String userId;

    /** Datos del formulario enviados en este paso */
    private Map<String, Object> formData;

    /** Información adicional (ej: resultado de condición, motivo de rechazo) */
    private String notes;

    private Instant occurredAt;
}
