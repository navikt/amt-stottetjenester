create table migrering_diff
(
    resurs_id    uuid primary key,
    endepunkt    varchar                             not null,
    request_body jsonb,
    diff         jsonb,
    error        varchar,
    modified_at  timestamp default current_timestamp not null,
    created_at   timestamp default current_timestamp not null
);
