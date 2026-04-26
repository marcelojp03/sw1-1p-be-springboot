package sw1.p1.policy.dto;

import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.policy.domain.WorkflowTransition;
import sw1.p1.policy.domain.Swimlane;

import java.util.List;
import java.util.Map;

/** Actualización completa del diagrama: nodos, transiciones, swimlanes y datos visuales */
public record DiagramUpdateRequest(
        Map<String, Object> diagram,
        List<WorkflowNode> nodes,
        List<WorkflowTransition> transitions,
        List<Swimlane> swimlanes
) {}
