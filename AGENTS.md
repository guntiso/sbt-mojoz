# AGENTS.md

This file provides guidance to coding agents when working with this repository.

## Project Overview

`sbt-mojoz` is an SBT plugin that provides code generation and metadata management for the Mojoz framework. It generates Scala DTOs and compiles database views/queries from YAML metadata definitions.

## Commands

### Build
```bash
sbt clean compile
```

### Run All Tests
Tests use SBT's scripted plugin test framework:
```bash
sbt clean scripted
```

### Run a Single Test
```bash
sbt 'scripted sbt-mojoz-simple-test-cases/compile'
```
Replace `compile` with any test case name from `src/sbt-test/sbt-mojoz-simple-test-cases/`.

### CI Check (both sbt lines + version policy)
Matches `.github/workflows/ci.yaml`:
```bash
sbt "++2.12.21; clean; scripted; ++3.8.4; scripted; +versionPolicyCheck"
```

### Publish Locally (for testing in other projects)
```bash
sbt publishLocal
```

## Architecture

The plugin is implemented as three composable SBT plugins in `src/main/scala/`:

### Plugin Hierarchy

1. **`MojozTableMetadataPlugin`** — Base plugin. Loads YAML table metadata from configurable folders (`tables/`, etc.), applies naming conventions and type definitions, and produces `mojozTableMetadata` (a `TableMetadata` instance). Must be enabled before the other two plugins.

2. **`MojozPlugin`** — Requires `MojozTableMetadataPlugin`. The main plugin. Reads view/job/route YAML metadata, compiles views via Querease's `ViewCompiler`, and generates `Dtos.scala` (with DTO case classes, `Tables` inner object, and `DtoMapping`). Registers source and resource generators in the `Compile` scope.

3. **`MojozGenerateSchemaPlugin`** — Requires `MojozTableMetadataPlugin`. Optional plugin that generates SQL DDL files per database using `DdlGenerator` from the mojoz library.

### Key Task/Setting Keys (MojozPlugin)

| Key | Purpose |
|-----|---------|
| `mojozTableMetadataFolders` | Folders containing table YAML files |
| `mojozViewMetadataFolders` | Folders containing view/job/route YAML files |
| `mojozShouldCompileViews` | Toggle view compilation (default: true) |
| `mojozShouldGenerateDtos` | Toggle DTO generation (default: true) |
| `mojozShouldGenerateMdFileList` | Toggle generation of `-md-files.txt` (default: true) |
| `mojozScalaGenerator` | Customizable `ScalaDtoGenerator` instance |
| `mojozQuerease` | Customizable `Querease` (with `ViewCompiler`) instance |
| `mojozCompileViews` | Task that compiles views and returns compiled view defs |
| `mojozGenerateDtosScala` | Task that writes `Dtos.scala` |

### Generated Artifacts
- `Dtos.scala` — Scala DTO case classes derived from view metadata
- `tresql-table-metadata.yaml` — Table metadata for TreSQL compiler
- `db-schema.sql` / `db-schema-{db}.sql` — SQL DDL files (MojozGenerateSchemaPlugin)
- `-md-files.txt` — List of all metadata files (for classpath packaging)

### Testing Approach

Tests live in `src/sbt-test/sbt-mojoz-simple-test-cases/`, one subdirectory per test case. Each test is a minimal SBT project with a `test` script using sbt scripted assertions (`>` for SBT commands, `$` for file/process checks). Test cases cover: basic DTO generation, resource packaging, SQL schema generation, query compiler caching, feature toggles, and concurrency (race conditions).

## Key Dependencies

- **mojoz** 7.2.1 — Metadata model (`TableDef`, `ViewDef`) and `ScalaDtoGenerator`, `DdlGenerator`
- **querease** 10.2.1 — View compilation via `ViewCompiler` trait
- **tresql** 13.5.1 — SQL query building; `TableMetadata` consumed by tresql at runtime
- **sbt2-compat** 0.2.0 — Shared API for the sbt 1 / sbt 2 plugin cross-build
- **sbt-version-policy** 3.3.0 — Binary-compatibility check (`versionPolicyCheck`)

## Toolchain

- **Java 17** is required to *build* this project (enforced in `initialize`)
- Cross-built for **sbt 1.13.0** (Scala 2.12.21, JVM 8 bytecode via `-release 8`) and **sbt 2.0.7** (Scala 3.8.4, JVM 17 bytecode)
- CI runs on **ubuntu-24.04** with Temurin 17 (`actions/checkout@v7`, `actions/setup-java@v5`, `sbt/setup-sbt@v1`)
