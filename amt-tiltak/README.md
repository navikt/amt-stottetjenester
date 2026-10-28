# amt-tiltak

Behandler tiltaksgjennomføringer og deltakelser for arrangører, Nav-ansatte og andre AMT-applikasjoner.

## Innhold

- [Kom i gang](#kom-i-gang)
- [Bygg og test](#bygg-og-test)
- [Moduler](#moduler)
- [API og auth](#api-og-auth)
- [Integrasjoner](#integrasjoner)
- [Deploy](#deploy)

## Kom i gang

Du trenger Java 25 og Docker.

Start PostgreSQL, Kafka og en mock OAuth2-server fra roten av `amt-stottetjenester`:

```bash
docker compose -f amt-tiltak/docker-compose.yaml up -d
```

Start deretter `main`-funksjonen i
`amt-tiltak/application/src/test/kotlin/no/nav/amt/tiltak/LocalApplication.kt` fra IntelliJ.

Den lokale databasen er tilgjengelig på `jdbc:postgresql://localhost:5454/amt-tiltak-db`. Brukernavn og passord
står i [`docker-compose.yaml`](docker-compose.yaml).

### Hent lokalt token

Mock OAuth2-serveren kjører på port `8082`:

- TokenX: <http://localhost:8082/local-tokenx/debugger>
- Azure AD: <http://localhost:8082/local-azuread/debugger>

Velg «Get a token», fyll inn ønsket subject og `acr`, og logg inn. Send tokenet som:

```http
Authorization: Bearer <access_token>
```

### Nullstill lokal database

```bash
docker compose -f amt-tiltak/docker-compose.yaml down
docker volume rm amt-tiltak_postgres-db-volume
```

Kommandoen sletter alle lokale data i volumet.

## Bygg og test

Kjør fra roten av `amt-stottetjenester`:

```bash
# Bygg den deploybare applikasjonen
./gradlew :amt-tiltak:application:build

# Kjør check i alle amt-tiltak-modulene
./gradlew :amt-tiltak:checkAll
```

Testene bruker tidssonen `Europe/Oslo` i CI.

## Moduler

`amt-tiltak` er et Gradle-multiprosjekt med flere ansvarsområder:

| Område | Moduler |
|--------|---------|
| Applikasjon | `application` |
| API-er | `bff-tiltaksarrangor`, `bff-nav_ansatt`, `bff-internal`, `external-api` |
| Domene | `core`, `tiltak`, `arrangor`, `navansatt` |
| Tilgangskontroll | `tilgangskontroll-tiltaksansvarlig`, `tilgangskontroll-tiltaksarrangor` |
| Integrasjoner | `clients/*`, `kafka/*`, `data-publisher` |
| Database | `db-migrations` |
| Teststøtte | `test/*`, `test-integration` |

Se [README-en for `bff-tiltaksarrangor`](bff-tiltaksarrangor/README.md) for arrangør-API-et.

## API og auth

Applikasjonen har egne API-er for:

- tiltaksarrangører
- Nav-ansatte
- interne kall mellom AMT-applikasjoner
- eksterne konsumenter

API-ene bruker TokenX for brukerrettede kall og Azure AD for maskin-til-maskin-kall. Tilgangskontrollen ligger i
egne moduler for tiltaksarrangører og tiltaksansvarlige.

Helse og metrikker er tilgjengelige internt:

| Endepunkt | Formål |
|-----------|--------|
| `/internal/health/readiness` | Readiness |
| `/internal/health/liveness` | Liveness |
| `/internal/prometheus` | Prometheus-metrikker |

## Integrasjoner

Applikasjonen bruker PostgreSQL og Kafka. Den kaller blant annet:

- `amt-person-service`
- `amt-arrangor`
- POAO Tilgang
- Mulighetsrommet API
- Unleash

Kafka-modulene leser data om ansatte, arrangører, deltakere og gjennomføringer, og publiserer oppdaterte
domenehendelser.

## Deploy

GitHub Actions bygger `:amt-tiltak:application:build` og deployer endringer på `main` til `dev-gcp` og deretter
`prod-gcp`.

- Nais-manifest: [`.nais/nais.yaml`](.nais/nais.yaml)
- Miljøvariabler: [`.nais/vars-dev.yaml`](.nais/vars-dev.yaml) og
  [`.nais/vars-prod.yaml`](.nais/vars-prod.yaml)
