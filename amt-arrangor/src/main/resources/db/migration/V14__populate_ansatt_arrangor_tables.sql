-- Backfill av ansatt_arrangor(_rolle|_veileder|_koordinator) fra ansatt.arrangorer (jsonb).
--
-- Håndterer to bekreftede databehov, avdekket ved manuell analyse mot dev/preprod (se
-- docs/ansatt-arrangor-normalisering-plan.md §3):
--   1) Enkelte ZonedDateTime-verdier er serialisert med et [IANA-sone]-suffiks
--      (f.eks. "...+01:00[Europe/Oslo]"), som PostgreSQLs ::timestamptz ikke forstår.
--      Alle datofelt normaliseres derfor med regexp_replace(felt, '\[.*\]$', '') før cast.
--   2) 162 rader i "roller" har identisk (ansatt_id, arrangor_id, rolle, gyldig_fra), men med
--      gyldig_til som avviker < 1 ms (samme hendelse skrevet to ganger). Disse deduplikeres
--      eksplisitt med DISTINCT ON, der raden med høyest gyldig_til (NULL = fortsatt gyldig,
--      rangeres høyest) beholdes.

-- 1) Foreldretabell: distinkte (ansatt_id, arrangor_id)-par
INSERT INTO ansatt_arrangor (ansatt_id, arrangor_id)
SELECT DISTINCT
    a.id,
    (arr.value ->> 'arrangorId')::uuid
FROM ansatt a,
     LATERAL jsonb_array_elements(a.arrangorer) AS arr(value)
ON CONFLICT (ansatt_id, arrangor_id) DO NOTHING;

-- 2) Roller — normaliserer [ZoneId]-suffiks, og deduplikerer eksplisitt på
-- (ansatt_id, arrangor_id, rolle, gyldig_fra): 162 bekreftede nær-duplikater i
-- kildedata har identisk nøkkel med gyldig_til som avviker < 1 ms. Vi beholder
-- alltid raden med høyest gyldig_til (NULL = fortsatt gyldig = "høyest").
INSERT INTO ansatt_arrangor_rolle (ansatt_id, arrangor_id, rolle, gyldig_fra, gyldig_til)
SELECT DISTINCT ON (ansatt_id, arrangor_id, rolle, gyldig_fra)
    ansatt_id, arrangor_id, rolle, gyldig_fra, gyldig_til
FROM (
    SELECT
        a.id AS ansatt_id,
        (arr.value ->> 'arrangorId')::uuid AS arrangor_id,
        rolle.value ->> 'rolle' AS rolle,
        (regexp_replace(rolle.value ->> 'gyldigFra', '\[.*\]$', ''))::timestamptz AS gyldig_fra,
        CASE
            WHEN rolle.value ->> 'gyldigTil' IS NULL THEN NULL
            ELSE (regexp_replace(rolle.value ->> 'gyldigTil', '\[.*\]$', ''))::timestamptz
        END AS gyldig_til
    FROM ansatt a,
         LATERAL jsonb_array_elements(a.arrangorer) AS arr(value),
         LATERAL jsonb_array_elements(arr.value -> 'roller') AS rolle(value)
) normalisert
ORDER BY ansatt_id, arrangor_id, rolle, gyldig_fra, gyldig_til DESC NULLS FIRST
ON CONFLICT (ansatt_id, arrangor_id, rolle, gyldig_fra) DO NOTHING;

-- 3) Veiledere — normaliserer [ZoneId]-suffiks (242 rader i kildedata har dette
-- formatet). Ingen reelle nøkkelkonflikter funnet i analysen, så ren DISTINCT holder.
INSERT INTO ansatt_arrangor_veileder (ansatt_id, arrangor_id, deltaker_id, veileder_type, gyldig_fra, gyldig_til)
SELECT DISTINCT
    a.id,
    (arr.value ->> 'arrangorId')::uuid,
    (veileder.value ->> 'deltakerId')::uuid,
    veileder.value ->> 'veilederType',
    (regexp_replace(veileder.value ->> 'gyldigFra', '\[.*\]$', ''))::timestamptz,
    CASE
        WHEN veileder.value ->> 'gyldigTil' IS NULL THEN NULL
        ELSE (regexp_replace(veileder.value ->> 'gyldigTil', '\[.*\]$', ''))::timestamptz
    END
FROM ansatt a,
     LATERAL jsonb_array_elements(a.arrangorer) AS arr(value),
     LATERAL jsonb_array_elements(arr.value -> 'veileder') AS veileder(value)
ON CONFLICT (ansatt_id, arrangor_id, deltaker_id, veileder_type, gyldig_fra) DO NOTHING;

-- 4) Koordinatorer — ingen duplikater eller formatavvik funnet, men normaliserer
-- [ZoneId]-suffiks likevel for robusthet mot fremtidige data med samme mønster.
INSERT INTO ansatt_arrangor_koordinator (ansatt_id, arrangor_id, deltakerliste_id, gyldig_fra, gyldig_til)
SELECT DISTINCT
    a.id,
    (arr.value ->> 'arrangorId')::uuid,
    (koordinator.value ->> 'deltakerlisteId')::uuid,
    (regexp_replace(koordinator.value ->> 'gyldigFra', '\[.*\]$', ''))::timestamptz,
    CASE
        WHEN koordinator.value ->> 'gyldigTil' IS NULL THEN NULL
        ELSE (regexp_replace(koordinator.value ->> 'gyldigTil', '\[.*\]$', ''))::timestamptz
    END
FROM ansatt a,
     LATERAL jsonb_array_elements(a.arrangorer) AS arr(value),
     LATERAL jsonb_array_elements(arr.value -> 'koordinator') AS koordinator(value)
ON CONFLICT (ansatt_id, arrangor_id, deltakerliste_id, gyldig_fra) DO NOTHING;
