--liquibase formatted sql

--changeset nimko:2
ALTER TABLE chat_context ADD COLUMN is_group_chat BOOLEAN NOT NULL DEFAULT FALSE;
