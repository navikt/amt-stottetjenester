# Overgang fra JSONB til normaliserte ansatt–arrangør-tabeller

**Team:** amt
**Status:** Opprydding gjenstår

`ansatt.arrangorer` er erstattet av `ansatt_arrangor`, `ansatt_arrangor_rolle`,
`ansatt_arrangor_veileder` og `ansatt_arrangor_koordinator` som kilde til sannhet.
Normalisert lesing har vært aktiv i produksjon siden 26. september 2026, og driftssjekken
etter V14 fant ingen avvik.

Dette dokumentet beskriver det som gjenstår. Skjemaet, backfillen og dual-write-mekanikken
er gjennomført og dokumenteres nå av migreringsfilene og av KDoc på
`AnsattArrangorSyncService`.

## Denne releasen — opprydding

Normaliserte tabeller blir eneste lesekilde og eneste skrivested for arrangørrelasjoner.
Unleash og all runtime-kode som bruker JSONB fjernes fra applikasjonen.

`ansatt.arrangorer` og Unleash-token-ressursene beholdes gjennom utrullingen, slik at pods
fra forrige versjon fortsatt kan kjøre mens nye pods starter.

**Før deploy:**

Slå på `amt.arrangor-les-normaliserte-tabeller` manuelt i Unleash.

**Migreringer i releasen:**

`V15__set_ansatt_arrangorer_default.sql` setter `DEFAULT '[]'::jsonb` på
`ansatt.arrangorer`. Ny kode skriver ikke kolonnen, men den er `NOT NULL`, og gamle pods
kan ikke lese `NULL`. Ansatte som opprettes etter utrulling får derfor en tom JSONB-liste.

### Utsatt: FK fra `ansatt_arrangor_veileder` til `deltaker`

En slik FK var planlagt i denne releasen, men er utsatt.

`POST /api/ansatt/veiledere/{deltakerId}` skriver veilederkoblingen synkront, uten å
sjekke at deltakeren finnes. Deltakerraden opprettes først når `amt.deltaker-v2`
konsumeres. En koordinator som tildeler veileder for en nyopprettet deltaker, ville
dermed fått en skrivefeil. Preflight-spørringen dekker bare eksisterende data og fanger
ikke dette kappløpet.

Preflight-spørringen under viste null foreldreløse rader da den ble kjørt. Den må kjøres
på nytt rett før FK-en eventuelt legges til, og `violations` må være `0`:

```sql
SELECT
    count(*) FILTER (WHERE v.deltaker_id IS NULL OR d.id IS NULL) AS violations,
    count(*) FILTER (WHERE v.deltaker_id IS NULL) AS null_reference_count,
    count(*) FILTER (WHERE v.deltaker_id IS NOT NULL AND d.id IS NULL) AS orphan_count
FROM ansatt_arrangor_veileder v
LEFT JOIN deltaker d ON d.id = v.deltaker_id;
```

En grønn preflight er nødvendig, men ikke tilstrekkelig: den dekker bare eksisterende
data, ikke kappløpet beskrevet over. FK-en kan legges til når skriveflyten enten venter
på eller oppretter deltakerraden.

Det legges heller ikke til FK fra `ansatt_arrangor_koordinator.deltakerliste_id`, fordi
`deltakerliste` ikke finnes i gjeldende skjema.

## Neste release — fjern JSONB

Applikasjonskoden er allerede fri for JSONB. Det som gjenstår er skjemaet og
Nais-ressursene, og det kan først gjøres når alle pods kjører oppryddingsversjonen:

1. Drop `ansatt_arrangorer_gin_idx` og kolonnen `ansatt.arrangorer` med en ny migrering.
   Indeksen forsvinner riktignok automatisk når kolonnen droppes, så et eksplisitt
   `DROP INDEX` er valgfritt.
2. Fjern Unleash-token-ressurser (`.nais/unleash-apitoken-dev.yaml`,
   `.nais/unleash-apitoken-prod.yaml`), secret-referansen og egress-regelen for
   `amt-unleash-api.nav.cloud.nais.io` fra Nais-manifestene.

Feltet `AnsattDbo.arrangorer` beholdes. Det er ikke lenger en JSONB-representasjon, men
den sammensatte tilstanden som hentes fra de normaliserte tabellene.

Etter at kolonnen er droppet skal gammel applikasjonsversjon ikke rulles tilbake.

## Rollback

| Situasjon | Handling | Datakonsekvens |
|-----------|----------|----------------|
| Feil i oppryddingsreleasen | Deploy forrige image. JSONB er fortsatt lesbart, men foreldet fra det øyeblikket nye operasjoner er skrevet | Målrettet reparasjon kreves dersom operasjoner er utført |
| Applikasjonen feiler mot nytt skjema | Deploy en fremoverrettet migrering eller et kompatibelt image | Droppet kolonne gjenopprettes ikke som akutt rollback |

Databasemigreringer rulles fremover, ikke reverseres.

## Post-deploy-verifisering

- [ ] Hent ansatt med ID og personident.
- [ ] Hent en side med ansatte og kontroller responstid.
- [ ] Opprett en ny ansatt med minst én rolle.
- [ ] Legg til og fjern en veilederkobling.
- [ ] Legg til og fjern en koordinatorkobling.
- [ ] Fjern en rolle og kontroller at avhengige tilganger deaktiveres.
- [ ] Kjør eller observer rollesynkronisering.
- [ ] Observer deaktivering og reaktivering for en deltaker.
- [ ] Kontroller at publisert Kafka-payload reflekterer normalisert tilstand.
- [ ] Kontroller logger og metrikker for lesefeil.

Logger skal bruke tekniske ID-er og antall, aldri personident eller navn.

## Ferdigkriterier

Oppfylt i denne releasen:

- [x] alle lesinger, beslutninger og publiseringer bruker normaliserte tabeller
- [x] alle skriv går bare til normaliserte tabeller
- [x] ingen produksjonskode refererer til `ansatt.arrangorer`
- [x] tester dekker normalisert lesing og skriving uten JSONB-fixtures
- [x] Unleash er fjernet fra applikasjonskoden

Gjenstår:

- [ ] JSONB-kolonnen og GIN-indeksen er fjernet
- [ ] Unleash-ressursene er fjernet fra Nais-manifestene
