# Plan for enklere Kafka-konfigurasjon

## Mål og avgrensning

Gjør det mulig å finne klientoppsett, topic-koblinger og nedstengingsrekkefølge på samme måte i appene. Standardiser navn og ansvarsdeling, men behold forskjellene mellom direkte publisering, outbox og Spring Kafka.

Hoveddelen er et forslag til senere refaktorering. Gjennomfør navneendringer og oppdeling av konfigurasjonsfiler i små PR-er etter at livssyklusendringene er merget.

Disse avgrensede oppryddingene er tatt inn i livssyklusarbeidet:

- Spring-administrerte producere opprettes med builder og eksplisitt `.withShutdownHookEnabled(false)`.
- `KafkaMessageSender` injiserer den eksisterende byte-produceren. Hjelperen oppretter eller lukker ikke en egen producer.
- Tiltak-appens `KafkaBeans` og `KafkaConfiguration` bruker `proxyBeanMethods = false`. Bean-metodene kaller ikke hverandre.

Ikke endre consumer group-ID-er, client-ID-er, topics, meldingsformat eller serialisering. Behold acknowledgement, offset-håndtering, retry og transaksjonsgrenser. Nye avhengigheter, databaseendringer og endringer i auth, Nais eller CI/CD inngår ikke.

## Dagens løsning

| App | Konsumering | Publisering | Det som bør ryddes |
|---|---|---|---|
| amt-enhetsregister | Ingen | Direkte common Kafka-producer | Gi konfigurasjonsklassen samme navnekonvensjon som de andre |
| amt-arena-acl | To common Kafka-klienter | Direkte common Kafka-producer | Skill properties og topic-koblinger fra klientopprettelse. Gi livssyklusklassen et beskrivende navn |
| amt-tiltak | Common Kafka-klient og prosessor for feilede records | Direkte byte- og string-producere | Flytt topic-koblinger ut av konfigurasjonsklassens konstruktør. Samle opprettelsen av Kafka-komponenter etter ansvar |
| amt-person-service | Common Kafka-klient og prosessor for feilede records | Common Kafka-outbox | Skill consumer- og outbox-oppsett. Flytt storage-konfigurasjonen til egen fil |
| amt-arrangor | Spring Kafka med `@KafkaListener` og manuell acknowledgement | Common Kafka-outbox | Bruk navn som viser forskjellen mellom listener-konfigurasjon og outbox-konfigurasjon |

amt-altinn-acl og amt-arena-ords-proxy har ingen Kafka-konfigurasjon i applikasjonskoden og omfattes ikke.

Utgangspunktet finnes i disse filene:

- [amt-enhetsregister/KafkaConfig.kt](../amt-enhetsregister/src/main/kotlin/no/nav/enhetsregister/kafka/KafkaConfig.kt)
- [amt-arena-acl/KafkaConfiguration.kt](../amt-arena-acl/src/main/kotlin/no/nav/amt/arena/acl/kafka/KafkaConfiguration.kt)
- [amt-tiltak/KafkaConfiguration.kt](../amt-tiltak/kafka/kafka-config/src/main/kotlin/no/nav/amt/tiltak/kafka/config/KafkaConfiguration.kt)
- [amt-person-service/KafkaConfiguration.kt](../amt-person-service/src/main/kotlin/no/nav/amt/person/service/kafka/config/KafkaConfiguration.kt)
- [amt-arrangor/KafkaConfig.kt](../amt-arrangor/src/main/kotlin/no/nav/arrangor/kafka/config/KafkaConfig.kt)
- [amt-arrangor/KafkaOutboxProcessorConfig.kt](../amt-arrangor/src/main/kotlin/no/nav/arrangor/kafka/config/KafkaOutboxProcessorConfig.kt)

## Felles konvensjon

### Navn og ansvar

Bruk suffikset `Configuration` på konfigurasjonsklasser. Unngå `KafkaBeans`, som ikke forteller hvilke beans filen inneholder.

