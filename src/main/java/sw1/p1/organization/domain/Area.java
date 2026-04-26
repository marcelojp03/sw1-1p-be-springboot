package sw1.p1.organization.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/** Área embebida dentro de Organization */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Area {

    private String id;
    private String name;
    private String description;
}
