package ru.vlsu.myng.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vlsu.myng.entities.Review;
import ru.vlsu.myng.entities.ReviewReport;
import ru.vlsu.myng.entities.User;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Integer> {

    /**
     * Проверяет, жаловался ли уже пользователь на этот отзыв.
     */
    boolean existsByReviewAndUser(Review review, User user);
}
