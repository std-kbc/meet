USE DEMO;

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  nickname      VARCHAR(50)  NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at DATETIME NULL
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS refresh_tokens (
  user_id    BIGINT PRIMARY KEY,
  token      VARCHAR(512) NOT NULL,
  expires_at DATETIME NOT NULL
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS chat_room (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_type      VARCHAR(20) NOT NULL,
  reference_type VARCHAR(50) NULL,
  reference_id   BIGINT NULL,
  name           VARCHAR(100) NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at     DATETIME NULL
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS chat_room_participant (
  chat_room_id         BIGINT NOT NULL,
  user_id              BIGINT NOT NULL,
  participant_role     VARCHAR(20) NOT NULL,
  participant_status   VARCHAR(20) NOT NULL,
  last_read_message_id BIGINT NULL,
  muted_yn             CHAR(1) NOT NULL DEFAULT 'N',
  joined_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  left_at              DATETIME NULL,
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (chat_room_id, user_id)
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  chat_room_id      BIGINT NOT NULL,
  sender_id         BIGINT NOT NULL,
  message_type      VARCHAR(20) NOT NULL,
  message           TEXT,
  parent_message_id BIGINT NULL,
  file_group_id     BIGINT NULL,
  status            VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  edited_at         DATETIME NULL,
  sent_at           DATETIME NOT NULL,
  deleted_at        DATETIME NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_room_id (chat_room_id, id)
) DEFAULT CHARSET = utf8mb4;
