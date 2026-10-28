# amt-arena-ords-proxy

Gir AMT-applikasjoner i GCP kontrollert tilgang til Arena ORDS i FSS.

## Kom i gang

Du trenger Java 25.

Kjør tester fra roten av `amt-stottetjenester`:

```bash
./gradlew :amt-arena-ords-proxy:check
```

Repoet har foreløpig ikke et komplett lokalt oppsett. Testkoden inneholder en lokal entrypoint, men fake-klienten
mot ORDS er ikke ferdig implementert.

## API

| Metode | Endepunkt | Beskrivelse |
|--------|-----------|-------------|
| `GET` | `/api/ords/fnr?personId={id}` | Henter fødselsnummer for en Arena-person. |
| `GET` | `/api/ords/arbeidsgiver?arbeidsgiverId={id}` | Henter organisasjonsnummer for en Arena-arbeidsgiver. |

Endepunktene krever Azure AD-token. Proxyen henter et client credentials-token mot ORDS og mellomlagrer tokenet
frem til kort tid før det utløper.

## Konfigurasjon

Applikasjonen trenger disse innstillingene:

| Variabel | Beskrivelse |
|----------|-------------|
| `ARENA_ORDS_URL` | Basis-URL til Arena ORDS. |
| `ARENA_ORDS_CLIENT_ID` | Client-ID for ORDS. |
| `ARENA_ORDS_CLIENT_SECRET` | Client-secret for ORDS. |

Client-ID og client-secret leveres som en Nais-secret med navnet `amt-arena-ords-proxy`. Ikke legg verdiene i
repoet eller opprett dem manuelt med `kubectl`.

## Deploy

GitHub Actions deployer applikasjonen til Q1 og Q2 i `dev-fss`, og deretter til `prod-fss`.

- Nais-manifest: [`.nais/nais.yaml`](.nais/nais.yaml)
- Q1: [`.nais/vars-dev-q1.yaml`](.nais/vars-dev-q1.yaml)
- Q2: [`.nais/vars-dev-q2.yaml`](.nais/vars-dev-q2.yaml)
- Prod: [`.nais/vars-prod.yaml`](.nais/vars-prod.yaml)
