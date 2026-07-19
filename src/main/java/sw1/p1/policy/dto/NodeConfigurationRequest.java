package sw1.p1.policy.dto;

public record NodeConfigurationRequest(
        String taskKind,
        String assignmentMode,
        String departmentId,
        String formVersionId,
        Integer slaHours,
        String label,
        String description
) {}
