# Driftsprosedyrer for amt-arena-acl

Denne runbooken beskriver manuelle inngrep i databehandlingen. Kontroller miljøet og utvalget før du endrer data.

## Rekonstruer Kafka-meldinger

Prosedyren setter siste ferdigbehandlede melding per Arena-ID til `RETRY`. Retry-jobben behandler meldingen på
nytt og kan republisere data.

### 1. Kontroller utvalget

```sql
WITH max_ids AS (
    SELECT max(id) AS max_id
    FROM arena_data
    WHERE arena_table_name = 'SIAMO.TILTAKDELTAKER'
      AND ingest_status = 'HANDLED'
    GROUP BY arena_id
)
SELECT id, arena_id, ingest_status, ingested_timestamp
FROM arena_data
WHERE id IN (SELECT max_id FROM max_ids)
ORDER BY id;
```

Avgrens spørringen hvis du ikke skal behandle alle deltakerne.

### 2. Sett meldingene til retry

Kjør oppdateringen i en transaksjon. Kontroller antall oppdaterte rader før du gjør `COMMIT`.

```sql
BEGIN;

WITH max_ids AS (
    SELECT max(id) AS max_id
    FROM arena_data
    WHERE arena_table_name = 'SIAMO.TILTAKDELTAKER'
      AND ingest_status = 'HANDLED'
    GROUP BY arena_id
)
UPDATE arena_data
SET ingest_status = 'RETRY',
    ingest_attempts = 0,
    last_attempted = NULL
WHERE id IN (SELECT max_id FROM max_ids);
```

Gjør endringen permanent hvis utvalget og antallet rader er riktig:

```sql
COMMIT;
```

Avbryt ellers transaksjonen:

```sql
ROLLBACK;
```

### 3. Kontroller resultatet

Nye meldinger kan komme mens retry-jobben behandler de gamle. Kontroller derfor om den sist mottatte meldingen
også er den sist behandlede:

```sql
WITH max_ids AS (
    SELECT arena_id, max(id) AS id
    FROM arena_data
    WHERE arena_table_name = 'SIAMO.TILTAKDELTAKER'
      AND ingest_status = 'HANDLED'
    GROUP BY arena_id
),
max_ingest AS (
    SELECT DISTINCT ON (arena_id) arena_id, id
    FROM arena_data
    WHERE arena_table_name = 'SIAMO.TILTAKDELTAKER'
      AND ingest_status = 'HANDLED'
    ORDER BY arena_id, ingested_timestamp DESC
)
SELECT max_ids.arena_id,
       max_ids.id AS siste_melding_id,
       max_ingest.id AS siste_ingestet
FROM max_ids
JOIN max_ingest
  ON max_ids.arena_id = max_ingest.arena_id
 AND max_ids.id != max_ingest.id;
```

Undersøk radene spørringen returnerer før du eventuelt kjører prosedyren på nytt.
