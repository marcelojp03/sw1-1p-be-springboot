package sw1.p1.policy.dto;

import sw1.p1.policy.domain.NodeConfiguration;

public final class NodeConfigurationMapper {

    private NodeConfigurationMapper() {}

    public static NodeConfiguration create(String policyId, String versionId, String elementId,
                                           NodeConfigurationRequest request) {
        return NodeConfiguration.builder()
                .policyId(policyId)
                .policyVersionId(versionId)
                .bpmnElementId(elementId)
                .taskKind(request.taskKind())
                .assignmentMode(request.assignmentMode())
                .departmentId(request.departmentId())
                .formVersionId(normalizeId(request.formVersionId()))
                .slaHours(request.slaHours())
                .label(request.label())
                .description(request.description())
                .build();
    }

    public static void update(NodeConfiguration target, NodeConfigurationRequest request) {
        target.setTaskKind(request.taskKind());
        target.setDepartmentId(request.departmentId());
        target.setAssignmentMode(request.assignmentMode());
        target.setFormVersionId(normalizeId(request.formVersionId()));
        target.setSlaHours(request.slaHours());
        target.setLabel(request.label());
        target.setDescription(request.description());
    }

    public static NodeConfigurationResponse toResponse(NodeConfiguration config) {
        return new NodeConfigurationResponse(
                config.getId(),
                config.getPolicyId(),
                config.getPolicyVersionId(),
                config.getBpmnElementId(),
                config.getTaskKind(),
                config.getAssignmentMode(),
                config.getDepartmentId(),
                config.getFormVersionId(),
                config.getSlaHours(),
                config.getLabel(),
                config.getDescription());
    }

    private static String normalizeId(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
