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
import java.util.List;
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

    /** Código único legible del trámite, ej: TRM-2026-0001 */
    @Indexed(unique = true, sparse = true)
    private String code;

    @Indexed
    private String organizationId;

    @Indexed
    private String policyId;

    private int policyVersion;

    @Indexed
    private String clientId;

    /** ID del usuario (OFFICER o CLIENT) que inició el trámite */
    private String startedBy;

    /** Información del solicitante al momento de iniciar el trámite */
    private RequesterInfo requester;

    /** IDs de los nodos activos en el momento actual */
    private List<String> currentNodeIds;

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

    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;

    /** Información básica del solicitante, embebida para proteger ante cambios en el registro client */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RequesterInfo {
        private String fullName;
        private String documentType;
        private String documentNumber;
        private String phone;
        private String email;
    }
}
