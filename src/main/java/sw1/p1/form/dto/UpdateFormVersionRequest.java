package sw1.p1.form.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import sw1.p1.form.domain.FormFieldDefinition;

import java.util.List;

public record UpdateFormVersionRequest(
        @NotNull @Valid List<FormFieldDefinition> fields
) {}
