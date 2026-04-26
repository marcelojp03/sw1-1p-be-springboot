package sw1.p1.policy.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Transición entre nodos, embebida en WorkflowPolicy */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowTransition {

    private String transitionId;

    private String from;

    private String to;

    /**
     * Condición de evaluación en formato simple: "fieldId == value"
     * Null significa transición por defecto (sin condición).
     */
    private String condition;

    /** Etiqueta visual de la transición */
    private String label;
}
