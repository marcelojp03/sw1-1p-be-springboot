package sw1.p1.policy.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Swimlane (carril) del diagrama, agrupando nodos por área */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Swimlane {

    private String laneId;

    private String areaId;

    private String label;

    @Builder.Default
    private List<String> nodeIds = new ArrayList<>();
}
