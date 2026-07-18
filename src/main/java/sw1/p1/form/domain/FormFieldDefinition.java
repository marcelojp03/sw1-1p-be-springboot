package sw1.p1.form.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormFieldDefinition {
    private String id;
    private String key;
    private FormFieldType type;
    private String label;
    private String description;
    private String placeholder;
    private boolean required;
    private boolean readOnly;
    private Integer order;

    private Object defaultValue;

    @Builder.Default
    private FieldValidation validation = new FieldValidation();

    @Builder.Default
    private List<FormOption> options = new ArrayList<>();

    @Builder.Default
    private List<GridColumnDefinition> columns = new ArrayList<>();
}
