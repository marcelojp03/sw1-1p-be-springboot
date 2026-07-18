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
public class GridColumnDefinition {
    private String id;
    private String key;
    private String label;
    private GridColumnType type;
    private boolean required;

    @Builder.Default
    private List<FormOption> options = new ArrayList<>();
}
