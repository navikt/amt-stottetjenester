CREATE TABLE rolle
(
    id                  BIGSERIAL PRIMARY KEY,
    person_id           BIGINT references person (id) NOT NULL,
    organisasjonsnummer VARCHAR                       NOT NULL,
    rolle               VARCHAR                       NOT NULL,
    valid_from          TIMESTAMP WITH TIME ZONE      NOT NULL,
    valid_to            TIMESTAMP WITH TIME ZONE,

    unique (person_id, organisasjonsnummer, rolle, valid_from)

);

CREATE INDEX rolle_person_id ON rolle (person_id);
