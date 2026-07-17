package sw1.p1.document.application;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sw1.p1.document.domain.*;
import sw1.p1.document.dto.*;
import sw1.p1.exception.BusinessException;
import sw1.p1.exception.NotFoundException;
import sw1.p1.shared.storage.StorageService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final StorageService storageService;
    private final DocumentAuditService auditService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    @Value("${onlyoffice.url:http://localhost:8083}")
    private String onlyofficeUrl;

    @Value("${onlyoffice.jwt-secret:onlyoffice-secret-key}")
    private String onlyofficeJwtSecret;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${onlyoffice.callback-base-url:${app.base-url:http://localhost:8080}}")
    private String onlyofficeCallbackBaseUrl;

    // ── CRUD básico ────────────────────────────────────────────────────────────

    public DocumentResponse createDocument(CreateDocumentRequest request) {
        String username = currentUsername();
        Document doc = Document.builder()
                .organizationId(request.organizationId())
                .scope(request.scope())
                .scopeReferenceId(request.scopeReferenceId())
                .title(request.title())
                .description(request.description())
                .status(DocumentStatus.ACTIVE)
                .allowedRoles(request.allowedRoles() != null ? request.allowedRoles() : new ArrayList<>())
                .permissions(request.permissions() != null ? request.permissions() : new ArrayList<>())
                .tags(request.tags() != null ? request.tags() : new ArrayList<>())
                .createdBy(username)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        doc = documentRepository.save(doc);
        return toResponse(doc);
    }

    public DocumentResponse getDocument(String documentId) {
        return toResponse(findOrThrow(documentId));
    }

    public List<DocumentResponse> listByScope(String scopeReferenceId) {
        return documentRepository.findByScopeReferenceId(scopeReferenceId)
                .stream().map(this::toResponse).toList();
    }

    public List<DocumentResponse> listByOrganization(String organizationId) {
        return documentRepository.findByOrganizationId(organizationId, Pageable.unpaged())
                .stream().map(this::toResponse).toList();
    }

    public DocumentResponse updateStatus(String documentId, DocumentStatus newStatus) {
        Document doc = findOrThrow(documentId);
        doc.setStatus(newStatus);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
        auditService.log(documentId, doc.getCurrentVersionId(),
                newStatus == DocumentStatus.APPROVED ? AuditAction.APPROVED : AuditAction.OBSERVED,
                currentUsername(), "Status changed to " + newStatus);
        return toResponse(doc);
    }

    public DocumentResponse addComment(String documentId, String text) {
        String username = currentUsername();
        Document doc = findOrThrow(documentId);
        DocumentComment comment = DocumentComment.builder()
                .id(UUID.randomUUID().toString())
                .userId(username)
                .userDisplayName(username)
                .text(text)
                .createdAt(Instant.now())
                .build();
        doc.getComments().add(comment);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
        auditService.log(documentId, null, AuditAction.COMMENTED, username, text);
        broadcastActivity(documentId, username, "COMMENTED");
        return toResponse(doc);
    }

    // ── Versiones / S3 ────────────────────────────────────────────────────────

    public DocumentVersionResponse uploadVersion(String documentId, MultipartFile file,
                                                 String changeReason, VersionType versionType) {
        Document doc = findOrThrow(documentId);
        String username = currentUsername();

        int nextVersion = versionRepository.countByDocumentId(documentId) + 1;
        String storageKey = buildStorageKey(doc.getOrganizationId(), documentId, nextVersion, file.getOriginalFilename());

        storageService.upload(file, storageKey);

        String checksum = computeChecksumQuiet(file);

        DocumentVersion version = DocumentVersion.builder()
                .documentId(documentId)
                .versionNumber(nextVersion)
                .versionType(versionType != null ? versionType : VersionType.ORIGINAL)
                .storageKey(storageKey)
                .fileName(file.getOriginalFilename())
                .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .sizeBytes(file.getSize())
                .uploadedBy(username)
                .uploadedAt(Instant.now())
                .changeReason(changeReason)
                .status(DocumentStatus.ACTIVE)
                .checksum(checksum)
                .build();

        version = versionRepository.save(version);

        doc.setCurrentVersionId(version.getId());
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        auditService.log(documentId, version.getId(), AuditAction.UPLOADED, username,
                "v" + nextVersion + " " + file.getOriginalFilename());
        broadcastActivity(documentId, username, "DOCUMENT_UPLOADED");

        return toVersionResponse(version, null);
    }

    public List<DocumentVersionResponse> listVersions(String documentId) {
        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId)
                .stream()
                .map(v -> toVersionResponse(v, null))
                .toList();
    }

    public ResponseEntity<Resource> downloadFile(String documentId, String versionId) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Versión no encontrada: " + versionId));
        if (!version.getDocumentId().equals(documentId)) {
            throw new BusinessException("La versión no pertenece al documento indicado");
        }
        byte[] content = storageService.download(version.getStorageKey());
        auditService.log(documentId, versionId, AuditAction.DOWNLOADED, currentUsername(), null);

        ByteArrayResource resource = new ByteArrayResource(content);
        String encodedFilename = URLEncoder.encode(version.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(version.getMimeType() != null ? version.getMimeType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }

    // ── OnlyOffice ─────────────────────────────────────────────────────────────

    /**
     * Genera el token de configuración para el OnlyOffice Document Server.
     * Angular embeds el editor usando este token.
     */
    public Map<String, Object> generateOnlyOfficeToken(String documentId, String versionId) {
        Document doc = findOrThrow(documentId);
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Versión no encontrada: " + versionId));

        String username = currentUsername();
        // Usar URL local para que OnlyOffice (Docker) pueda descargar via host.docker.internal
        String callbackUrl = onlyofficeCallbackBaseUrl + "/api/documents/" + documentId + "/onlyoffice/callback";
        String downloadUrl = onlyofficeCallbackBaseUrl + "/api/documents/" + documentId + "/versions/" + versionId + "/download";

        // Clave única de sesión de edición (OnlyOffice requiere cambiarla en cada nueva sesión)
        String editKey = documentId + "_v" + version.getVersionNumber() + "_" + System.currentTimeMillis();

        Map<String, Object> documentConfig = new LinkedHashMap<>();
        documentConfig.put("fileType", getExtension(version.getFileName()));
        documentConfig.put("key", editKey);
        documentConfig.put("title", version.getFileName());
        documentConfig.put("url", downloadUrl);

        String fileType = getExtension(version.getFileName());
        String documentType = resolveDocumentType(fileType);

        Map<String, Object> userConfig = Map.of("id", username, "name", username);
        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("user", userConfig);
        editorConfig.put("customization", Map.of("forcesave", true));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document", documentConfig);
        payload.put("documentType", documentType);
        payload.put("editorConfig", editorConfig);

        SecretKey secretKey = Keys.hmacShaKeyFor(onlyofficeJwtSecret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .claims(payload)
                .signWith(secretKey)
                .compact();

        auditService.log(documentId, versionId, AuditAction.EDIT_STARTED, username, null);
        broadcastActivity(documentId, username, "EDIT_STARTED");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("onlyofficeUrl", onlyofficeUrl);
        result.put("documentId", documentId);
        result.put("versionId", versionId);
        result.put("title", doc.getTitle());
        // Full config fields — Angular uses these to construct the IConfig object
        result.put("document", documentConfig);
        result.put("documentType", documentType);
        result.put("editorConfig", editorConfig);
        return result;
    }

    /**
     * Callback que OnlyOffice llama cuando el usuario guarda el documento.
     * status=2 -> documento listo al cerrar la sesion.
     * status=6 -> force-save, por ejemplo al presionar Guardar.
     */
    public void onlyOfficeCallback(String documentId, Map<String, Object> payload) {
        Object rawStatus = payload.get("status");
        if (!(rawStatus instanceof Number)) return;

        int status = ((Number) rawStatus).intValue();
        if (status == 7) {
            log.warn("OnlyOffice force-save fallo para documento {}: {}", documentId, payload);
            return;
        }
        if (status != 2 && status != 6) return;

        String downloadUrlFromOO = (String) payload.get("url");
        if (downloadUrlFromOO == null || downloadUrlFromOO.isBlank()) return;

        Document doc = findOrThrow(documentId);
        String username = "onlyoffice-callback";
        DocumentVersion sourceVersion = findCurrentVersion(doc).orElse(null);

        // Descargar el contenido editado desde la URL temporal que provee OnlyOffice
        byte[] content = downloadBytesFromUrl(downloadUrlFromOO);
        String checksum = computeChecksum(content);

        if (sourceVersion != null && checksum != null && checksum.equals(sourceVersion.getChecksum())) {
            log.info("OnlyOffice callback sin cambios nuevos para documento {}", documentId);
            return;
        }

        int nextVersion = versionRepository.countByDocumentId(documentId) + 1;
        String fileName = sourceVersion != null && sourceVersion.getFileName() != null
                ? sourceVersion.getFileName()
                : "document.docx";
        String mimeType = sourceVersion != null && sourceVersion.getMimeType() != null
                ? sourceVersion.getMimeType()
                : resolveMimeType(getExtension(fileName));
        String storageKey = buildStorageKey(doc.getOrganizationId(), documentId, nextVersion, fileName);
        storageService.uploadBytes(content, storageKey, mimeType);

        DocumentVersion newVersion = DocumentVersion.builder()
                .documentId(documentId)
                .versionNumber(nextVersion)
                .versionType(VersionType.EDITABLE)
                .storageKey(storageKey)
                .fileName(fileName)
                .mimeType(mimeType)
                .sizeBytes(content.length)
                .uploadedBy(username)
                .uploadedAt(Instant.now())
                .changeReason(buildOnlyOfficeChangeReason(status, payload.get("forcesavetype")))
                .status(DocumentStatus.ACTIVE)
                .checksum(checksum)
                .build();

        newVersion = versionRepository.save(newVersion);

        doc.setCurrentVersionId(newVersion.getId());
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        auditService.log(documentId, newVersion.getId(), AuditAction.EDIT_SAVED, username,
                "OnlyOffice callback v" + nextVersion);
        broadcastActivity(documentId, username, "DOCUMENT_VERSIONED");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Document findOrThrow(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado: " + id));
    }

    private Optional<DocumentVersion> findCurrentVersion(Document doc) {
        String currentVersionId = doc.getCurrentVersionId();
        if (currentVersionId == null || currentVersionId.isBlank()) return Optional.empty();
        return versionRepository.findById(currentVersionId);
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private String buildStorageKey(String orgId, String docId, int version, String filename) {
        return String.format("org/%s/doc/%s/v%d/%s", orgId, docId, version,
                filename != null ? filename : "file");
    }

    private String getExtension(String filename) {
        if (filename == null) return "docx";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "docx";
    }

    private String resolveDocumentType(String fileType) {
        return switch (fileType) {
            case "pdf", "doc", "docx", "odt", "rtf", "txt" -> "word";
            case "xls", "xlsx", "ods", "csv" -> "cell";
            case "ppt", "pptx", "odp" -> "slide";
            default -> "word";
        };
    }

    private String resolveMimeType(String fileType) {
        return switch (fileType) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private String computeChecksumQuiet(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            log.warn("No se pudo calcular checksum: {}", e.getMessage());
            return null;
        }
    }

    private String computeChecksum(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            log.warn("No se pudo calcular checksum de callback OnlyOffice: {}", e.getMessage());
            return null;
        }
    }

    private String buildOnlyOfficeChangeReason(int status, Object forceSaveType) {
        if (status != 6) return "OnlyOffice autosave";
        return forceSaveType == null
                ? "OnlyOffice force-save"
                : "OnlyOffice force-save type " + forceSaveType;
    }

    private byte[] downloadBytesFromUrl(String url) {
        try (var in = new java.net.URI(url).toURL().openStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new BusinessException("Error descargando documento desde OnlyOffice: " + e.getMessage());
        }
    }

    private void broadcastActivity(String documentId, String actor, String eventType) {
        Map<String, Object> event = Map.of(
                "documentId", documentId,
                "actor", actor,
                "eventType", eventType,
                "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/activity", (Object) event);
    }

    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(
                doc.getId(), doc.getOrganizationId(), doc.getScope(),
                doc.getScopeReferenceId(), doc.getTitle(), doc.getDescription(),
                doc.getStatus(), doc.getCurrentVersionId(), doc.getAllowedRoles(),
                doc.getPermissions(), doc.getComments(), doc.getTags(),
                doc.getCreatedBy(), doc.getCreatedAt(), doc.getUpdatedAt()
        );
    }

    private DocumentVersionResponse toVersionResponse(DocumentVersion v, String downloadUrl) {
        return new DocumentVersionResponse(
                v.getId(), v.getDocumentId(), v.getVersionNumber(), v.getVersionType(),
                v.getStorageKey(), v.getFileName(), v.getMimeType(), v.getSizeBytes(),
                v.getUploadedBy(), v.getUploadedAt(), v.getChangeReason(), v.getStatus(),
                v.getChecksum(), v.getDerivedFromStorageKey(), downloadUrl
        );
    }
}
