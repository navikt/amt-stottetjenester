CREATE TYPE enhet_type AS ENUM (
    'MODERENHET',
    'UNDERENHET'
    );

CREATE TABLE delta_enhet_oppdatering
(
    id                SERIAL PRIMARY KEY,
    oppdatering_id    INT                      NOT NULL,
    enhet_type        enhet_type UNIQUE        NOT NULL,
    siste_oppdatering TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Setter oppdatering_id til maks verdi for å forhindre oppdatering helt fra starten.
-- Når man skal starte delta oppdatering så må man manuelt endre til ønsket id å starte fra
INSERT INTO delta_enhet_oppdatering (oppdatering_id, enhet_type)
VALUES (2147483647, 'MODERENHET');
INSERT INTO delta_enhet_oppdatering (oppdatering_id, enhet_type)
VALUES (2147483647, 'UNDERENHET');
