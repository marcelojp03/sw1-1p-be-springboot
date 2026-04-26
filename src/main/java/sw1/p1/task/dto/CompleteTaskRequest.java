package sw1.p1.task.dto;

import java.util.Map;

/**
 * Payload para que un OFFICER complete una tarea con la respuesta del formulario.
 */
public record CompleteTaskRequest(
        Map<String, Object> formResponse,
        String notes
) {}
