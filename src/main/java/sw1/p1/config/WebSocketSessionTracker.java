package sw1.p1.config;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rastreador en memoria de sesiones WebSocket activas.
 * Escucha los eventos de conexión/desconexión STOMP y mantiene un contador atómico.
 */
@Component
public class WebSocketSessionTracker {

    private final AtomicInteger connectedCount = new AtomicInteger(0);

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        connectedCount.incrementAndGet();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        int current = connectedCount.decrementAndGet();
        if (current < 0) connectedCount.set(0);
    }

    public int getConnectedCount() {
        return connectedCount.get();
    }
}
