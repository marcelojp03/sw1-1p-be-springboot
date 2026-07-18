package sw1.p1.form.dto;

import jakarta.validation.constraints.NotNull;
import sw1.p1.form.domain.FormFieldDefinition;

import java.util.List;

public record CreateFormVersionRequest(
        @NotNull List<FormFieldDefinition> fields
) {}
