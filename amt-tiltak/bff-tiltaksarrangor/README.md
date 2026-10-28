# bff-tiltaksarrangor

Tilbyr TokenX-beskyttede API-er som tiltaksarrangørflaten bruker til å følge opp deltakere.

## Ansvar

Modulen håndterer blant annet:

- oppstarts- og sluttdato
- deltakelsesprosent og forlengelse
- sluttårsak og statusen «ikke aktuell»
- vurderinger og tilbakekalling av endringsmeldinger

Alle kall kontrollerer at den innloggede arrangøransatte har tilgang til deltakeren.

## API

Endepunktene ligger under:

- `/api/tiltaksarrangor/deltaker/{deltakerId}/**`
- `/api/tiltaksarrangor/endringsmelding/{id}/tilbakekall`

Se controllerne i `src/main/kotlin` for request-modeller og de enkelte operasjonene.

## Bygg og test

Kjør fra roten av `amt-stottetjenester`:

```bash
./gradlew :amt-tiltak:bff-tiltaksarrangor:check
```

Modulen deployes ikke alene. `amt-tiltak:application` inkluderer den i hovedapplikasjonen.
