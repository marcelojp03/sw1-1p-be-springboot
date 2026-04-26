package sw1.p1.ai.dto;

import java.util.List;

public record SuggestFormFieldsResponse(
        List<FieldSuggestionDto> suggestions
) {
    public record FieldSuggestionDto(
            String fieldId,
            String label,
            String type,
            boolean required,
            String description,
            List<String> options
    ) {}
}
