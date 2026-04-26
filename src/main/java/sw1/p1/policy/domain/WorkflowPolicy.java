package sw1.p1.policy.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import sw1.p1.shared.PolicyStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Document(collection = "workflow_policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "org_status", def = "{'organizationId': 1, 'status': 1}"),
    @CompoundIndex(name = "org_key", def = "{'organizationId': 1, 'policyKey': 1}")
})
public class WorkflowPolicy {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    /** Clave de negocio compartida entre versiones (ej: "policy_credit") */
    private String policyKey;

    private String name;

    private String description;

    private int version;

    private PolicyStatus status;

    /**
     * Canales desde los que se puede iniciar el trámite.
     * Valores posibles: "WEB", "MOBILE"
     */
    @Builder.Default
    private List<String> allowedStartChannels = new ArrayList<>();

    /**
     * Datos del diagrama visual (JointJS / reactflow cells).
     * Se guarda como objeto genérico, no como string.
     */
    private Map<String, Object> diagram;

    /** Nodos ejecutables del flujo */
    @Builder.Default
    private List<WorkflowNode> nodes = new ArrayList<>();

    /** Transiciones ejecutables entre nodos */
    @Builder.Default
    private List<WorkflowTransition> transitions = new ArrayList<>();

    /** Swimlanes del diagrama (agrupación visual por área) */
    @Builder.Default
    private List<Swimlane> swimlanes = new ArrayList<>();

    private String createdBy;

    private String publishedBy;

    private Instant publishedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
