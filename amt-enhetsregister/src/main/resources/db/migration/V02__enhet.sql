
CREATE TABLE enhet (
    id SERIAL PRIMARY KEY,
    organisasjonsnummer CHAR(9) UNIQUE NOT NULL,
    overordnet_enhet CHAR(9),
    navn VARCHAR NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
