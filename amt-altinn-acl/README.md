# AMT Altinn ACL

Et anti corruption layer mot Altinn.

I Altinn er det mulig for daglig leder eller andre med riktige rettigheter å gi personer tilgang til "enkelttjenestene" også kalt roller i appen her:
- nav_tiltaksarrangor_deltakeroversikt-koordinator
- nav_tiltaksarrangor_deltakeroversikt-veileder

## Caching
Roller caches ved å sjekke på når en person sist var oppdatert via `last_synchronized` feltet. Hvis det har gått en stund siden sist så spør vi Altinn om hvilke ressurser de har tilgang til.

## Testmiljø

Vi i Komet jobber med deltakere på tiltak, en deltaker deltar på en gjennomføring. Gjennomføringer i testmiljø (Q2) er koblet mot ekte organsiasjonser (typisk orgnr 9XX XXX XXX). Mens testmiljøet til Altinn bruker kun syntetiske organsiasjoner (typisk orgnr 3- eller 2XX XXX XXX). Dette gjør at vi ikke kan styre tilganger til koordinatorer og veiledere i Deltakeroversikten for gjennomføringer i testmiljø. Måten dette er omgått på er ved å manuelt inserte roller i `rolle` tabellen samt at man har satt `last_synchronized` til en gang langt frem i tid (slik at den er teknisk sett cachet for alltid). 

Det betyr at det er litt vanskelig å teste integrasjonen mot Altinn utenfor prod. Men man kan tildele ressursene på syntetiske organisasjoner, også forsøke å logge inn på Deltakeroversikten. Det vil ikke være mulig å logge inne på Deltakeroversikten, men altinn-acl vil oppdatere `person` og `rolle` tabellene med informasjon fra Altinn, så det er mulig å spørre databasen og se om man får det forventede resultatet.

### Tildeling av roller
1. Logg inn som en daglig leder i https://tt02.altinn.no/
2. Velg en organisasjon som aktør
3. Naviger til `Profil`
4. Under `Andre med rettigheter til virksomheten`:
    - `Legge til ny person eller virksomhet` eller `Gi eller fjern tilgang`
    - `Gi nye enkeltrettigheter`
    - Søk opp `Tiltaksarrangør (koordinator | veileder)`
    - Legg til