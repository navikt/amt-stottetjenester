# AMT Arena ORDS Proxy

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-arena-ords-proxy&metric=bugs)](https://sonarcloud.io/dashboard?id=navikt_amt-arena-ords-proxy)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-arena-ords-proxy&metric=code_smells)](https://sonarcloud.io/dashboard?id=navikt_amt-arena-ords-proxy)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-arena-ords-proxy&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=navikt_amt-arena-ords-proxy)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-arena-ords-proxy&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=navikt_amt-arena-ords-proxy)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=navikt_amt-arena-ords-proxy&metric=security_rating)](https://sonarcloud.io/dashboard?id=navikt_amt-arena-ords-proxy)

## SonarCloud
https://sonarcloud.io/dashboard?id=navikt_amt-arena-ords-proxy


## Setup K8s secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: amt-arena-ords-proxy
  namespace: amt
type: Opaque
data:
  ARENA_ORDS_CLIENT_ID: <base64>
  ARENA_ORDS_CLIENT_SECRET: <base64>
```
