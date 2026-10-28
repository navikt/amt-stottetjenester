# AMT Enhetsregister

ACL mot Brønnøysundregisteret

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-enhetsregister&metric=bugs)](https://sonarcloud.io/dashboard?id=navikt_amt-enhetsregister)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-enhetsregister&metric=code_smells)](https://sonarcloud.io/dashboard?id=navikt_amt-enhetsregister)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-enhetsregister&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=navikt_amt-enhetsregister)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-enhetsregister&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=navikt_amt-enhetsregister)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-enhetsregister&metric=security_rating)](https://sonarcloud.io/dashboard?id=navikt_amt-enhetsregister)

## SonarCloud
https://sonarcloud.io/dashboard?id=navikt_amt-enhetsregister

## Justere på deltaoppdatering

For å justere på deltaoppdateringen må man inn i databasen og kjøre:
```
UPDATE delta_enhet_oppdatering set oppdatering_id = ? where enhet_type = 'MODERENHET';
UPDATE delta_enhet_oppdatering set oppdatering_id = ? where enhet_type = 'UNDERENHET';
```

Det er viktig å sette en oppdatering_id som er relativt ny slik at man ikke starter å laste inn oppdateringer helt fra starten.

Hvis man går inn på
f.eks https://data.brreg.no/enhetsregisteret/api/oppdateringer/enheter?dato=2022-02-01T00:00:00.000Z
eller
f.eks https://data.brreg.no/enhetsregisteret/api/oppdateringer/underenheter?dato=2022-02-01T00:00:00.000Z

så kan man hente ut en relativt ny oppdatering_id og legge den inn i delta_enhet_oppdatering tabellen.

## Hvordan laste alle enheter på nytt

Exec inn i podden eller port-forward.

1. Last inn alle moderenheter:
`wget --method=POST -O - localhost:8080/local/api/oppdater-alle/moderenheter`.

2. Last inn alle underenheter:
`wget --method=POST -O - localhost:8080/local/api/oppdater-alle/underenheter`.
