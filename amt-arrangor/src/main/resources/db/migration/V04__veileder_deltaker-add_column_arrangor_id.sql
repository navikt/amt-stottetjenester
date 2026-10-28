ALTER TABLE veileder_deltaker
    ADD COLUMN arrangor_id uuid references arrangor (id);

ALTER TABLE veileder_deltaker
    ALTER COLUMN arrangor_id SET NOT NULL;
