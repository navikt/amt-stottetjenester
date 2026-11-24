# Reviewing amt-lib dependencies

The shared amt-lib code lives in `navikt/amt-deltakelser`, under `amt-lib`, not in the separate repository named `amt-lib`.

Before reviewing changes that use `no.nav.amt.lib.models`:

1. Read the `amt-lib` version in `gradle/libs.versions.toml`.
2. Read `.copilot-context/amt-lib-provenance.json`. Confirm that `repository` is `navikt/amt-deltakelser`, `dependency_version` matches the catalog and `source_commit` is a full commit SHA starting with the source SHA at the end of that version. The setup writes this file only after verifying the checkout's HEAD and source directory.
3. Inspect the matching sources in the provenance file's `source_directory`, normally `.copilot-context/amt-deltakelser/amt-lib/models/src/main/kotlin`. Use the provenance as setup-verified revision evidence. Git commands, GitHub API calls and setup logs are optional supplementary checks, not prerequisites for reviewing these local sources. Do not use `main` or assume a release tag points to the build-time commit.
4. Compare the imported types with the previous local definitions, including field names, types, nullability, defaults, Jackson annotations and JSON shape. Check nested DTOs and local mapper functions.
5. Keep HTTP contracts separate from Kafka payloads and domain behavior. Identical field shapes do not imply identical business rules.

The context checkout and provenance file are reference material, not application code. Do not edit them, commit them or add the checkout as a Gradle project dependency.

Report missing sources, missing or mismatched provenance, and unavailable remote verification as separate limitations. If provenance is missing or does not match, do not claim to have verified the pinned revision. Readable sources may still support field-level observations, with that revision limitation stated.

If provenance matches and the sources are readable, do not report the checkout as unavailable because Git commands, remote APIs or setup logs are inaccessible. Source inspection is not execution of serialization tests; report unexecuted tests separately. Do not infer that a type is missing from amt-lib because its source is outside this repository.

# Reviewing nav-common dependencies

Before reviewing changes that use `no.nav.common.kafka` or `no.nav.common.job`:

1. Read the `nav-common` version in `gradle/libs.versions.toml`.
2. Read `.copilot-context/nav-common-provenance.json`. Confirm that `repository` is `navikt/common-java-modules`, `dependency_version` matches the catalog and `source_commit` is a full commit SHA starting with the version's SHA suffix. The setup writes this file only after verifying the checkout's HEAD and both source directories.
3. Inspect the paths in `source_directories`: `kafka` points to `.copilot-context/common-java-modules/kafka/src/main/java` and `job` to `.copilot-context/common-java-modules/job/src/main/java`. Inspect the called implementations, including outbox persistence, transaction participation, delivery guarantees, leader election and processor startup/shutdown where relevant. Do not substitute `main` for the pinned commit.

The checkout and provenance are reference material. Do not edit or commit them, or add them as Gradle project dependencies. Matching provenance and readable sources are sufficient for local source inspection; unavailable Git commands, remote APIs or setup logs do not make those sources unavailable. Report missing sources, mismatched provenance and unavailable remote verification separately. A failed checkout or a previous review limitation does not establish that sources are unpublished; an exact-version sources JAR is another source of evidence.

Source inspection does not execute PostgreSQL/Kafka integration tests. Report test execution separately, and do not describe a `FROM-CACHE` test task as fresh execution. If fresh execution is needed, rerun the relevant test task with `--no-build-cache --rerun-tasks`.
