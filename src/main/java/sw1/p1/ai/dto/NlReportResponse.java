package sw1.p1.ai.dto;

import java.util.List;
import java.util.Map;

public record NlReportResponse(
        List<Map<String, Object>> results,
        String queryUsed
) {}
