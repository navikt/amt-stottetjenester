CREATE TABLE deltakerliste
(
    id          uuid PRIMARY KEY,
    arrangor_id uuid not null references arrangor (id)
);

CREATE INDEX deltakerliste_arrangor_id ON deltakerliste (arrangor_id);
