CREATE TABLE deltaker
(
    id             uuid PRIMARY KEY,
    statustype     varchar                  not null,
    gyldig_fra     timestamp with time zone not null,
    opprettet_dato timestamp with time zone not null,
    modified_at    timestamp with time zone not null default current_timestamp
);