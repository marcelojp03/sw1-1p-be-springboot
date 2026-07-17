package sw1.p1.shared.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import sw1.p1.exception.BusinessException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StorageService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "pdf", "docx", "xlsx");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    /**
     * Sube un archivo a S3 con el storageKey dado y retorna el AttachmentRef para embeber.
     */
    public AttachmentRef upload(MultipartFile file, String storageKey) {
        validateFile(file);

        String contentType = file.getContentType() != null
                ? file.getContentType() : "application/octet-stream";
        String uploadedBy = SecurityContextHolder.getContext().getAuthentication().getName();

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new BusinessException("Error al leer el archivo: " + e.getMessage());
        }

        return AttachmentRef.builder()
                .fileName(file.getOriginalFilename())
                .storageKey(storageKey)
                .mimeType(contentType)
                .sizeBytes(file.getSize())
                .uploadedAt(Instant.now())
                .uploadedBy(uploadedBy)
                .build();
    }

    /**
     * Genera una URL pre-firmada temporal de descarga para un storageKey dado.
     */
    public String generatePresignedUrl(String storageKey, Duration expiry) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(r -> r.bucket(bucket).key(storageKey))
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Descarga el contenido de un objeto S3 como arreglo de bytes.
     * Usado por DocumentSigningService para enviar el archivo al servicio de firma.
     */
    public byte[] download(String storageKey) {
        return s3Client.getObjectAsBytes(r -> r.bucket(bucket).key(storageKey)).asByteArray();
    }

    /**
     * Sube bytes crudos a S3 (sin validación de extensión).
     * Usado para subir versiones firmadas o convertidas (SIGNED / EDITABLE).
     */
    public void uploadBytes(byte[] content, String storageKey, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
    }

    /**
     * Elimina un objeto del bucket S3 (usado en rollback).
     */
    public void delete(String storageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build();
        s3Client.deleteObject(request);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo está vacío");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("El archivo supera el tamaño máximo de 10 MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("El archivo no tiene nombre");
        }
        String ext = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(
                    "Extensión no permitida: " + ext + ". Permitidas: jpg, jpeg, png, pdf, docx, xlsx");
        }
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot >= filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
