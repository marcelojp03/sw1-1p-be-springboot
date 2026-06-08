package sw1.p1.document.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Comentario embebido dentro de Document */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentComment {

    private String id;
    private String userId;
    private String userDisplayName;
    private String text;
    private Instant createdAt;
}
