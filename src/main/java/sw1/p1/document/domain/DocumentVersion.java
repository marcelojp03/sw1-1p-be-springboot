package sw1.p1.document.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Versión individual de un documento almacenado en S3.
 * Colección: document_versions
 */
@Document(collection = "document_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "doc_version", def = "{'documentId': 1, 'versionNumber': 1}")
})
public class DocumentVersion {

    @Id
    private String id;

    @Indexed
    private String documentId;

    private int versionNumber;

    /** ORIGINAL | EDITABLE | SIGNED */
    private VersionType versionType;

    /** Clave del objeto en S3: org/{orgId}/doc/{docId}/v{n}/{filename} */
    private String storageKey;

    private String fileName;

    private String mimeType;

    private long sizeBytes;

    private String uploadedBy;

    private Instant uploadedAt;

    /** Motivo del cambio o comentario de versión */
    private String changeReason;

    private DocumentStatus status;

    /** SHA-256 del contenido para verificar integridad */
    private String checksum;

    /**
     * Clave del documento ORIGINAL del que deriva esta versión editable.
     * Solo aplica cuando versionType == EDITABLE o SIGNED.
     */
    private String derivedFromStorageKey;
}
