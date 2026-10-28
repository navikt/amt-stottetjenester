# amt-person-service

Samler og deler personopplysninger som AMT-applikasjonene trenger.

## Kom i gang

Du trenger Java 25. Kjør fra roten av `amt-stottetjenester`:

```bash
./gradlew :amt-person-service:check
./gradlew :amt-person-service:build
```

Bygget genererer Java-klasser fra Avro-skjemaene i `src/main/avro`. De genererte klassene havner i
`build/generated/avro/java`.

Applikasjonen har en lokal Spring-profil:

```bash
./gradlew :amt-person-service:bootRun --args='--spring.profiles.active=local'
```

Den lokale profilen forventer PostgreSQL, Kafka, Schema Registry, en OAuth2-issuer og lokale eller mockede
utgaver av de eksterne tjenestene. Repoet starter ikke disse avhengighetene automatisk.

## Ansvar

Applikasjonen håndterer blant annet:

- Nav-brukere og Nav-ansatte
- ansatte hos tiltaksarrangører
- Nav-enheter
- kontaktinformasjon og adressebeskyttelse

Data lagres i PostgreSQL og publiseres med en databasebasert outbox.

## API

Tjenesten har disse Azure AD-beskyttede endepunktene for oppslag:

- `POST /api/nav-bruker`
- `POST /api/nav-ansatt`
- `POST /api/arrangor-ansatt`
- `POST /api/nav-enhet`

Endepunkter under `/internal/**` brukes til vedlikehold, republisering og synkronisering. De har egen intern
tilgangskontroll.

## Integrasjoner

Applikasjonen henter data fra blant annet PDL, Kontakt- og reservasjonsregisteret, NORG, Kodeverk og NOM. Den
leser Kafka-meldinger om identiteter, oppfølging og deltakelser, og publiserer oppdaterte person- og enhetsdata.

## Deploy

GitHub Actions bygger og deployer endringer på `main` til `dev-gcp` og deretter `prod-gcp`.

- Nais-manifest: [`.nais/nais.yaml`](.nais/nais.yaml)
- Miljøvariabler: [`.nais/vars-dev.yaml`](.nais/vars-dev.yaml) og
  [`.nais/vars-prod.yaml`](.nais/vars-prod.yaml)

## Henvendelser

Opprett et GitHub-issue eller kontakt Team Komet på `komet@nav.no`. Nav-ansatte kan også bruke Slack-kanalen
`#team_komet`.
