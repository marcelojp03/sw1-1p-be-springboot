package sw1.p1.policy.dto;

public record NodeConfigurationResponse(
        String id,
        String policyId,
        String policyVersionId,
        String bpmnElementId,
        String taskKind,
        String assignmentMode,
        String departmentId,
        String formVersionId,
        Integer slaHours,
        String label,
        String description
) {}
