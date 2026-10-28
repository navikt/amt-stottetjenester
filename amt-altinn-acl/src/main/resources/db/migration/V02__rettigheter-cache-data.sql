ALTER TABLE rettigheter_cache ADD COLUMN data_version INT DEFAULT 1;

ALTER TABLE rettigheter_cache RENAME COLUMN rettigheter_json TO data_json;
