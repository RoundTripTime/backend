package roundtrip.market.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "credit_type", nullable = false, length = 30)
    private String creditType;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static CreditHistory create(UUID userId, String creditType, int amount,
                                        int balanceAfter, String description) {
        var ch = new CreditHistory();
        ch.userId = userId;
        ch.creditType = creditType;
        ch.amount = amount;
        ch.balanceAfter = balanceAfter;
        ch.description = description;
        ch.createdAt = OffsetDateTime.now();
        return ch;
    }
}
