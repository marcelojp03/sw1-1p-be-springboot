package sw1.p1.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank
    private String message;

    /** Área destinataria, opcional */
    private String receiverAreaId;
}
