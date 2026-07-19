package sw1.p1.policy.application;

import org.junit.jupiter.api.Test;
import sw1.p1.policy.domain.NodeConfiguration;
import sw1.p1.shared.NodeType;

import static org.junit.jupiter.api.Assertions.*;

class NodeExecutionProfileResolverTest {

    private final NodeExecutionProfileResolver resolver = new NodeExecutionProfileResolver();

    @Test
    void clientTaskWithFormResolvesAsClientTask() {
        var profile = resolver.resolve("userTask", config("CLIENT_TASK", null, "form-v2", 24));

        assertEquals(NodeType.CLIENT_TASK, profile.runtimeType());
        assertEquals("form-v2", profile.formVersionId());
        assertFalse(profile.departmentRequired());
    }

    @Test
    void clientTaskWithoutFormResolvesAsClientTask() {
        var profile = resolver.resolve("bpmn:userTask", config("CLIENT_TASK", null, null, null));

        assertEquals(NodeType.CLIENT_TASK, profile.runtimeType());
        assertNull(profile.formVersionId());
    }

    @Test
    void officerTaskWithFormResolvesAsManualForm() {
        var profile = resolver.resolve("userTask", config("OFFICER_TASK", "dep-1", "form-v3", 48));

        assertEquals(NodeType.MANUAL_FORM, profile.runtimeType());
        assertEquals("dep-1", profile.departmentId());
        assertEquals("form-v3", profile.formVersionId());
        assertEquals(48, profile.slaHours());
    }

    @Test
    void officerTaskWithoutFormResolvesAsManualAction() {
        var profile = resolver.resolve("userTask", config("OFFICER_TASK", "dep-1", null, null));

        assertEquals(NodeType.MANUAL_ACTION, profile.runtimeType());
        assertNull(profile.formVersionId());
    }

    @Test
    void serviceTaskWithFormIsRejected() {
        assertThrows(NodeExecutionProfileResolver.ResolutionException.class,
                () -> resolver.resolve("serviceTask", config("OFFICER_TASK", "dep-1", "form-v1", 1)));
    }

    @Test
    void unsupportedTaskKindIsRejected() {
        assertThrows(NodeExecutionProfileResolver.ResolutionException.class,
                () -> resolver.resolve("userTask", config("AUTOMATIC_TASK", null, null, 1)));
    }

    @Test
    void officerTaskWithoutDepartmentIsRejected() {
        assertThrows(NodeExecutionProfileResolver.ResolutionException.class,
                () -> resolver.resolve("userTask", config("OFFICER_TASK", null, null, 1)));
    }

    @Test
    void zeroSlaIsRejected() {
        assertThrows(NodeExecutionProfileResolver.ResolutionException.class,
                () -> resolver.resolve("userTask", config("CLIENT_TASK", null, null, 0)));
    }

    @Test
    void negativeSlaIsRejected() {
        assertThrows(NodeExecutionProfileResolver.ResolutionException.class,
                () -> resolver.resolve("userTask", config("CLIENT_TASK", null, null, -1)));
    }

    private NodeConfiguration config(String taskKind, String departmentId,
                                     String formVersionId, Integer slaHours) {
        return NodeConfiguration.builder()
                .bpmnElementId("u1")
                .taskKind(taskKind)
                .departmentId(departmentId)
                .formVersionId(formVersionId)
                .slaHours(slaHours)
                .build();
    }
}
