package sw1.p1.form.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import sw1.p1.form.domain.FormFieldDefinition;

import java.util.List;

public record CreateFormVersionRequest(
        @NotEmpty @Valid List<FormFieldDefinition> fields
) {}
