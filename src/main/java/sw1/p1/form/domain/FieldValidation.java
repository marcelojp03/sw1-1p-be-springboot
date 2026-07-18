package sw1.p1.form.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldValidation {
    private Integer minLength;
    private Integer maxLength;
    private BigDecimal min;
    private BigDecimal max;
    private String regex;
    private Integer minItems;
    private Integer maxItems;
    private String customMessage;
}
