# Normalisering av `ansatt.arrangorer` (arkivert)

Dette dokumentet er en plassholder. Den opprinnelige planen for normaliseringen er fjernet
fordi arbeidet er gjennomført, men filnavnet beholdes fordi det er referert fra
`V14__populate_ansatt_arrangor_tables.sql`. Den migreringen er kjørt i produksjon, og
kommentarene i den kan ikke endres uten å bryte Flyway-checksummen.

## Hva §3 viste til

V14-kommentaren peker på den manuelle dataanalysen som ble gjort mot dev og preprod før
backfillen ble ferdigstilt. Analysen avdekket to forhold som migreringen håndterer
eksplisitt, og som er beskrevet i selve migreringsfilen:

1. Enkelte `ZonedDateTime`-verdier var serialisert med et `[IANA-sone]`-suffiks, som
   PostgreSQL ikke kan caste til `timestamptz`. Alle datofelt normaliseres derfor med
   `regexp_replace` før cast.
2. 162 rader i `roller` hadde identisk `(ansatt_id, arrangor_id, rolle, gyldig_fra)` med
   `gyldig_til` som avvek under ett millisekund — samme hendelse skrevet to ganger.
   Disse deduplikeres med `DISTINCT ON`.

Rotårsaken til punkt 2 er rettet i `AnsattRolleService`, som nå beregner ett delt
tidspunkt per synkroniseringsoperasjon.

Spørringene fra analysen ble kjørt manuelt mot dev og preprod og er ikke sjekket inn.

Status for gjenstående arbeid: se
[ansatt-arrangor-cutover-plan.md](ansatt-arrangor-cutover-plan.md).
