package sw1.p1.chat.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import sw1.p1.shared.storage.AttachmentRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "chat_messages")
@Data
@NoArgsConstructor
@CompoundIndex(name = "org_area", def = "{'organizationId': 1, 'receiverAreaId': 1}")
public class ChatMessage {

    @Id
    private String id;

    private String organizationId;

    @Indexed
    private String procedureId;

    private String senderUserId;

    private String senderName;

    /** Área destinataria del mensaje, puede ser null */
    private String receiverAreaId;

    private String message;

    private List<AttachmentRef> attachments = new ArrayList<>();

    private Instant createdAt;
}
