package sw1.p1.ai.dto;

import java.util.List;

public record SuggestWorkflowResponse(
        List<NodeSuggestionDto> suggestions,
        List<TransitionSuggestionDto> suggestedTransitions
) {
    public record NodeSuggestionDto(
            String label,
            String type,
            String description,
            String suggestedArea,
            List<String> suggestedFields
    ) {}

    public record TransitionSuggestionDto(String from, String to, String condition) {}
}
