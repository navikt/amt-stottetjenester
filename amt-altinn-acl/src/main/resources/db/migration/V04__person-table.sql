CREATE TABLE person
(
    id                BIGSERIAL PRIMARY KEY,
    norsk_ident       VARCHAR                  NOT NULL UNIQUE,
    created           TIMESTAMP WITH TIME ZONE NOT NULL default current_timestamp,
    last_synchronized TIMESTAMP WITH TIME ZONE NOT NULL default 'epoch'
);

CREATE INDEX person_norsk_ident ON person (norsk_ident);
