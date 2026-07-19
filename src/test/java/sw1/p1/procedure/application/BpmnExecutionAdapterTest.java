package sw1.p1.procedure.application;

import org.junit.jupiter.api.Test;
import sw1.p1.exception.BusinessException;
import sw1.p1.policy.domain.NodeConfiguration;
import sw1.p1.policy.application.NodeExecutionProfileResolver;
import sw1.p1.policy.domain.PolicyVersion;
import sw1.p1.shared.PolicyVersionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BpmnExecutionAdapterTest {

    private final BpmnExecutionAdapter adapter =
            new BpmnExecutionAdapter(new NodeExecutionProfileResolver());

    private final String VALID_BPMN = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:startEvent id="s1" name="Inicio"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                <bpmn:userTask id="u1" name="Completar"><bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing></bpmn:userTask>
                <bpmn:endEvent id="e1" name="Fin"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="f1" sourceRef="s1" targetRef="u1"/>
                <bpmn:sequenceFlow id="f2" sourceRef="u1" targetRef="e1"/>
              </bpmn:process>
            </bpmn:definitions>""";

    private PolicyVersion makeVersion(String xml) {
        return PolicyVersion.builder()
                .id("POL-V1").policyId("POL")
                .versionNumber(1).status(PolicyVersionStatus.PUBLISHED)
                .bpmnXml(xml).createdAt(Instant.now()).build();
    }

    private NodeConfiguration config(String id, String taskKind) {
        NodeConfiguration c = new NodeConfiguration();
        c.setBpmnElementId(id);
        c.setTaskKind(taskKind);
        return c;
    }

    @Test
    void validClientTask_ShouldParse() {
        var v = makeVersion(VALID_BPMN);
        var config = config("u1", "CLIENT_TASK");
        config.setFormVersionId("form-v2");
        config.setSlaHours(24);
        var def = adapter.parse(v, List.of(config));
        assertEquals(3, def.nodes().size());
        assertEquals(2, def.transitions().size());
        var node = def.nodes().stream().filter(n -> "u1".equals(n.getNodeId())).findFirst().orElseThrow();
        assertEquals(sw1.p1.shared.NodeType.CLIENT_TASK, node.getType());
        assertEquals("form-v2", node.getFormVersionId());
        assertEquals(24, node.getSlaHours());
        assertNull(node.getForm());
    }

    @Test
    void clientTaskWithoutForm_ShouldKeepNullFormVersion() {
        var node = adapter.parse(makeVersion(VALID_BPMN),
                        List.of(config("u1", "CLIENT_TASK"))).nodes().stream()
                .filter(n -> "u1".equals(n.getNodeId())).findFirst().orElseThrow();

        assertEquals(sw1.p1.shared.NodeType.CLIENT_TASK, node.getType());
        assertNull(node.getFormVersionId());
    }

    @Test
    void draft_ShouldThrow() {
        var v = makeVersion(VALID_BPMN);
        v.setStatus(PolicyVersionStatus.DRAFT);
        assertThrows(BusinessException.class,
                () -> adapter.parse(v, List.of(config("u1", "CLIENT_TASK"))));
    }

    @Test
    void userTaskWithoutConfig_ShouldThrow() {
        var v = makeVersion(VALID_BPMN);
        assertThrows(BusinessException.class,
                () -> adapter.parse(v, List.of()));
    }

    @Test
    void officerTaskWithForm_ShouldBecomeManualForm() {
        var v = makeVersion(VALID_BPMN);
        var config = config("u1", "OFFICER_TASK");
        config.setDepartmentId("dep-1");
        config.setFormVersionId("form-v3");
        config.setSlaHours(48);

        var node = adapter.parse(v, List.of(config)).nodes().stream()
                .filter(n -> "u1".equals(n.getNodeId())).findFirst().orElseThrow();

        assertEquals(sw1.p1.shared.NodeType.MANUAL_FORM, node.getType());
        assertEquals("dep-1", node.getDepartmentId());
        assertEquals("form-v3", node.getFormVersionId());
        assertEquals(48, node.getSlaHours());
    }

    @Test
    void officerTaskWithoutForm_ShouldBecomeManualAction() {
        var config = config("u1", "OFFICER_TASK");
        config.setDepartmentId("dep-1");

        var node = adapter.parse(makeVersion(VALID_BPMN), List.of(config)).nodes().stream()
                .filter(n -> "u1".equals(n.getNodeId())).findFirst().orElseThrow();

        assertEquals(sw1.p1.shared.NodeType.MANUAL_ACTION, node.getType());
        assertNull(node.getFormVersionId());
    }

    @Test
    void invalidConfiguration_DoesNotProducePartialDefinition() {
        var config = config("u1", "OFFICER_TASK");
        config.setFormVersionId("form-v3");

        assertThrows(BusinessException.class,
                () -> adapter.parse(makeVersion(VALID_BPMN), List.of(config)));
    }

    @Test
    void gateway_ShouldThrow() {
        var xml = VALID_BPMN.replace("userTask id=\"u1\"", "exclusiveGateway id=\"u1\"")
                .replace("bpmn:userTask", "bpmn:exclusiveGateway");
        var v = makeVersion(xml);
        assertThrows(BusinessException.class,
                () -> adapter.parse(v, List.of()));
    }

    @Test
    void duplicateConfig_ShouldThrow() {
        var v = makeVersion(VALID_BPMN);
        var c1 = config("u1", "CLIENT_TASK");
        c1.setId("id-aaa");
        var c2 = config("u1", "CLIENT_TASK");
        c2.setId("id-bbb");
        assertThrows(BusinessException.class,
                () -> adapter.parse(v, List.of(c1, c2)));
    }

    @Test
    void doctype_ShouldThrow() {
        var xml = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + VALID_BPMN;
        var v = makeVersion(xml);
        assertThrows(BusinessException.class,
                () -> adapter.parse(v, List.of(config("u1", "CLIENT_TASK"))));
    }

    @Test
    void differentPrefix_ShouldWork() {
        var xml = VALID_BPMN.replace("xmlns:bpmn", "xmlns:model")
                .replace("bpmn:", "model:");
        var v = makeVersion(xml);
        var def = adapter.parse(v, List.of(config("u1", "CLIENT_TASK")));
        assertEquals(3, def.nodes().size());
    }
}
