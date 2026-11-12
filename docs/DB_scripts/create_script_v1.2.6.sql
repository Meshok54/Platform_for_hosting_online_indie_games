-- MySQL dump 10.13  Distrib 9.2.0, for Win64 (x86_64)
--
-- Host: localhost    Database: myngdb
-- ------------------------------------------------------
-- Server version	9.2.0

CREATE DATABASE IF NOT EXISTS `myngdb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `myngdb`;

-- Таблицы идут по алфавиту, а не в порядке зависимостей,
-- поэтому на время создания проверка внешних ключей отключена
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `ban`;
CREATE TABLE `ban` (
  `id` int NOT NULL AUTO_INCREMENT,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_time` timestamp NOT NULL,
  `end_time` timestamp NOT NULL,
  `fk_mod` int NOT NULL,
  `fk_user` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ban_mod` (`fk_mod`),
  KEY `fk_ban_user` (`fk_user`),
  CONSTRAINT `fk_ban_mod` FOREIGN KEY (`fk_mod`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ban_user` FOREIGN KEY (`fk_user`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_ban_time` CHECK ((`end_time` > `start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `collection`;
CREATE TABLE `collection` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `fk_user` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`,`fk_user`),
  KEY `fk_collection_user` (`fk_user`),
  CONSTRAINT `fk_collection_user` FOREIGN KEY (`fk_user`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `dev_application`;
CREATE TABLE `dev_application` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `github_username` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `text` text COLLATE utf8mb4_unicode_ci,
  `fk_user` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_dev_app_user` (`fk_user`),
  CONSTRAINT `fk_dev_app_user` FOREIGN KEY (`fk_user`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `game`;
CREATE TABLE `game` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `descr` text COLLATE utf8mb4_unicode_ci,
  `repo` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `genre` enum('action','adventure','rpg','simulation','strategy','sports','puzzle','horror','platformer','sandbox','visual_novel','roguelike') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fk_dev` int DEFAULT NULL,
  `image` mediumblob,
  `average_rating` double NOT NULL DEFAULT '0',
  `rating_sum` int NOT NULL DEFAULT '0',
  `review_count` int NOT NULL DEFAULT '0',
  `total_views` int NOT NULL DEFAULT '0',
  `total_launches` int NOT NULL DEFAULT '0',
  `first_release_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_game_dev` (`fk_dev`),
  CONSTRAINT `fk_game_dev` FOREIGN KEY (`fk_dev`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `game_collection`;
CREATE TABLE `game_collection` (
  `fk_game` int NOT NULL,
  `fk_collection` int NOT NULL,
  PRIMARY KEY (`fk_game`,`fk_collection`),
  KEY `fk_game_collection_collection` (`fk_collection`),
  CONSTRAINT `fk_game_collection_collection` FOREIGN KEY (`fk_collection`) REFERENCES `collection` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_game_collection_game` FOREIGN KEY (`fk_game`) REFERENCES `game` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `game_stats`;
CREATE TABLE `game_stats` (
  `id` int NOT NULL AUTO_INCREMENT,
  `fk_game` int NOT NULL,
  `event_type` enum('view','launch') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Тип события: просмотр или запуск',
  `event_date` date NOT NULL COMMENT 'Дата события',
  `count` int NOT NULL DEFAULT '1' COMMENT 'Количество событий за день',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_daily_stat` (`fk_game`,`event_type`,`event_date`),
  KEY `idx_game_stats_game` (`fk_game`),
  KEY `idx_game_stats_date` (`event_date`),
  CONSTRAINT `fk_game_stats_game` FOREIGN KEY (`fk_game`) REFERENCES `game` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `game_tag`;
CREATE TABLE `game_tag` (
  `fk_game` int NOT NULL,
  `fk_tag` int NOT NULL,
  PRIMARY KEY (`fk_game`,`fk_tag`),
  KEY `fk_game_tag_tag` (`fk_tag`),
  CONSTRAINT `fk_game_tag_game` FOREIGN KEY (`fk_game`) REFERENCES `game` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_game_tag_tag` FOREIGN KEY (`fk_tag`) REFERENCES `tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `game_version`;
CREATE TABLE `game_version` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `commit_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `changelog` text COLLATE utf8mb4_unicode_ci,
  `fk_game` int NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `files` text COLLATE utf8mb4_unicode_ci,
  `entry_point` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `fk_game` (`fk_game`,`commit_hash`),
  KEY `idx_game_version_game_created` (`created_at`),
  CONSTRAINT `fk_game_version_game` FOREIGN KEY (`fk_game`) REFERENCES `game` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `moderation_verdict`;
CREATE TABLE `moderation_verdict` (
  `id` int NOT NULL AUTO_INCREMENT,
  `approved` tinyint(1) DEFAULT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fk_game_version` int DEFAULT NULL,
  `fk_dev_application` int DEFAULT NULL,
  `fk_review` int DEFAULT NULL,
  `fk_mod` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_moderation_game_version` (`fk_game_version`),
  UNIQUE KEY `uq_moderation_review` (`fk_review`),
  KEY `fk_moderation_dev_app` (`fk_dev_application`),
  KEY `fk_moderation_verdict_mod` (`fk_mod`),
  CONSTRAINT `fk_moderation_dev_app` FOREIGN KEY (`fk_dev_application`) REFERENCES `dev_application` (`id`),
  CONSTRAINT `fk_moderation_game_version` FOREIGN KEY (`fk_game_version`) REFERENCES `game_version` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_moderation_review` FOREIGN KEY (`fk_review`) REFERENCES `review` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_moderation_verdict_mod` FOREIGN KEY (`fk_mod`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_moderation_one_target` CHECK (((((`fk_game_version` is not null) + (`fk_dev_application` is not null)) + (`fk_review` is not null)) = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `type` enum('system','warning','moderation','news') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `text` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
  `id` int NOT NULL AUTO_INCREMENT,
  `rating` tinyint unsigned NOT NULL,
  `text` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `report_count` smallint unsigned DEFAULT '0',
  `fk_user` int NOT NULL,
  `fk_game` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `fk_user` (`fk_user`,`fk_game`),
  KEY `fk_review_game` (`fk_game`),
  CONSTRAINT `fk_review_game` FOREIGN KEY (`fk_game`) REFERENCES `game` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_review_user` FOREIGN KEY (`fk_user`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_review_rating` CHECK ((`rating` between 1 and 5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  CONSTRAINT `chk_tag_format` CHECK (regexp_like(`name`,_utf8mb4'^[a-z0-9]+(-[a-z0-9]+)*$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `bio` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_pic` longblob,
  `registered_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `role` enum('user','dev','mod','admin') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user',
  `github_username` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `github_username` (`github_username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_notification`;
CREATE TABLE `user_notification` (
  `fk_user` int NOT NULL,
  `fk_notification` int NOT NULL,
  PRIMARY KEY (`fk_user`,`fk_notification`),
  KEY `fk_notification` (`fk_notification`),
  CONSTRAINT `user_notification_ibfk_1` FOREIGN KEY (`fk_user`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `user_notification_ibfk_2` FOREIGN KEY (`fk_notification`) REFERENCES `notification` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `warning`;
CREATE TABLE `warning` (
  `id` int NOT NULL AUTO_INCREMENT,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `fk_mod` int NOT NULL,
  `fk_user` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_warning_mod` (`fk_mod`),
  KEY `fk_warning_user` (`fk_user`),
  CONSTRAINT `fk_warning_mod` FOREIGN KEY (`fk_mod`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_warning_user` FOREIGN KEY (`fk_user`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- Dump completed on 2026-06-15  3:34:28
