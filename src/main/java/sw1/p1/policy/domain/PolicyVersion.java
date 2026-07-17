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
import sw1.p1.shared.PolicyVersionStatus;

import java.time.Instant;

@Document(collection = "policy_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "policy_status", def = "{'policyId': 1, 'status': 1}")
})
public class PolicyVersion {

    @Id
    private String id;

    @Indexed
    private String policyId;

    private int versionNumber;

    private PolicyVersionStatus status;

    /** BPMN 2.0 XML del diagrama */
    private String bpmnXml;

    private String createdBy;

    private Instant createdAt;

    private Instant publishedAt;
}
