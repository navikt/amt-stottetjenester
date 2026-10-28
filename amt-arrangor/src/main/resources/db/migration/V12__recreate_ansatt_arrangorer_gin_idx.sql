DROP INDEX IF EXISTS ansatt_arrangorer_gin_idx;

CREATE INDEX ansatt_arrangorer_gin_idx ON ansatt USING gin (arrangorer);