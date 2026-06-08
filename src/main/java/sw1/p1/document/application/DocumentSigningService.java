package sw1.p1.document.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import sw1.p1.document.domain.*;
import sw1.p1.document.dto.DocumentVersionResponse;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.shared.storage.StorageService;

import java.time.Instant;

/**
 * Envía un documento al servicio externo de firma y registra la versión SIGNED.
 * No implica validez jurídica completa — es trazabilidad académica del sistema.
 * El servicio externo recibe el binario vía POST multipart y devuelve el firmado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSigningService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final StorageService storageService;
    private final DocumentAuditService auditService;
    private final RestTemplate restTemplate;

    @Value("${signing.service-url:http://localhost:9090}")
    private String signingServiceUrl;

    /**
     * Flujo completo de firma:
     * 1. Descarga el binario desde S3
     * 2. Envía al servicio externo (POST multipart, sin auth)
     * 3. Sube el resultado a S3 como nueva versión SIGNED
     * 4. Registra auditoría en DynamoDB
     */
    public DocumentVersionResponse sign(String documentId, String versionId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado: " + documentId));

        DocumentVersion sourceVersion = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Versión no encontrada: " + versionId));

        if (!sourceVersion.getDocumentId().equals(documentId)) {
            throw new BusinessException("La versión no pertenece al documento indicado");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        // 1. Descargar binario desde S3
        byte[] originalBytes = storageService.download(sourceVersion.getStorageKey());

        // 2. Enviar al servicio externo de firma
        byte[] signedBytes = sendToSigningService(originalBytes, sourceVersion.getFileName(),
                sourceVersion.getMimeType());

        // 3. Subir resultado firmado a S3
        int nextVersion = versionRepository.countByDocumentId(documentId) + 1;
        String signedStorageKey = buildStorageKey(doc.getOrganizationId(), documentId,
                nextVersion, "signed_" + sourceVersion.getFileName());

        storageService.uploadBytes(signedBytes, signedStorageKey, sourceVersion.getMimeType());

        DocumentVersion signedVersion = DocumentVersion.builder()
                .documentId(documentId)
                .versionNumber(nextVersion)
                .versionType(VersionType.SIGNED)
                .storageKey(signedStorageKey)
                .fileName("signed_" + sourceVersion.getFileName())
                .mimeType(sourceVersion.getMimeType())
                .sizeBytes(signedBytes.length)
                .uploadedBy(username)
                .uploadedAt(Instant.now())
                .changeReason("Firma electrónica asistida por servicio externo")
                .status(DocumentStatus.ACTIVE)
                .derivedFromStorageKey(sourceVersion.getStorageKey())
                .build();

        signedVersion = versionRepository.save(signedVersion);

        doc.setCurrentVersionId(signedVersion.getId());
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        // 4. Auditoría en DynamoDB
        auditService.log(documentId, signedVersion.getId(), AuditAction.SIGNED, username,
                "Firmado desde versión v" + sourceVersion.getVersionNumber());

        return new DocumentVersionResponse(
                signedVersion.getId(), signedVersion.getDocumentId(),
                signedVersion.getVersionNumber(), signedVersion.getVersionType(),
                signedVersion.getStorageKey(), signedVersion.getFileName(),
                signedVersion.getMimeType(), signedVersion.getSizeBytes(),
                signedVersion.getUploadedBy(), signedVersion.getUploadedAt(),
                signedVersion.getChangeReason(), signedVersion.getStatus(),
                null, signedVersion.getDerivedFromStorageKey(), null
        );
    }

    private byte[] sendToSigningService(byte[] fileBytes, String fileName, String mimeType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // Wrap bytes as a named resource for multipart
            org.springframework.core.io.ByteArrayResource resource =
                    new org.springframework.core.io.ByteArrayResource(fileBytes) {
                        @Override
                        public String getFilename() { return fileName; }
                    };
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<byte[]> response = restTemplate.postForEntity(
                    signingServiceUrl + "/sign", requestEntity, byte[].class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException("El servicio de firma retornó error: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al contactar el servicio de firma: {}", e.getMessage());
            throw new BusinessException("Error al contactar el servicio de firma: " + e.getMessage());
        }
    }

    private String buildStorageKey(String orgId, String docId, int version, String filename) {
        return String.format("org/%s/doc/%s/v%d/%s", orgId, docId, version, filename);
    }
}
