package sw1.p1.ai.dto;

import java.util.List;

public record SuggestFormFieldsRequest(
        String policyName,
        String nodeLabel,
        String nodeType,
        String areaName,
        List<String> existingFields,
        String language
) {}
