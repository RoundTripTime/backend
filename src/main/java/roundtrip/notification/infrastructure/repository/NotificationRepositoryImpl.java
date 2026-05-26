package roundtrip.notification.infrastructure.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.notification.domain.entity.Notification;
import roundtrip.notification.domain.repository.NotificationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpa;
    private final EntityManager em;

    @Override
    public Notification save(Notification notification) {
        return jpa.save(notification);
    }

    @Override
    public Optional<Notification> findByIdAndUserId(UUID id, UUID userId) {
        return jpa.findByIdAndUserId(id, userId);
    }

    @Override
    public List<Notification> findByUserIdBefore(UUID userId, Boolean isRead, UUID cursorId, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT n FROM Notification n WHERE n.userId = :userId");

        if (isRead != null) {
            jpql.append(" AND n.isRead = :isRead");
        }
        if (cursorId != null) {
            jpql.append(" AND n.createdAt < (SELECT nn.createdAt FROM Notification nn WHERE nn.id = :cursorId)");
        }
        jpql.append(" ORDER BY n.createdAt DESC");

        TypedQuery<Notification> query = em.createQuery(jpql.toString(), Notification.class);
        query.setParameter("userId", userId);
        if (isRead != null) {
            query.setParameter("isRead", isRead);
        }
        if (cursorId != null) {
            query.setParameter("cursorId", cursorId);
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }
}
