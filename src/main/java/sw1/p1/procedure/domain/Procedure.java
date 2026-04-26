package sw1.p1.procedure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import sw1.p1.shared.ProcedureStatus;

import java.time.Instant;
import java.util.Map;

@Document(collection = "procedures")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndex(name = "org_status", def = "{'organizationId': 1, 'status': 1}")
public class Procedure {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    @Indexed
    private String clientId;

    /** ID del usuario (OFFICER) que inició el trámite */
    private String startedBy;

    /** ID del nodo activo en el momento actual */
    private String currentNodeId;

    private ProcedureStatus status;

    /** Snapshot inmutable de la política al inicio */
    private PolicySnapshot policySnapshot;

    /**
     * Datos acumulados del formulario a lo largo del trámite.
     * Clave: nodeId, Valor: Map de fieldId->value
     */
    private Map<String, Map<String, Object>> formData;

    /** Canal por el que se inició: "WEB" o "MOBILE" */
    private String startChannel;

    private Instant createdAt;
    private Instant updatedAt;
}
