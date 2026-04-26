package sw1.p1.procedure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.policy.domain.WorkflowTransition;
import sw1.p1.shared.PolicyStatus;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot de la política al momento de iniciar el trámite.
 * Protege al trámite de cambios futuros en la política.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicySnapshot {

    private String policyId;
    private String policyKey;
    private String policyName;
    private int version;
    private PolicyStatus status;
    private List<WorkflowNode> nodes;
    private List<WorkflowTransition> transitions;
    private Instant snapshotAt;
}
