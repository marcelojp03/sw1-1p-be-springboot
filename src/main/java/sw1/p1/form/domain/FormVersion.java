package sw1.p1.form.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "form_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "template_version", def = "{'formTemplateId': 1, 'versionNumber': 1}", unique = true)
})
public class FormVersion {

    @Id
    private String id;

    private String formTemplateId;
    private String organizationId;

    private int versionNumber;

    @Builder.Default
    private FormVersionStatus status = FormVersionStatus.DRAFT;

    @Builder.Default
    private List<FormFieldDefinition> fields = new ArrayList<>();

    private String createdBy;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    private Instant publishedAt;
}
