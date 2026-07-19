package sw1.p1.policy.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "node_configurations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "version_element", def = "{'policyVersionId': 1, 'bpmnElementId': 1}", unique = true)
})
public class NodeConfiguration {

    @Id
    private String id;

    private String policyId;

    private String policyVersionId;

    private String bpmnElementId;

    private String taskKind;

    private String assignmentMode;

    private String departmentId;

    private String formVersionId;

    private Integer slaHours;

    private String label;

    private String description;
}
