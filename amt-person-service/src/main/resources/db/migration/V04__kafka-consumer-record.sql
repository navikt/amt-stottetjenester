create sequence kafka_consumer_record_id_seq;

create table kafka_consumer_record
(
    id               bigint       not null primary key,
    topic            varchar(100) not null,
    partition        integer      not null,
    record_offset    bigint       not null,
    retries          integer      not null default 0,
    last_retry       timestamp,
    key              bytea,
    value            bytea,
    headers_json     text,
    record_timestamp bigint,
    created_at       timestamp             default current_timestamp not null,
    unique (topic, partition, record_offset)
);
