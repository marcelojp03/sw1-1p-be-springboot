package sw1.p1.chat.dto;

import lombok.Builder;
import lombok.Data;
import sw1.p1.shared.storage.AttachmentRef;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ChatMessageResponse {

    private String id;
    private String procedureId;
    private String senderUserId;
    private String senderName;
    private String receiverAreaId;
    private String message;
    private List<AttachmentRef> attachments;
    private Instant createdAt;
}