| Fil eller klasse | Ansvar | Når den trengs |
|---|---|---|
| `KafkaConfiguration` | Samlet oppsett for en liten Kafka-integrasjon | amt-enhetsregister kan beholde properties og producer i én fil |
| `KafkaPropertiesConfiguration` | Properties og miljøavhengige varianter | Når properties ellers skjuler klientoppsettet |
| `KafkaTopicConfiguration` | Topic, deserialisering og kobling til meldingsbehandler | For større common Kafka-consumere |
| `KafkaConsumerConfiguration` | Consumer-klienter, consumer-repository og prosessor for feilede records. I arrangor: Spring Kafka-fabrikker | Appene som konsumerer |
| `KafkaProducerConfiguration` | Direkte producer-beans | Appene som publiserer uten common Kafka-outbox |
| `KafkaOutboxConfiguration` | Outbox-producer, leader election og producer-record-prosessor | amt-person-service og amt-arrangor |
| `KafkaOutboxStorageConfiguration` | Outbox-repository og record storage | amt-person-service og amt-arrangor |
| `KafkaLifecycle` | Oppstart og ordnet avslutning av common Kafka-arbeidere | amt-arena-acl, amt-tiltak og amt-person-service |
| `KafkaOutboxLifecycle` | Livssyklus for outbox-prosessoren, separat fra Spring Kafka-listenerne | amt-arrangor |

Opprett bare filene appen trenger. Ikke legg inn tomme lag for å få identiske mappestrukturer. Behold eksisterende pakker og Gradle-moduler i første omgang.

### Opprettelse av beans

- Bruk `KafkaProducerClientBuilder` for Spring-administrerte common Kafka-producere. Skriv `.withShutdownHookEnabled(false)` eksplisitt fremfor `KafkaProducerClientImpl(properties, false)`.
- Behold `.withMetrics(...)` der det allerede brukes. Ikke innfør nye metrikker som en skjult følge av å bytte builder.
- Bruk `@Configuration(proxyBeanMethods = false)` og injiser avhengigheter gjennom bean-metodenes parametere. Ikke kall andre `@Bean`-metoder direkte.
- Gi repositoryer og topic-konfigurasjoner eksplisitte beans når de deles. Consumer-klienten og prosessoren for feilede records skal bruke samme topic-oppsett og lagringsgrunnlag.
- Behold bean-navn og qualifiers, særlig `stringKafkaProducer`, `kafkaOutboxProducer` og arena-appens to consumer-klienter. Oppdater eksplisitte importer i tester ved klassenavnendringer.

Flytting av beans skal bevare gjeldende profiler og betingelser. Ikke innfør et nytt `kafka.enabled`-flagg i enhetsregister som del av oppryddingen. Outbox-storage i person-service og arrangor ligger i dag utenfor den betingede processor-konfigurasjonen. Behold dette skillet, slik at lagring ikke forsvinner når Kafka-arbeiderne er deaktivert.

### Livssyklus og eierskap

Spring eier klienter og prosessorer. Komponentenes egne JVM-hooks skal være deaktivert. Vanlige producer- og consumer-beans bruker `destroyMethod = "close"`, mens producer-record-prosessorer bruker `destroyMethod = "closeAndAwait"`.

`SmartLifecycle` starter og stopper bakgrunnsarbeid før bean-destruksjon. Destroy-metodene er fortsatt nødvendige for opprydding når en komponent aldri ble startet. En direkte producer uten egen applikasjonsstyrt arbeidsløkke trenger ikke en egen `SmartLifecycle`.

Behold denne nedstengingen:

| App | Rekkefølge i livssyklusen |
|---|---|
| amt-arena-acl | Lukk begge consumer-klientene før Spring destruerer deres avhengigheter |
| amt-tiltak | Consumer-klient `close()`, deretter consumer-record-prosessor `close()` |
| amt-person-service | Consumer-klient `close()`, consumer-record-prosessor `close()`, deretter producer-record-prosessor `closeAndAwait()` |
| amt-arrangor | Behold Spring Kafka-listenernes livssyklus og den separate outbox-livssyklusen. Dokumenter og test deres relative Spring-faser før konfigurasjonen flyttes |
| amt-enhetsregister | Spring lukker produceren under bean-destruksjon |

Behold feilhåndteringen som forsøker øvrig opprydding ved lukkefeil, og callback-håndteringen i livssyklusene som har dette. Ikke endre timeout, faseverdier eller restartkontrakt i en strukturell opprydding. Standardfristen for common consumer-`close()` og producer-prosessorens `closeAndAwait()` er 10 sekunder per kall. Ved tidsavbrudd fortsetter arbeidet i bakgrunnen.

