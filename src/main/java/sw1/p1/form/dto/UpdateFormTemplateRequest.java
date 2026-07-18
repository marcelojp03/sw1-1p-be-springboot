package sw1.p1.form.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFormTemplateRequest(
        @NotBlank @Size(min = 2, max = 100) String name,
        String description,
        boolean active
) {}
