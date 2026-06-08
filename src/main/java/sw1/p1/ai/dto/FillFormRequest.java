package sw1.p1.ai.dto;

import java.util.List;
import java.util.Map;

public record FillFormRequest(
        String text,
        List<Map<String, Object>> formDefinition
) {}
