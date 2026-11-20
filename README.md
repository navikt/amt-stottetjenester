# amt-stottetjenester

Monorepo for sju av Team Komets støtteapplikasjoner for tiltak og deltakelser.

## Applikasjoner

| Applikasjon | Ansvar |
|-------------|--------|
| [amt-altinn-acl](amt-altinn-acl/README.md) | Henter og mellomlagrer tilganger for tiltaksarrangører fra Altinn. |
| [amt-arena-acl](amt-arena-acl/README.md) | Leser data fra Arena og oversetter dem til AMT-domenet. |
| [amt-arena-ords-proxy](amt-arena-ords-proxy/README.md) | Gir GCP-applikasjoner kontrollert tilgang til Arena ORDS i FSS. |
| [amt-arrangor](amt-arrangor/README.md) | Håndterer arrangører, ansatte og roller i Deltakeroversikten. |
| [amt-enhetsregister](amt-enhetsregister/README.md) | Leser virksomhetsdata fra Brønnøysundregistrene og publiserer dem på Kafka. |
| [amt-person-service](amt-person-service/README.md) | Samler og deler personopplysninger som AMT-applikasjonene trenger. |
| [amt-tiltak](amt-tiltak/README.md) | Tilbyr API-er og databehandling for tiltaksgjennomføringer og deltakelser. |

## Kom i gang

Du trenger Java 25. Flere av testene bruker Testcontainers og krever derfor Docker.

```bash
git clone git@github.com:navikt/amt-stottetjenester.git
cd amt-stottetjenester

./gradlew check
```

Kjør tester for én applikasjon:

```bash
./gradlew :amt-person-service:check
```

`amt-tiltak` er et multiprosjekt. Kjør alle testene i prosjektet med:

```bash
./gradlew :amt-tiltak:checkAll
```

Se README-en til hver applikasjon for lokalt oppsett og appspesifikke kommandoer.

## Bygg

Alle applikasjonene bygges fra samme Gradle-oppsett. Felles versjoner ligger i
[`gradle/libs.versions.toml`](gradle/libs.versions.toml), og felles byggkonfigurasjon ligger i
[`buildSrc`](buildSrc).

```bash
# Bygg én applikasjon
./gradlew :amt-arrangor:build

# Formater Kotlin-koden
./gradlew ktlintFormat
```

## Teknologi

- Kotlin 2.4 og Java 25
- Spring Boot 4
- Gradle med Kotlin DSL
- PostgreSQL og Flyway
- Kafka og Avro
- Nais på GCP og FSS

`amt-tiltak` er en eldre applikasjon med flere interne Gradle-moduler. De andre applikasjonene er selvstendige
Spring Boot-moduler.

## Test og deploy

Pull requests kjører tester for alle sju applikasjonene. CodeQL analyserer Kotlin- og Java-koden.

Når endringer merges til `main`, bygger GitHub Actions et image for hver berørte applikasjon. Workflowen deployer
først til dev og deretter til prod. Det opprettes ingen GitHub Release eller tag.

De fleste applikasjonene kjører i `dev-gcp` og `prod-gcp`. `amt-arena-ords-proxy` kjører i FSS og har to
dev-miljøer, Q1 og Q2. Hver applikasjon har Nais-manifest og miljøvariabler i sin egen `.nais`-mappe.

## Team

Team Komet vedlikeholder applikasjonene. Interne henvendelser kan sendes i Slack-kanalen `#team_komet`.

## Lisens

Dette prosjektet er lisensiert under MIT License. Se [LICENSE](LICENSE) for fullstendig lisensinformasjon.
