package ru.vlsu.myng.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Жалоба пользователя на отзыв.
 *
 * <p>
 * Хранится отдельной записью, чтобы один пользователь мог пожаловаться
 * на отзыв только один раз (уникальная пара «отзыв — пользователь»).
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "review_report", uniqueConstraints = @UniqueConstraint(columnNames = { "fk_review", "fk_user" }))
public class ReviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_review", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Review review;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;
}
