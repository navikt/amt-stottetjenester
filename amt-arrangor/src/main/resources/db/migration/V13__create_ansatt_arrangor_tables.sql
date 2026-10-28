CREATE TABLE ansatt_arrangor (
    ansatt_id   UUID NOT NULL,
    arrangor_id UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_ansatt_arrangor PRIMARY KEY (ansatt_id, arrangor_id),
    CONSTRAINT fk_ansatt_arrangor_ansatt FOREIGN KEY (ansatt_id) REFERENCES ansatt (id),
    CONSTRAINT fk_ansatt_arrangor_arrangor FOREIGN KEY (arrangor_id) REFERENCES arrangor (id)
);

CREATE TABLE ansatt_arrangor_rolle (
    ansatt_id   UUID NOT NULL,
    arrangor_id UUID NOT NULL,
    rolle       TEXT NOT NULL,
    gyldig_fra  TIMESTAMPTZ NOT NULL,
    gyldig_til  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ansatt_arrangor_rolle UNIQUE (ansatt_id, arrangor_id, rolle, gyldig_fra),
    CONSTRAINT fk_ansatt_arrangor_rolle_arrangor
        FOREIGN KEY (ansatt_id, arrangor_id) REFERENCES ansatt_arrangor (ansatt_id, arrangor_id) ON DELETE CASCADE
);
-- Ingen egen indeks på (ansatt_id, arrangor_id): dekkes allerede som prefiks
-- av UNIQUE-indeksen uq_ansatt_arrangor_rolle.

CREATE TABLE ansatt_arrangor_veileder (
    ansatt_id     UUID NOT NULL,
    arrangor_id   UUID NOT NULL,
    deltaker_id   UUID NOT NULL,
    veileder_type TEXT NOT NULL,
    gyldig_fra    TIMESTAMPTZ NOT NULL,
    gyldig_til    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ansatt_arrangor_veileder UNIQUE (ansatt_id, arrangor_id, deltaker_id, veileder_type, gyldig_fra),
    CONSTRAINT fk_ansatt_arrangor_veileder_arrangor
        FOREIGN KEY (ansatt_id, arrangor_id) REFERENCES ansatt_arrangor (ansatt_id, arrangor_id) ON DELETE CASCADE
);
-- (ansatt_id, arrangor_id) dekkes av UNIQUE-prefiks; deltaker_id trenger egen
-- indeks siden det ikke er første kolonne i noen annen indeks.
CREATE INDEX idx_ansatt_arrangor_veileder_deltaker ON ansatt_arrangor_veileder (deltaker_id);
CREATE INDEX idx_ansatt_arrangor_arrangor_id ON ansatt_arrangor (arrangor_id);

CREATE TABLE ansatt_arrangor_koordinator (
    ansatt_id        UUID NOT NULL,
    arrangor_id      UUID NOT NULL,
    deltakerliste_id UUID NOT NULL,
    gyldig_fra       TIMESTAMPTZ NOT NULL,
    gyldig_til       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ansatt_arrangor_koordinator UNIQUE (ansatt_id, arrangor_id, deltakerliste_id, gyldig_fra),
    CONSTRAINT fk_ansatt_arrangor_koordinator_arrangor
        FOREIGN KEY (ansatt_id, arrangor_id) REFERENCES ansatt_arrangor (ansatt_id, arrangor_id) ON DELETE CASCADE
);
-- Samme resonnement: kun deltakerliste_id trenger egen indeks.
CREATE INDEX idx_ansatt_arrangor_koordinator_deltakerliste ON ansatt_arrangor_koordinator (deltakerliste_id);
