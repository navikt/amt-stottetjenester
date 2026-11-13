# Reviewing amt-lib dependencies

The shared amt-lib code lives in `navikt/amt-deltakelser`, under `amt-lib`, not in the separate repository named `amt-lib`.

Before reviewing changes that use `no.nav.amt.lib.models`:

1. Read the `amt-lib` version in `gradle/libs.versions.toml`.
2. Inspect the matching sources in `.copilot-context/amt-deltakelser/amt-lib/models/src/main/kotlin`. The code review setup checks out the source commit embedded in that dependency version.
3. Confirm that the context checkout's `git rev-parse HEAD` starts with the source SHA at the end of the dependency version. Do not use `main` or assume a release tag points to the build-time commit.
4. Compare the imported types with the previous local definitions, including field names, types, nullability, defaults, Jackson annotations and JSON shape. Check nested DTOs and local mapper functions.
5. Keep HTTP contracts separate from Kafka payloads and domain behavior. Identical field shapes do not imply identical business rules.

The context checkout is reference material, not application code. Do not edit it, commit it or add it as a Gradle project dependency.

If the directory is missing or its revision does not match, inspect the review setup logs and report that library compatibility could not be verified. Do not infer that a type is missing from amt-lib because its source is outside this repository.
