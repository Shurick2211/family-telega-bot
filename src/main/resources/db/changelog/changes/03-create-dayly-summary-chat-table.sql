--liquibase formatted sql

--changeset nimko:3
CREATE TABLE dayly_summary_chat (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX dayly_summary_chat_id_idx ON dayly_summary_chat(chat_id);
CREATE INDEX dayly_summary_created_at_idx ON dayly_summary_chat(created_at);
