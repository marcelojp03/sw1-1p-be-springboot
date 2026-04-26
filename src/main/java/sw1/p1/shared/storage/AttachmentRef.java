package sw1.p1.shared.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentRef {

    private String fileName;
    private String storageKey;
    private String mimeType;
    private long sizeBytes;
    private Instant uploadedAt;
    private String uploadedBy;
}
