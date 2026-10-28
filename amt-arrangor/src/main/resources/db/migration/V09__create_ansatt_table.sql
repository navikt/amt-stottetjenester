CREATE TABLE ansatt
(
    id                uuid PRIMARY KEY,
    person_id         uuid                     not null UNIQUE,
    personident       varchar                  not null UNIQUE,
    fornavn           varchar                  not null,
    mellomnavn        varchar,
    etternavn         varchar                  not null,
    arrangorer        jsonb                    not null,
    modified_at       timestamp with time zone not null default current_timestamp,
    last_synchronized timestamp with time zone not null default current_timestamp
);