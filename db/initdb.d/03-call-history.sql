USE DEMO;

CREATE TABLE IF NOT EXISTS call_history (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  chat_room_id  BIGINT NOT NULL,
  caller_id     BIGINT NOT NULL,
  callee_id     BIGINT NOT NULL,
  result        VARCHAR(20) NOT NULL,
  requested_at  DATETIME NOT NULL,
  answered_at   DATETIME NULL,
  ended_at      DATETIME NOT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_call_history_room (chat_room_id, id)
) DEFAULT CHARSET = utf8mb4;
