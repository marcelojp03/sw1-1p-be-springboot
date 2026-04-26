package sw1.p1.client.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndex(name = "doc_org_unique", def = "{'documentNumber': 1, 'organizationId': 1}", unique = true)
public class Client {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    private String fullName;

    /** CI, PASSPORT, NIT, etc. */
    private String documentType;

    private String documentNumber;

    private String phone;

    private String email;

    private String address;

    /** ID del usuario con rol CLIENT vinculado (puede ser null) */
    @Indexed
    private String userId;

    private String createdBy;

    private Instant createdAt;
    private Instant updatedAt;
}
