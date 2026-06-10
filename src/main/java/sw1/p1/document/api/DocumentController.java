package sw1.p1.document.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sw1.p1.document.application.DocumentAuditService;
import sw1.p1.document.application.DocumentService;
import sw1.p1.document.application.DocumentSigningService;
import sw1.p1.document.domain.DocumentStatus;
import sw1.p1.document.domain.VersionType;
import sw1.p1.document.dto.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentSigningService signingService;
    private final DocumentAuditService auditService;

    // ── Documento ──────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<DocumentResponse> createDocument(@RequestBody CreateDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.createDocument(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable String id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    /** Listar documentos vinculados a una política, procedimiento, nodo, etc. */
    @GetMapping("/scope/{scopeReferenceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<List<DocumentResponse>> listByScope(@PathVariable String scopeReferenceId) {
        return ResponseEntity.ok(documentService.listByScope(scopeReferenceId));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> updateStatus(
            @PathVariable String id,
            @RequestParam DocumentStatus status) {
        return ResponseEntity.ok(documentService.updateStatus(id, status));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<DocumentResponse> addComment(
            @PathVariable String id,
            @RequestBody AddCommentRequest request) {
        return ResponseEntity.ok(documentService.addComment(id, request.text()));
    }

    // ── Versiones / S3 ────────────────────────────────────────────────────────

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<DocumentVersionResponse> uploadVersion(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String changeReason,
            @RequestParam(required = false, defaultValue = "ORIGINAL") VersionType versionType) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadVersion(id, file, changeReason, versionType));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<List<DocumentVersionResponse>> listVersions(@PathVariable String id) {
        return ResponseEntity.ok(documentService.listVersions(id));
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable String id,
            @PathVariable String versionId) {
        String url = documentService.getDownloadUrl(id, versionId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    // ── OnlyOffice ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}/versions/{versionId}/onlyoffice-token")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<Map<String, Object>> getOnlyOfficeToken(
            @PathVariable String id,
            @PathVariable String versionId) {
        return ResponseEntity.ok(documentService.generateOnlyOfficeToken(id, versionId));
    }

    /** Callback que OnlyOffice llama cuando el usuario guarda el documento */
    @PostMapping("/{id}/onlyoffice/callback")
    public ResponseEntity<Map<String, Integer>> onlyOfficeCallback(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload) {
        documentService.onlyOfficeCallback(id, payload);
        return ResponseEntity.ok(Map.of("error", 0));
    }

    // ── Auditoría documental ───────────────────────────────────────────────────

    @GetMapping("/{id}/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(@PathVariable String id) {
        return ResponseEntity.ok(auditService.queryByDocumentId(id));
    }

    // ── Firma electrónica asistida ─────────────────────────────────────────────

    @PostMapping("/{id}/versions/{versionId}/sign")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<DocumentVersionResponse> sign(
            @PathVariable String id,
            @PathVariable String versionId) {
        return ResponseEntity.ok(signingService.sign(id, versionId));
    }
}
