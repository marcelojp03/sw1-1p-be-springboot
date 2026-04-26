package sw1.p1.chat.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import sw1.p1.auth.domain.User;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.chat.domain.ChatMessage;
import sw1.p1.chat.domain.ChatMessageRepository;
import sw1.p1.chat.dto.ChatMessageResponse;
import sw1.p1.chat.dto.SendMessageRequest;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public List<ChatMessageResponse> listByProcedure(String procedureId) {
        return chatMessageRepository.findByProcedureIdOrderByCreatedAtAsc(procedureId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ChatMessageResponse sendMessage(String procedureId, SendMessageRequest req, String senderEmail) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        ChatMessage msg = new ChatMessage();
        msg.setOrganizationId(sender.getOrganizationId());
        msg.setProcedureId(procedureId);
        msg.setSenderUserId(sender.getId());
        msg.setSenderName(sender.getFullName());
        msg.setReceiverAreaId(req.getReceiverAreaId());
        msg.setMessage(req.getMessage());
        msg.setCreatedAt(Instant.now());

        ChatMessage saved = chatMessageRepository.save(msg);

        ChatMessageResponse response = toResponse(saved);
        messagingTemplate.convertAndSend("/topic/chat/" + procedureId, response);

        return response;
    }

    private ChatMessageResponse toResponse(ChatMessage m) {
        return ChatMessageResponse.builder()
                .id(m.getId())
                .procedureId(m.getProcedureId())
                .senderUserId(m.getSenderUserId())
                .senderName(m.getSenderName())
                .receiverAreaId(m.getReceiverAreaId())
                .message(m.getMessage())
                .attachments(m.getAttachments())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
