package sw1.p1.policy.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Definición de un campo en un formulario dinámico */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormField {

    private String fieldId;

    /** TEXT, NUMBER, DATE, BOOLEAN, FILE, SELECT, TEXTAREA */
    private String type;

    private String label;

    private boolean required;

    /** Para tipo SELECT */
    @Builder.Default
    private List<String> options = new ArrayList<>();

    /** Para tipo NUMBER */
    private Double min;
    private Double max;

    /** Para tipo FILE */
    @Builder.Default
    private List<String> allowedExtensions = new ArrayList<>();
}
