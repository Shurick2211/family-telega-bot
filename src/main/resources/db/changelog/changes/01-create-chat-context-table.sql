--liquibase formatted sql

--changeset nimko:1
CREATE TABLE chat_context (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    user_name VARCHAR(255),
    name VARCHAR(255),
    message_id INT,
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX chat_id_idx ON chat_context(chat_id);
CREATE INDEX message_id_idx ON chat_context(message_id);
