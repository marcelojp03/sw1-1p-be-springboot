package sw1.p1.ai.dto;

import java.util.Map;

public record GenerateDiagramResponse(
        Map<String, Object> diagram
) {}
