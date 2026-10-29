# Driftsprosedyrer for amt-enhetsregister

Denne runbooken beskriver manuelle inngrep i innlastingen fra Brønnøysundregistrene. Kontroller miljøet og
verdiene før du endrer data.

## Endre startpunktet for deltaoppdateringer

Tabellen `delta_enhet_oppdatering` lagrer siste behandlede oppdaterings-ID for hovedenheter og underenheter.

### 1. Kontroller gjeldende startpunkt

```sql
SELECT enhet_type, oppdatering_id
FROM delta_enhet_oppdatering
ORDER BY enhet_type;
```

### 2. Finn en ny oppdaterings-ID

Brønnøysundregistrenes oppdateringsendepunkter returnerer ID-er for et gitt tidspunkt:

- `https://data.brreg.no/enhetsregisteret/api/oppdateringer/enheter?dato={tidspunkt}`
- `https://data.brreg.no/enhetsregisteret/api/oppdateringer/underenheter?dato={tidspunkt}`

Velg en ID fra et nylig tidspunkt. En gammel ID gjør at tjenesten leser en større del av endringshistorikken.

### 3. Oppdater startpunktet

Erstatt plassholderne med ID-ene du har kontrollert. Fjern oppdateringen for enhetstypen du ikke skal flytte,
og kjør endringen i en transaksjon.

```sql
BEGIN;

UPDATE delta_enhet_oppdatering
SET oppdatering_id = {oppdatering_id_moderenhet}
WHERE enhet_type = 'MODERENHET';

UPDATE delta_enhet_oppdatering
SET oppdatering_id = {oppdatering_id_underenhet}
WHERE enhet_type = 'UNDERENHET';

SELECT enhet_type, oppdatering_id
FROM delta_enhet_oppdatering
ORDER BY enhet_type;
```

Gjør endringen permanent hvis verdiene er riktige:

```sql
COMMIT;
```

Avbryt ellers transaksjonen:

```sql
ROLLBACK;
```

## Last inn alle enheter på nytt

Endepunktene starter asynkrone jobber. Kjør kallene fra podden eller via port-forward:

```bash
curl --request POST http://localhost:8080/local/api/oppdater-alle/moderenheter
curl --request POST http://localhost:8080/local/api/oppdater-alle/underenheter
```

Full innlasting bruker mye minne. Følg logger og ressursbruk i Nais etter at du har startet en jobb.
