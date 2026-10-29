# amt-enhetsregister

Henter virksomhetsdata fra Brønnøysundregistrene og deler dem med AMT-applikasjonene.

## Kom i gang

Du trenger Java 25 og Docker. Kjør tester fra roten av `amt-stottetjenester`:

```bash
./gradlew :amt-enhetsregister:check
```

Applikasjonen har en lokal Spring-profil:

```bash
./gradlew :amt-enhetsregister:bootRun --args='--spring.profiles.active=local'
```

Det lokale oppsettet er ikke selvstendig. Verdiene i `docker-compose.yaml` samsvarer ikke med alle portene og
databasenavnene i lokalprofilen, og Compose-filen starter verken Kafka eller en mock for Brønnøysundregistrene.
Kontroller konfigurasjonen før du starter applikasjonen lokalt.

## Ansvar

Applikasjonen:

- lagrer hovedenheter (`MODERENHET` i koden) og underenheter i PostgreSQL
- tilbyr oppslag på organisasjonsnummer
- henter deltaoppdateringer fra Brønnøysundregistrene
- publiserer virksomhetsdata på Kafka

Jobber som bare kjører på leader-instansen, henter deltaoppdateringer hver time: hovedenheter ved hel time og
underenheter 30 minutter over hel time.

## API

| Metode | Endepunkt | Beskrivelse |
|--------|-----------|-------------|
| `GET` | `/api/enhet/{organisasjonsnummer}` | Henter en enhet. |
| `POST` | `/local/api/oppdater-alle/moderenheter` | Starter full innlasting av hovedenheter. |
| `POST` | `/local/api/oppdater-alle/underenheter` | Starter full innlasting av underenheter. |

Det ordinære API-et krever Azure AD-token med rollen `access_as_application`. Endepunktene for full innlasting
har intern tilgangskontroll og starter jobben asynkront.

## Drift

Se [driftsprosedyrene](docs/runbook.md) for å:

- endre startpunktet for deltaoppdateringer
- laste inn alle hovedenheter eller underenheter på nytt

Prosedyrene kan behandle store datamengder og skal følges opp i logger og Nais.

## Deploy

GitHub Actions bygger og deployer endringer på `main` til `dev-gcp` og deretter `prod-gcp`.

- Nais-manifest: [`.nais/nais.yaml`](.nais/nais.yaml)
- Miljøvariabler: [`.nais/vars-dev.yaml`](.nais/vars-dev.yaml) og
  [`.nais/vars-prod.yaml`](.nais/vars-prod.yaml)
