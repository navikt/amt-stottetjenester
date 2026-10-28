ALTER TABLE rettigheter_cache ADD COLUMN modified_at timestamp with time zone not null default current_timestamp;
