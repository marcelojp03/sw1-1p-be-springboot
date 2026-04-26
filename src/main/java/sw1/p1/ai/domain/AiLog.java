package sw1.p1.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Registro de cada consulta realizada a FastAPI IA.
 * Campos alineados con DATABASE.md sección 9 "ai_logs".
 */
@Document(collection = "ai_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiLog {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    /** ID del usuario que inició la petición */
    @Indexed
    private String userId;

    /**
     * Contexto de la consulta IA.
     * Valores: WORKFLOW_DESIGN | FORM_SUGGESTION | BOTTLENECK_ANALYSIS | GENERAL
     */
    private String context;

    @Indexed
    private String policyId;

    /** Payload enviado a FastAPI */
    private Object input;

    /** Payload recibido de FastAPI */
    private Object output;

    private String model;

    private Integer tokensUsed;

    private long durationMs;

    private boolean success;

    /** null cuando success=true */
    private String errorMessage;

    private Instant createdAt;
}
