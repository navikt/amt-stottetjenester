create table if not exists personident
(
    ident varchar primary key,
    person_id   uuid                     not null references person (id),
    type        varchar,
    historisk   boolean,
    created_at  timestamp with time zone not null default current_timestamp,
    modified_at timestamp with time zone not null default current_timestamp
);

create index personident_person_id_idx on personident(person_id);
