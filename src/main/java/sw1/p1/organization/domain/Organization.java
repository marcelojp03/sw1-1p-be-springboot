package sw1.p1.organization.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "organizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String description;

    /** Áreas embebidas */
    @Builder.Default
    private List<Area> areas = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;
}
