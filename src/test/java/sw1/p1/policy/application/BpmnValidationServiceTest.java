package sw1.p1.policy.application;

import org.junit.jupiter.api.Test;
import sw1.p1.policy.domain.NodeConfiguration;
import sw1.p1.policy.domain.NodeConfigurationRepository;
import sw1.p1.policy.domain.WorkflowPolicyRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmnValidationServiceTest {

    private final NodeConfigurationRepository nodeConfigRepo = mock(NodeConfigurationRepository.class);
    private final WorkflowPolicyRepository policyRepo = mock(WorkflowPolicyRepository.class);
    private final BpmnValidationService svc = new BpmnValidationService(nodeConfigRepo, policyRepo);

    private final String VALID_MIN_XML = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:startEvent id="s1"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                <bpmn:endEvent id="e1"><bpmn:incoming>f1</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="f1" sourceRef="s1" targetRef="e1"/>
              </bpmn:process>
            </bpmn:definitions>""";

    @Test
    void validMinXml_ShouldPass() {
        var r = svc.validate("POL", "V1", VALID_MIN_XML);
        if (!r.valid()) {
            r.violations().forEach(v ->
                System.out.println("  " + v.code() + ": " + v.message()));
        }
        assertTrue(r.valid(), "XML mínimo válido debería pasar. Violations: " + r.violations());
    }

    @Test
    void emptyXml_ShouldFail() {
        var r = svc.validate("POL", "V1", null);
        assertFalse(r.valid());
        assertTrue(r.violations().stream().anyMatch(v -> v.code().equals("BPMN_EMPTY")));
    }

    @Test
    void malformedXml_ShouldFail() {
        var r = svc.validate("POL", "V1", "not xml <<<");
        assertFalse(r.valid());
        assertTrue(r.violations().stream().anyMatch(v -> v.code().equals("BPMN_PARSE_ERROR")));
    }

    @Test
    void missingStartEvent_ShouldFail() {
        var xml = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:endEvent id="e1"/>
              </bpmn:process>
            </bpmn:definitions>""";
        var r = svc.validate("POL", "V1", xml);
        assertTrue(r.violations().stream().anyMatch(v -> v.code().equals("BPMN_START_EVENT_COUNT")));
    }

    @Test
    void missingEndEvent_ShouldFail() {
        var xml = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:startEvent id="s1"/>
              </bpmn:process>
            </bpmn:definitions>""";
        var r = svc.validate("POL", "V1", xml);
        assertTrue(r.violations().stream().anyMatch(v -> v.code().equals("BPMN_END_EVENT_COUNT")));
    }

    @Test
    void unsupportedElement_ShouldFail() {
        var xml = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:startEvent id="s1"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                <bpmn:task id="bad1"><bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing></bpmn:task>
                <bpmn:endEvent id="e1"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="f1" sourceRef="s1" targetRef="bad1"/>
                <bpmn:sequenceFlow id="f2" sourceRef="bad1" targetRef="e1"/>
              </bpmn:process>
            </bpmn:definitions>""";
        var r = svc.validate("POL", "V1", xml);
        assertTrue(r.violations().stream().anyMatch(v -> v.code().equals("BPMN_UNSUPPORTED_ELEMENT")),
                "bpmn:task no soportado debería ser detectado. Violations: " + r.violations());
    }

    @Test
    void deadEndNode_ShouldFail() {
        var xml = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:startEvent id="s1"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                <bpmn:endEvent id="e1"/>
                <bpmn:userTask id="u1"><bpmn:incoming>f1</bpmn:incoming></bpmn:userTask>
                <bpmn:sequenceFlow id="f1" sourceRef="s1" targetRef="u1"/>
              </bpmn:process>
            </bpmn:definitions>""";
        when(nodeConfigRepo.findByPolicyVersionId("V1")).thenReturn(
                List.of(buildConfig("u1", "OFFICER_TASK", "D1")));
        var r = svc.validate("POL", "V1", xml);
        assertTrue(r.violations().stream().anyMatch(v -> v.code().equals("BPMN_DEAD_END")
                && "u1".equals(v.elementId())));
    }

    @Test
    void gatewayNoConditions_ShouldFail() {
        var xml = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:startEvent id="s1"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                <bpmn:exclusiveGateway id="gw1">
                  <bpmn:incoming>f1</bpmn:incoming>
                  <bpmn:outgoing>f2</bpmn:outgoing>
                  <bpmn:outgoing>f3</bpmn:outgoing>
                </bpmn:exclusiveGateway>
                <bpmn:endEvent id="e1"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                <bpmn:endEvent id="e2"><bpmn:incoming>f3</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="f1" sourceRef="s1" targetRef="gw1"/>
                <bpmn:sequenceFlow id="f2" sourceRef="gw1" targetRef="e1"/>
                <bpmn:sequenceFlow id="f3" sourceRef="gw1" targetRef="e2"/>
              </bpmn:process>
            </bpmn:definitions>""";
        var r = svc.validate("POL", "V1", xml);
        assertTrue(r.violations().stream().anyMatch(v -> v.code().equals("BPMN_GATEWAY_NO_CONDITIONS")));
    }

    @Test
    void userTaskWithoutConfig_ShouldFail() {
        var xml = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:startEvent id="s1"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                <bpmn:userTask id="u1"><bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing></bpmn:userTask>
                <bpmn:endEvent id="e1"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="f1" sourceRef="s1" targetRef="u1"/>
                <bpmn:sequenceFlow id="f2" sourceRef="u1" targetRef="e1"/>
              </bpmn:process>
            </bpmn:definitions>""";
        when(nodeConfigRepo.findByPolicyVersionId("V1")).thenReturn(List.of());
        var r = svc.validate("POL", "V1", xml);
        assertTrue(r.violations().stream().anyMatch(v -> v.code().equals("BPMN_MISSING_CONFIG")));
    }

    @Test
    void officerTaskWithoutDepartment_ShouldFail() {
        var xml = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:startEvent id="s1"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                <bpmn:userTask id="u1"><bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing></bpmn:userTask>
                <bpmn:endEvent id="e1"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="f1" sourceRef="s1" targetRef="u1"/>
                <bpmn:sequenceFlow id="f2" sourceRef="u1" targetRef="e1"/>
              </bpmn:process>
            </bpmn:definitions>""";
        var config = new NodeConfiguration();
        config.setBpmnElementId("u1");
        config.setTaskKind("OFFICER_TASK");
        // departmentId is null
        when(nodeConfigRepo.findByPolicyVersionId("V1")).thenReturn(List.of(config));
        var r = svc.validate("POL", "V1", xml);
        assertTrue(r.violations().stream().anyMatch(v -> v.code().equals("BPMN_CONFIG_NO_DEPARTMENT")));
    }

    @Test
    void validWithConfig_ShouldPass() {
        var xml = """
            <?xml version="1.0"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="P1" isExecutable="true">
                <bpmn:startEvent id="s1"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                <bpmn:userTask id="u1"><bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing></bpmn:userTask>
                <bpmn:endEvent id="e1"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="f1" sourceRef="s1" targetRef="u1"/>
                <bpmn:sequenceFlow id="f2" sourceRef="u1" targetRef="e1"/>
              </bpmn:process>
            </bpmn:definitions>""";
        when(nodeConfigRepo.findByPolicyVersionId("V1")).thenReturn(
                List.of(buildConfig("u1", "OFFICER_TASK", "DEP-1")));
        var r = svc.validate("POL", "V1", xml);
        assertTrue(r.valid(), "XML válido con config debería pasar. Violations: " + r.violations());
    }

    private NodeConfiguration buildConfig(String elementId, String taskKind, String deptId) {
        var c = new NodeConfiguration();
        c.setBpmnElementId(elementId);
        c.setTaskKind(taskKind);
        c.setDepartmentId(deptId);
        return c;
    }
}
