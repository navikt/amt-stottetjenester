CREATE TABLE ansatt
(
    id                uuid PRIMARY KEY,
    person_id         uuid                     not null UNIQUE,
    personident       varchar                  not null UNIQUE,
    fornavn           varchar                  not null,
    mellomnavn        varchar,
    etternavn         varchar                  not null,
    modified_at       timestamp with time zone not null default current_timestamp,
    last_synchronized timestamp with time zone not null default current_timestamp
);

CREATE TABLE ansatt_rolle
(
    id          serial PRIMARY KEY,
    ansatt_id   uuid                     not null references ansatt (id),
    arrangor_id uuid                     not null references arrangor (id),
    rolle       varchar                  not null,
    gyldig_fra  timestamp with time zone not null default current_timestamp,
    gyldig_til  timestamp with time zone,
    UNIQUE (ansatt_id, arrangor_id, rolle, gyldig_fra)
);

CREATE TABLE koordinator_deltakerliste
(
    id               serial PRIMARY KEY,
    ansatt_id        uuid                     not null references ansatt (id),
    deltakerliste_id uuid                     not null references deltakerliste (id),
    gyldig_fra       timestamp with time zone not null default current_timestamp,
    gyldig_til       timestamp with time zone,
    UNIQUE (ansatt_id, deltakerliste_id, gyldig_fra)
);

CREATE TABLE veileder_deltaker
(
    id            serial PRIMARY KEY,
    ansatt_id     uuid                     not null references ansatt (id),
    deltaker_id   uuid                     not null,
    veileder_type varchar                  not null,
    gyldig_fra    timestamp with time zone not null default current_timestamp,
    gyldig_til    timestamp with time zone,
    UNIQUE (ansatt_id, deltaker_id, gyldig_fra)
)
