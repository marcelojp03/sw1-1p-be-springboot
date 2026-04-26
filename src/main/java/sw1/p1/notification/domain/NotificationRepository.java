package sw1.p1.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadOrderByCreatedAtDesc(
            String userId, boolean read, Pageable pageable);

    long countByUserIdAndRead(String userId, boolean read);
}
