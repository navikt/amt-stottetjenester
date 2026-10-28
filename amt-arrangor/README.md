# amt-arrangor

Håndterer tiltaksarrangører, ansatte og roller som brukes i Deltakeroversikten.

## Kom i gang

Du trenger Java 25.

Kjør tester og bygg fra roten av `amt-stottetjenester`:

```bash
./gradlew :amt-arrangor:check
./gradlew :amt-arrangor:build
```

Start med lokal Spring-profil:

```bash
./gradlew :amt-arrangor:bootRun --args='--spring.profiles.active=local'
```

Den lokale profilen forventer PostgreSQL på port `5432`, Kafka på port `9092`, en lokal OAuth2-issuer og lokale
eller mockede utgaver av tjenestene under [Integrasjoner](#integrasjoner). Repoet har ingen Compose-fil som
starter disse avhengighetene.

## Ansvar

Applikasjonen:

- lagrer arrangører og ansatte
- vedlikeholder koordinator-, veileder- og mentorrelasjoner
- oppdaterer ansattroller fra Altinn
- publiserer arrangør- og ansattdata på Kafka

En jobb som bare kjører på leader-instansen, oppdaterer ansattroller hver time.

## API

| Område | Eksempler | Auth |
|--------|-----------|------|
| Ansatt | `GET /api/ansatt` og endepunkter for koordinatorer og mentorer | TokenX |
| Arrangør | `/api/service/arrangor/**` | Azure AD |
| Internt | `/internal/ansatte/republiser` | Intern tilgangskontroll |

Azure AD-kall til ordinære service-endepunkter krever rollen `access_as_application`.

## Integrasjoner

Applikasjonen kaller:

- `amt-enhetsregister` for virksomhetsdata
- `amt-altinn-acl` for arrangørroller
- `amt-person-service` for personopplysninger

Den leser og publiserer Kafka-meldinger om arrangører, ansatte, virksomheter og deltakere.

## Deploy

GitHub Actions bygger og deployer endringer på `main` til `dev-gcp` og deretter `prod-gcp`.

- Nais-manifest: [`.nais/nais.yaml`](.nais/nais.yaml)
- Miljøvariabler: [`.nais/vars-dev.yaml`](.nais/vars-dev.yaml) og
  [`.nais/vars-prod.yaml`](.nais/vars-prod.yaml)
