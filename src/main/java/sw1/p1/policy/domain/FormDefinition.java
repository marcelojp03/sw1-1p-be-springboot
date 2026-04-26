package sw1.p1.policy.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Formulario dinámico embebido en un nodo */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormDefinition {

    private String formId;

    @Builder.Default
    private List<FormField> fields = new ArrayList<>();
}
