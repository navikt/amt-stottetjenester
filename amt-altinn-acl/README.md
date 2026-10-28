# amt-altinn-acl

Henter roller for tiltaksarrangører fra Altinn og mellomlagrer dem for Deltakeroversikten.

## Kom i gang

Du trenger Java 25 og Docker. Testene starter PostgreSQL med Testcontainers.

Kjør fra roten av `amt-stottetjenester`:

```bash
./gradlew :amt-altinn-acl:check
```

Applikasjonen har en lokal Spring-profil:

```bash
./gradlew :amt-altinn-acl:bootRun --args='--spring.profiles.active=local'
```

Den lokale profilen forventer PostgreSQL på port `5432`, en lokal OAuth2-issuer og endepunkter som erstatter
Altinn og Maskinporten. Repoet starter ikke disse tjenestene automatisk.

## Ansvar

Altinn lar personer med riktige fullmakter delegere disse ressursene:

- `nav_tiltaksarrangor_deltakeroversikt-koordinator`
- `nav_tiltaksarrangor_deltakeroversikt-veileder`

`amt-altinn-acl` henter delegeringene fra Altinn og lagrer dem som roller knyttet til person og organisasjon.
En jobb som bare kjører på leader-instansen, oppdaterer personer med utdaterte mellomlagrede data hver time.
Data regnes som utdaterte etter én uke.

## API

| Metode | Endepunkt | Beskrivelse |
|--------|-----------|-------------|
| `POST` | `/api/v1/rolle/tiltaksarrangor` | Henter organisasjoner og roller for en person. |
| `GET` | `/internal/altinn/synkroniser` | Starter synkronisering mot Altinn. |

Det ordinære API-et krever Azure AD-token med rollen `access_as_application`. Interne endepunkter har egen
tilgangskontroll.

## Testmiljø

Gjennomføringer i Q2 er koblet til reelle organisasjoner, mens Altinn TT02 bruker syntetiske organisasjoner.
Det gjør at en Altinn-rolle i TT02 ikke gir tilgang til en vanlig gjennomføring i Deltakeroversikten.

Du kan likevel teste integrasjonen ved å delegere en ressurs for en syntetisk organisasjon i TT02. Et kall mot
`/api/v1/rolle/tiltaksarrangor` oppdaterer tabellene `person` og `rolle`, slik at du kan kontrollere resultatet i
databasen. Testtilganger for vanlige Q2-gjennomføringer må vedlikeholdes manuelt i `rolle`-tabellen. Sett også
`person.last_synchronized` langt frem i tid for disse testbrukerne. Ellers synkroniserer neste oppslag mot Altinn
etter én time og ugyldiggjør de manuelt tildelte rollene.

## Integrasjoner og konfigurasjon

Tjenesten bruker Maskinporten for å kalle Altinns API for autoriserte parter. Den trenger databasekonfigurasjon,
Azure AD-oppsett, token-endepunkt, Altinn-URL og Altinn-scope. Nais setter disse verdiene i deploymiljøene.

## Deploy

GitHub Actions bygger og deployer endringer på `main` til `dev-gcp` og deretter `prod-gcp`.

- Nais-manifest: [`.nais/nais.yaml`](.nais/nais.yaml)
- Miljøvariabler: [`.nais/vars-dev.yaml`](.nais/vars-dev.yaml) og
  [`.nais/vars-prod.yaml`](.nais/vars-prod.yaml)
