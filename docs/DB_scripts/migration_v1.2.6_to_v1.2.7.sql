-- Обновление существующей базы с версии 1.2.6 до 1.2.7 без потери данных.
-- Добавляет таблицу жалоб на отзывы: один пользователь — одна жалоба на отзыв.
--
-- Запуск: mysql -u root -p < docs/DB_scripts/migration_v1.2.6_to_v1.2.7.sql

USE `myngdb`;

CREATE TABLE IF NOT EXISTS `review_report` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `fk_review` int NOT NULL,
  `fk_user` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_review_report` (`fk_review`,`fk_user`),
  KEY `fk_review_report_user` (`fk_user`),
  CONSTRAINT `fk_review_report_review` FOREIGN KEY (`fk_review`) REFERENCES `review` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_review_report_user` FOREIGN KEY (`fk_user`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
