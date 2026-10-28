ALTER TABLE person ADD COLUMN IF NOT EXISTS er_falsk_identitet BOOLEAN DEFAULT FALSE;
UPDATE person SET er_falsk_identitet = FALSE WHERE er_falsk_identitet IS NULL;
ALTER TABLE person ALTER COLUMN er_falsk_identitet SET DEFAULT FALSE;
ALTER TABLE person ALTER COLUMN er_falsk_identitet SET NOT NULL;