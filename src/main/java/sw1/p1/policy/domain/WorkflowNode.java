package sw1.p1.policy.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sw1.p1.shared.NodeType;

/** Nodo del diagrama de actividad, embebido en WorkflowPolicy */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowNode {

    private String nodeId;

    private NodeType type;

    private String label;

    /** Para nodos manuales: área responsable */
    private String areaId;

    /** Posición en el canvas (para el diseñador visual) */
    private Integer posX;
    private Integer posY;

    /** SLA en horas (opcional) */
    private Integer slaHours;

    /** Formulario dinámico (para MANUAL_FORM) */
    private FormDefinition form;

    /** Plantilla de mensaje (para NOTIFICATION) */
    private String notificationTemplate;
}
