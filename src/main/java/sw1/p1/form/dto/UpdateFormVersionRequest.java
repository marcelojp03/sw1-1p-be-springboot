package sw1.p1.form.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import sw1.p1.form.domain.FormFieldDefinition;

import java.util.List;

public record UpdateFormVersionRequest(
        @NotEmpty @Valid List<FormFieldDefinition> fields
) {}
