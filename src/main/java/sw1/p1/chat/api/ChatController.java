package sw1.p1.chat.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sw1.p1.chat.application.ChatService;
import sw1.p1.chat.dto.ChatMessageResponse;
import sw1.p1.chat.dto.SendMessageRequest;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/{procedureId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<List<ChatMessageResponse>> list(@PathVariable String procedureId) {
        return ResponseEntity.ok(chatService.listByProcedure(procedureId));
    }

    @PostMapping("/{procedureId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<ChatMessageResponse> send(
            @PathVariable String procedureId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(chatService.sendMessage(procedureId, request, authentication.getName()));
    }
}
