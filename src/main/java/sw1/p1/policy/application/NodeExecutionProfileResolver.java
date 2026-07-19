package sw1.p1.policy.application;

import org.springframework.stereotype.Component;
import sw1.p1.exception.BusinessException;
import sw1.p1.policy.domain.NodeConfiguration;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.shared.NodeType;

@Component
public class NodeExecutionProfileResolver {

    public record RuntimeNodeProfile(
            NodeType runtimeType,
            boolean departmentRequired,
            String departmentId,
            String formVersionId,
            Integer slaHours
    ) {}

    public static final class ResolutionException extends BusinessException {
        private final String code;

        private ResolutionException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public RuntimeNodeProfile resolve(String bpmnElementType, NodeConfiguration config) {
        if (!"userTask".equals(localName(bpmnElementType))) {
            throw invalid("BPMN_CONFIG_NOT_USER_TASK",
                    "La configuración runtime solo se admite en BPMN UserTask.");
        }
        if (config == null || config.getTaskKind() == null || config.getTaskKind().isBlank()) {
            throw invalid("BPMN_CONFIG_NO_TASKKIND",
                    "NodeConfiguration no tiene taskKind definido.");
        }

        validateSla(config.getSlaHours());
        String formVersionId = normalizeId(config.getFormVersionId());

        return switch (config.getTaskKind()) {
            case "CLIENT_TASK" -> {
                if (hasText(config.getDepartmentId())) {
                    throw invalid("BPMN_CONFIG_CLIENT_NO_DEPT",
                            "CLIENT_TASK no debe tener departmentId.");
                }
                yield new RuntimeNodeProfile(
                        NodeType.CLIENT_TASK, false, null, formVersionId, config.getSlaHours());
            }
            case "OFFICER_TASK" -> {
                if (!hasText(config.getDepartmentId())) {
                    throw invalid("BPMN_CONFIG_NO_DEPARTMENT",
                            "OFFICER_TASK requiere departmentId.");
                }
                NodeType type = formVersionId == null ? NodeType.MANUAL_ACTION : NodeType.MANUAL_FORM;
                yield new RuntimeNodeProfile(
                        type, true, config.getDepartmentId(), formVersionId, config.getSlaHours());
            }
            default -> throw invalid("BPMN_CONFIG_UNSUPPORTED_TASKKIND",
                    "taskKind no soportado para UserTask: " + config.getTaskKind());
        };
    }

    public void validateRuntimeNode(WorkflowNode node) {
        validateSla(node.getSlaHours());
        switch (node.getType()) {
            case CLIENT_TASK -> {
                if (hasText(node.getDepartmentId())) {
                    throw invalid("RUNTIME_CLIENT_NO_DEPT",
                            "CLIENT_TASK no debe tener departmentId.");
                }
            }
            case MANUAL_FORM -> {
                requireDepartment(node);
                if (!hasText(node.getFormVersionId())) {
                    throw invalid("RUNTIME_MANUAL_FORM_NO_FORM",
                            "MANUAL_FORM requiere formVersionId.");
                }
            }
            case MANUAL_ACTION -> {
                requireDepartment(node);
                if (hasText(node.getFormVersionId())) {
                    throw invalid("RUNTIME_MANUAL_ACTION_HAS_FORM",
                            "MANUAL_ACTION no debe tener formVersionId.");
                }
            }
            default -> throw invalid("RUNTIME_NOT_HUMAN_TASK",
                    "El nodo no representa una tarea humana.");
        }
    }

    private void requireDepartment(WorkflowNode node) {
        if (!hasText(node.getDepartmentId())) {
            throw invalid("RUNTIME_NO_DEPARTMENT",
                    node.getType() + " requiere departmentId.");
        }
    }

    private void validateSla(Integer slaHours) {
        if (slaHours != null && slaHours <= 0) {
            throw invalid("BPMN_CONFIG_INVALID_SLA", "slaHours debe ser mayor que cero.");
        }
    }

    private String localName(String elementType) {
        if (elementType == null) return null;
        int separator = elementType.indexOf(':');
        return separator >= 0 ? elementType.substring(separator + 1) : elementType;
    }

    private String normalizeId(String value) {
        return hasText(value) ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ResolutionException invalid(String code, String message) {
        return new ResolutionException(code, message);
    }
}
