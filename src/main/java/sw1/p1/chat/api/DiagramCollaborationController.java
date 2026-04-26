package sw1.p1.chat.api;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import sw1.p1.chat.dto.DiagramPatchMessage;

import java.time.Instant;

/**
 * Controlador WebSocket STOMP para la colaboración en tiempo real del diagramador.
 *
 * Flujo:
 *   Cliente Angular → /app/diagram/{policyId}  (envía DiagramPatchMessage)
 *   Spring redistribuye → /topic/diagram/{policyId}  (reciben todos los suscriptores)
 */
@Controller
@RequiredArgsConstructor
public class DiagramCollaborationController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/diagram/{policyId}")
    public void broadcastDiagramPatch(
            @DestinationVariable String policyId,
            DiagramPatchMessage patch
    ) {
        patch.setSentAt(Instant.now());
        messagingTemplate.convertAndSend("/topic/diagram/" + policyId, patch);
    }
}
