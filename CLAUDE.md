# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

### CI Check (version policy + tests)
```bash
sbt clean scripted versionPolicyCheck
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

- **mojoz** — Metadata model (`TableDef`, `ViewDef`) and `ScalaDtoGenerator`, `DdlGenerator`
- **querease** — View compilation via `ViewCompiler` trait
- **tresql** — SQL query building; `TableMetadata` consumed by tresql at runtime
- Scala 2.12.21, Java 11 (enforced via `javacOptions`)
