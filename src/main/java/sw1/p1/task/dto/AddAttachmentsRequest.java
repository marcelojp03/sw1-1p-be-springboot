package sw1.p1.task.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddAttachmentsRequest(
        @NotEmpty List<String> urls
) {}
