package sw1.p1.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mensaje de colaboración en tiempo real para el diagramador.
 * Solo se transporta vía WebSocket STOMP — no persiste en MongoDB.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagramPatchMessage {

    private String policyId;

    private String senderUserId;

    /** Estructura JSON del diagrama (celdas / nodos / transiciones) */
    private Object cells;

    private Instant sentAt;
}
