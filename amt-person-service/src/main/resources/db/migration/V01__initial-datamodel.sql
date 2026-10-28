create table if not exists person
(
    id                 uuid primary key,
    person_ident       varchar unique           not null,
    fornavn            varchar                  not null,
    mellomnavn         varchar,
    etternavn          varchar                  not null,
    historiske_identer text ARRAY                        default ARRAY []::text[],
    person_ident_type  varchar,
    created_at         timestamp with time zone not null default current_timestamp,
    modified_at        timestamp with time zone not null default current_timestamp
);

create table if not exists nav_ansatt
(
    id          uuid primary key,
    nav_ident   varchar                  not null,
    navn        varchar                  not null,
    telefon     varchar,
    epost       varchar,
    created_at  timestamp with time zone not null default current_timestamp,
    modified_at timestamp with time zone not null default current_timestamp
);

create table if not exists nav_enhet
(
    id           uuid primary key,
    nav_enhet_id varchar                  not null,
    navn         varchar                  not null,
    created_at   timestamp with time zone not null default current_timestamp,
    modified_at  timestamp with time zone not null default current_timestamp
);

create table if not exists nav_bruker
(
    id              uuid primary key,
    person_id       uuid                     not null references person (id),
    nav_veileder_id uuid references nav_ansatt (id),
    nav_enhet_id    uuid references nav_enhet (id),
    telefon         varchar,
    epost           varchar,
    er_skjermet     boolean                           default false,
    created_at      timestamp with time zone not null default current_timestamp,
    modified_at     timestamp with time zone not null default current_timestamp
);

create table if not exists person_rolle
(
    id          uuid primary key,
    person_id   uuid                     not null references person (id),
    type        varchar,
    created_at  timestamp with time zone not null default current_timestamp,
    modified_at timestamp with time zone not null default current_timestamp,
    unique (person_id, type)
)
