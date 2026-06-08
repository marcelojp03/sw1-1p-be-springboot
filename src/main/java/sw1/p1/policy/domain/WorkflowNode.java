package sw1.p1.policy.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sw1.p1.shared.NodeType;

import java.util.ArrayList;
import java.util.List;

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

    /** IDs de documentos requeridos en este nodo (Ciclo 2) */
    @Builder.Default
    private List<String> documentIds = new ArrayList<>();

    /** Nombres/tipos de documentos que el cliente debe adjuntar en este nodo */
    @Builder.Default
    private List<String> requiredDocuments = new ArrayList<>();
}
