package sw1.p1.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import sw1.p1.document.domain.Document;
import sw1.p1.document.domain.DocumentPermission;
import sw1.p1.document.domain.DocumentRepository;
import sw1.p1.document.domain.DocumentScope;
import sw1.p1.document.domain.DocumentVersion;
import sw1.p1.document.domain.DocumentVersionRepository;
import sw1.p1.document.domain.VersionType;
import sw1.p1.document.dto.CreateDocumentRequest;
import sw1.p1.document.dto.DocumentResponse;
import sw1.p1.document.dto.DocumentVersionResponse;
import sw1.p1.shared.storage.StorageService;

class DocumentServiceTest {

    private final DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
    private final DocumentVersionRepository versionRepository = org.mockito.Mockito.mock(DocumentVersionRepository.class);
    private final StorageService storageService = org.mockito.Mockito.mock(StorageService.class);
    private final DocumentAuditService auditService = org.mockito.Mockito.mock(DocumentAuditService.class);
    private final SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                documentRepository,
                versionRepository,
                storageService,
                auditService,
                messagingTemplate);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", "n/a"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsDocumentUploadsVersionAndListsVersions() {
        AtomicReference<Document> savedDocument = new AtomicReference<>();
        AtomicReference<DocumentVersion> savedVersion = new AtomicReference<>();

        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            if (document.getId() == null) {
                document.setId("doc-1");
            }
            savedDocument.set(document);
            return document;
        });
        when(documentRepository.findById("doc-1")).thenAnswer(invocation -> Optional.of(savedDocument.get()));
        when(versionRepository.countByDocumentId("doc-1")).thenReturn(0);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            version.setId("version-1");
            savedVersion.set(version);
            return version;
        });
        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc("doc-1"))
                .thenAnswer(invocation -> List.of(savedVersion.get()));

        DocumentResponse created = documentService.createDocument(new CreateDocumentRequest(
                "org-1",
                DocumentScope.PROCEDURE,
                "procedure-1",
                "Contract",
                "Test document",
                List.of("ADMIN", "OFFICER"),
                List.of(DocumentPermission.DOCUMENT_VIEW, DocumentPermission.DOCUMENT_UPLOAD),
                List.of("test")));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "sample content".getBytes(StandardCharsets.UTF_8));

        DocumentVersionResponse uploaded = documentService.uploadVersion(
                created.id(),
                file,
                "Initial upload",
                VersionType.ORIGINAL);
        List<DocumentVersionResponse> versions = documentService.listVersions(created.id());

        assertThat(created.id()).isEqualTo("doc-1");
        assertThat(created.createdBy()).isEqualTo("admin@example.com");
        assertThat(uploaded.id()).isEqualTo("version-1");
        assertThat(uploaded.versionNumber()).isEqualTo(1);
        assertThat(uploaded.storageKey()).isEqualTo("org/org-1/doc/doc-1/v1/contract.pdf");
        assertThat(uploaded.checksum()).isNotBlank();
        assertThat(savedDocument.get().getCurrentVersionId()).isEqualTo("version-1");
        assertThat(versions).hasSize(1);
        assertThat(versions.getFirst().id()).isEqualTo("version-1");

        verify(storageService).upload(eq(file), eq("org/org-1/doc/doc-1/v1/contract.pdf"));
    }
}
