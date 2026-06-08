# ADR-0002: Single Gradle project with Spring Modulith package modules

- Status: **Accepted**
- Date: 2026-05-28
- Relates to: ADR-0001

## Context

The spec calls for a "Gradle multi-module project" with each Modulith
module potentially being a separate Gradle subproject. We have to choose between:

1. **Single Gradle project, packages = Modulith modules** — idiomatic Modulith.
2. **Gradle subproject per Modulith module** — literal reading of the spec.

## Decision

Option 1: single Gradle project, modules expressed as packages under
`com.rightcrowd.sopstore.<module>`, with `package-info.java` annotated
`@org.springframework.modulith.ApplicationModule(...)`. ArchUnit + Modulith's
own verifier enforce boundaries.

## Rationale

- Modulith already enforces module boundaries at the package level; adding
  Gradle subprojects layers a second, weaker enforcement mechanism on top.
- Subproject-per-module roughly doubles build configuration (one
  `build.gradle.kts` per module, repeated convention plugin wiring) for zero
  isolation gain that Modulith doesn't already give us.
- Inter-module **events** are the contract; Gradle dependency arrows do not
  add information beyond what Modulith's `@NamedInterface` already encodes.
- Single project keeps the build graph shallow → faster incremental builds and
  faster test cycles than N subprojects.

## Consequences

- Future split into separate deployables remains possible: any Modulith module
  can be extracted into its own jar without touching consumer code, because
  cross-module access already goes through `@NamedInterface` types.
- Developers reading the spec literally may be surprised; this ADR is the
  documented deviation.
