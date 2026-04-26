package sw1.p1.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.exception.NotFoundException;
import sw1.p1.notification.domain.Notification;
import sw1.p1.notification.domain.NotificationRepository;
import sw1.p1.notification.dto.CreateNotificationRequest;
import sw1.p1.notification.dto.NotificationResponse;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Crea una notificación y la envía en tiempo real por WebSocket al destinatario.
     * Usado internamente por el motor de workflow y desde el controlador (ADMIN/OFFICER).
     */
    public NotificationResponse create(CreateNotificationRequest request) {
        Notification notification = Notification.builder()
                .organizationId(request.organizationId())
                .recipientId(request.recipientId())
                .type(request.type())
                .title(request.title())
                .message(request.message())
                .procedureId(request.procedureId())
                .taskId(request.taskId())
                .read(false)
                .createdAt(Instant.now())
                .build();

        notification = notificationRepository.save(notification);

        // Push WebSocket al usuario específico (/user/{recipientId}/queue/notifications)
        NotificationResponse response = toResponse(notification);
        messagingTemplate.convertAndSendToUser(
                request.recipientId(), "/queue/notifications", response);

        return response;
    }

    /** Listar notificaciones del usuario autenticado */
    public Page<NotificationResponse> myNotifications(Boolean unreadOnly, Pageable pageable) {
        String userId = currentUserId();
        if (Boolean.TRUE.equals(unreadOnly)) {
            return notificationRepository
                    .findByRecipientIdAndReadOrderByCreatedAtDesc(userId, false, pageable)
                    .map(this::toResponse);
        }
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    /** Contar notificaciones no leídas del usuario autenticado */
    public long countUnread() {
        return notificationRepository.countByRecipientIdAndRead(currentUserId(), false);
    }

    /** Marcar una notificación como leída */
    public NotificationResponse markAsRead(String id) {
        Notification notification = getOrThrow(id);
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    /** Marcar todas las notificaciones del usuario autenticado como leídas */
    public void markAllAsRead() {
        String userId = currentUserId();
        notificationRepository
                .findByRecipientIdAndReadOrderByCreatedAtDesc(userId, false, Pageable.unpaged())
                .forEach(n -> {
                    n.setRead(true);
                    n.setReadAt(Instant.now());
                    notificationRepository.save(n);
                });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Notification getOrThrow(String id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notificación no encontrada: " + id));
    }

    private String currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .map(u -> u.getId())
                .orElse(null);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getOrganizationId(), n.getRecipientId(),
                n.getType(), n.getTitle(), n.getMessage(),
                n.getProcedureId(), n.getTaskId(),
                n.isRead(), n.getCreatedAt(), n.getReadAt()
        );
    }
}
