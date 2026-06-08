package sw1.p1.document.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio documental central.
 * Almacena metadatos; el binario vive en S3 (referenciado en DocumentVersion).
 * Colección: documents
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "org_scope", def = "{'organizationId': 1, 'scope': 1, 'scopeReferenceId': 1}")
})
public class Document {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    /** Contexto al que pertenece: POLICY, PROCEDURE, NODE, FORM, TASK */
    private DocumentScope scope;

    /** ID de la política, procedimiento, nodo, etc. al que está vinculado */
    @Indexed
    private String scopeReferenceId;

    private String title;

    private String description;

    /** Estado del documento: ACTIVE, REPLACED, OBSERVED, APPROVED, ARCHIVED, DELETED */
    @Builder.Default
    private DocumentStatus status = DocumentStatus.ACTIVE;

    /** ID de la versión más reciente (referencia a document_versions) */
    private String currentVersionId;

    /**
     * Roles con acceso al documento.
     * Ej: ["ADMIN", "OFFICER"] — vacío = acceso público dentro de la org.
     */
    @Builder.Default
    private List<String> allowedRoles = new ArrayList<>();

    /** Permisos granulares sobre este documento */
    @Builder.Default
    private List<DocumentPermission> permissions = new ArrayList<>();

    /** Comentarios embebidos */
    @Builder.Default
    private List<DocumentComment> comments = new ArrayList<>();

    /** Etiquetas para búsqueda y filtrado */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private String createdBy;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
