--liquibase formatted sql

--changeset template:000-init-schema
--comment: Placeholder initial changeset. Safe to delete once you add the first
--comment: real migration (e.g. 001-create-<your-table>.sql).
SELECT 1;
--rollback SELECT 1;