**Rød sone: forstå livssyklusrekkefølgen, ventingen og transaksjonsgrensene grundig før kode flyttes.** En uønsket eksisterende rekkefølge må eventuelt rettes som en eksplisitt atferdsendring med egne tester, ikke skjules i navneendringer.

## Gjennomføring

### 1. Lås dagens oppførsel

Kartlegg effektive properties, aktive beans og ressursavhengigheter per app. Registrer hvilke varianter som gjelder for `default`, `local` og eksisterende testprofiler. Ta med `kafka.enabled` satt til `true`, `false` og utelatt der flagget støttes.

Bruk syntetiske verdier i tester. Ikke lagre faktiske credentials eller komplette runtime-properties i testdata eller dokumentasjon.

Utvid eksisterende karakteriseringstester før flytting dersom profildekning eller bean-koblinger mangler. Sjekk både normal context-avslutning og avslutning uten forutgående start.

### 2. Bruk enhetsregister som liten pilot

Gi `KafkaConfig` navnet `KafkaConfiguration`. Behold producer-builderen med eksplisitt hook-opt-out og det korte oppsettet samlet i én fil. Kontroller at properties og ressurslukking er uendret før mønsteret brukes videre.

### 3. Rydd arena og tiltak

I arena flyttes miljøoppsettet til `KafkaPropertiesConfiguration` og topic-koblingene til `KafkaTopicConfiguration`. Opprett consumer-klientene i `KafkaConsumerConfiguration` og produceren i `KafkaProducerConfiguration`. Gi dagens livssyklusklasse `KafkaConsumer` navnet `KafkaLifecycle`.

I tiltak deles `KafkaBeans` etter samme ansvar. Flytt topic-listen ut av konstruktøren i `KafkaConfiguration`, og gjør consumer-repositoryet til en delt bean. Behold batchstørrelse, consumer group-ID-er og koblingen til alle ingestorene. Gi `KafkaConsumerLifecycle` navnet `KafkaLifecycle`.

Oppdater tiltak-appens integrasjonskonfigurasjon samtidig. Behold `KafkaMessageSender` sin injisering av den Spring-administrerte byte-produceren med qualifier `kafkaProducer`. Senderen skal ikke lukke den delte produceren.

### 4. Skill consumer og outbox i person-service

Behold `KafkaTopicConfiguration`, men flytt consumer-repositoryet til `KafkaConsumerConfiguration`. Del dagens `KafkaConfiguration` i consumer- og outbox-konfigurasjon. Flytt `KafkaOutboxStorageConfiguration` til egen fil.

Flytt properties fra `KafkaBeans` til `KafkaPropertiesConfiguration`, og producer-beanen til `KafkaOutboxConfiguration`. Gi `KafkaConsumerLifecycle` navnet `KafkaLifecycle`, siden den også styrer producer-prosessoren. Behold nåværende oppstarts- og nedstengingsrekkefølge.

### 5. Rydd arrangor uten å bytte Kafka-framework

Gi `KafkaConfig` navnet `KafkaConsumerConfiguration`, `KafkaOutboxProcessorConfig` navnet `KafkaOutboxConfiguration` og `KafkaOutboxStorageConfig` navnet `KafkaOutboxStorageConfiguration`. Gi handlerklassen `KafkaConsumer` et navn som `KafkaListeners`, slik at den ikke forveksles med livssykluskoordinering.

Behold `@KafkaListener`, container-fabrikkene, manuell acknowledgement og `KafkaErrorHandler`. Behold `KafkaOutboxLifecycle` som separat koordinator. Ikke kopier properties mellom Spring Kafka og common Kafka for å få oppsettene til å se like ut.

### 6. Vurder gjenværende duplisering

Sammenlign appene etter oppryddingen. Del bare hjelpere som har samme kontrakt og faktisk brukes flere steder. Ikke innfør en ny felles Kafka-modul, autokonfigurasjon eller generell livssyklusbaseklasse i denne planen. Noe lokal duplisering er lettere å forstå enn et nytt abstraksjonslag med unntak for hver app.

## Teststrategi

