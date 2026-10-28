ALTER TABLE deltakerliste
    ADD COLUMN modified_at timestamp with time zone not null default current_timestamp,
    ADD COLUMN created_at  timestamp with time zone not null default current_timestamp
