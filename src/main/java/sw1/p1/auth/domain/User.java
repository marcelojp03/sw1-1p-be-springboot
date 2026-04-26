package sw1.p1.auth.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;

@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String password;

    private String fullName;

    /** ADMIN | OFFICER | CLIENT */
    private List<Role> roles;

    /** Solo texto descriptivo, no condiciona asignación de tareas */
    private String positionName;

    private String phone;

    /** ID del área a la que pertenece (solo OFFICER) */
    private String areaId;

    /** ID de la organización (ADMIN y OFFICER) */
    private String organizationId;

    /** Vincula usuario CLIENT con su registro en la colección clients */
    private String clientId;

    private boolean enabled;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLogin;
}
