package sw1.p1.form.dto;

import java.util.List;

public record ValidationResult(
        boolean valid,
        List<String> errors
) {}
