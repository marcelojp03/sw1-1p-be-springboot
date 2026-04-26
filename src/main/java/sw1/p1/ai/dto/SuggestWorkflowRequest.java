package sw1.p1.ai.dto;

import java.util.List;

public record SuggestWorkflowRequest(
        String organizationName,
        String policyName,
        String policyDescription,
        List<ExistingNodeDto> existingNodes,
        String language
) {
    public record ExistingNodeDto(String nodeId, String type, String label) {}
}
