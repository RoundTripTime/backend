package roundtrip.notification.infrastructure.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.candidate.domain.entity.CandidateStatus;
import roundtrip.extract.domain.entity.JobStatus;
import roundtrip.notification.application.ReviewReminderTarget;
import roundtrip.notification.domain.entity.NotificationType;
import roundtrip.notification.domain.repository.ReviewReminderRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewReminderRepositoryImpl implements ReviewReminderRepository {

    private final EntityManager em;

    @Override
    public List<ReviewReminderTarget> findJobsNeedingReviewReminder(OffsetDateTime completedBefore) {
        String jpql = """
                SELECT new roundtrip.notification.application.ReviewReminderTarget(j.id, sl.userId)
                FROM ExtractionJob j, SourceLink sl
                WHERE sl.id = j.sourceLinkId
                  AND j.jobStatus = :doneStatus
                  AND j.completedAt <= :completedBefore
                  AND EXISTS (
                      SELECT 1 FROM PlaceCandidate c
                      WHERE c.jobId = j.id AND c.status = :proposedStatus
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM Notification n
                      WHERE n.jobId = j.id AND n.type = :reminderType
                  )
                """;

        TypedQuery<ReviewReminderTarget> query = em.createQuery(jpql, ReviewReminderTarget.class);
        query.setParameter("doneStatus", JobStatus.DONE);
        query.setParameter("completedBefore", completedBefore);
        query.setParameter("proposedStatus", CandidateStatus.PROPOSED);
        query.setParameter("reminderType", NotificationType.EXTRACTION_REVIEW_REMINDER.value());
        return query.getResultList();
    }
}
