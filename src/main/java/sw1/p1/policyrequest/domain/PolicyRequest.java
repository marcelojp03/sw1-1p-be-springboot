package sw1.p1.policyrequest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Solicitud de política creada cuando identify_policy retorna confidence < 40%.
 * El ADMIN decide: convierte a política o rechaza. La IA solo recomienda.
 */
@Document(collection = "policy_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRequest {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    private String requestText;
    private String suggestedPolicyKey;
    private Double confidence;

    @Builder.Default
    private PolicyRequestStatus status = PolicyRequestStatus.PENDING_REVIEW;

    private Instant createdAt;

    /** Usuario o funcionario que originó la solicitud (puede ser null si es cliente anónimo). */
    private String createdBy;

    private String reviewedBy;
    private Instant reviewedAt;

    /** Nota opcional del revisor al convertir o rechazar. */
    private String reviewNote;
}