Bruk eksisterende JUnit-, Kotest-, MockK- og integrasjonstestoppsett. Alle nye og endrede tester skal ha norske navn og tydelige Arrange/Act/Assert-faser.

| Område | Hva skal dokumenteres med tester? |
|---|---|
| Properties | Effektive Kafka-verdier er like før og etter refaktoreringen, inkludert serialisering, group-ID-er, acknowledgement og offset-reset |
| Spring-koblinger | Riktige beans og qualifiers velges for hver støttet profil og `kafka.enabled`-variant, uten nye bean-overrides |
| Livssyklus | Oppstart én gang, avtalt lukkerekkefølge, opprydding uten start og fortsatt opprydding etter feil |
| Ressurseierskap | Hooks er deaktivert i bean-oppsettet. Test wiring uten refleksjon inn i bibliotekets private felt. Delte producere lukkes av eieren |
| Outbox og feilbehandling | Eksisterende transaksjons-, publisher- og replay-tester beholder forventningene sine |
| Integrasjon | Kafka og database virker sammen etter profil- og filflytting. Producer-oppsettet virker også uten metrics-wrapper i appene som ikke bruker den |

Start med de berørte appenes konfigurasjons- og livssyklustester. Eksisterende målrettede regresjonstester kan kjøres samlet:

```bash
./gradlew \
  :amt-arena-acl:test --tests '*KafkaConsumerLifecycleTest' \
  :amt-person-service:test --tests '*KafkaLifecycleTest' --tests '*KafkaProducerRecordPublisherTest' \
  :amt-arrangor:test --tests '*KafkaOutboxLifecycleTest' --tests '*MetricsKafkaProducerRecordPublisherTest' \
  :amt-tiltak:test-integration:test --tests '*KafkaConsumerLifecycleTest'
```

Oppdater testselektorene hvis testklassene får nye navn. Kompiler også enhetsregister og tiltak-modulene som bruker producer-beans. Kjør eksisterende Kotlin-lint der det er konfigurert, og repoets ordinære build før merge. Docker-baserte Kafka- og databasetester må kjøres i et miljø med Docker; mockbaserte livssyklustester erstatter ikke disse.

## Utrulling og rollback

Lever én app per PR når praktisk mulig. Ikke bland strukturendringer med dependency-oppgraderinger. Bruk eksisterende CI/CD og deploy til dev før prod. Ikke endre Nais-manifest eller legg til feature toggles for rene fil- og navneendringer.

Før hver deploy velges forrige fungerende image som rollback-mål. Ved regresjon deployes dette imaget på nytt. Ingen schema- eller topic-migrasjon skal være nødvendig. Reverter deretter den aktuelle refaktorerings-PR-en før neste release; ikke slett Kafka-offsets, feilede records eller outbox-rader.

Bruk eksisterende dashboards og logger til å sammenligne consumer-lag, publiseringsfeil og køutvikling mot normal drift før deploy. For appene med outbox eller lagrede feilede records følges også backloggen. Nye vedvarende feil, manglende konsumering eller vedvarende køvekst utløser feilsøking og vurdering av rollback.

### Sjekkliste etter deploy

- [ ] Appen starter med forventede Kafka-beans og uten bean-konflikter.
- [ ] Consumerne bruker uendrede group-ID-er og mottar forventede topics.
- [ ] Publisering virker, og eksisterende feilkøer og outbox fortsetter å behandles.
- [ ] En kontrollert restart i dev fullfører normal nedstenging uten nye tidsavbrudd eller tilgang til lukkede avhengigheter.
- [ ] Eksisterende metrics er fortsatt tilgjengelige med samme navn og tags.
- [ ] Tilsvarende signaler er normale etter deploy til prod.

## Ferdig når

Konfigurasjonen har navn som viser ansvar, og relevante appforskjeller er synlige uten å følge bean-opprettelse gjennom flere lag. Ingen app har fått en ny Kafka-stack eller endret meldingsbehandling. Profiltester og livssyklustester dokumenterer at bean-valg og ressursrekkefølge er bevart.

Behold denne planen som beslutningsgrunnlag under arbeidet. Oppdater den med faktiske avvik når hver app er ryddet, fremfor å markere planlagte endringer som gjennomført på forhånd.
